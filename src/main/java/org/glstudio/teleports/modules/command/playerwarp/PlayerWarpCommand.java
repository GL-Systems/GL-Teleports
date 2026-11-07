package org.glstudio.teleports.modules.command.playerwarp;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.nexus.utils.DurationParser;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.menu.playerwarp.PlayerWarpAdminMenu;
import org.glstudio.teleports.modules.menu.playerwarp.PlayerWarpMainMenu;
import org.glstudio.teleports.modules.menu.playerwarp.PlayerWarpManageMenu;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpBan;
import org.glstudio.teleports.modules.model.PlayerWarpCategory;
import org.glstudio.teleports.modules.model.PlayerWarpManagerEntry;
import org.glstudio.teleports.modules.model.PlayerWarpWhitelistEntry;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The player warp command and its sub-commands.
 */
public class PlayerWarpCommand extends Command {
    private static final int PAGE_SIZE = 8;
    private static final long INVALID_DURATION = -1L;

    private final GTeleports plugin;

    /**
     * Creates the command.
     */
    public PlayerWarpCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("playerwarp", "playerwarps", "pwarp");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.pw"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            openMain(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String actionPermission = ACTION_PERMISSIONS.get(sub);

        if (actionPermission != null && !requirePermission(sender, actionPermission)) {
            return;
        }

        switch (sub) {
            case "set" -> handleSet(sender, args);
            case "remove", "delete" -> handleRemove(sender, args);
            case "desc", "description" -> handleDesc(sender, args);
            case "list" -> handleList(sender, args);
            case "icon" -> handleIcon(sender, args);
            case "category" -> handleCategory(sender, args);
            case "rate" -> handleRate(sender, args);
            case "lock" -> handleLock(sender, args);
            case "cost" -> handleCost(sender, args);
            case "reset" -> handleReset(sender, args);
            case "rename" -> handleRename(sender, args);
            case "setowner" -> handleSetOwner(sender, args);
            case "password", "pass" -> handlePassword(sender, args);
            case "whitelist" -> handleWhitelist(sender, args);
            case "ban" -> handleBan(sender, args);
            case "favourite", "favorite", "fav" -> handleFavourite(sender, args);
            case "settings" -> handleSettings(sender, args);
            case "removeall" -> handleRemoveAll(sender, args);
            case "managers" -> handleManagers(sender, args);
            case "reload" -> handleReload(sender);
            case "admin" -> handleAdmin(sender);
            default -> handleTeleport(sender, args[0], args.length > 1 ? args[1] : null);
        }
    }

    // Per-action permission nodes (golden.teleports.playerwarp.*), checked before the matching
    // subcommand runs. Ownership/manager rights are still enforced separately, in the manager
    // layer - this only gates whether the sender may attempt the action at all. Subcommands not
    // listed here (removeall, managers, reload, admin) carry their own admin.* permission check
    // inside their handler instead, since those are staff-only regardless of rank.
    private static final Map<String, String> ACTION_PERMISSIONS = Map.ofEntries(
            Map.entry("set", Permissions.PLAYERWARP_SET),
            Map.entry("remove", Permissions.PLAYERWARP_REMOVE),
            Map.entry("delete", Permissions.PLAYERWARP_REMOVE),
            Map.entry("desc", Permissions.PLAYERWARP_DESC),
            Map.entry("description", Permissions.PLAYERWARP_DESC),
            Map.entry("list", Permissions.PLAYERWARP_LIST),
            Map.entry("icon", Permissions.PLAYERWARP_ICON),
            Map.entry("category", Permissions.PLAYERWARP_CATEGORY),
            Map.entry("rate", Permissions.PLAYERWARP_RATE),
            Map.entry("lock", Permissions.PLAYERWARP_LOCK),
            Map.entry("cost", Permissions.PLAYERWARP_COST),
            Map.entry("reset", Permissions.PLAYERWARP_RESET),
            Map.entry("rename", Permissions.PLAYERWARP_RENAME),
            Map.entry("setowner", Permissions.PLAYERWARP_SETOWNER),
            Map.entry("password", Permissions.PLAYERWARP_PASSWORD),
            Map.entry("pass", Permissions.PLAYERWARP_PASSWORD),
            Map.entry("whitelist", Permissions.PLAYERWARP_WHITELIST),
            Map.entry("ban", Permissions.PLAYERWARP_BAN),
            Map.entry("favourite", Permissions.PLAYERWARP_FAVOURITE),
            Map.entry("favorite", Permissions.PLAYERWARP_FAVOURITE),
            Map.entry("fav", Permissions.PLAYERWARP_FAVOURITE),
            Map.entry("settings", Permissions.PLAYERWARP_SETTINGS));

    // ── Navigation ──────────────────────────────────────────────────────────

    private void openMain(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }
        new PlayerWarpMainMenu(plugin, player).open();
    }

    private void handleTeleport(CommandSender sender, String warpName, String password) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }
        plugin.getPlayerWarpManager().attemptTeleport(player, warpName, password);
    }

    private void handleSettings(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }
        if (!requireArgs(sender, args, 2)) {
            return;
        }

        Futures.guard(plugin.getPlayerWarpManager().getWarpByName(args[1]).thenAccept(warp -> {
            if (warp == null) {
                plugin.getMessages().send(sender, "errors.player-warp-not-found", "{name}", args[1]);
                return;
            }

            plugin.getPlayerWarpManager().mayManage(warp, sender).thenAccept(allowed -> {
                if (!allowed) {
                    plugin.getMessages().send(sender, "errors.no-permission");
                    return;
                }
                org.bukkit.Bukkit.getScheduler().runTask(plugin,
                        () -> new PlayerWarpManageMenu(plugin, player, warp).open());
            });
        }), "PlayerWarpCommand#settings", sender);
    }

    private void handleAdmin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }
        if (!requirePermission(sender, Permissions.PLAYERWARP_ADMIN_VIEW)) {
            return;
        }
        new PlayerWarpAdminMenu(plugin, player).open();
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.getMessages().send(sender, "errors.no-permission");
        return false;
    }

    // ── set / remove ────────────────────────────────────────────────────────

    private void handleSet(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 2)) {
            return;
        }

        if (args.length >= 3) {
            if (!requirePermission(sender, Permissions.PLAYERWARP_ADMIN_REMOVEALL)) {
                return;
            }
            if (!(sender instanceof Player player)) {
                plugin.getMessages().send(sender, "errors.player-only");
                return;
            }

            Futures.guard(PlayerWarpAdmin.resolve(plugin, args[2]).thenAccept(target -> {
                if (target == null) {
                    plugin.getMessages().send(sender, "errors.player-not-found", "{player}", args[2]);
                    return;
                }
                plugin.getPlayerWarpManager().createWarpFor(sender, target, args[1], player.getLocation());
            }), "PlayerWarpCommand#setFor", sender);
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        plugin.getPlayerWarpManager().createWarp(player, args[1]);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 2)) {
            return;
        }
        plugin.getPlayerWarpManager().removeWarp(sender, args[1]);
    }

    private void handleRemoveAll(CommandSender sender, String[] args) {
        if (!requirePermission(sender, Permissions.PLAYERWARP_ADMIN_REMOVEALL)) {
            return;
        }
        if (!requireArgs(sender, args, 2)) {
            return;
        }

        Futures.guard(PlayerWarpAdmin.resolve(plugin, args[1]).thenAccept(target -> {
            if (target == null) {
                plugin.getMessages().send(sender, "errors.player-not-found", "{player}", args[1]);
                return;
            }
            plugin.getPlayerWarpManager().removeAllOf(sender, target.uuid(), target.name());
        }), "PlayerWarpCommand#removeall", sender);
    }

    // ── description / icon / category ──────────────────────────────────────

    private void handleDesc(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }

        if (args[1].equalsIgnoreCase("remove")) {
            plugin.getPlayerWarpManager().setDescription(sender, args[2], null);
            return;
        }

        if (!args[1].equalsIgnoreCase("set") || args.length < 4) {
            sendUsage(sender);
            return;
        }

        String description = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        plugin.getPlayerWarpManager().setDescription(sender, args[2], description);
    }

    private void handleIcon(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }

        if (args[1].equalsIgnoreCase("remove")) {
            plugin.getPlayerWarpManager().setIcon(sender, args[2], null);
            return;
        }

        if (!args[1].equalsIgnoreCase("set")) {
            sendUsage(sender);
            return;
        }

        // A material name may be given explicitly; without one, /pw icon set <warp> uses
        // whatever the player is currently holding, matching the spec's argument-less form.
        if (args.length >= 4) {
            plugin.getPlayerWarpManager().setIcon(sender, args[2], args[3]);
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        org.bukkit.Material held = player.getInventory().getItemInMainHand().getType();
        if (held == org.bukkit.Material.AIR) {
            plugin.getMessages().send(sender, "errors.player-warp-icon-empty-hand");
            return;
        }

        plugin.getPlayerWarpManager().setIcon(sender, args[2], held.name());
    }

    private void handleCategory(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 2)) {
            return;
        }

        if (args[1].equalsIgnoreCase("list")) {
            listCategories(sender);
            return;
        }

        if (!requireArgs(sender, args, 3)) {
            return;
        }

        if (args[1].equalsIgnoreCase("remove")) {
            plugin.getPlayerWarpManager().setCategory(sender, args[2], null);
            return;
        }

        if (!args[1].equalsIgnoreCase("set") || args.length < 4) {
            sendUsage(sender);
            return;
        }

        plugin.getPlayerWarpManager().setCategory(sender, args[2], args[3]);
    }

    private void listCategories(CommandSender sender) {
        List<PlayerWarpCategory> categories = plugin.getPlayerWarpCategoryRegistry().all();

        plugin.getMessages().send(sender, "player-warp.category.list-header",
                "{count}", String.valueOf(categories.size()));

        for (PlayerWarpCategory category : categories) {
            plugin.getMessages().send(sender, "player-warp.category.list-entry",
                    "{id}", category.id(), "{name}", category.displayName());
        }
    }

    // ── rate / lock / cost / reset / rename / setowner ─────────────────────

    private void handleRate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }
        if (!requireArgs(sender, args, 3)) {
            return;
        }

        int stars;
        try {
            stars = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendUsage(sender);
            return;
        }

        String comment = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "";

        Futures.guard(plugin.getPlayerWarpManager().getWarpByName(args[1]).thenAccept(warp -> {
            if (warp == null) {
                plugin.getMessages().send(sender, "errors.player-warp-not-found", "{name}", args[1]);
                return;
            }
            plugin.getPlayerWarpRatingManager().rate(player, warp, stars, comment);
        }), "PlayerWarpCommand#rate", sender);
    }

    private void handleLock(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 2)) {
            return;
        }

        if (args.length >= 3) {
            plugin.getPlayerWarpManager().setLocked(sender, args[1], Boolean.parseBoolean(args[2]));
            return;
        }

        Futures.guard(plugin.getPlayerWarpManager().getWarpByName(args[1]).thenAccept(warp -> {
            if (warp == null) {
                plugin.getMessages().send(sender, "errors.player-warp-not-found", "{name}", args[1]);
                return;
            }
            plugin.getPlayerWarpManager().setLocked(sender, args[1], !warp.isLocked());
        }), "PlayerWarpCommand#lock", sender);
    }

    private void handleCost(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }

        if (args[1].equalsIgnoreCase("remove")) {
            plugin.getPlayerWarpManager().setCost(sender, args[2], null);
            return;
        }

        if (!args[1].equalsIgnoreCase("set") || args.length < 4) {
            sendUsage(sender);
            return;
        }

        try {
            double cost = Double.parseDouble(args[3]);
            plugin.getPlayerWarpManager().setCost(sender, args[2], cost);
        } catch (NumberFormatException e) {
            plugin.getMessages().send(sender, "errors.invalid-amount", "{value}", args[3]);
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }
        if (!requireArgs(sender, args, 2)) {
            return;
        }
        plugin.getPlayerWarpManager().resetLocation(player, args[1]);
    }

    private void handleRename(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }
        plugin.getPlayerWarpManager().renameWarp(sender, args[1], args[2]);
    }

    private void handleSetOwner(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }
        plugin.getPlayerWarpManager().transferOwner(sender, args[1], args[2]);
    }

    // ── password ────────────────────────────────────────────────────────────

    private void handlePassword(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }

        if (args[1].equalsIgnoreCase("remove")) {
            plugin.getPlayerWarpManager().removePassword(sender, args[2]);
            return;
        }

        if (!args[1].equalsIgnoreCase("set") || args.length < 4) {
            sendUsage(sender);
            return;
        }

        plugin.getPlayerWarpManager().setPassword(sender, args[2], args[3]);
    }

    // ── whitelist ───────────────────────────────────────────────────────────

    private void handleWhitelist(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        String warpName = args[2];

        switch (action) {
            case "set" -> {
                if (args.length < 4) {
                    sendUsage(sender);
                    return;
                }
                withWarp(sender, warpName, warp ->
                        plugin.getPlayerWarpWhitelistManager().add(sender, warp, args[3]));
            }
            case "remove" -> {
                if (args.length < 4) {
                    sendUsage(sender);
                    return;
                }
                withWarp(sender, warpName, warp ->
                        plugin.getPlayerWarpWhitelistManager().remove(sender, warp, args[3]));
            }
            case "list" -> Futures.guard(plugin.getPlayerWarpManager().getWarpByName(warpName)
                    .thenCompose(warp -> {
                        if (warp == null) {
                            plugin.getMessages().send(sender, "errors.player-warp-not-found", "{name}", warpName);
                            return java.util.concurrent.CompletableFuture.completedFuture(null);
                        }
                        return plugin.getPlayerWarpWhitelistManager().list(warp.getId()).thenAccept(entries ->
                                printWhitelist(sender, warp, entries));
                    }), "PlayerWarpCommand#whitelistList", sender);
            case "enable" -> plugin.getPlayerWarpManager().setWhitelistEnabled(sender, warpName, true);
            case "disable" -> plugin.getPlayerWarpManager().setWhitelistEnabled(sender, warpName, false);
            default -> sendUsage(sender);
        }
    }

    private void printWhitelist(CommandSender sender, PlayerWarp warp, List<PlayerWarpWhitelistEntry> entries) {
        plugin.getMessages().send(sender, "player-warp.whitelist.header",
                "{warp}", warp.getName(), "{count}", String.valueOf(entries.size()));

        if (entries.isEmpty()) {
            plugin.getMessages().send(sender, "player-warp.whitelist.empty");
            return;
        }

        for (PlayerWarpWhitelistEntry entry : entries) {
            plugin.getMessages().send(sender, "player-warp.whitelist.entry", "{player}", entry.playerName());
        }
    }

    // ── bans ────────────────────────────────────────────────────────────────

    private void handleBan(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 3)) {
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        String warpName = args[2];

        switch (action) {
            case "set" -> {
                if (args.length < 4) {
                    sendUsage(sender);
                    return;
                }
                long duration = 0L;
                int reasonStart = 4;

                if (args.length >= 5) {
                    long parsed = parseSeconds(args[4]);
                    if (parsed != INVALID_DURATION) {
                        duration = parsed;
                        reasonStart = 5;
                    }
                }

                String reason = reasonStart < args.length
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, reasonStart, args.length))
                        : "";

                long finalDuration = duration;
                withWarp(sender, warpName, warp ->
                        plugin.getPlayerWarpBanManager().add(sender, warp, args[3], finalDuration, reason));
            }
            case "remove" -> {
                if (args.length < 4) {
                    sendUsage(sender);
                    return;
                }
                withWarp(sender, warpName, warp ->
                        plugin.getPlayerWarpBanManager().remove(sender, warp, args[3]));
            }
            case "list" -> Futures.guard(plugin.getPlayerWarpManager().getWarpByName(warpName)
                    .thenCompose(warp -> {
                        if (warp == null) {
                            plugin.getMessages().send(sender, "errors.player-warp-not-found", "{name}", warpName);
                            return java.util.concurrent.CompletableFuture.completedFuture(null);
                        }
                        return plugin.getPlayerWarpBanManager().list(warp.getId()).thenAccept(bans ->
                                printBans(sender, warp, bans));
                    }), "PlayerWarpCommand#banList", sender);
            default -> sendUsage(sender);
        }
    }

    private void printBans(CommandSender sender, PlayerWarp warp, List<PlayerWarpBan> bans) {
        plugin.getMessages().send(sender, "player-warp.ban.header",
                "{warp}", warp.getName(), "{count}", String.valueOf(bans.size()));

        if (bans.isEmpty()) {
            plugin.getMessages().send(sender, "player-warp.ban.empty");
            return;
        }

        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
        for (PlayerWarpBan ban : bans) {
            String expires = ban.isPermanent() ? "never" : format.format(new Date(ban.expiresAt()));
            plugin.getMessages().send(sender, "player-warp.ban.entry",
                    "{player}", ban.playerName(), "{reason}", ban.reason() == null ? "-" : ban.reason(),
                    "{expires}", expires);
        }
    }

    // ── managers ────────────────────────────────────────────────────────────

    private void handleManagers(CommandSender sender, String[] args) {
        if (!requirePermission(sender, Permissions.PLAYERWARP_ADMIN_MANAGERS)) {
            return;
        }
        if (!requireArgs(sender, args, 3)) {
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        String warpName = args[2];

        switch (action) {
            case "set" -> {
                if (args.length < 4) {
                    sendUsage(sender);
                    return;
                }
                withWarp(sender, warpName, warp ->
                        plugin.getPlayerWarpManagerRoleManager().add(sender, warp, args[3]));
            }
            case "remove" -> {
                if (args.length < 4) {
                    sendUsage(sender);
                    return;
                }
                withWarp(sender, warpName, warp ->
                        plugin.getPlayerWarpManagerRoleManager().remove(sender, warp, args[3]));
            }
            case "list" -> Futures.guard(plugin.getPlayerWarpManager().getWarpByName(warpName)
                    .thenCompose(warp -> {
                        if (warp == null) {
                            plugin.getMessages().send(sender, "errors.player-warp-not-found", "{name}", warpName);
                            return java.util.concurrent.CompletableFuture.completedFuture(null);
                        }
                        return plugin.getPlayerWarpManagerRoleManager().list(warp.getId()).thenAccept(entries ->
                                printManagers(sender, warp, entries));
                    }), "PlayerWarpCommand#managersList", sender);
            default -> sendUsage(sender);
        }
    }

    private void printManagers(CommandSender sender, PlayerWarp warp, List<PlayerWarpManagerEntry> entries) {
        plugin.getMessages().send(sender, "player-warp.managers.header",
                "{warp}", warp.getName(), "{count}", String.valueOf(entries.size()));

        if (entries.isEmpty()) {
            plugin.getMessages().send(sender, "player-warp.managers.empty");
            return;
        }

        for (PlayerWarpManagerEntry entry : entries) {
            plugin.getMessages().send(sender, "player-warp.managers.entry", "{player}", entry.playerName());
        }
    }

    // ── favourite / list / reload ───────────────────────────────────────────

    private void handleFavourite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }
        if (!requireArgs(sender, args, 2)) {
            return;
        }

        Futures.guard(plugin.getPlayerWarpManager().getWarpByName(args[1]).thenAccept(warp -> {
            if (warp == null) {
                plugin.getMessages().send(sender, "errors.player-warp-not-found", "{name}", args[1]);
                return;
            }
            plugin.getPlayerWarpFavouriteManager().toggle(player, warp);
        }), "PlayerWarpCommand#favourite", sender);
    }

    private void handleList(CommandSender sender, String[] args) {
        int page = 1;
        String ownerFilter = null;

        if (args.length >= 2) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                ownerFilter = args[1];
            }
        }
        if (args.length >= 3) {
            ownerFilter = args[2];
        }

        String finalOwnerFilter = ownerFilter;
        int finalPage = page;

        Futures.guard(plugin.getPlayerWarpManager().getWarps().thenAccept(all -> {
            List<PlayerWarp> visible = new ArrayList<>();
            for (PlayerWarp warp : all) {
                if (finalOwnerFilter == null || finalOwnerFilter.equalsIgnoreCase(warp.getOwnerName())) {
                    visible.add(warp);
                }
            }

            if (visible.isEmpty()) {
                plugin.getMessages().send(sender, "errors.no-warps");
                return;
            }

            int pages = (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            int clampedPage = Math.min(finalPage, pages);
            int from = (clampedPage - 1) * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, visible.size());

            plugin.getMessages().send(sender, "player-warp.list.header",
                    "{count}", String.valueOf(visible.size()),
                    "{page}", String.valueOf(clampedPage), "{pages}", String.valueOf(pages));

            for (PlayerWarp warp : visible.subList(from, to)) {
                plugin.getMessages().send(sender, "player-warp.list.entry",
                        "{name}", warp.getName(),
                        "{owner}", warp.getOwnerName() == null ? "?" : warp.getOwnerName(),
                        "{world}", warp.getWorld() == null ? "?" : warp.getWorld());
            }
        }), "PlayerWarpCommand#list", sender);
    }

    private void handleReload(CommandSender sender) {
        if (!requirePermission(sender, Permissions.PLAYERWARP_ADMIN_RELOAD)) {
            return;
        }

        plugin.getConfigManager().getPlayerWarpsConfig().reload();
        plugin.getConfigManager().reload();
        plugin.getPlayerWarpCategoryRegistry().load();
        plugin.getPlayerWarpManager().invalidateCache();

        plugin.getMessages().send(sender, "reload.success");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void withWarp(CommandSender sender, String name, java.util.function.Consumer<PlayerWarp> action) {
        Futures.guard(plugin.getPlayerWarpManager().getWarpByName(name).thenAccept(warp -> {
            if (warp == null) {
                plugin.getMessages().send(sender, "errors.player-warp-not-found", "{name}", name);
                return;
            }
            action.accept(warp);
        }), "PlayerWarpCommand#withWarp", sender);
    }

    private static long parseSeconds(String input) {
        return DurationParser.tryParse(input, true).map(Duration::toSeconds).filter(seconds -> seconds > 0)
                .orElse(INVALID_DURATION);
    }

    // ── tab completion ──────────────────────────────────────────────────────

    private static final List<String> ROOT_SUBCOMMANDS = List.of("set", "remove", "desc", "list", "icon",
            "category", "rate", "lock", "cost", "reset", "rename", "setowner", "password", "whitelist", "ban",
            "favourite", "settings", "removeall", "managers", "reload", "admin");

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(ROOT_SUBCOMMANDS);
            options.addAll(PlayerWarpAdmin.allWarpNames(plugin));
            return filterTabCompletion(options, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            return switch (sub) {
                case "desc", "icon", "cost", "password" -> filterTabCompletion(List.of("set", "remove"), args[1]);
                case "category" -> filterTabCompletion(List.of("set", "remove", "list"), args[1]);
                case "whitelist" -> filterTabCompletion(
                        List.of("set", "remove", "list", "enable", "disable"), args[1]);
                case "ban" -> filterTabCompletion(List.of("set", "remove", "list"), args[1]);
                case "managers" -> filterTabCompletion(List.of("set", "remove", "list"), args[1]);
                case "remove", "reset", "rename", "setowner", "lock", "rate", "favourite", "settings" ->
                        filterTabCompletion(PlayerWarpAdmin.allWarpNames(plugin), args[1]);
                case "removeall" -> sender instanceof Player p
                        ? filterTabCompletion(PlayerWarpAdmin.completePlayerArgument(p), args[1]) : List.of();
                default -> List.of();
            };
        }

        if (args.length == 3) {
            return switch (sub) {
                case "desc", "icon", "category", "cost", "password", "whitelist", "ban", "managers" ->
                        filterTabCompletion(PlayerWarpAdmin.allWarpNames(plugin), args[2]);
                case "set" -> sender instanceof Player p
                        ? filterTabCompletion(PlayerWarpAdmin.completePlayerArgument(p), args[2]) : List.of();
                default -> List.of();
            };
        }

        if (args.length == 4) {
            if ((sub.equals("whitelist") || sub.equals("ban") || sub.equals("managers"))
                    && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove"))
                    && sender instanceof Player p) {
                return filterTabCompletion(PlayerWarpAdmin.completePlayerArgument(p), args[3]);
            }
            if (sub.equals("setowner") && sender instanceof Player p) {
                return filterTabCompletion(PlayerWarpAdmin.completePlayerArgument(p), args[3]);
            }
        }

        return List.of();
    }
}
