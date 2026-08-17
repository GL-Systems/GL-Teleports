package org.glstudio.teleports.common.storage.redis;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitTask;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.MultiServerSettings;
import org.glstudio.teleports.core.TeleportExecutors;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PlayerRegistry {
    private static final long ENTRY_TTL_SECONDS = 90L;
    private static final long HEARTBEAT_TICKS = 20L * 30;

    private static final long DIRECTORY_REFRESH_SECONDS = 20L;

    private final GTeleports plugin;
    private final RedisService redisService;
    private final TeleportExecutors executors;
    private final MultiServerSettings settings;

    private BukkitTask heartbeatTask;
    private ScheduledFuture<?> directoryTask;

    private volatile List<String> networkPlayers = List.of();

    private volatile Set<String> knownServers = Set.of();

    public PlayerRegistry(GTeleports plugin, RedisService redisService, TeleportExecutors executors,
                          MultiServerSettings settings) {
        this.plugin = plugin;
        this.redisService = redisService;
        this.executors = executors;
        this.settings = settings;
    }

    public void startHeartbeat() {
        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<NetworkPlayer> online = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isVanished(player)) {
                    continue;
                }
                online.add(new NetworkPlayer(player.getUniqueId(), player.getName(), settings.getServerName()));
            }

            publishRoster(null);

            if (online.isEmpty()) {
                return;
            }

            CompletableFuture.runAsync(() -> {
                for (NetworkPlayer player : online) {
                    write(player);
                }
            }, executors.io()).exceptionally(error -> {
                LoggerUtils.logWarn("Player registry heartbeat failed: " + error.getMessage());
                return null;
            });
        }, 20L, HEARTBEAT_TICKS);

        directoryTask = executors.scheduler().scheduleWithFixedDelay(
                this::refreshDirectory, DIRECTORY_REFRESH_SECONDS, DIRECTORY_REFRESH_SECONDS,
                TimeUnit.SECONDS);
    }

    public void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
        if (directoryTask != null) {
            directoryTask.cancel(false);
            directoryTask = null;
        }
        networkPlayers = List.of();
        knownServers = Set.of();

        try {
            List<String> keys = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                keys.add(uuidKey(player.getUniqueId()));
                keys.add(nameKey(player.getName()));
            }
            redisService.deleteAll(keys);

            redisService.delete(rosterKey(settings.getServerName()));
            redisService.removeFromSet(serversKey(), settings.getServerName());
        } catch (Exception e) {
            LoggerUtils.logDebug("PlayerRegistry", "Could not withdraw this server's roster: "
                    + e.getMessage());
        }
    }

    public void register(Player player) {
        if (isVanished(player)) {
            return;
        }

        NetworkPlayer entry = new NetworkPlayer(player.getUniqueId(), player.getName(), settings.getServerName());

        CompletableFuture.runAsync(() -> write(entry), executors.io()).exceptionally(error -> {
            LoggerUtils.logWarn("Could not register " + entry.getName() + " in the player registry: "
                    + error.getMessage());
            return null;
        });

        publishRoster(null);
        refreshSoon();
    }

    public void unregister(Player player) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        CompletableFuture.runAsync(
                        () -> {
                            redisService.deleteIfOwnedBy(uuidKey(uuid), settings.getServerName());
                            redisService.deleteIfValueIs(nameKey(playerName), uuid.toString());
                        },
                        executors.io())
                .exceptionally(error -> {
                    LoggerUtils.logDebug("PlayerRegistry", "Could not remove " + playerName + ": "
                            + error.getMessage());
                    return null;
                });

        publishRoster(playerName);
        refreshSoon();
    }

    private void refreshSoon() {
        executors.scheduler().schedule(this::refreshDirectory, 1, TimeUnit.SECONDS);
    }

    public CompletableFuture<NetworkPlayer> lookup(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String uuid = redisService.get(nameKey(playerName));
            if (uuid == null) {
                return null;
            }

            String raw = redisService.get(uuidKey(uuid));
            return raw == null ? null : parse(raw);
        }, executors.io()).exceptionally(error -> {
            LoggerUtils.logWarn("Player registry lookup for '" + playerName + "' failed: "
                    + error.getMessage());
            return null;
        });
    }

    public List<String> networkPlayerNames() {
        return networkPlayers;
    }

    public Set<String> knownServers() {
        return knownServers;
    }

    public boolean isKnownServer(String server) {
        Set<String> servers = knownServers;
        return servers.isEmpty() || settings.isLocalServer(server) || servers.contains(lower(server));
    }

    public void markServer(String server, boolean online) {
        if (server == null || server.isBlank() || settings.isLocalServer(server)) {
            return;
        }

        Set<String> updated = new LinkedHashSet<>(knownServers);
        updated.add(lower(settings.getServerName()));

        boolean changed = online ? updated.add(lower(server)) : updated.remove(lower(server));

        if (!changed) {
            return;
        }

        knownServers = Set.copyOf(updated);

        LoggerUtils.logDebug("PlayerRegistry", "Server '" + server + "' announced itself as "
                + (online ? "online" : "offline") + ".");

        if (online) {

            refreshSoon();
        }
    }

    private void publishRoster(String leaving) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (leaving != null && player.getName().equalsIgnoreCase(leaving)) {
                continue;
            }
            if (isVanished(player)) {
                continue;
            }
            names.add(player.getName());
        }

        String roster = String.join("\n", names);
        String server = settings.getServerName();

        CompletableFuture.runAsync(() -> {
            redisService.set(rosterKey(server), roster, ENTRY_TTL_SECONDS);
            redisService.addToSet(serversKey(), server);
        }, executors.io()).exceptionally(error -> {
            LoggerUtils.logWarn("Could not publish this server's roster; other servers will not "
                    + "complete its players: " + error.getMessage());
            return null;
        });
    }

    private void refreshDirectory() {
        try {
            Set<String> servers = redisService.membersOf(serversKey());
            List<String> others = new ArrayList<>();
            for (String server : servers) {
                if (!settings.isLocalServer(server)) {
                    others.add(server);
                }
            }

            Set<String> seen = new LinkedHashSet<>();
            seen.add(lower(settings.getServerName()));
            servers.forEach(server -> seen.add(lower(server)));
            knownServers = Set.copyOf(seen);

            if (others.isEmpty()) {
                networkPlayers = List.of();
                return;
            }

            List<String> keys = new ArrayList<>(others.size());
            for (String server : others) {
                keys.add(rosterKey(server));
            }

            List<String> rosters = redisService.getAll(keys);
            Set<String> names = new LinkedHashSet<>();
            List<String> lapsed = new ArrayList<>();

            for (int i = 0; i < others.size(); i++) {
                String roster = i < rosters.size() ? rosters.get(i) : null;
                if (roster == null) {
                    lapsed.add(others.get(i));
                    continue;
                }
                for (String name : roster.split("\n")) {
                    if (!name.isEmpty()) {
                        names.add(name);
                    }
                }
            }

            networkPlayers = List.copyOf(names);

            for (String server : lapsed) {
                redisService.removeFromSet(serversKey(), server);
                seen.remove(lower(server));
            }
            knownServers = Set.copyOf(seen);
        } catch (Exception e) {
            LoggerUtils.logWarn("Could not refresh the network player directory; tab completion "
                    + "will keep the previous names: " + e.getMessage());
        }
    }

    private boolean isVanished(Player player) {
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private void write(NetworkPlayer player) {
        redisService.set(uuidKey(player.getUuid()),
                player.getUuid() + ";" + player.getName() + ";" + player.getServer(),
                ENTRY_TTL_SECONDS);

        redisService.set(nameKey(player.getName()), player.getUuid().toString(), ENTRY_TTL_SECONDS);
    }

    private NetworkPlayer parse(String raw) {
        String[] parts = raw.split(";", 3);
        if (parts.length != 3) {
            return null;
        }
        try {
            return new NetworkPlayer(UUID.fromString(parts[0]), parts[1], parts[2]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String uuidKey(Object uuid) {
        return settings.getChannel() + ":player:" + uuid;
    }

    private String nameKey(String playerName) {
        return settings.getChannel() + ":name:" + lower(playerName);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String rosterKey(String server) {
        return settings.getChannel() + ":roster:" + server.toLowerCase(Locale.ROOT);
    }

    private String serversKey() {
        return settings.getChannel() + ":servers";
    }
}
