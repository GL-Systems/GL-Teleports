package org.glstudio.teleports.core;

import lombok.Getter;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;

import java.util.List;

/**
 * Holds every file of the plugin.
 */
@Getter
public class ConfigManager {
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
        this.mainConfig = new ConfigFile(plugin, "config.yml");
        this.languageConfig = new ConfigFile(plugin, "language.yml");
        this.databaseConfig = new ConfigFile(plugin, "database.yml");

        this.homesMenu = new ConfigFile(plugin, "menus", "teleports-homes.yml");

        this.playerWarpsConfig = new ConfigFile(plugin, "player-warps.yml");

        this.playerWarpsMainMenu = new ConfigFile(plugin, "menus", "player-warps-main.yml");
        this.playerWarpsListMenu = new ConfigFile(plugin, "menus", "player-warps-list.yml");
        this.playerWarpsInfoMenu = new ConfigFile(plugin, "menus", "player-warps-info.yml");
        this.playerWarpsConfirmMenu = new ConfigFile(plugin, "menus", "player-warps-confirm.yml");
        this.playerWarpsManageMenu = new ConfigFile(plugin, "menus", "player-warps-manage.yml");
        this.playerWarpsCategoriesMenu = new ConfigFile(plugin, "menus", "player-warps-categories.yml");
        this.playerWarpsRatingMenu = new ConfigFile(plugin, "menus", "player-warps-rating.yml");
        this.playerWarpsWhitelistMenu = new ConfigFile(plugin, "menus", "player-warps-whitelist.yml");
        this.playerWarpsBansMenu = new ConfigFile(plugin, "menus", "player-warps-bans.yml");
        this.playerWarpsManagersMenu = new ConfigFile(plugin, "menus", "player-warps-managers.yml");
        this.playerWarpsTransferMenu = new ConfigFile(plugin, "menus", "player-warps-transfer.yml");
        this.playerWarpsAdminMenu = new ConfigFile(plugin, "menus", "player-warps-admin.yml");
    }

    /**
     * Lists the plugin's files.
     *
     * @return every file
     */
    public List<ConfigFile> all() {
        return List.of(
                mainConfig, languageConfig, databaseConfig, homesMenu,
                playerWarpsConfig, playerWarpsMainMenu, playerWarpsListMenu, playerWarpsInfoMenu,
                playerWarpsConfirmMenu, playerWarpsManageMenu, playerWarpsCategoriesMenu, playerWarpsRatingMenu,
                playerWarpsWhitelistMenu, playerWarpsBansMenu, playerWarpsManagersMenu, playerWarpsTransferMenu,
                playerWarpsAdminMenu);
    }

    /** Reloads every file from disk. */
    public void reload() {
        all().forEach(ConfigFile::reload);
    }
}
