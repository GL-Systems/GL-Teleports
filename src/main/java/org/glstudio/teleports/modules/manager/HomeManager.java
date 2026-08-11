package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class HomeManager {

    public static final int UNLIMITED = -1;

    private final GTeleports plugin;
    private final ConcurrentHashMap<UUID, List<Home>> homeCache;
    private final Set<UUID> warmingUp = ConcurrentHashMap.newKeySet();

    public HomeManager(GTeleports plugin) {
        this.plugin = plugin;
        this.homeCache = new ConcurrentHashMap<>();
    }

    public CompletableFuture<List<Home>> getHomes(UUID owner) {
        List<Home> cached = homeCache.get(owner);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return plugin.getDatabaseManager().getProvider().getHomes(owner)
                .thenApply(homes -> {
                    homeCache.put(owner, homes);
                    return homes;
                });
    }

    public List<Home> getCachedHomes(UUID owner) {
        List<Home> cached = homeCache.get(owner);
        if (cached != null) {
            return cached;
        }

        if (warmingUp.add(owner)) {
            getHomes(owner).whenComplete((homes, error) -> warmingUp.remove(owner));
        }
        return List.of();
    }

    public CompletableFuture<Home> getHome(UUID owner, String name) {
        return plugin.getDatabaseManager().getProvider().getHome(owner, name);
    }

    public CompletableFuture<Void> setHome(Player player, String name) {
        return getHomes(player.getUniqueId()).thenCompose(homes -> {
            int limit = getHomeLimit(player);
            boolean homeExists = homes.stream().anyMatch(h -> h.getName().equalsIgnoreCase(name));

            if (!homeExists && limit != UNLIMITED && homes.size() >= limit) {
                player.sendMessage(lang("errors.home-limit-reached")
                        .replace("{limit}", String.valueOf(limit)));
                return CompletableFuture.completedFuture(null);
            }

            Home home = new Home(player.getUniqueId(), name, player.getLocation(),
                    plugin.getCrossServerManager().currentServer());

            return plugin.getDatabaseManager().getProvider().saveHome(home)
                    .thenRun(() -> {
                        invalidateEverywhere(player.getUniqueId());
                        String message = lang(homeExists ? "home.updated" : "home.created");
                        player.sendMessage(message.replace("{name}", name));
                    });
        });
    }

    public CompletableFuture<Void> deleteHome(Player player, String name) {
        return getHome(player.getUniqueId(), name).thenCompose(existing -> {
            if (existing == null) {
                player.sendMessage(lang("errors.home-not-found").replace("{name}", name));
                return CompletableFuture.completedFuture(null);
            }

            return plugin.getDatabaseManager().getProvider().deleteHome(player.getUniqueId(), name)
                    .thenRun(() -> {
                        invalidateEverywhere(player.getUniqueId());
                        player.sendMessage(lang("home.deleted").replace("{name}", name));
                    });
        });
    }

    public CompletableFuture<Void> setHomeFor(CommandSender staff, UUID owner, String ownerName,
                                              String name, Location location) {
        return getHome(owner, name).thenCompose(existing -> {
            Home home = new Home(owner, name, location, plugin.getCrossServerManager().currentServer());

            return plugin.getDatabaseManager().getProvider().saveHome(home)
                    .thenRun(() -> {
                        invalidateEverywhere(owner);
                        String message = lang(existing != null ? "home.admin-updated" : "home.admin-created");
                        staff.sendMessage(message.replace("{name}", name).replace("{player}", ownerName));
                    });
        });
    }

    public CompletableFuture<Void> deleteHomeFor(CommandSender staff, UUID owner, String ownerName, String name) {
        return getHome(owner, name).thenCompose(existing -> {
            if (existing == null) {
                staff.sendMessage(lang("errors.home-not-found-other")
                        .replace("{name}", name)
                        .replace("{player}", ownerName));
                return CompletableFuture.completedFuture(null);
            }

            return plugin.getDatabaseManager().getProvider().deleteHome(owner, name)
                    .thenRun(() -> {
                        invalidateEverywhere(owner);
                        staff.sendMessage(lang("home.admin-deleted")
                                .replace("{name}", name)
                                .replace("{player}", ownerName));
                    });
        });
    }

    public void invalidate(UUID owner) {
        if (owner == null) {
            homeCache.clear();
            return;
        }
        homeCache.remove(owner);
    }

    private void invalidateEverywhere(UUID owner) {
        homeCache.remove(owner);
        plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.HOME, owner);
    }

    public void teleportToHome(Player player, String name) {
        getHome(player.getUniqueId(), name).thenAccept(home -> {
            if (home == null) {
                player.sendMessage(lang("errors.home-not-found").replace("{name}", name));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.getCrossServerManager().isLocal(home.getServer())) {
                    plugin.getCrossServerManager().sendTo(player, TeleportType.HOME, home);
                    return;
                }

                Location location = home.toLocation();
                if (location == null) {
                    player.sendMessage(lang("errors.world-not-found"));
                    return;
                }

                plugin.getTeleportManager().requestTeleport(player, location, TeleportType.HOME, success -> {
                });
            });
        });
    }

    public int getHomeLimit(Player player) {
        String basePerm = "golden.teleports.homes.limit.";

        if (player.hasPermission(basePerm + "unlimited")) {
            return UNLIMITED;
        }

        int maxLimit = plugin.getConfigManager().getMainConfig().getInt("home-limits.default", 3);

        if (player.hasPermission(basePerm + "owner")) {
            int limit = plugin.getConfigManager().getMainConfig().getInt("home-limits.permissions.owner", UNLIMITED);
            if (limit > maxLimit || limit == UNLIMITED) {
                maxLimit = limit;
            }
        } else if (player.hasPermission(basePerm + "staff")) {
            int limit = plugin.getConfigManager().getMainConfig().getInt("home-limits.permissions.staff", 10);
            if (limit > maxLimit) {
                maxLimit = limit;
            }
        } else if (player.hasPermission(basePerm + "vip")) {
            int limit = plugin.getConfigManager().getMainConfig().getInt("home-limits.permissions.vip", 5);
            if (limit > maxLimit) {
                maxLimit = limit;
            }
        }

        return maxLimit;
    }

    private String lang(String path) {
        return plugin.getConfigManager().getLanguageConfig().getString(path);
    }

    public void safeDisable() {
        homeCache.clear();
        warmingUp.clear();
    }
}
