package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public class BlacklistWorldManager {
    public static final String BYPASS_PERMISSION = Permissions.BYPASS_BLACKLIST;

    private final GTeleports plugin;

    public BlacklistWorldManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public boolean isBlacklisted(Player player, World world, TeleportType type) {
        if (world == null) {
            return false;
        }

        if (player != null && player.hasPermission(BYPASS_PERMISSION)) {
            return false;
        }

        for (String blacklisted : getBlacklistedWorlds(type)) {
            if (blacklisted.equalsIgnoreCase(world.getName())) {
                return true;
            }
        }

        return false;
    }

    public List<String> getBlacklistedWorlds(TeleportType type) {
        String path = "blacklist." + type.getConfigKey();
        return plugin.getSettings().getStringList(path);
    }
}
