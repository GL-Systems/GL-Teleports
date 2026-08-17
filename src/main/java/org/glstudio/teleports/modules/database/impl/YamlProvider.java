package org.glstudio.teleports.modules.database.impl;

import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.DestinationLock;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.HomeShare;
import org.glstudio.teleports.modules.model.PlayerRef;
import org.glstudio.teleports.modules.model.StoredLocation;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.TeleportRecord;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.teleports.modules.model.Warp;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class YamlProvider extends AbstractProvider {
    private final File dataFolder;
    private final File warpsFile;
    private final File spawnFile;
    private final File homesFolder;
    private final File settingsFile;
    private final File logFile;
    private final File sharesFile;
    private final File locksFile;

    private final Object warpsLock = new Object();

    private final Object spawnLock = new Object();

    private final Object settingsLock = new Object();

    private final Object logLock = new Object();
    private final Object sharesLock = new Object();
    private final Object locksLock = new Object();

    private final Map<UUID, Object> homeLocks = new ConcurrentHashMap<>();

    private final AtomicLong nextLogId = new AtomicLong(1L);

    private YamlConfiguration warpsConfig;
    private YamlConfiguration spawnConfig;
    private YamlConfiguration settingsConfig;
    private YamlConfiguration logConfig;
    private YamlConfiguration sharesConfig;
    private YamlConfiguration locksConfig;

    public YamlProvider(GTeleports plugin) {
        super(plugin);
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        this.warpsFile = new File(dataFolder, "warps.yml");
        this.spawnFile = new File(dataFolder, "spawn.yml");
        this.homesFolder = new File(dataFolder, "homes");
        this.settingsFile = new File(dataFolder, "players.yml");
        this.logFile = new File(dataFolder, "teleport-log.yml");
        this.sharesFile = new File(dataFolder, "home-shares.yml");
        this.locksFile = new File(dataFolder, "locks.yml");
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return run(() -> {
            try {
                if (!dataFolder.exists()) {
                    dataFolder.mkdirs();
                }
                if (!homesFolder.exists()) {
                    homesFolder.mkdirs();
                }
                if (!warpsFile.exists()) {
                    warpsFile.createNewFile();
                }
                if (!spawnFile.exists()) {
                    spawnFile.createNewFile();
                }
                for (File file : new File[]{settingsFile, logFile, sharesFile, locksFile}) {
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                }

                synchronized (warpsLock) {
                    warpsConfig = YamlConfiguration.loadConfiguration(warpsFile);
                }
                synchronized (spawnLock) {
                    spawnConfig = YamlConfiguration.loadConfiguration(spawnFile);
                    migrateLegacySpawn();
                }
                synchronized (settingsLock) {
                    settingsConfig = YamlConfiguration.loadConfiguration(settingsFile);
                }
                synchronized (logLock) {
                    logConfig = YamlConfiguration.loadConfiguration(logFile);
                    seedLogId();
                }
                synchronized (sharesLock) {
                    sharesConfig = YamlConfiguration.loadConfiguration(sharesFile);
                }
                synchronized (locksLock) {
                    locksConfig = YamlConfiguration.loadConfiguration(locksFile);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize YAML provider", e);
            }
        });
    }

    private void migrateLegacySpawn() {
        if (!spawnConfig.contains("spawn.world")) {
            return;
        }

        String server = currentServer();
        String target = spawnPath(server);

        for (String field : new String[]{"world", "x", "y", "z", "yaw", "pitch"}) {
            spawnConfig.set(target + "." + field, spawnConfig.get("spawn." + field));
            spawnConfig.set("spawn." + field, null);
        }
        spawnConfig.set(target + ".server", server);
        spawnConfig.set("spawn.server", null);

        saveAtomically(spawnConfig, spawnFile);
        LoggerUtils.logInfo("Migrated the existing spawn to server '" + server + "'.");
    }

    private String currentServer() {
        return plugin.getMultiServerSettings() == null
                ? "unnamed-server"
                : plugin.getMultiServerSettings().getServerName();
    }

    @Override
    public CompletableFuture<Void> reload() {
        return run(() -> {
            synchronized (warpsLock) {
                warpsConfig = YamlConfiguration.loadConfiguration(warpsFile);
            }
            synchronized (spawnLock) {
                spawnConfig = YamlConfiguration.loadConfiguration(spawnFile);
            }
            synchronized (settingsLock) {
                settingsConfig = YamlConfiguration.loadConfiguration(settingsFile);
            }
            synchronized (logLock) {
                logConfig = YamlConfiguration.loadConfiguration(logFile);
                seedLogId();
            }
            synchronized (sharesLock) {
                sharesConfig = YamlConfiguration.loadConfiguration(sharesFile);
            }
            synchronized (locksLock) {
                locksConfig = YamlConfiguration.loadConfiguration(locksFile);
            }
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Home>> getHomes(UUID owner) {
        return supply(() -> {
            synchronized (lockFor(owner)) {
                File homeFile = homeFile(owner);
                if (!homeFile.exists()) {
                    return new ArrayList<>();
                }

                YamlConfiguration config = YamlConfiguration.loadConfiguration(homeFile);
                List<Home> homes = new ArrayList<>();

                ConfigurationSection homesSection = config.getConfigurationSection("homes");
                if (homesSection != null) {
                    for (String homeName : homesSection.getKeys(false)) {
                        Home home = readHome(config, owner, homeName);
                        if (home != null) {
                            homes.add(home);
                        }
                    }
                }

                return homes;
            }
        });
    }

    @Override
    public CompletableFuture<Home> getHome(UUID owner, String name) {
        return supply(() -> {
            synchronized (lockFor(owner)) {
                File homeFile = homeFile(owner);
                if (!homeFile.exists()) {
                    return null;
                }

                YamlConfiguration config = YamlConfiguration.loadConfiguration(homeFile);
                return config.contains("homes." + name) ? readHome(config, owner, name) : null;
            }
        });
    }

    private Home readHome(YamlConfiguration config, UUID owner, String name) {
        String path = "homes." + name;
        String world = config.getString(path + ".world");

        if (world == null) {
            LoggerUtils.logWarn("Skipping malformed home '" + name + "' of " + owner + ": no world recorded.");
            return null;
        }

        return new Home(
                owner,
                name,
                world,
                config.getDouble(path + ".x"),
                config.getDouble(path + ".y"),
                config.getDouble(path + ".z"),
                (float) config.getDouble(path + ".yaw"),
                (float) config.getDouble(path + ".pitch"),
                config.getLong(path + ".created-at"),
                config.getString(path + ".server")
        );
    }

    @Override
    public CompletableFuture<Void> saveHome(Home home) {
        return run(() -> {
            synchronized (lockFor(home.getOwner())) {
                File homeFile = homeFile(home.getOwner());
                YamlConfiguration config = YamlConfiguration.loadConfiguration(homeFile);
                String path = "homes." + home.getName();

                config.set(path + ".world", home.getWorld());
                config.set(path + ".x", home.getX());
                config.set(path + ".y", home.getY());
                config.set(path + ".z", home.getZ());
                config.set(path + ".yaw", home.getYaw());
                config.set(path + ".pitch", home.getPitch());
                config.set(path + ".created-at", home.getCreatedAt());
                config.set(path + ".server", home.getServer());

                saveAtomically(config, homeFile);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteHome(UUID owner, String name) {
        return run(() -> {
            synchronized (lockFor(owner)) {
                File homeFile = homeFile(owner);
                if (!homeFile.exists()) {
                    return;
                }

                YamlConfiguration config = YamlConfiguration.loadConfiguration(homeFile);
                config.set("homes." + name, null);

                ConfigurationSection homesSection = config.getConfigurationSection("homes");
                boolean empty = homesSection == null || homesSection.getKeys(false).isEmpty();

                saveAtomically(config, homeFile);

                if (empty) {
                    try {
                        Files.deleteIfExists(homeFile.toPath());
                    } catch (IOException e) {
                        LoggerUtils.logDebug("YamlProvider", "Kept the now-empty home file for " + owner
                                + ": " + e.getMessage());
                    }
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<Warp>> getAllWarps() {
        return supply(() -> {
            synchronized (warpsLock) {
                List<Warp> warps = new ArrayList<>();
                ConfigurationSection warpsSection = warpsConfig.getConfigurationSection("warps");

                if (warpsSection != null) {
                    for (String warpName : warpsSection.getKeys(false)) {
                        Warp warp = readWarp(warpName);
                        if (warp != null) {
                            warps.add(warp);
                        }
                    }
                }

                return warps;
            }
        });
    }

    @Override
    public CompletableFuture<Warp> getWarp(String name) {
        return supply(() -> {
            synchronized (warpsLock) {
                return warpsConfig.contains("warps." + name) ? readWarp(name) : null;
            }
        });
    }

    private Warp readWarp(String name) {
        String path = "warps." + name;
        String world = warpsConfig.getString(path + ".world");

        if (world == null) {
            LoggerUtils.logWarn("Skipping malformed warp '" + name + "': no world recorded.");
            return null;
        }

        return new Warp(
                name,
                world,
                warpsConfig.getDouble(path + ".x"),
                warpsConfig.getDouble(path + ".y"),
                warpsConfig.getDouble(path + ".z"),
                (float) warpsConfig.getDouble(path + ".yaw"),
                (float) warpsConfig.getDouble(path + ".pitch"),
                warpsConfig.getString(path + ".permission"),
                warpsConfig.getLong(path + ".created-at"),
                warpsConfig.getString(path + ".server"),
                warpsConfig.isSet(path + ".cost") ? warpsConfig.getDouble(path + ".cost") : null,
                warpsConfig.isSet(path + ".cooldown") ? warpsConfig.getInt(path + ".cooldown") : null,
                warpsConfig.isSet(path + ".delay") ? warpsConfig.getInt(path + ".delay") : null
        );
    }

    @Override
    public CompletableFuture<Void> saveWarp(Warp warp) {
        return run(() -> {
            synchronized (warpsLock) {
                String path = "warps." + warp.getName();

                warpsConfig.set(path + ".world", warp.getWorld());
                warpsConfig.set(path + ".x", warp.getX());
                warpsConfig.set(path + ".y", warp.getY());
                warpsConfig.set(path + ".z", warp.getZ());
                warpsConfig.set(path + ".yaw", warp.getYaw());
                warpsConfig.set(path + ".pitch", warp.getPitch());
                warpsConfig.set(path + ".permission", warp.getPermission());
                warpsConfig.set(path + ".created-at", warp.getCreatedAt());
                warpsConfig.set(path + ".server", warp.getServer());

                warpsConfig.set(path + ".cost", warp.getCost());
                warpsConfig.set(path + ".cooldown", warp.getCooldown());
                warpsConfig.set(path + ".delay", warp.getDelay());

                saveAtomically(warpsConfig, warpsFile);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteWarp(String name) {
        return supply(() -> {
            synchronized (warpsLock) {
                if (!warpsConfig.contains("warps." + name)) {
                    return false;
                }

                warpsConfig.set("warps." + name, null);
                saveAtomically(warpsConfig, warpsFile);
                return true;
            }
        });
    }

    @Override
    public CompletableFuture<SpawnLocation> getSpawn() {
        return supply(() -> {
            synchronized (spawnLock) {
                ConfigurationSection spawns = spawnConfig.getConfigurationSection("spawn");

                if (spawns == null) {
                    return null;
                }

                for (String key : spawns.getKeys(false)) {
                    ConfigurationSection entry = spawns.getConfigurationSection(key);
                    String world = entry == null ? null : entry.getString("world");

                    if (world == null) {
                        continue;
                    }

                    return new SpawnLocation(
                            world,
                            entry.getDouble("x"),
                            entry.getDouble("y"),
                            entry.getDouble("z"),
                            (float) entry.getDouble("yaw"),
                            (float) entry.getDouble("pitch"),
                            entry.getString("server", key)
                    );
                }

                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveSpawn(SpawnLocation spawn) {
        return run(() -> {
            synchronized (spawnLock) {
                String path = spawnPath(spawn.getServer());

                spawnConfig.set("spawn", null);

                spawnConfig.set(path + ".world", spawn.getWorld());
                spawnConfig.set(path + ".x", spawn.getX());
                spawnConfig.set(path + ".y", spawn.getY());
                spawnConfig.set(path + ".z", spawn.getZ());
                spawnConfig.set(path + ".yaw", spawn.getYaw());
                spawnConfig.set(path + ".pitch", spawn.getPitch());
                spawnConfig.set(path + ".server", spawn.getServer());

                saveAtomically(spawnConfig, spawnFile);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> isTpaBlocked(UUID uuid) {
        return supply(() -> {
            synchronized (settingsLock) {
                return settingsConfig.getBoolean("players." + uuid + ".tpa-blocked", false);
            }
        });
    }

    @Override
    public CompletableFuture<Void> setTpaBlocked(UUID uuid, boolean blocked) {
        return run(() -> {
            synchronized (settingsLock) {
                String path = "players." + uuid + ".tpa-blocked";

                settingsConfig.set(path, blocked ? true : null);
                saveAtomically(settingsConfig, settingsFile);
            }
        });
    }

    @Override
    public CompletableFuture<Void> rememberPlayer(UUID uuid, String name) {
        return run(() -> {
            synchronized (settingsLock) {
                settingsConfig.set("names." + name.toLowerCase(Locale.ROOT) + ".uuid", uuid.toString());
                settingsConfig.set("names." + name.toLowerCase(Locale.ROOT) + ".name", name);
                saveAtomically(settingsConfig, settingsFile);
            }
        });
    }

    @Override
    public CompletableFuture<PlayerRef> findPlayer(String name) {
        return supply(() -> {
            synchronized (settingsLock) {
                String path = "names." + name.toLowerCase(Locale.ROOT);
                String uuid = settingsConfig.getString(path + ".uuid");

                if (uuid == null) {
                    return null;
                }

                try {
                    return new PlayerRef(UUID.fromString(uuid), settingsConfig.getString(path + ".name", name));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveLastLocation(UUID uuid, String server, StoredLocation location) {
        return run(() -> {
            synchronized (settingsLock) {
                String path = lastLocationPath(uuid, server);

                settingsConfig.set(path + ".world", location.world());
                settingsConfig.set(path + ".x", location.x());
                settingsConfig.set(path + ".y", location.y());
                settingsConfig.set(path + ".z", location.z());
                settingsConfig.set(path + ".yaw", location.yaw());
                settingsConfig.set(path + ".pitch", location.pitch());

                saveAtomically(settingsConfig, settingsFile);
            }
        });
    }

    @Override
    public CompletableFuture<StoredLocation> getLastLocation(UUID uuid, String server) {
        return supply(() -> {
            synchronized (settingsLock) {
                String path = lastLocationPath(uuid, server);
                String world = settingsConfig.getString(path + ".world");

                if (world == null) {
                    return null;
                }

                return new StoredLocation(world,
                        settingsConfig.getDouble(path + ".x"),
                        settingsConfig.getDouble(path + ".y"),
                        settingsConfig.getDouble(path + ".z"),
                        (float) settingsConfig.getDouble(path + ".yaw"),
                        (float) settingsConfig.getDouble(path + ".pitch"));
            }
        });
    }

    private static String lastLocationPath(UUID uuid, String server) {
        return "last-locations." + uuid + "." + server.toLowerCase(Locale.ROOT);
    }

    private void seedLogId() {
        ConfigurationSection entries = logConfig.getConfigurationSection("entries");
        if (entries == null) {
            return;
        }

        long highest = 0L;
        for (String key : entries.getKeys(false)) {
            try {
                highest = Math.max(highest, Long.parseLong(key));
            } catch (NumberFormatException ignored) {
            }
        }
        nextLogId.set(highest + 1L);
    }

    @Override
    public CompletableFuture<Void> logTeleport(TeleportRecord record) {
        return run(() -> {
            synchronized (logLock) {
                String path = "entries." + nextLogId.getAndIncrement();

                logConfig.set(path + ".player", record.player().toString());
                logConfig.set(path + ".player-name", record.playerName());
                logConfig.set(path + ".type", record.type().name());
                logConfig.set(path + ".destination", record.destination());
                logConfig.set(path + ".from-server", record.fromServer());
                logConfig.set(path + ".from-world", record.fromWorld());
                logConfig.set(path + ".from-x", record.fromX());
                logConfig.set(path + ".from-y", record.fromY());
                logConfig.set(path + ".from-z", record.fromZ());
                logConfig.set(path + ".to-server", record.toServer());
                logConfig.set(path + ".to-world", record.toWorld());
                logConfig.set(path + ".to-x", record.toX());
                logConfig.set(path + ".to-y", record.toY());
                logConfig.set(path + ".to-z", record.toZ());
                logConfig.set(path + ".cost", record.cost());
                logConfig.set(path + ".created-at", record.timestamp());
                logConfig.set(path + ".result", record.result());

                saveAtomically(logConfig, logFile);
            }
        });
    }

    @Override
    public CompletableFuture<List<TeleportRecord>> getTeleportHistory(UUID player, int limit) {
        return supply(() -> readLog(limit,
                record -> record.player().equals(player)));
    }

    @Override
    public CompletableFuture<List<TeleportRecord>> getWarpHistory(String warp, int limit) {
        return supply(() -> readLog(limit,
                record -> record.type() == TeleportType.WARP
                        && warp.equalsIgnoreCase(record.destination())));
    }

    private List<TeleportRecord> readLog(int limit, java.util.function.Predicate<TeleportRecord> filter) {
        synchronized (logLock) {
            ConfigurationSection entries = logConfig.getConfigurationSection("entries");
            if (entries == null) {
                return new ArrayList<>();
            }

            List<TeleportRecord> matches = new ArrayList<>();

            for (String key : entries.getKeys(false)) {
                TeleportRecord record = readRecord(entries, key);
                if (record != null && filter.test(record)) {
                    matches.add(record);
                }
            }

            matches.sort(Comparator.comparingLong(TeleportRecord::timestamp).reversed());
            return matches.size() <= limit ? matches : new ArrayList<>(matches.subList(0, Math.max(1, limit)));
        }
    }

    private TeleportRecord readRecord(ConfigurationSection entries, String key) {
        String player = entries.getString(key + ".player");
        if (player == null) {
            return null;
        }

        TeleportType type;
        try {
            type = TeleportType.valueOf(entries.getString(key + ".type", "WARP"));
        } catch (IllegalArgumentException e) {
            type = TeleportType.WARP;
        }

        long id;
        try {
            id = Long.parseLong(key);
        } catch (NumberFormatException e) {
            id = 0L;
        }

        try {
            return new TeleportRecord(id, UUID.fromString(player),
                    entries.getString(key + ".player-name"), type,
                    entries.getString(key + ".destination"),
                    entries.getString(key + ".from-server"), entries.getString(key + ".from-world"),
                    entries.getDouble(key + ".from-x"), entries.getDouble(key + ".from-y"),
                    entries.getDouble(key + ".from-z"),
                    entries.getString(key + ".to-server"), entries.getString(key + ".to-world"),
                    entries.getDouble(key + ".to-x"), entries.getDouble(key + ".to-y"),
                    entries.getDouble(key + ".to-z"),
                    entries.getDouble(key + ".cost"), entries.getLong(key + ".created-at"),
                    entries.getString(key + ".result", TeleportRecord.SUCCESS));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public CompletableFuture<Integer> purgeTeleportHistory(long cutoff) {
        return supply(() -> {
            synchronized (logLock) {
                ConfigurationSection entries = logConfig.getConfigurationSection("entries");
                if (entries == null) {
                    return 0;
                }

                List<String> stale = new ArrayList<>();
                for (String key : entries.getKeys(false)) {
                    if (entries.getLong(key + ".created-at") < cutoff) {
                        stale.add(key);
                    }
                }

                if (stale.isEmpty()) {
                    return 0;
                }

                stale.forEach(key -> logConfig.set("entries." + key, null));
                saveAtomically(logConfig, logFile);
                return stale.size();
            }
        });
    }

    @Override
    public CompletableFuture<Void> shareHome(HomeShare share) {
        return run(() -> {
            synchronized (sharesLock) {
                String path = sharePath(share.owner(), share.home(), share.invitee());

                sharesConfig.set(path + ".owner-name", share.ownerName());
                sharesConfig.set(path + ".invitee-name", share.inviteeName());
                sharesConfig.set(path + ".granted-at", share.grantedAt());
                sharesConfig.set(path + ".expires-at", share.expiresAt());

                saveAtomically(sharesConfig, sharesFile);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> unshareHome(UUID owner, String home, UUID invitee) {
        return supply(() -> {
            synchronized (sharesLock) {
                String path = sharePath(owner, home, invitee);
                if (!sharesConfig.contains(path)) {
                    return false;
                }

                sharesConfig.set(path, null);
                saveAtomically(sharesConfig, sharesFile);
                return true;
            }
        });
    }

    @Override
    public CompletableFuture<List<HomeShare>> getSharesByOwner(UUID owner) {
        return supply(() -> {
            synchronized (sharesLock) {
                List<HomeShare> shares = new ArrayList<>();
                ConfigurationSection homes = sharesConfig.getConfigurationSection("shares." + owner);

                if (homes == null) {
                    return shares;
                }

                for (String home : homes.getKeys(false)) {
                    ConfigurationSection invitees = homes.getConfigurationSection(home);
                    if (invitees == null) {
                        continue;
                    }

                    for (String invitee : invitees.getKeys(false)) {
                        HomeShare share = readShare(owner, home, invitee, invitees.getConfigurationSection(invitee));
                        if (share != null) {
                            shares.add(share);
                        }
                    }
                }

                return shares;
            }
        });
    }

    @Override
    public CompletableFuture<List<HomeShare>> getSharesForInvitee(UUID invitee) {
        return supply(() -> {
            synchronized (sharesLock) {
                List<HomeShare> shares = new ArrayList<>();
                ConfigurationSection owners = sharesConfig.getConfigurationSection("shares");

                if (owners == null) {
                    return shares;
                }

                String wanted = invitee.toString();

                for (String owner : owners.getKeys(false)) {
                    ConfigurationSection homes = owners.getConfigurationSection(owner);
                    if (homes == null) {
                        continue;
                    }

                    UUID ownerId;
                    try {
                        ownerId = UUID.fromString(owner);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }

                    for (String home : homes.getKeys(false)) {
                        ConfigurationSection section = homes.getConfigurationSection(home + "." + wanted);
                        HomeShare share = readShare(ownerId, home, wanted, section);
                        if (share != null) {
                            shares.add(share);
                        }
                    }
                }

                return shares;
            }
        });
    }

    private HomeShare readShare(UUID owner, String home, String invitee, ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        try {
            return new HomeShare(owner, section.getString("owner-name"), home,
                    UUID.fromString(invitee), section.getString("invitee-name"),
                    section.getLong("granted-at"), section.getLong("expires-at"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public CompletableFuture<Void> clearSharesOf(UUID owner, String home) {
        return run(() -> {
            synchronized (sharesLock) {
                String path = "shares." + owner + "." + home;
                if (!sharesConfig.contains(path)) {
                    return;
                }

                sharesConfig.set(path, null);
                saveAtomically(sharesConfig, sharesFile);
            }
        });
    }

    private static String sharePath(UUID owner, String home, UUID invitee) {
        return "shares." + owner + "." + home + "." + invitee;
    }

    @Override
    public CompletableFuture<Set<UUID>> getTpaBlocks(UUID owner) {
        return supply(() -> {
            synchronized (settingsLock) {
                Set<UUID> blocked = new HashSet<>();

                for (String value : settingsConfig.getStringList("players." + owner + ".tpa-blocks")) {
                    try {
                        blocked.add(UUID.fromString(value));
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                return blocked;
            }
        });
    }

    @Override
    public CompletableFuture<Void> addTpaBlock(UUID owner, UUID blocked) {
        return run(() -> {
            synchronized (settingsLock) {
                String path = "players." + owner + ".tpa-blocks";
                List<String> current = new ArrayList<>(settingsConfig.getStringList(path));

                if (current.contains(blocked.toString())) {
                    return;
                }

                current.add(blocked.toString());
                settingsConfig.set(path, current);
                saveAtomically(settingsConfig, settingsFile);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> removeTpaBlock(UUID owner, UUID blocked) {
        return supply(() -> {
            synchronized (settingsLock) {
                String path = "players." + owner + ".tpa-blocks";
                List<String> current = new ArrayList<>(settingsConfig.getStringList(path));

                if (!current.remove(blocked.toString())) {
                    return false;
                }

                settingsConfig.set(path, current.isEmpty() ? null : current);
                saveAtomically(settingsConfig, settingsFile);
                return true;
            }
        });
    }

    @Override
    public CompletableFuture<List<DestinationLock>> getLocks() {
        return supply(() -> {
            synchronized (locksLock) {
                List<DestinationLock> locks = new ArrayList<>();
                ConfigurationSection section = locksConfig.getConfigurationSection("locks");

                if (section == null) {
                    return locks;
                }

                for (String key : section.getKeys(false)) {
                    String type = section.getString(key + ".type");
                    String target = section.getString(key + ".target");

                    if (type == null || target == null) {
                        continue;
                    }

                    locks.add(new DestinationLock(type, target,
                            section.getString(key + ".reason"),
                            section.getString(key + ".locked-by"),
                            section.getLong(key + ".locked-at"),
                            section.getLong(key + ".until")));
                }

                return locks;
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveLock(DestinationLock lock) {
        return run(() -> {
            synchronized (locksLock) {
                String path = "locks." + lockKey(lock.type(), lock.target());

                locksConfig.set(path + ".type", lock.type());
                locksConfig.set(path + ".target", lock.target());
                locksConfig.set(path + ".reason", lock.reason());
                locksConfig.set(path + ".locked-by", lock.lockedBy());
                locksConfig.set(path + ".locked-at", lock.lockedAt());
                locksConfig.set(path + ".until", lock.until());

                saveAtomically(locksConfig, locksFile);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteLock(String type, String target) {
        return supply(() -> {
            synchronized (locksLock) {
                String path = "locks." + lockKey(DestinationLock.normalize(type),
                        DestinationLock.normalize(target));

                if (!locksConfig.contains(path)) {
                    return false;
                }

                locksConfig.set(path, null);
                saveAtomically(locksConfig, locksFile);
                return true;
            }
        });
    }

    private static String lockKey(String type, String target) {
        return type + "__" + target.replace('.', '_');
    }

    private Object lockFor(UUID owner) {
        return homeLocks.computeIfAbsent(owner, key -> new Object());
    }

    private File homeFile(UUID owner) {
        return new File(homesFolder, owner.toString() + ".yml");
    }

    private static String spawnPath(String server) {
        return "spawn." + (server == null ? "unnamed-server" : server.toLowerCase(Locale.ROOT));
    }

    private void saveAtomically(YamlConfiguration config, File target) {
        Path destination = target.toPath();
        Path temporary = destination.resolveSibling(target.getName() + ".tmp");

        try {
            config.save(temporary.toFile());

            try {
                Files.move(temporary, destination,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Failed to write " + target.getName(), e);
        }
    }
}
