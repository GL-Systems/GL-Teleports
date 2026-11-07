package org.glstudio.teleports.modules.manager;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Permissions;

/**
 * Works out how many warps a player may own.
 */
public class PlayerWarpLimits {
    public static final int UNLIMITED = -1;

    private final GTeleports plugin;

    /**
     * Creates the player warp limits.
     */
    public PlayerWarpLimits(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the limit.
     */
    public int getLimit(Player player) {
        if (player.hasPermission(Permissions.PLAYERWARP_LIMIT_UNLIMITED)) {
            return UNLIMITED;
        }

        int best = plugin.getConfigManager().getPlayerWarpsConfig().getInt("limits.default", 1);

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }

            String permission = info.getPermission();
            if (!permission.startsWith(Permissions.PLAYERWARP_LIMIT_PREFIX)) {
                continue;
            }

            String suffix = permission.substring(Permissions.PLAYERWARP_LIMIT_PREFIX.length());
            try {
                best = Math.max(best, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
            }
        }

        return best;
    }
}
