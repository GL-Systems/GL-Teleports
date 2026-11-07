package org.glstudio.teleports.modules.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.config.MultiServerSettings;
import org.glstudio.teleports.common.storage.redis.PlayerRegistry;
import org.glstudio.teleports.core.ReloadService;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.command.home.HomeAdmin;
import org.glstudio.teleports.modules.manager.CrossServerManager;
import org.glstudio.teleports.modules.migration.MigrationSelection;
import org.glstudio.teleports.modules.model.DestinationLock;
import org.glstudio.teleports.modules.model.TeleportRecord;
import org.glstudio.teleports.modules.model.TeleportType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * The admin command {@code /glteleports}.
 */
public class GLTeleportsCommand extends Command {
    private final GTeleports plugin;

    /**
     * Creates the command.
     */
    public GLTeleportsCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.glteleports"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> ifPermitted(sender, Permissions.ADMIN_RELOAD, () -> new ReloadService(plugin).reload(sender));
            case "backup" -> ifPermitted(sender, Permissions.ADMIN_BACKUP,
                    () -> plugin.getBackupService().backupNow(sender));
            case "status" -> ifPermitted(sender, Permissions.ADMIN_RELOAD, () -> status(sender));
            case "debug" -> ifPermitted(sender, Permissions.ADMIN_RELOAD, () -> debug(sender, args));
            case "history" -> ifPermitted(sender, Permissions.ADMIN_HISTORY, () -> history(sender, args));
            case "lock" -> ifPermitted(sender, Permissions.ADMIN_LOCK, () -> lock(sender, args));
            case "unlock" -> ifPermitted(sender, Permissions.ADMIN_LOCK, () -> unlock(sender, args));
            case "locks" -> ifPermitted(sender, Permissions.ADMIN_LOCK, () -> locks(sender));
            case "migrate" -> ifPermitted(sender, Permissions.ADMIN_MIGRATE, () -> migrate(sender, args));
            case "version" -> plugin.getMessages().send(sender, "status.version",
                    "{version}", plugin.getPluginMeta().getVersion());
            default -> sendUsage(sender);
        }
    }

    private void history(CommandSender sender, String[] args) {
        if (!plugin.getHistoryManager().isEnabled()) {
            plugin.getMessages().send(sender, "errors.history-disabled");
            return;
        }

        if (args.length < 2) {
            plugin.getMessages().send(sender, "errors.specify-player");
            return;
        }

        boolean byWarp = args[1].equalsIgnoreCase("warp");

        if (byWarp && args.length < 3) {
            plugin.getMessages().send(sender, "errors.specify-warp");
            return;
        }

        String subject = byWarp ? args[2] : args[1];
        int limit = parseLimit(args, byWarp ? 3 : 2);

        if (byWarp) {
            show(sender, subject, plugin.getHistoryManager().warpHistory(subject, limit));
            return;
        }

        Futures.guard(HomeAdmin.resolve(plugin, subject).thenAccept(owner -> {
            if (owner == null) {
                plugin.getMessages().send(sender, "errors.player-never-played", "{player}", subject);
                return;
            }

            show(sender, owner.name(), plugin.getHistoryManager().history(owner.uuid(), limit));
        }), "GLTeleportsCommand#history", sender);
    }

    private int parseLimit(String[] args, int index) {
        if (args.length <= index) {
            return 10;
        }
        try {
            return Math.max(1, Math.min(50, Integer.parseInt(args[index])));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    private void show(CommandSender sender, String subject,
                      java.util.concurrent.CompletableFuture<List<TeleportRecord>> lookup) {
        Futures.guard(lookup.thenAccept(records -> {
            if (records.isEmpty()) {
                plugin.getMessages().send(sender, "history.empty", "{subject}", subject);
                return;
            }

            plugin.getMessages().send(sender, "history.header",
                    "{subject}", subject, "{count}", String.valueOf(records.size()));

            SimpleDateFormat format = new SimpleDateFormat("dd/MM HH:mm");

            for (TeleportRecord record : records) {
                plugin.getMessages().send(sender,
                        record.crossedServers() ? "history.entry-cross" : "history.entry",
                        "{date}", format.format(new Date(record.timestamp())),
                        "{player}", record.playerName() == null ? "?" : record.playerName(),
                        "{type}", record.type().getConfigKey(),
                        "{destination}", record.destination() == null ? "-" : record.destination(),
                        "{from}", describe(record.fromWorld(), record.fromX(), record.fromY(), record.fromZ()),
                        "{to}", describe(record.toWorld(), record.toX(), record.toY(), record.toZ()),
                        "{from-server}", String.valueOf(record.fromServer()),
                        "{to-server}", String.valueOf(record.toServer()),
                        "{result}", record.result() == null ? "?" : record.result());
            }
        }), "GLTeleportsCommand#showHistory", sender);
    }

    private static String describe(String world, double x, double y, double z) {
        if (world == null) {
            return "-";
        }
        return world + " " + Math.round(x) + "," + Math.round(y) + "," + Math.round(z);
    }

    private void lock(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessages().send(sender, "errors.specify-lock-target");
            return;
        }

        String type = args[1].toLowerCase(java.util.Locale.ROOT);

        if (!type.equals(DestinationLock.WARP) && !type.equals(DestinationLock.SERVER)) {
            plugin.getMessages().send(sender, "errors.specify-lock-target");
            return;
        }

        String reason = args.length > 3
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length))
                : plugin.getMessages().raw("lock.default-reason");

        plugin.getLockManager().lock(sender, type, args[2], reason, DestinationLock.INDEFINITE);
    }

    private void unlock(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessages().send(sender, "errors.specify-lock-target");
            return;
        }

        plugin.getLockManager().unlock(sender, args[1], args[2]);
    }

    private void locks(CommandSender sender) {
        List<DestinationLock> active = plugin.getLockManager().active();

        if (active.isEmpty()) {
            plugin.getMessages().send(sender, "lock.none");
            return;
        }

        plugin.getMessages().send(sender, "lock.header", "{count}", String.valueOf(active.size()));

        for (DestinationLock entry : active) {
            plugin.getMessages().send(sender, "lock.entry",
                    "{type}", entry.type(),
                    "{target}", entry.target(),
                    "{reason}", entry.reason() == null ? "-" : entry.reason(),
                    "{by}", entry.lockedBy() == null ? "?" : entry.lockedBy());
        }
    }

    private void migrate(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 4) {
            plugin.getMessages().send(sender, "migrate.usage",
                    "{sources}", String.join(", ", plugin.getMigrationManager().sourceIds()));
            return;
        }

        boolean confirm = args[args.length - 1].equalsIgnoreCase("confirm");
        if (args.length == 4 && !confirm) {
            plugin.getMessages().send(sender, "migrate.usage",
                    "{sources}", String.join(", ", plugin.getMigrationManager().sourceIds()));
            return;
        }

        String selector = args.length == 2 || (args.length == 3 && confirm) ? "all" : args[2];
        MigrationSelection selection = MigrationSelection.parse(selector);
        if (selection == null) {
            plugin.getMessages().send(sender, "migrate.errors.invalid-selection", "{selection}", selector);
            return;
        }

        if (confirm) {
            plugin.getMigrationManager().confirm(sender, args[1], selection);
        } else {
            plugin.getMigrationManager().scan(sender, args[1], selection);
        }
    }

    private void ifPermitted(CommandSender sender, String permission, Runnable action) {
        if (!sender.hasPermission(permission)) {
            plugin.getMessages().send(sender, "errors.no-permission");
            return;
        }
        action.run();
    }

    private void debug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessages().send(sender, "errors.specify-player");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.getMessages().send(sender, "errors.player-offline");
            return;
        }

        plugin.getMessages().send(sender, "status.debug-header", "{player}", target.getName());
        plugin.getMessages().send(sender, "status.debug-homes",
                "{count}", String.valueOf(plugin.getHomeManager().getCachedHomes(target.getUniqueId()).size()),
                "{limit}", String.valueOf(plugin.getHomeManager().getHomeLimit(target)));
        plugin.getMessages().send(sender, "status.debug-delay",
                "{active}", String.valueOf(plugin.getDelayManager().hasDelay(target)));
        plugin.getMessages().send(sender, "status.debug-immunity",
                "{active}", String.valueOf(plugin.getTeleportManager().hasImmunity(target)));
        plugin.getMessages().send(sender, "status.debug-requests",
                "{incoming}", String.valueOf(plugin.getTpaManager().pendingRequesterNames(target).size()),
                "{outgoing}", String.valueOf(plugin.getTpaManager().outgoingTargetNames(target).size()));

        for (TeleportType type : TeleportType.values()) {
            long remaining = plugin.getCooldownManager().getRemainingCooldown(target, type);
            if (remaining > 0) {
                plugin.getMessages().send(sender, "status.debug-cooldown",
                        "{type}", type.getConfigKey(), "{time}", String.valueOf(remaining));
            }
        }
    }

    private void status(CommandSender sender) {
        CrossServerManager crossServer = plugin.getCrossServerManager();
        MultiServerSettings settings = plugin.getMultiServerSettings();

        plugin.getMessages().send(sender, "status.header");

        plugin.getMessages().send(sender, "status.database",
                "{type}", plugin.getConfigManager().getDatabaseConfig().getString("type", "yaml"));

        if (!settings.isEnabled()) {
            plugin.getMessages().send(sender, "status.bus-disabled");
            return;
        }

        plugin.getMessages().send(sender, "status.server", "{server}", crossServer.currentServer());

        if (!crossServer.isEnabled()) {
            plugin.getMessages().send(sender, "status.bus-offline");
            return;
        }

        PlayerRegistry registry = crossServer.getPlayerRegistry();

        plugin.getMessages().send(sender, "status.bus-online", "{channel}", settings.getChannel());
        plugin.getMessages().send(sender, "status.servers",
                "{count}", String.valueOf(registry.knownServers().size()),
                "{servers}", String.join(", ", registry.knownServers()));
        plugin.getMessages().send(sender, "status.network-players",
                "{count}", String.valueOf(registry.networkPlayerNames().size()));
        plugin.getMessages().send(sender, "status.pending",
                "{count}", String.valueOf(crossServer.getPendingTeleports().size()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2 && (args[0].equalsIgnoreCase("debug") || args[0].equalsIgnoreCase("history"))) {
            return filterTabCompletion(getOnlinePlayerNames(), args[1]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("lock") || args[0].equalsIgnoreCase("unlock"))) {
            return filterTabCompletion(List.of(DestinationLock.WARP, DestinationLock.SERVER), args[1]);
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("lock") || args[0].equalsIgnoreCase("unlock"))) {
            if (args[1].equalsIgnoreCase(DestinationLock.WARP)) {
                return filterTabCompletion(plugin.getWarpManager().getCachedWarps().stream()
                        .map(org.glstudio.teleports.modules.model.Warp::getName).toList(), args[2]);
            }

            var registry = plugin.getCrossServerManager().getPlayerRegistry();
            return filterTabCompletion(registry == null ? List.of() : List.copyOf(registry.knownServers()), args[2]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("migrate")) {
            return filterTabCompletion(plugin.getMigrationManager().sourceIds(), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("migrate")) {
            return filterTabCompletion(List.of("all", "homes", "warps", "playerwarps", "confirm"), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("migrate")) {
            return filterTabCompletion(List.of("confirm"), args[3]);
        }

        if (args.length != 1) {
            return List.of();
        }

        return filterTabCompletion(
                List.of("reload", "backup", "status", "debug", "history", "lock", "unlock", "locks", "migrate",
                        "version"),
                args[0]);
    }
}
