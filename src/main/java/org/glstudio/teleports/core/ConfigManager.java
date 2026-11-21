package org.glstudio.teleports.core;

import lombok.Getter;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.nexus.utils.ConfigSet;
import org.glstudio.teleports.GTeleports;

/**
 * Holds every file of the plugin.
 */
@Getter
public class ConfigManager extends ConfigSet {
    private final ConfigFile mainConfig, languageConfig, databaseConfig;

    private final ConfigFile homesMenu;

    private final ConfigFile playerWarpsConfig;

    private final ConfigFile playerWarpsMainMenu;
    private final ConfigFile playerWarpsListMenu;
    private final ConfigFile playerWarpsInfoMenu;
    private final ConfigFile playerWarpsConfirmMenu;
    private final ConfigFile playerWarpsManageMenu;
    private final ConfigFile playerWarpsCategoriesMenu;
    private final ConfigFile playerWarpsRatingMenu;
    private final ConfigFile playerWarpsWhitelistMenu;
    private final ConfigFile playerWarpsBansMenu;
    private final ConfigFile playerWarpsManagersMenu;
    private final ConfigFile playerWarpsTransferMenu;
    private final ConfigFile playerWarpsAdminMenu;

    /**
     * Creates the manager.
     */
    public ConfigManager(GTeleports plugin) {
        this.mainConfig = track(new ConfigFile(plugin, "config.yml"));
        this.languageConfig = track(new ConfigFile(plugin, "language.yml"));
        this.databaseConfig = track(new ConfigFile(plugin, "database.yml"));

        this.homesMenu = track(new ConfigFile(plugin, "menus", "teleports-homes.yml"));

        this.playerWarpsConfig = track(new ConfigFile(plugin, "player-warps.yml"));

        this.playerWarpsMainMenu = track(new ConfigFile(plugin, "menus", "player-warps-main.yml"));
        this.playerWarpsListMenu = track(new ConfigFile(plugin, "menus", "player-warps-list.yml"));
        this.playerWarpsInfoMenu = track(new ConfigFile(plugin, "menus", "player-warps-info.yml"));
        this.playerWarpsConfirmMenu = track(new ConfigFile(plugin, "menus", "player-warps-confirm.yml"));
        this.playerWarpsManageMenu = track(new ConfigFile(plugin, "menus", "player-warps-manage.yml"));
        this.playerWarpsCategoriesMenu = track(new ConfigFile(plugin, "menus", "player-warps-categories.yml"));
        this.playerWarpsRatingMenu = track(new ConfigFile(plugin, "menus", "player-warps-rating.yml"));
        this.playerWarpsWhitelistMenu = track(new ConfigFile(plugin, "menus", "player-warps-whitelist.yml"));
        this.playerWarpsBansMenu = track(new ConfigFile(plugin, "menus", "player-warps-bans.yml"));
        this.playerWarpsManagersMenu = track(new ConfigFile(plugin, "menus", "player-warps-managers.yml"));
        this.playerWarpsTransferMenu = track(new ConfigFile(plugin, "menus", "player-warps-transfer.yml"));
        this.playerWarpsAdminMenu = track(new ConfigFile(plugin, "menus", "player-warps-admin.yml"));
    }
}
