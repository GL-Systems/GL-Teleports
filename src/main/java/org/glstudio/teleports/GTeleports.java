package org.glstudio.teleports;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;
import org.glstudio.nexus.api.NexusAPI;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.nexus.modules.menu.MenuManager;
import org.glstudio.teleports.common.network.PacketHandler;
import org.glstudio.teleports.common.storage.redis.PlayerRegistry;
import org.glstudio.teleports.common.storage.redis.RedisService;
import org.glstudio.teleports.modules.service.BackupService;
import org.glstudio.teleports.core.CommandRegistry;
import org.glstudio.teleports.core.ConfigManager;
import org.glstudio.teleports.modules.manager.Confirmations;
import org.glstudio.teleports.common.config.Messages;
import org.glstudio.teleports.common.config.MultiServerSettings;
import org.glstudio.teleports.core.MultiServerSupervisor;
import org.glstudio.teleports.common.config.Settings;
import org.glstudio.teleports.core.ShutdownService;
import org.glstudio.teleports.core.StartupService;
import org.glstudio.teleports.common.concurrent.TeleportExecutors;
import org.glstudio.teleports.modules.database.DatabaseManager;
import org.glstudio.teleports.modules.manager.BedRespawnManager;
import org.glstudio.teleports.modules.manager.BlacklistWorldManager;
import org.glstudio.teleports.modules.manager.CooldownManager;
import org.glstudio.teleports.modules.manager.CrossServerManager;
import org.glstudio.teleports.modules.manager.DelayManager;
import org.glstudio.teleports.modules.manager.EconomyManager;
import org.glstudio.teleports.modules.manager.EffectManager;
import org.glstudio.teleports.modules.manager.HistoryManager;
import org.glstudio.teleports.modules.manager.HomeManager;
import org.glstudio.teleports.modules.manager.HomeShareManager;
import org.glstudio.teleports.modules.manager.LastLocationManager;
import org.glstudio.teleports.modules.manager.LockManager;
import org.glstudio.teleports.modules.manager.BackManager;
import org.glstudio.teleports.modules.manager.ChatInputManager;
import org.glstudio.teleports.modules.manager.PlayerSettingsManager;
import org.glstudio.teleports.modules.manager.PlayerWarpBanManager;
import org.glstudio.teleports.modules.manager.PlayerWarpCategoryRegistry;
import org.glstudio.teleports.modules.manager.PlayerWarpFavouriteManager;
import org.glstudio.teleports.modules.manager.PlayerWarpLimits;
import org.glstudio.teleports.modules.manager.PlayerWarpManager;
import org.glstudio.teleports.modules.manager.PlayerWarpManagerRoleManager;
import org.glstudio.teleports.modules.manager.PlayerWarpRatingManager;
import org.glstudio.teleports.modules.manager.PlayerWarpSponsorManager;
import org.glstudio.teleports.modules.manager.PlayerWarpWhitelistManager;
import org.glstudio.teleports.modules.service.PlayerWarpPurgeService;
import org.glstudio.teleports.modules.manager.SpawnManager;
import org.glstudio.teleports.modules.manager.TeleportManager;
import org.glstudio.teleports.modules.manager.TeleportQueue;
import org.glstudio.teleports.modules.manager.TpaBlockManager;
import org.glstudio.teleports.modules.manager.TeleportSafetyManager;
import org.glstudio.teleports.modules.manager.TpaManager;
import org.glstudio.teleports.modules.manager.WarpManager;
import org.glstudio.teleports.modules.migration.MigrationManager;
import org.glstudio.teleports.modules.transfer.PlayerTransferService;

@Getter
@Setter
public final class GTeleports extends JavaPlugin {
    private StartupService startupService;
    private ShutdownService shutdownService;

    private ConfigManager configManager;
    private CommandManager commandManager;
    private CommandRegistry commandRegistry;
    private Messages messages;
    private Settings settings;
    private Confirmations confirmations;
    private BackupService backupService;

    private TeleportExecutors executors;

    private DatabaseManager databaseManager;

    private MultiServerSettings multiServerSettings;
    private MultiServerSupervisor multiServerSupervisor;
    private RedisService redisService;
    private PacketHandler packetHandler;
    private PlayerTransferService playerTransferService;
    private PlayerRegistry playerRegistry;

    private HomeManager homeManager;
    private TpaManager tpaManager;
    private WarpManager warpManager;
    private SpawnManager spawnManager;
    private EffectManager effectManager;
    private TeleportManager teleportManager;
    private CooldownManager cooldownManager;
    private DelayManager delayManager;
    private EconomyManager economyManager;
    private BlacklistWorldManager blacklistWorldManager;
    private TeleportSafetyManager teleportSafetyManager;
    private CrossServerManager crossServerManager;
    private LastLocationManager lastLocationManager;
    private BedRespawnManager bedRespawnManager;
    private PlayerSettingsManager playerSettings;
    private HistoryManager historyManager;
    private HomeShareManager homeShareManager;
    private TpaBlockManager tpaBlockManager;
    private LockManager lockManager;
    private TeleportQueue teleportQueue;

    private PlayerWarpManager playerWarpManager;
    private PlayerWarpWhitelistManager playerWarpWhitelistManager;
    private PlayerWarpBanManager playerWarpBanManager;
    private PlayerWarpManagerRoleManager playerWarpManagerRoleManager;
    private PlayerWarpRatingManager playerWarpRatingManager;
    private PlayerWarpFavouriteManager playerWarpFavouriteManager;
    private PlayerWarpSponsorManager playerWarpSponsorManager;
    private PlayerWarpCategoryRegistry playerWarpCategoryRegistry;
    private PlayerWarpLimits playerWarpLimits;
    private PlayerWarpPurgeService playerWarpPurgeService;
    private ChatInputManager chatInputManager;
    private BackManager backManager;
    private MigrationManager migrationManager;

    private MenuManager menuManager;

    @Override
    public void onEnable() {
        commandManager = NexusAPI.get().createCommandManager(this);

        startupService = new StartupService(this);

        if (!startupService.initialize()) {
            LoggerUtils.logError("Plugin initialization failed! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (shutdownService == null) {
            shutdownService = new ShutdownService(this);
        }

        shutdownService.shutdown();
    }
}
