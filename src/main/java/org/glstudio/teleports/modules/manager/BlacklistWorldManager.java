package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.World;

import java.util.List;

public class BlacklistWorldManager {

    private final GTeleports plugin;

    public BlacklistWorldManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public boolean isBlacklisted(World world, TeleportType type) {
        String path = "blacklist." + type.getConfigKey();
        List<String> blacklistedWorlds = plugin.getConfigManager().getMainConfig().getStringList(path);

        return blacklistedWorlds.contains(world.getName());
    }

    public List<String> getBlacklistedWorlds(TeleportType type) {
        String path = "blacklist." + type.getConfigKey();
        return plugin.getConfigManager().getMainConfig().getStringList(path);
    }
}
