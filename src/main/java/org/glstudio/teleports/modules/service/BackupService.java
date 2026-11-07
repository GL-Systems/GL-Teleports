package org.glstudio.teleports.modules.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.Warp;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Backs up the data on a schedule.
 */
public class BackupService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final GTeleports plugin;

    /**
     * Creates the service.
     */
    public BackupService(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the schedule.
     */
    public void schedule() {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("backups.enabled", true)) {
            return;
        }

        int intervalMinutes = Math.max(15,
                plugin.getConfigManager().getMainConfig().getInt("backups.interval-minutes", 360));
        long intervalTicks = intervalMinutes * 60L * 20L;

        Bukkit.getScheduler().runTaskTimer(plugin, () -> Futures.log(run(), "BackupService#scheduled"),
                20L * 60, intervalTicks);
    }

    /**
     * Makes a backup now.
     */
    public void backupNow(CommandSender sender) {
        plugin.getMessages().send(sender, "backup.started");

        run().whenComplete((path, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                LoggerUtils.logException("BackupService#backupNow", error);
                plugin.getMessages().send(sender, "backup.failed");
                return;
            }

            plugin.getMessages().send(sender, "backup.finished", "{file}", path.getFileName().toString());
        }));
    }

    private CompletableFuture<Path> run() {
        List<UUID> owners = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(player -> owners.add(player.getUniqueId()));

        CompletableFuture<List<Warp>> warps = plugin.getDatabaseManager().getProvider().getAllWarps();
        CompletableFuture<SpawnLocation> spawn = plugin.getDatabaseManager().getProvider().getSpawn();

        return warps.thenCombine(spawn, (warpList, spawnLocation) -> {
            Map<String, List<Home>> homes = new java.util.LinkedHashMap<>();

            for (UUID owner : owners) {
                homes.put(owner.toString(), plugin.getHomeManager().getCachedHomes(owner));
            }

            return write(new Snapshot(System.currentTimeMillis(),
                    plugin.getCrossServerManager().currentServer(), warpList, spawnLocation, homes));
        });
    }

    private Path write(Snapshot snapshot) {
        try {
            Path folder = plugin.getDataFolder().toPath().resolve("backups");
            Files.createDirectories(folder);

            Path target = folder.resolve("backup-" + LocalDateTime.now().format(STAMP) + ".json");

            try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot, writer);
            }

            prune(folder);
            return target;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write backup", e);
        }
    }

    private void prune(Path folder) throws IOException {
        int keep = Math.max(1, plugin.getConfigManager().getMainConfig().getInt("backups.keep", 10));

        try (Stream<Path> files = Files.list(folder)) {
            List<Path> ordered = files
                    .filter(path -> path.getFileName().toString().startsWith("backup-"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();

            for (int index = keep; index < ordered.size(); index++) {
                Files.deleteIfExists(ordered.get(index));
            }
        }
    }

    private record Snapshot(long takenAt, String server, List<Warp> warps,
                            SpawnLocation spawn, Map<String, List<Home>> homes) {
    }
}
