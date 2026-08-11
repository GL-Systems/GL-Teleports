package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.SafetyCheckResult;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

public class TeleportSafetyManager {

    private final GTeleports plugin;

    public TeleportSafetyManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public SafetyCheckResult checkSafety(Location location, TeleportType type) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("safety.enabled", true)) {
            return SafetyCheckResult.safe();
        }

        SafetyCheckResult voidCheck = checkVoid(location);
        if (!voidCheck.isSafe()) return voidCheck;

        SafetyCheckResult lavaCheck = checkLava(location);
        if (!lavaCheck.isSafe()) return lavaCheck;

        SafetyCheckResult suffocationCheck = checkSuffocation(location);
        if (!suffocationCheck.isSafe()) return suffocationCheck;

        SafetyCheckResult waterCheck = checkWaterTrap(location);
        if (!waterCheck.isSafe()) return waterCheck;

        SafetyCheckResult heightCheck = checkHeight(location);
        if (!heightCheck.isSafe()) return heightCheck;

        SafetyCheckResult floorCheck = checkFloor(location);
        if (!floorCheck.isSafe()) return floorCheck;

        return SafetyCheckResult.safe();
    }

    private SafetyCheckResult checkVoid(Location location) {
        int minHeight = plugin.getConfigManager().getMainConfig().getInt("safety.min-height", 0);

        if (location.getY() < minHeight) {
            return SafetyCheckResult.unsafe("void");
        }

        return SafetyCheckResult.safe();
    }

    private SafetyCheckResult checkLava(Location location) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("safety.check-lava", true)) {
            return SafetyCheckResult.safe();
        }

        int radius = plugin.getConfigManager().getMainConfig().getInt("safety.check-radius", 2);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = location.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.LAVA) {
                        return SafetyCheckResult.unsafe("lava");
                    }
                }
            }
        }

        return SafetyCheckResult.safe();
    }

    private SafetyCheckResult checkSuffocation(Location location) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("safety.check-suffocation", true)) {
            return SafetyCheckResult.safe();
        }

        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();

        if (feet.getType().isSolid() || head.getType().isSolid()) {
            return SafetyCheckResult.unsafe("suffocation");
        }

        return SafetyCheckResult.safe();
    }

    private SafetyCheckResult checkWaterTrap(Location location) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("safety.check-water-trap", true)) {
            return SafetyCheckResult.safe();
        }

        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();

        if (feet.getType() == Material.WATER || head.getType() == Material.WATER) {
            Block above = location.clone().add(0, 2, 0).getBlock();
            if (above.getType() == Material.WATER || above.getType().isSolid()) {
                return SafetyCheckResult.unsafe("water-trap");
            }
        }

        return SafetyCheckResult.safe();
    }

    private SafetyCheckResult checkHeight(Location location) {
        int maxHeight = plugin.getConfigManager().getMainConfig().getInt("safety.max-height", 320);

        if (location.getY() > maxHeight) {
            return SafetyCheckResult.unsafe("too-high");
        }

        return SafetyCheckResult.safe();
    }

    private SafetyCheckResult checkFloor(Location location) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("safety.require-floor", true)) {
            return SafetyCheckResult.safe();
        }

        Block below = location.clone().add(0, -1, 0).getBlock();

        if (!below.getType().isSolid()) {
            boolean foundFloor = false;
            int maxSearchDown = plugin.getConfigManager().getMainConfig().getInt("safety.max-search-down", 5);

            for (int y = -2; y >= -maxSearchDown; y--) {
                Block check = location.clone().add(0, y, 0).getBlock();
                if (check.getType().isSolid()) {
                    foundFloor = true;
                    break;
                }
            }

            if (!foundFloor) {
                return SafetyCheckResult.unsafe("no-floor");
            }
        }

        return SafetyCheckResult.safe();
    }
}
