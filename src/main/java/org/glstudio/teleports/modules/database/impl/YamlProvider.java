package org.glstudio.teleports.modules.database.impl;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.database.DatabaseProvider;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.Warp;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class YamlProvider implements DatabaseProvider {

    private final GTeleports plugin;
    private final File dataFolder;
    private final File warpsFile;
    private final File spawnFile;
    private final File homesFolder;

    private YamlConfiguration warpsConfig;
    private YamlConfiguration spawnConfig;

    public YamlProvider(GTeleports plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        this.warpsFile = new File(dataFolder, "warps.yml");
        this.spawnFile = new File(dataFolder, "spawn.yml");
        this.homesFolder = new File(dataFolder, "homes");
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
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

                warpsConfig = YamlConfiguration.loadConfiguration(warpsFile);
                spawnConfig = YamlConfiguration.loadConfiguration(spawnFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize YAML provider", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Home>> getHomes(UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
            File homeFile = new File(homesFolder, owner.toString() + ".yml");
            if (!homeFile.exists()) {
                return new ArrayList<>();
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(homeFile);
            List<Home> homes = new ArrayList<>();

            ConfigurationSection homesSection = config.getConfigurationSection("homes");
            if (homesSection != null) {
                for (String homeName : homesSection.getKeys(false)) {
                    String path = "homes." + homeName;
                    Home home = new Home(
                            owner,
                            homeName,
                            config.getString(path + ".world"),
                            config.getDouble(path + ".x"),
                            config.getDouble(path + ".y"),
                            config.getDouble(path + ".z"),
                            (float) config.getDouble(path + ".yaw"),
                            (float) config.getDouble(path + ".pitch"),
                            config.getLong(path + ".created-at"),
                            config.getString(path + ".server")
                    );
                    homes.add(home);
                }
            }

            return homes;
        });
    }

    @Override
    public CompletableFuture<Home> getHome(UUID owner, String name) {
        return CompletableFuture.supplyAsync(() -> {
            File homeFile = new File(homesFolder, owner.toString() + ".yml");
            if (!homeFile.exists()) {
                return null;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(homeFile);
            String path = "homes." + name;

            if (!config.contains(path)) {
                return null;
            }

            return new Home(
                    owner,
                    name,
                    config.getString(path + ".world"),
                    config.getDouble(path + ".x"),
                    config.getDouble(path + ".y"),
                    config.getDouble(path + ".z"),
                    (float) config.getDouble(path + ".yaw"),
                    (float) config.getDouble(path + ".pitch"),
                    config.getLong(path + ".created-at"),
                    config.getString(path + ".server")
            );
        });
    }

    @Override
    public CompletableFuture<Void> saveHome(Home home) {
        return CompletableFuture.runAsync(() -> {
            try {
                File homeFile = new File(homesFolder, home.getOwner().toString() + ".yml");
                if (!homeFile.exists()) {
                    homeFile.createNewFile();
                }

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

                config.save(homeFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save home", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteHome(UUID owner, String name) {
        return CompletableFuture.runAsync(() -> {
            try {
                File homeFile = new File(homesFolder, owner.toString() + ".yml");
                if (!homeFile.exists()) {
                    return;
                }

                YamlConfiguration config = YamlConfiguration.loadConfiguration(homeFile);
                config.set("homes." + name, null);

                ConfigurationSection homesSection = config.getConfigurationSection("homes");
                if (homesSection == null || homesSection.getKeys(false).isEmpty()) {
                    homeFile.delete();
                } else {
                    config.save(homeFile);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete home", e);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> getHomeCount(UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
            File homeFile = new File(homesFolder, owner.toString() + ".yml");
            if (!homeFile.exists()) {
                return 0;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(homeFile);
            ConfigurationSection homesSection = config.getConfigurationSection("homes");

            return homesSection != null ? homesSection.getKeys(false).size() : 0;
        });
    }

    @Override
    public CompletableFuture<List<Warp>> getAllWarps() {
        return CompletableFuture.supplyAsync(() -> {
            List<Warp> warps = new ArrayList<>();
            ConfigurationSection warpsSection = warpsConfig.getConfigurationSection("warps");

            if (warpsSection != null) {
                for (String warpName : warpsSection.getKeys(false)) {
                    String path = "warps." + warpName;
                    Warp warp = new Warp(
                            warpName,
                            warpsConfig.getString(path + ".world"),
                            warpsConfig.getDouble(path + ".x"),
                            warpsConfig.getDouble(path + ".y"),
                            warpsConfig.getDouble(path + ".z"),
                            (float) warpsConfig.getDouble(path + ".yaw"),
                            (float) warpsConfig.getDouble(path + ".pitch"),
                            warpsConfig.getString(path + ".permission"),
                            warpsConfig.getLong(path + ".created-at"),
                            warpsConfig.getString(path + ".server")
                    );
                    warps.add(warp);
                }
            }

            return warps;
        });
    }

    @Override
    public CompletableFuture<Warp> getWarp(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String path = "warps." + name;
            if (!warpsConfig.contains(path)) {
                return null;
            }

            return new Warp(
                    name,
                    warpsConfig.getString(path + ".world"),
                    warpsConfig.getDouble(path + ".x"),
                    warpsConfig.getDouble(path + ".y"),
                    warpsConfig.getDouble(path + ".z"),
                    (float) warpsConfig.getDouble(path + ".yaw"),
                    (float) warpsConfig.getDouble(path + ".pitch"),
                    warpsConfig.getString(path + ".permission"),
                    warpsConfig.getLong(path + ".created-at"),
                    warpsConfig.getString(path + ".server")
            );
        });
    }

    @Override
    public CompletableFuture<Void> saveWarp(Warp warp) {
        return CompletableFuture.runAsync(() -> {
            try {
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

                warpsConfig.save(warpsFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save warp", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteWarp(String name) {
        return CompletableFuture.runAsync(() -> {
            try {
                warpsConfig.set("warps." + name, null);
                warpsConfig.save(warpsFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete warp", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> warpExists(String name) {
        return CompletableFuture.supplyAsync(() -> warpsConfig.contains("warps." + name));
    }

    @Override
    public CompletableFuture<SpawnLocation> getSpawn() {
        return CompletableFuture.supplyAsync(() -> {
            if (!spawnConfig.contains("spawn")) {
                return null;
            }

            return new SpawnLocation(
                    spawnConfig.getString("spawn.world"),
                    spawnConfig.getDouble("spawn.x"),
                    spawnConfig.getDouble("spawn.y"),
                    spawnConfig.getDouble("spawn.z"),
                    (float) spawnConfig.getDouble("spawn.yaw"),
                    (float) spawnConfig.getDouble("spawn.pitch"),
                    spawnConfig.getString("spawn.server")
            );
        });
    }

    @Override
    public CompletableFuture<Void> saveSpawn(SpawnLocation spawn) {
        return CompletableFuture.runAsync(() -> {
            try {
                spawnConfig.set("spawn.world", spawn.getWorld());
                spawnConfig.set("spawn.x", spawn.getX());
                spawnConfig.set("spawn.y", spawn.getY());
                spawnConfig.set("spawn.z", spawn.getZ());
                spawnConfig.set("spawn.yaw", spawn.getYaw());
                spawnConfig.set("spawn.pitch", spawn.getPitch());
                spawnConfig.set("spawn.server", spawn.getServer());

                spawnConfig.save(spawnFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save spawn", e);
            }
        });
    }
}
