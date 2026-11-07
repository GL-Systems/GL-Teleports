package org.glstudio.teleports.modules.model;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.glstudio.teleports.GTeleports;

/**
 * The costs and waits of a teleport.
 */
public record TeleportRules(double cost, int cooldownSeconds, int delaySeconds) {
    public static final String COOLDOWN_PERMISSION = "golden.teleports.cooldown.";

    public static final String DELAY_PERMISSION = "golden.teleports.delay.";

    /**
     * Returns the rules from the settings.
     */
    public static TeleportRules global(GTeleports plugin, TeleportType type) {
        String base = "teleport-settings." + type.getConfigKey() + ".";

        return new TeleportRules(
                plugin.getSettings().getDouble(base + "cost", 0.0D),
                plugin.getSettings().getInt(base + "cooldown", 0),
                plugin.getSettings().getInt(base + "delay", 0));
    }

    /**
     * Returns the rules that apply to a player.
     */
    public static TeleportRules resolve(GTeleports plugin, Player player, TeleportType type,
                                        TeleportDestination destination) {
        TeleportRules base = global(plugin, type);

        double cost = base.cost();
        int cooldown = base.cooldownSeconds();
        int delay = base.delaySeconds();

        if (destination instanceof Warp warp) {
            if (warp.getCost() != null) {
                cost = warp.getCost();
            }
            if (warp.getCooldown() != null) {
                cooldown = warp.getCooldown();
            }
            if (warp.getDelay() != null) {
                delay = warp.getDelay();
            }
        } else if (destination instanceof PlayerWarp playerWarp) {
            if (playerWarp.getCost() != null) {
                cost = playerWarp.getCost();
            }
            if (playerWarp.getCooldown() != null) {
                cooldown = playerWarp.getCooldown();
            }
            if (playerWarp.getDelay() != null) {
                delay = playerWarp.getDelay();
            }
        }

        cooldown = Math.min(cooldown, lowestGranted(player, COOLDOWN_PERMISSION + type.getConfigKey(), cooldown));
        delay = Math.min(delay, lowestGranted(player, DELAY_PERMISSION + type.getConfigKey(), delay));

        return new TeleportRules(Math.max(0.0D, cost), Math.max(0, cooldown), Math.max(0, delay));
    }

    private static int lowestGranted(Player player, String prefix, int fallback) {
        String dotted = prefix + ".";
        int best = fallback;

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }

            String permission = info.getPermission();
            if (!permission.startsWith(dotted)) {
                continue;
            }

            try {
                best = Math.min(best, Integer.parseInt(permission.substring(dotted.length())));
            } catch (NumberFormatException ignored) {
            }
        }

        return best;
    }
}
