package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.model.SafetyCheckResult;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class TeleportSafetyManager {
    public static final String BYPASS_PERMISSION = Permissions.BYPASS_SAFETY;

    private final GTeleports plugin;

    public TeleportSafetyManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public Location resolve(Player player, Location destination, TeleportType type) {
        if (player != null && player.hasPermission(BYPASS_PERMISSION)) {
            return destination;
        }

        if (checkSafety(destination, type).isSafe()) {
            return destination;
        }

        if (!plugin.getSettings().getBoolean("safety.relocate", true)) {
            return null;
        }

        int radius = Math.max(0, plugin.getSettings().getInt("safety.relocate-radius", 4));
        World world = destination.getWorld();

        for (int offset = 1; offset <= radius; offset++) {
            for (int direction : new int[]{1, -1}) {
                Location candidate = destination.clone().add(0, offset * direction, 0);

                if (candidate.getY() < world.getMinHeight() || candidate.getY() > world.getMaxHeight()) {
                    continue;
                }

                if (checkSafety(candidate, type).isSafe()) {
                    return candidate;
                }
            }
        }

        return null;
    }

    public SafetyCheckResult checkSafety(Location location, TeleportType type) {
        if (!plugin.getSettings().getBoolean("safety.enabled", true)) {
            return SafetyCheckResult.safe();
        }

        SafetyCheckResult heightCheck = checkHeight(location);
        if (!heightCheck.isSafe()) return heightCheck;

        SafetyCheckResult lavaCheck = checkLava(location);
        if (!lavaCheck.isSafe()) return lavaCheck;

        SafetyCheckResult suffocationCheck = checkSuffocation(location);
        if (!suffocationCheck.isSafe()) return suffocationCheck;

        SafetyCheckResult waterCheck = checkWaterTrap(location);
        if (!waterCheck.isSafe()) return waterCheck;

        SafetyCheckResult floorCheck = checkFloor(location);
        if (!floorCheck.isSafe()) return floorCheck;

        return SafetyCheckResult.safe();
    }

    private SafetyCheckResult checkHeight(Location location) {
        World world = location.getWorld();

        int minHeight = Math.max(world.getMinHeight(),
                plugin.getSettings().getInt("safety.min-height", world.getMinHeight()));
        int maxHeight = Math.min(world.getMaxHeight(),
                plugin.getSettings().getInt("safety.max-height", world.getMaxHeight()));

        if (location.getY() < minHeight) {
            return SafetyCheckResult.unsafe("void");
        }

        if (location.getY() > maxHeight) {
            return SafetyCheckResult.unsafe("too-high");
        }

        return SafetyCheckResult.safe();
    }

    private SafetyCheckResult checkLava(Location location) {
        if (!plugin.getSettings().getBoolean("safety.check-lava", true)) {
            return SafetyCheckResult.safe();
        }

        int radius = plugin.getSettings().getInt("safety.check-radius", 1);
        World world = location.getWorld();
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (world.getBlockAt(baseX + x, baseY + y, baseZ + z).getType() == Material.LAVA) {
                        return SafetyCheckResult.unsafe("lava");
                    }
                }
            }
        }

        return SafetyCheckResult.safe();
    }

    private SafetyCheckResult checkSuffocation(Location location) {
        if (!plugin.getSettings().getBoolean("safety.check-suffocation", true)) {
            return SafetyCheckResult.safe();
        }

        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);

        if (feet.getType().isSolid() || head.getType().isSolid()) {
            return SafetyCheckResult.unsafe("suffocation");
        }

        return SafetyCheckResult.safe();
    }

    private SafetyCheckResult checkWaterTrap(Location location) {
        if (!plugin.getSettings().getBoolean("safety.check-water-trap", true)) {
            return SafetyCheckResult.safe();
        }

        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);

        if (feet.getType() == Material.WATER || head.getType() == Material.WATER) {
            Block above = feet.getRelative(0, 2, 0);
            if (above.getType() == Material.WATER || above.getType().isSolid()) {
                return SafetyCheckResult.unsafe("water-trap");
            }
        }

        return SafetyCheckResult.safe();
    }

    private SafetyCheckResult checkFloor(Location location) {
        if (!plugin.getSettings().getBoolean("safety.require-floor", true)) {
            return SafetyCheckResult.safe();
        }

        Block below = location.getBlock().getRelative(0, -1, 0);

        if (below.getType().isSolid()) {
            return SafetyCheckResult.safe();
        }

        int maxSearchDown = plugin.getSettings().getInt("safety.max-search-down", 5);
        int minHeight = location.getWorld().getMinHeight();

        for (int y = -2; y >= -maxSearchDown; y--) {
            if (location.getBlockY() + y < minHeight) {
                break;
            }
            if (location.getBlock().getRelative(0, y, 0).getType().isSolid()) {
                return SafetyCheckResult.safe();
            }
        }

        return SafetyCheckResult.unsafe("no-floor");
    }
}
