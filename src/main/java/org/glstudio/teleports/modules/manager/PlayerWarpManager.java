package org.glstudio.teleports.modules.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.core.util.TeleportNames;
import org.glstudio.teleports.modules.menu.playerwarp.PlayerWarpConfirmMenu;
import org.glstudio.teleports.modules.model.PlayerRef;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.TeleportRules;
import org.glstudio.teleports.modules.model.TeleportType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Creates, changes and teleports to player warps.
 */
public class PlayerWarpManager {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GTeleports plugin;

    private volatile CompletableFuture<List<PlayerWarp>> warpCache;

    private final Map<UUID, Set<String>> unlockedPasswords = new ConcurrentHashMap<>();

    /**
     * Creates the manager.
     */
    public PlayerWarpManager(GTeleports plugin) {
        this.plugin = plugin;
        Futures.log(getWarps(), "PlayerWarpManager#preload");
    }

    // ── Cache & lookup ─────────────────────────────────────────────────────

    /**
     * Returns the warps.
     */
    public CompletableFuture<List<PlayerWarp>> getWarps() {
        CompletableFuture<List<PlayerWarp>> cached = warpCache;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (warpCache != null) {
                return warpCache;
            }

            CompletableFuture<List<PlayerWarp>> pending = plugin.getDatabaseManager().getProvider()
                    .getAllPlayerWarps()
                    .thenApply(List::copyOf);

            pending.whenComplete((warps, error) -> {
                if (error != null) {
                    invalidateIfStill(pending);
                }
            });

            warpCache = pending;
            return pending;
        }
    }

    /**
     * Returns the cached warps.
     */
    public List<PlayerWarp> getCachedWarps() {
        CompletableFuture<List<PlayerWarp>> cached = warpCache;

        if (cached != null && cached.isDone() && !cached.isCompletedExceptionally()) {
            return cached.join();
        }

        Futures.log(getWarps(), "PlayerWarpManager#warmCache");
        return List.of();
    }

    /**
     * Returns the warp.
     */
    public CompletableFuture<PlayerWarp> getWarp(String id) {
        return getWarps().thenApply(warps -> warps.stream()
                .filter(warp -> warp.getId().equals(id))
                .findFirst()
                .orElse(null));
    }

    /**
     * Returns the warp by name.
     */
    public CompletableFuture<PlayerWarp> getWarpByName(String name) {
        String normalized = TeleportNames.normalize(name);
        return getWarps().thenApply(warps -> warps.stream()
                .filter(warp -> warp.getName().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null));
    }

    /**
     * Returns the cached warps by owner.
     */
    public List<PlayerWarp> getCachedWarpsByOwner(UUID owner) {
        List<PlayerWarp> result = new ArrayList<>();
        for (PlayerWarp warp : getCachedWarps()) {
            if (warp.isOwner(owner)) {
                result.add(warp);
            }
        }
        return result;
    }

    /**
     * Drops the cache.
     */
    public void invalidateCache() {
        warpCache = null;
    }

    private synchronized void invalidateIfStill(CompletableFuture<List<PlayerWarp>> expected) {
        if (warpCache == expected) {
            warpCache = null;
        }
    }

    private void invalidateEverywhere() {
        invalidateCache();
        plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.PLAYER_WARP, null);
    }

    // ── Ownership / authorization ───────────────────────────────────────────

    /**
     * Tells whether a player may change a warp.
     */
    public CompletableFuture<Boolean> mayManage(PlayerWarp warp, CommandSender sender) {
        if (sender.hasPermission(Permissions.PLAYERWARP_ADMIN_REMOVEALL)) {
            return CompletableFuture.completedFuture(true);
        }
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(false);
        }
        return plugin.getPlayerWarpManagerRoleManager().mayManage(warp, player.getUniqueId());
    }

    private CompletableFuture<Boolean> mayOwn(PlayerWarp warp, CommandSender sender) {
        if (sender.hasPermission(Permissions.PLAYERWARP_ADMIN_REMOVEALL)) {
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(sender instanceof Player player && warp.isOwner(player.getUniqueId()));
    }

    private boolean isWorldBlacklisted(CommandSender sender, Location location) {
        Player player = sender instanceof Player p ? p : null;
        return plugin.getBlacklistWorldManager().isBlacklisted(player, location.getWorld(), TeleportType.PLAYER_WARP);
    }

    // ── CRUD ────────────────────────────────────────────────────────────────

    /**
     * Creates the warp.
     */
    public CompletableFuture<Void> createWarp(Player player, String name) {
        return createWarpInternal(player, player.getUniqueId(), player.getName(), name, player.getLocation(), true);
    }

    /**
     * Creates the warp for.
     */
    public CompletableFuture<Void> createWarpFor(CommandSender staff, PlayerRef owner, String name,
                                                 Location location) {
        return createWarpInternal(staff, owner.uuid(), owner.name(), name, location, false);
    }

    private CompletableFuture<Void> createWarpInternal(CommandSender sender, UUID owner, String ownerName,
                                                        String name, Location location, boolean enforceLimit) {
        if (!TeleportNames.isValid(name)) {
            plugin.getMessages().send(sender, "errors.invalid-name",
                    "{max}", String.valueOf(TeleportNames.MAX_LENGTH));
            return CompletableFuture.completedFuture(null);
        }

        if (isWorldBlacklisted(sender, location)) {
            plugin.getMessages().send(sender, "errors.player-warp-world-blacklisted");
            return CompletableFuture.completedFuture(null);
        }

        String normalized = TeleportNames.normalize(name);
        String server = plugin.getCrossServerManager().currentServer();

        CompletableFuture<Void> result = getWarps().thenCompose(all -> {
            boolean taken = all.stream().anyMatch(w -> w.getName().equalsIgnoreCase(normalized));
            if (taken) {
                plugin.getMessages().send(sender, "errors.player-warp-name-taken", "{name}", normalized);
                return CompletableFuture.completedFuture(null);
            }

            if (enforceLimit && sender instanceof Player player) {
                int limit = plugin.getPlayerWarpLimits().getLimit(player);
                long owned = all.stream().filter(w -> w.isOwner(owner)).count();

                if (limit != PlayerWarpLimits.UNLIMITED && owned >= limit) {
                    plugin.getMessages().send(sender, "errors.player-warp-limit-reached",
                            "{limit}", String.valueOf(limit));
                    return CompletableFuture.completedFuture(null);
                }
            }

            PlayerWarp warp = new PlayerWarp(UUID.randomUUID().toString(), normalized, owner, ownerName,
                    location, server);

            return plugin.getDatabaseManager().getProvider().savePlayerWarp(warp)
                    .thenRun(() -> {
                        invalidateEverywhere();
                        plugin.getMessages().send(sender, "player-warp.created", "{name}", normalized);
                    });
        });

        return Futures.guard(result, "PlayerWarpManager#createWarp", sender);
    }

    /**
     * Removes the warp.
     */
    public CompletableFuture<Void> removeWarp(CommandSender sender, String name) {
        if (!sender.hasPermission(Permissions.PLAYERWARP_REMOVE)) {
            plugin.getMessages().send(sender, "errors.no-permission");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = getWarpByName(name).thenCompose(warp -> {
            if (warp == null) {
                plugin.getMessages().send(sender, "errors.player-warp-not-found", "{name}", name);
                return CompletableFuture.completedFuture(null);
            }

            return mayManage(warp, sender).thenCompose(allowed -> {
                if (!allowed) {
                    plugin.getMessages().send(sender, "errors.no-permission");
                    return CompletableFuture.completedFuture(null);
                }

                return deleteInternal(warp)
                        .thenRun(() -> plugin.getMessages().send(sender, "player-warp.deleted",
                                "{name}", warp.getName()));
            });
        });

        return Futures.guard(result, "PlayerWarpManager#removeWarp", sender);
    }

    /**
     * Removes the all of.
     */
    public CompletableFuture<Void> removeAllOf(CommandSender sender, UUID owner, String ownerName) {
        CompletableFuture<Void> result = getWarps().thenCompose(all -> {
            List<PlayerWarp> owned = all.stream().filter(w -> w.isOwner(owner)).toList();

            if (owned.isEmpty()) {
                plugin.getMessages().send(sender, "player-warp.removeall.none", "{player}", ownerName);
                return CompletableFuture.completedFuture(null);
            }

            @SuppressWarnings("unchecked")
            CompletableFuture<Boolean>[] deletions = owned.stream()
                    .map(w -> plugin.getDatabaseManager().getProvider().deletePlayerWarp(w.getId()))
                    .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(deletions).thenRun(() -> {
                invalidateEverywhere();
                plugin.getMessages().send(sender, "player-warp.removeall.success",
                        "{count}", String.valueOf(owned.size()), "{player}", ownerName);
            });
        });

        return Futures.guard(result, "PlayerWarpManager#removeAllOf", sender);
    }

    private CompletableFuture<Void> deleteInternal(PlayerWarp warp) {
        return plugin.getDatabaseManager().getProvider().deletePlayerWarp(warp.getId())
                .thenRun(this::invalidateEverywhere);
    }

    /**
     * Renames a warp.
     */
    public CompletableFuture<Void> renameWarp(CommandSender sender, String currentName, String newName) {
        if (!TeleportNames.isValid(newName)) {
            plugin.getMessages().send(sender, "errors.invalid-name",
                    "{max}", String.valueOf(TeleportNames.MAX_LENGTH));
            return CompletableFuture.completedFuture(null);
        }

        String normalizedNew = TeleportNames.normalize(newName);

        CompletableFuture<Void> result = mutate(sender, currentName, Permissions.PLAYERWARP_RENAME,
                warp -> getWarps().thenCompose(all -> {
            boolean taken = !warp.getName().equalsIgnoreCase(normalizedNew)
                    && all.stream().anyMatch(w -> w.getName().equalsIgnoreCase(normalizedNew));

            if (taken) {
                plugin.getMessages().send(sender, "errors.player-warp-name-taken", "{name}", normalizedNew);
                return CompletableFuture.completedFuture(null);
            }

            String oldName = warp.getName();
            warp.setName(normalizedNew);
            warp.touch();

            return save(warp).thenRun(() -> plugin.getMessages().send(sender, "player-warp.renamed",
                    "{old}", oldName, "{new}", normalizedNew));
        }));

        return Futures.guard(result, "PlayerWarpManager#renameWarp", sender);
    }

    /**
     * Resets the location.
     */
    public CompletableFuture<Void> resetLocation(Player player, String name) {
        CompletableFuture<Void> result = mutate(player, name, Permissions.PLAYERWARP_RESET, warp -> {
            Location location = player.getLocation();

            if (isWorldBlacklisted(player, location)) {
                plugin.getMessages().send(player, "errors.player-warp-world-blacklisted");
                return CompletableFuture.completedFuture(null);
            }

            warp.setWorld(location.getWorld().getName());
            warp.setX(location.getX());
            warp.setY(location.getY());
            warp.setZ(location.getZ());
            warp.setYaw(location.getYaw());
            warp.setPitch(location.getPitch());
            warp.setServer(plugin.getCrossServerManager().currentServer());
            warp.touch();

            return save(warp).thenRun(() -> plugin.getMessages().send(player, "player-warp.reset",
                    "{name}", warp.getName()));
        });

        return Futures.guard(result, "PlayerWarpManager#resetLocation", player);
    }

    /**
     * Hands a warp to another player.
     */
    public CompletableFuture<Void> transferOwner(CommandSender sender, String name, String newOwnerName) {
        if (!sender.hasPermission(Permissions.PLAYERWARP_SETOWNER)) {
            plugin.getMessages().send(sender, "errors.no-permission");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = getWarpByName(name).thenCompose(warp -> {
            if (warp == null) {
                plugin.getMessages().send(sender, "errors.player-warp-not-found", "{name}", name);
                return CompletableFuture.completedFuture(null);
            }

            return mayOwn(warp, sender).thenCompose(allowed -> {
                if (!allowed) {
                    plugin.getMessages().send(sender, "errors.no-permission");
                    return CompletableFuture.completedFuture(null);
                }

                return resolve(newOwnerName).thenCompose(target -> {
                    if (target == null) {
                        plugin.getMessages().send(sender, "errors.player-not-found", "{player}", newOwnerName);
                        return CompletableFuture.completedFuture(null);
                    }

                    warp.setOwner(target.uuid());
                    warp.setOwnerName(target.name());
                    warp.touch();

                    return save(warp).thenRun(() -> plugin.getMessages().send(sender,
                            "player-warp.transferred", "{name}", warp.getName(), "{player}", target.name()));
                });
            });
        });

        return Futures.guard(result, "PlayerWarpManager#transferOwner", sender);
    }

    /**
     * Sets the description.
     */
    public CompletableFuture<Void> setDescription(CommandSender sender, String name, String description) {
        int maxLength = plugin.getConfigManager().getPlayerWarpsConfig().getInt("description.max-length", 96);
        String trimmed = description == null ? null
                : description.length() > maxLength ? description.substring(0, maxLength) : description;

        return mutate(sender, name, Permissions.PLAYERWARP_DESC, warp -> {
            warp.setDescription(trimmed);
            warp.touch();
            return save(warp).thenRun(() -> plugin.getMessages().send(sender,
                    trimmed == null ? "player-warp.desc.removed" : "player-warp.desc.set",
                    "{name}", warp.getName()));
        });
    }

    /**
     * Sets the icon.
     */
    public CompletableFuture<Void> setIcon(CommandSender sender, String name, String materialName) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName.toUpperCase(Locale.ROOT));

        if (materialName != null && material == null) {
            plugin.getMessages().send(sender, "errors.invalid-material", "{material}", materialName);
            return CompletableFuture.completedFuture(null);
        }

        return mutate(sender, name, Permissions.PLAYERWARP_ICON, warp -> {
            warp.setIcon(material == null ? null : material.name());
            warp.touch();
            return save(warp).thenRun(() -> plugin.getMessages().send(sender,
                    material == null ? "player-warp.icon.removed" : "player-warp.icon.set",
                    "{name}", warp.getName()));
        });
    }

    /**
     * Sets the category.
     */
    public CompletableFuture<Void> setCategory(CommandSender sender, String name, String categoryId) {
        if (categoryId != null && !plugin.getPlayerWarpCategoryRegistry().exists(categoryId)) {
            plugin.getMessages().send(sender, "errors.player-warp-category-not-found", "{category}", categoryId);
            return CompletableFuture.completedFuture(null);
        }

        return mutate(sender, name, Permissions.PLAYERWARP_CATEGORY, warp -> {
            warp.setCategoryId(categoryId == null ? null : categoryId.toLowerCase(Locale.ROOT));
            warp.touch();
            return save(warp).thenRun(() -> plugin.getMessages().send(sender,
                    categoryId == null ? "player-warp.category.removed" : "player-warp.category.set",
                    "{name}", warp.getName(), "{category}", String.valueOf(categoryId)));
        });
    }

    /**
     * Sets the cost.
     */
    public CompletableFuture<Void> setCost(CommandSender sender, String name, Double cost) {
        if (cost != null && cost < 0) {
            plugin.getMessages().send(sender, "errors.invalid-amount", "{value}", String.valueOf(cost));
            return CompletableFuture.completedFuture(null);
        }

        return mutate(sender, name, Permissions.PLAYERWARP_COST, warp -> {
            warp.setCost(cost);
            warp.touch();
            return save(warp).thenRun(() -> plugin.getMessages().send(sender,
                    cost == null ? "player-warp.cost.removed" : "player-warp.cost.set",
                    "{name}", warp.getName(),
                    "{cost}", cost == null ? "-" : plugin.getEconomyManager().formatCost(cost)));
        });
    }

    /**
     * Sets the locked.
     */
    public CompletableFuture<Void> setLocked(CommandSender sender, String name, boolean locked) {
        return mutate(sender, name, Permissions.PLAYERWARP_LOCK, warp -> {
            warp.setLocked(locked);
            warp.touch();
            return save(warp).thenRun(() -> plugin.getMessages().send(sender,
                    locked ? "player-warp.lock.locked" : "player-warp.lock.unlocked", "{name}", warp.getName()));
        });
    }

    /**
     * Sets the password.
     */
    public CompletableFuture<Void> setPassword(CommandSender sender, String name, String password) {
        int minLength = plugin.getConfigManager().getPlayerWarpsConfig().getInt("password.min-length", 4);
        int maxLength = plugin.getConfigManager().getPlayerWarpsConfig().getInt("password.max-length", 32);

        if (password == null || password.length() < minLength || password.length() > maxLength) {
            plugin.getMessages().send(sender, "errors.player-warp-password-length",
                    "{min}", String.valueOf(minLength), "{max}", String.valueOf(maxLength));
            return CompletableFuture.completedFuture(null);
        }

        return mutate(sender, name, Permissions.PLAYERWARP_PASSWORD, warp -> {
            String salt = randomSalt();
            warp.setPasswordSalt(salt);
            warp.setPasswordHash(hash(password, salt));
            warp.touch();

            return save(warp).thenRun(() -> plugin.getMessages().send(sender, "player-warp.password.set",
                    "{name}", warp.getName()));
        });
    }

    /**
     * Removes the password.
     */
    public CompletableFuture<Void> removePassword(CommandSender sender, String name) {
        return mutate(sender, name, Permissions.PLAYERWARP_PASSWORD, warp -> {
            warp.setPasswordHash(null);
            warp.setPasswordSalt(null);
            warp.touch();

            return save(warp).thenRun(() -> plugin.getMessages().send(sender, "player-warp.password.removed",
                    "{name}", warp.getName()));
        });
    }

    /**
     * Sets the whitelist enabled.
     */
    public CompletableFuture<Void> setWhitelistEnabled(CommandSender sender, String name, boolean enabled) {
        return mutate(sender, name, Permissions.PLAYERWARP_WHITELIST, warp -> {
            warp.setWhitelistEnabled(enabled);
            warp.touch();
            return save(warp).thenRun(() -> plugin.getMessages().send(sender,
                    enabled ? "player-warp.whitelist.enabled" : "player-warp.whitelist.disabled",
                    "{name}", warp.getName()));
        });
    }

    private CompletableFuture<Void> mutate(CommandSender sender, String name, String actionPermission,
                                           Function<PlayerWarp, CompletableFuture<Void>> action) {
        if (actionPermission != null && !sender.hasPermission(actionPermission)) {
            plugin.getMessages().send(sender, "errors.no-permission");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = getWarpByName(name).thenCompose(warp -> {
            if (warp == null) {
                plugin.getMessages().send(sender, "errors.player-warp-not-found", "{name}", name);
                return CompletableFuture.completedFuture(null);
            }

            return mayManage(warp, sender).thenCompose(allowed -> {
                if (!allowed) {
                    plugin.getMessages().send(sender, "errors.no-permission");
                    return CompletableFuture.completedFuture(null);
                }

                return action.apply(warp);
            });
        });

        return Futures.guard(result, "PlayerWarpManager#mutate", sender);
    }

    /**
     * Stores a warp.
     */
    public CompletableFuture<Void> save(PlayerWarp warp) {
        return plugin.getDatabaseManager().getProvider().savePlayerWarp(warp)
                .thenRun(this::invalidateEverywhere);
    }

    // ── Password hashing ────────────────────────────────────────────────────

    /**
     * Creates a password salt.
     */
    public static String randomSalt() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Hashes a password.
     */
    public static String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Checks a warp's password.
     */
    public static boolean verifyPassword(PlayerWarp warp, String attempt) {
        if (!warp.hasPassword() || attempt == null) {
            return !warp.hasPassword();
        }

        String hashedAttempt = hash(attempt, warp.getPasswordSalt());
        return MessageDigest.isEqual(hashedAttempt.getBytes(StandardCharsets.UTF_8),
                warp.getPasswordHash().getBytes(StandardCharsets.UTF_8));
    }

    // ── Teleport gate pipeline ──────────────────────────────────────────────

    private enum GateStatus {
        NOT_FOUND,
        BANNED,
        LOCKED,
        NOT_WHITELISTED,
        PASSWORD_REQUIRED,
        PASSWORD_WRONG,
        OK
    }

    private record TeleportGate(GateStatus status, PlayerWarp warp) {
    }

    /**
     * Starts a teleport to a warp, with its checks.
     */
    public void attemptTeleport(Player player, String name, String passwordAttempt) {
        Futures.log(getWarpByName(name).thenCompose(warp -> {
            if (warp == null) {
                return CompletableFuture.completedFuture(new TeleportGate(GateStatus.NOT_FOUND, null));
            }
            return evaluateGates(player, warp, passwordAttempt);
        }).thenAccept(gate -> Bukkit.getScheduler().runTask(plugin, () -> applyGateResult(player, gate))),
                "PlayerWarpManager#attemptTeleport");
    }

    private CompletableFuture<TeleportGate> evaluateGates(Player player, PlayerWarp warp, String passwordAttempt) {
        UUID uuid = player.getUniqueId();

        if (warp.isOwner(uuid)) {
            return CompletableFuture.completedFuture(new TeleportGate(GateStatus.OK, warp));
        }

        return plugin.getPlayerWarpManagerRoleManager().isManager(warp.getId(), uuid).thenCompose(isManager -> {
            if (isManager) {
                return CompletableFuture.completedFuture(new TeleportGate(GateStatus.OK, warp));
            }

            if (player.hasPermission(Permissions.PLAYERWARP_BYPASS_BAN)) {
                return afterBan(player, warp, passwordAttempt);
            }

            return plugin.getPlayerWarpBanManager().find(warp.getId(), uuid).thenCompose(ban -> {
                if (ban != null) {
                    return CompletableFuture.completedFuture(new TeleportGate(GateStatus.BANNED, warp));
                }
                return afterBan(player, warp, passwordAttempt);
            });
        });
    }

    private CompletableFuture<TeleportGate> afterBan(Player player, PlayerWarp warp, String passwordAttempt) {
        UUID uuid = player.getUniqueId();

        if (warp.isLocked() && !player.hasPermission(Permissions.PLAYERWARP_BYPASS_LOCK)) {
            return CompletableFuture.completedFuture(new TeleportGate(GateStatus.LOCKED, warp));
        }

        if (warp.isWhitelistEnabled() && !player.hasPermission(Permissions.PLAYERWARP_BYPASS_WHITELIST)) {
            return plugin.getPlayerWarpWhitelistManager().isWhitelisted(warp.getId(), uuid).thenApply(whitelisted ->
                    whitelisted ? checkPassword(player, warp, passwordAttempt)
                            : new TeleportGate(GateStatus.NOT_WHITELISTED, warp));
        }

        return CompletableFuture.completedFuture(checkPassword(player, warp, passwordAttempt));
    }

    private TeleportGate checkPassword(Player player, PlayerWarp warp, String passwordAttempt) {
        if (!warp.hasPassword() || player.hasPermission(Permissions.PLAYERWARP_BYPASS_PASSWORD)) {
            return new TeleportGate(GateStatus.OK, warp);
        }

        Set<String> unlocked = unlockedPasswords.get(player.getUniqueId());
        if (unlocked != null && unlocked.contains(warp.getId())) {
            return new TeleportGate(GateStatus.OK, warp);
        }

        if (passwordAttempt == null) {
            return new TeleportGate(GateStatus.PASSWORD_REQUIRED, warp);
        }

        if (!verifyPassword(warp, passwordAttempt)) {
            return new TeleportGate(GateStatus.PASSWORD_WRONG, warp);
        }

        if (plugin.getConfigManager().getPlayerWarpsConfig().getBoolean("password.remember-per-session", true)) {
            unlockedPasswords.computeIfAbsent(player.getUniqueId(), key -> ConcurrentHashMap.newKeySet())
                    .add(warp.getId());
        }

        return new TeleportGate(GateStatus.OK, warp);
    }

    private void applyGateResult(Player player, TeleportGate gate) {
        if (!player.isOnline()) {
            return;
        }

        switch (gate.status()) {
            case NOT_FOUND -> plugin.getMessages().send(player, "errors.player-warp-not-found");
            case BANNED -> plugin.getMessages().send(player, "errors.player-warp-banned",
                    "{name}", gate.warp().getName());
            case LOCKED -> plugin.getMessages().send(player, "errors.player-warp-locked",
                    "{name}", gate.warp().getName());
            case NOT_WHITELISTED -> plugin.getMessages().send(player, "errors.player-warp-not-whitelisted",
                    "{name}", gate.warp().getName());
            case PASSWORD_REQUIRED -> promptPassword(player, gate.warp(), false);
            case PASSWORD_WRONG -> promptPassword(player, gate.warp(), true);
            case OK -> confirmCostThenTeleport(player, gate.warp());
        }
    }

    /**
     * Asks a player for a warp's password.
     */
    public void promptPassword(Player player, PlayerWarp warp, boolean wrongAttempt) {
        plugin.getMessages().send(player, wrongAttempt
                ? "player-warp.password.wrong" : "player-warp.password.required", "{name}", warp.getName());

        plugin.getChatPrompts().request(player,
                plugin.getMessages().raw("player-warp.password.prompt"),
                attempt -> attemptTeleport(player, warp.getName(), attempt));
    }

    private void confirmCostThenTeleport(Player player, PlayerWarp warp) {
        double cost = TeleportRules.resolve(plugin, player, TeleportType.PLAYER_WARP, warp).cost();
        if (!plugin.getEconomyManager().chargeable(player, cost)) {
            proceedToTeleport(player, warp);
            return;
        }

        PlayerWarpConfirmMenu.open(plugin, player, warp, cost, () -> proceedToTeleport(player, warp));
    }

    private void proceedToTeleport(Player player, PlayerWarp warp) {
        if (!plugin.getCrossServerManager().isLocal(warp.getServer())) {
            plugin.getCrossServerManager().sendTo(player, TeleportType.PLAYER_WARP, warp);
            return;
        }

        Location location = warp.toLocation();
        if (location == null) {
            plugin.getMessages().send(player, "errors.world-not-found");
            return;
        }

        plugin.getTeleportManager().requestTeleport(player, location, TeleportType.PLAYER_WARP, warp,
                warp.getName(), success -> {
                    if (success) {
                        warp.markUsed();
                        Futures.log(plugin.getDatabaseManager().getProvider().savePlayerWarp(warp),
                                "PlayerWarpManager#markUsed");
                    }
                });
    }

    private CompletableFuture<PlayerRef> resolve(String name) {
        Player online = plugin.getServer().getPlayerExact(name);

        if (online != null) {
            return CompletableFuture.completedFuture(new PlayerRef(online.getUniqueId(), online.getName()));
        }

        return plugin.getDatabaseManager().getProvider().findPlayer(name);
    }

    /**
     * Forgets which warps a player has unlocked.
     */
    public void forgetPasswordUnlocks(UUID uuid) {
        unlockedPasswords.remove(uuid);
    }

    /**
     * Stops this on shutdown.
     */
    public void safeDisable() {
        warpCache = null;
        unlockedPasswords.clear();
    }
}
