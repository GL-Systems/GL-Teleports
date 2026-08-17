package org.glstudio.teleports.modules.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.manager.HomeManager;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.teleports.modules.model.Warp;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class TeleportsExpansion extends PlaceholderExpansion {
    private final GTeleports plugin;

    public TeleportsExpansion(GTeleports plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "glteleports";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);

        switch (key) {
            case "server" -> {
                return plugin.getCrossServerManager().currentServer();
            }
            case "network" -> {
                return String.valueOf(plugin.getCrossServerManager().isEnabled());
            }
            case "warps_total" -> {
                return String.valueOf(plugin.getWarpManager().getCachedWarps().size());
            }
            default -> {
            }
        }

        if (!(offline instanceof Player player) || !player.isOnline()) {
            return null;
        }

        return forPlayer(player, key);
    }

    private String forPlayer(Player player, String key) {
        switch (key) {
            case "homes_used" -> {
                return String.valueOf(plugin.getHomeManager().getCachedHomes(player.getUniqueId()).size());
            }
            case "homes_limit" -> {
                int limit = plugin.getHomeManager().getHomeLimit(player);
                return limit == HomeManager.UNLIMITED
                        ? plugin.getMessages().raw("placeholders.unlimited") : String.valueOf(limit);
            }
            case "homes_remaining" -> {
                int limit = plugin.getHomeManager().getHomeLimit(player);
                if (limit == HomeManager.UNLIMITED) {
                    return plugin.getMessages().raw("placeholders.unlimited");
                }
                int used = plugin.getHomeManager().getCachedHomes(player.getUniqueId()).size();
                return String.valueOf(Math.max(0, limit - used));
            }
            case "tpa_pending" -> {
                return String.valueOf(plugin.getTpaManager().pendingRequesterNames(player).size());
            }
            case "tpa_outgoing" -> {
                return String.valueOf(plugin.getTpaManager().outgoingTargetNames(player).size());
            }
            case "warps_available" -> {
                return String.valueOf(availableWarps(player));
            }
            default -> {
            }
        }

        if (key.startsWith("cooldown_")) {
            return cooldown(player, key.substring("cooldown_".length()));
        }

        return null;
    }

    private long availableWarps(Player player) {
        List<Warp> warps = plugin.getWarpManager().getCachedWarps();

        return warps.stream()
                .filter(warp -> !warp.hasPermission() || player.hasPermission(warp.getPermission()))
                .filter(warp -> !plugin.getLockManager().isWarpLocked(warp.getName()))
                .count();
    }

    private String cooldown(Player player, String rawType) {
        try {
            TeleportType type = TeleportType.valueOf(rawType.toUpperCase(Locale.ROOT));
            return String.valueOf(plugin.getCooldownManager().getRemainingCooldown(player, type));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
