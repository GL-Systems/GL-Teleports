package org.glstudio.teleports.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.glstudio.nexus.api.NexusAPI;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.nexus.concurrent.NexusExecutors;
import org.glstudio.nexus.hooks.placeholder.PlaceholderHooks;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.nexus.modules.lang.LanguageFile;
import org.glstudio.nexus.proxy.ProxyMessenger;
import org.glstudio.nexus.utils.Diagnostics;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.nexus.utils.TextFormat;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.api.GLTeleportsAPI;
import org.glstudio.teleports.api.TeleportsAPI;
import org.glstudio.teleports.common.config.ConfigValidator;
import org.glstudio.teleports.common.config.MultiServerSettings;
import org.glstudio.teleports.common.network.PacketHandler;
import org.glstudio.teleports.common.storage.redis.PlayerRegistry;
import org.glstudio.teleports.common.storage.redis.RedisService;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.command.GLTeleportsCommand;
import org.glstudio.teleports.modules.command.home.DelHomeCommand;
import org.glstudio.teleports.modules.command.home.HomeCommand;
import org.glstudio.teleports.modules.command.home.HomeInviteCommand;
import org.glstudio.teleports.modules.command.home.HomeInvitesCommand;
import org.glstudio.teleports.modules.command.home.HomeUninviteCommand;
import org.glstudio.teleports.modules.command.home.HomesCommand;
import org.glstudio.teleports.modules.command.home.ListHomesCommand;
import org.glstudio.teleports.modules.command.home.MoveHomeCommand;
import org.glstudio.teleports.modules.command.home.RenameHomeCommand;
import org.glstudio.teleports.modules.command.home.SetHomeCommand;
import org.glstudio.teleports.modules.command.spawn.SetSpawnCommand;
import org.glstudio.teleports.modules.command.spawn.SpawnCommand;
import org.glstudio.teleports.modules.command.tpa.TpAcceptCommand;
import org.glstudio.teleports.modules.command.tpa.TpDenyCommand;
import org.glstudio.teleports.modules.command.tpa.TpaBlockCommand;
import org.glstudio.teleports.modules.command.tpa.TpaCancelCommand;
import org.glstudio.teleports.modules.command.tpa.TpaCommand;
import org.glstudio.teleports.modules.command.tpa.TpaHereCommand;
import org.glstudio.teleports.modules.command.tpa.TpaToggleCommand;
import org.glstudio.teleports.modules.command.tpoffline.TpOfflineCommand;
import org.glstudio.teleports.modules.command.warp.DelWarpCommand;
import org.glstudio.teleports.modules.command.warp.EditWarpCommand;
import org.glstudio.teleports.modules.command.warp.SetWarpCommand;
import org.glstudio.teleports.modules.command.warp.WarpCommand;
import org.glstudio.teleports.modules.command.warp.WarpsCommand;
import org.glstudio.teleports.modules.database.DatabaseManager;
import org.glstudio.teleports.modules.database.DatabaseType;
import org.glstudio.teleports.modules.listener.ArrivalListener;
import org.glstudio.teleports.modules.listener.BedRespawnListener;
import org.glstudio.teleports.modules.listener.DamageListener;
import org.glstudio.teleports.modules.listener.LoginPlacementListener;
import org.glstudio.teleports.modules.listener.MoveListener;
import org.glstudio.teleports.modules.listener.PresenceListener;
import org.glstudio.teleports.modules.listener.QuitListener;
import org.glstudio.teleports.modules.listener.RespawnListener;
import org.glstudio.teleports.modules.manager.BackManager;
import org.glstudio.teleports.modules.manager.BedRespawnManager;
import org.glstudio.teleports.modules.manager.BlacklistWorldManager;
import org.glstudio.teleports.modules.manager.ChatPrompts;
import org.glstudio.teleports.modules.manager.Confirmations;
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
import org.glstudio.teleports.modules.manager.SpawnManager;
import org.glstudio.teleports.modules.manager.TeleportManager;
import org.glstudio.teleports.modules.manager.TeleportQueue;
import org.glstudio.teleports.modules.manager.TeleportSafetyManager;
import org.glstudio.teleports.modules.manager.TpaBlockManager;
import org.glstudio.teleports.modules.manager.TpaManager;
import org.glstudio.teleports.modules.manager.WarpManager;
import org.glstudio.teleports.modules.migration.MigrationManager;
import org.glstudio.teleports.modules.placeholder.TeleportsExpansion;
import org.glstudio.teleports.modules.service.BackupService;
import org.glstudio.teleports.modules.service.PlayerWarpPurgeService;

import java.util.concurrent.TimeUnit;

/**
 * Starts the plugin and keeps what enabling multi-server support later needs.
 */
public class StartupService {
    private static final int CONFIG_VERSION = 1;

    private static final long REDIS_STARTUP_TIMEOUT_SECONDS = 15L;

    private final GTeleports plugin;

    /**
     * Creates the service.
     *
     * @param plugin the owning plugin
     */
    public StartupService(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs the whole startup.
     *
     * @return whether the plugin may stay enabled
     */
    public boolean start() {
        if (!loadAll()) {
            LoggerUtils.logError("Plugin initialization failed! Disabling...");
            return false;
        }

        LoggerUtils.sendEnable(plugin);
        return true;
    }

    private boolean loadAll() {
        if (!loadConfigs()) return false;
        if (!loadExecutors()) return false;
        if (!loadDatabase()) return false;
        if (!loadMultiServer()) return false;
        if (!loadManagers()) return false;
        if (!registerCommands()) return false;
        if (!registerListeners()) return false;

        registerApi();
        registerPlaceholders();

        startPacketDelivery();
        return true;
    }

    private boolean loadConfigs() {
        try {
            ConfigManager configs = new ConfigManager(plugin);
            plugin.setConfigManager(configs);
            plugin.setMessages(new LanguageFile(configs.getLanguageConfig(), TextFormat.MINIMESSAGE).prefixPath("prefix"));
            plugin.setSettings(configs.getMainConfig().resolved());
            plugin.setConfirmations(new Confirmations());
            plugin.setCommandManager(NexusAPI.get().createCommandManager(plugin));
            plugin.getMessages().applyTo(plugin.getCommandManager(), "errors.no-permission", "errors.player-only");
            Futures.setFailureNotifier((sender, error) -> plugin.getMessages().send(sender, "errors.database-error"));

            plugin.setMultiServerSettings(new MultiServerSettings(plugin.getConfigManager().getMainConfig()));

            plugin.getConfigManager().all().forEach(file -> file.checkVersion(CONFIG_VERSION, LoggerUtils::logWarn));
            reportLegacyKeys();

            ConfigValidator validator = new ConfigValidator(plugin);
            validator.validate();
            validator.reportUnknownKeys();

            LoggerUtils.logSuccess("Configs loaded.");
            return true;
        } catch (Exception e) {
            Diagnostics.logFailure("StartupService#loadConfigs", e);
            return false;
        }
    }

    private void reportLegacyKeys() {
        for (String renamed : MultiServerSettings.legacyKeysSeen()) {
            LoggerUtils.logWarn("config.yml uses the old key name " + renamed
                    + ". Both are read today; rename it before the next major version.");
        }
    }

    private boolean loadExecutors() {
        try {
            int poolSize = plugin.getConfigManager().getDatabaseConfig().getInt("mysql.pool-size", 10);
            plugin.setExecutors(NexusExecutors.builder(plugin.getName())
                    .ioThreads(Math.max(2, poolSize))
                    .scheduler(true)
                    .build());
            plugin.setMultiServerSupervisor(new MultiServerSupervisor(plugin));
            return true;
        } catch (Exception e) {
            Diagnostics.logFailure("StartupService#loadExecutors", e);
            return false;
        }
    }

    private boolean loadDatabase() {
        try {
            DatabaseManager databaseManager = new DatabaseManager(plugin);
            databaseManager.initialize();
            plugin.setDatabaseManager(databaseManager);
            return true;
        } catch (Exception e) {
            Diagnostics.logFailure("StartupService#loadDatabase", e);
            return false;
        }
    }

    private boolean loadMultiServer() {
        MultiServerSettings settings = plugin.getMultiServerSettings();

        if (!settings.isEnabled()) {
            plugin.setCrossServerManager(new CrossServerManager(plugin, settings, null, null, null));
            LoggerUtils.logInfo("Multi-server teleports disabled.");
            return true;
        }

        if (!multiServerConfigurationIsUsable(settings)) {
            plugin.setCrossServerManager(new CrossServerManager(plugin, settings, null, null, null));
            return true;
        }

        try {
            NexusExecutors executors = plugin.getExecutors();

            RedisService redisService = new RedisService(executors, settings);

            redisService.initialize().orTimeout(REDIS_STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
            plugin.setRedisService(redisService);

            PacketHandler packetHandler = new PacketHandler(settings, executors, redisService);
            plugin.setPacketHandler(packetHandler);

            ProxyMessenger proxyMessenger = new ProxyMessenger(plugin);
            proxyMessenger.register();
            plugin.setProxyMessenger(proxyMessenger);

            PlayerRegistry playerRegistry = new PlayerRegistry(plugin, redisService, executors, settings);
            playerRegistry.startHeartbeat();
            plugin.setPlayerRegistry(playerRegistry);

            CrossServerManager crossServerManager =
                    new CrossServerManager(plugin, settings, packetHandler, proxyMessenger, playerRegistry);
            plugin.setCrossServerManager(crossServerManager);

            packetHandler.setCrossServerManager(crossServerManager);
            redisService.setPacketHandler(packetHandler);

            LoggerUtils.logSuccess("Multi-server teleports enabled on channel '" + settings.getChannel()
                    + "' as '" + settings.getServerName() + "'.");
            return true;
        } catch (Exception e) {
            LoggerUtils.logError("Multi-server teleports could not start; continuing with local teleports only.");
            Diagnostics.logFailure("StartupService#loadMultiServer", e);

            shutdownPartialMultiServer();
            plugin.setCrossServerManager(new CrossServerManager(plugin, settings, null, null, null));

            plugin.getMultiServerSupervisor().scheduleRetry();
            return true;
        }
    }

    private boolean multiServerConfigurationIsUsable(MultiServerSettings settings) {
        DatabaseType type = DatabaseType.fromString(
                plugin.getConfigManager().getDatabaseConfig().getString("type", "yaml"));

        if (!type.isShared()) {
            LoggerUtils.logError("multi-server.enabled is true but database.yml uses '" + type.getName()
                    + "', which stores its data inside this server's own folder. Every server would see a "
                    + "different set of homes and warps. Use mysql or mongo, or turn multi-server off. "
                    + "Continuing with local teleports only.");
            return false;
        }

        if ("unnamed-server".equals(settings.getServerName())) {
            LoggerUtils.logError("multi-server.server-name is empty. It must match this server's name in the "
                    + "proxy config and be distinct per server. Continuing with local teleports only.");
            return false;
        }

        return true;
    }

    private void startPacketDelivery() {
        if (plugin.getRedisService() != null) {
            plugin.getRedisService().startSubscriber();
            plugin.getRedisService().startConsumer();
        }

        if (plugin.getCrossServerManager() != null) {
            plugin.getCrossServerManager().announcePresence(true);
        }
    }

    private void shutdownPartialMultiServer() {
        if (plugin.getPlayerRegistry() != null) {
            plugin.getPlayerRegistry().stopHeartbeat();
            plugin.setPlayerRegistry(null);
        }
        if (plugin.getRedisService() != null) {
            plugin.getRedisService().close();
            plugin.setRedisService(null);
        }
        plugin.setPacketHandler(null);
        if (plugin.getProxyMessenger() != null) {
            plugin.getProxyMessenger().close();
            plugin.setProxyMessenger(null);
        }
    }

    private boolean loadManagers() {
        try {
            plugin.setEconomyManager(new EconomyManager(plugin));
            plugin.setCooldownManager(new CooldownManager(plugin));
            plugin.setDelayManager(new DelayManager(plugin));
            plugin.setBlacklistWorldManager(new BlacklistWorldManager(plugin));
            plugin.setTeleportSafetyManager(new TeleportSafetyManager(plugin));
            plugin.setEffectManager(new EffectManager(plugin));
            plugin.setTeleportManager(new TeleportManager(plugin));

            plugin.setTeleportQueue(new TeleportQueue(plugin));

            plugin.setLockManager(new LockManager(plugin));

            plugin.setHomeManager(new HomeManager(plugin));
            plugin.setTpaManager(new TpaManager(plugin));
            plugin.setWarpManager(new WarpManager(plugin));
            plugin.setSpawnManager(new SpawnManager(plugin));
            plugin.setBedRespawnManager(new BedRespawnManager(plugin));
            plugin.setPlayerSettings(new PlayerSettingsManager(plugin));
            plugin.setHomeShareManager(new HomeShareManager(plugin));
            plugin.setTpaBlockManager(new TpaBlockManager(plugin));

            plugin.setChatInputManager(NexusAPI.get().createChatInputManager(plugin));
            plugin.setChatPrompts(new ChatPrompts(plugin.getChatInputManager(), plugin.getMessages()));
            plugin.setPlayerWarpCategoryRegistry(new PlayerWarpCategoryRegistry(plugin));
            plugin.setPlayerWarpLimits(new PlayerWarpLimits(plugin));
            plugin.setPlayerWarpWhitelistManager(new PlayerWarpWhitelistManager(plugin));
            plugin.setPlayerWarpBanManager(new PlayerWarpBanManager(plugin));
            plugin.setPlayerWarpManagerRoleManager(new PlayerWarpManagerRoleManager(plugin));
            plugin.setPlayerWarpRatingManager(new PlayerWarpRatingManager(plugin));
            plugin.setPlayerWarpFavouriteManager(new PlayerWarpFavouriteManager(plugin));
            plugin.setPlayerWarpSponsorManager(new PlayerWarpSponsorManager(plugin));
            plugin.setPlayerWarpManager(new PlayerWarpManager(plugin));

            plugin.setBackManager(new BackManager(plugin));
            plugin.setMigrationManager(new MigrationManager(plugin));

            PlayerWarpPurgeService playerWarpPurgeService = new PlayerWarpPurgeService(plugin);
            playerWarpPurgeService.schedule();
            plugin.setPlayerWarpPurgeService(playerWarpPurgeService);

            HistoryManager history = new HistoryManager(plugin);
            history.startPurge();
            plugin.setHistoryManager(history);

            plugin.setMenuManager(NexusAPI.get().createMenuManager(plugin));

            LastLocationManager lastLocations = new LastLocationManager(plugin);
            lastLocations.startAutoSave();
            plugin.setLastLocationManager(lastLocations);

            BackupService backupService = new BackupService(plugin);
            backupService.schedule();
            plugin.setBackupService(backupService);

            LoggerUtils.logSuccess("Managers loaded.");
            return true;
        } catch (Exception e) {
            Diagnostics.logFailure("StartupService#loadManagers", e);
            return false;
        }
    }

    /**
     * Registers the core listeners.
     */
    public void registerCoreListeners() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        pluginManager.registerEvents(new MoveListener(plugin), plugin);
        pluginManager.registerEvents(new DamageListener(plugin), plugin);
        pluginManager.registerEvents(new QuitListener(plugin), plugin);
        pluginManager.registerEvents(new RespawnListener(plugin), plugin);
        pluginManager.registerEvents(new BedRespawnListener(plugin), plugin);
    }

    /**
     * Registers the multi server listeners.
     */
    public void registerMultiServerListeners() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        pluginManager.registerEvents(new LoginPlacementListener(plugin), plugin);
        pluginManager.registerEvents(new ArrivalListener(plugin), plugin);
        pluginManager.registerEvents(new PresenceListener(plugin), plugin);
    }

    private boolean registerCommands() {
        try {
            CommandManager manager = plugin.getCommandManager();

            manager.register(new GLTeleportsCommand(manager, "glteleports", Permissions.ADMIN_RELOAD, plugin));

            manager.register(new SetHomeCommand(manager, "sethome", Permissions.HOME_SET, plugin));
            manager.register(new HomeCommand(manager, "home", Permissions.HOME_USE, plugin));
            manager.register(new DelHomeCommand(manager, "delhome", Permissions.HOME_DELETE, plugin));

            manager.register(new ListHomesCommand(manager, "listhomes", Permissions.HOME_LIST, plugin));
            manager.register(new RenameHomeCommand(manager, "renamehome", Permissions.HOME_SET, plugin));
            manager.register(new MoveHomeCommand(manager, "movehome", Permissions.HOME_SET, plugin));
            manager.register(new HomesCommand(manager, "homes", Permissions.HOME_LIST, plugin));

            manager.register(new HomeInviteCommand(manager, "homeinvite", Permissions.HOME_SHARE, plugin));
            manager.register(new HomeUninviteCommand(manager, "homeuninvite", Permissions.HOME_SHARE, plugin));
            manager.register(new HomeInvitesCommand(manager, "homeinvites", Permissions.HOME_SHARE, plugin));

            manager.register(new TpaCommand(manager, "tpa", Permissions.TPA_SEND, plugin));
            manager.register(new TpaHereCommand(manager, "tpahere", Permissions.TPA_HERE, plugin));
            manager.register(new TpAcceptCommand(manager, "tpaccept", Permissions.TPA_ACCEPT, plugin));
            manager.register(new TpDenyCommand(manager, "tpdeny", Permissions.TPA_DENY, plugin));
            manager.register(new TpaCancelCommand(manager, "tpacancel", Permissions.TPA_CANCEL, plugin));
            manager.register(new TpaToggleCommand(manager, "tpatoggle", Permissions.TPA_TOGGLE, plugin));
            manager.register(new TpaBlockCommand(manager, "tpablock", Permissions.TPA_BLOCK, plugin));

            manager.register(new SetWarpCommand(manager, "setwarp", Permissions.WARP_SET, plugin));
            manager.register(new WarpCommand(manager, "warp", Permissions.WARP_USE, plugin));
            manager.register(new WarpsCommand(manager, "warps", Permissions.WARP_LIST, plugin));
            manager.register(new DelWarpCommand(manager, "delwarp", Permissions.WARP_DELETE, plugin));
            manager.register(new EditWarpCommand(manager, "editwarp", Permissions.WARP_SET, plugin));

            manager.register(new SetSpawnCommand(manager, "setspawn", Permissions.SPAWN_SET, plugin));
            manager.register(new SpawnCommand(manager, "spawn", Permissions.SPAWN_USE, plugin));

            manager.register(new org.glstudio.teleports.modules.command.playerwarp.PlayerWarpCommand(
                    manager, "pw", Permissions.PLAYERWARP_USE, plugin));

            manager.register(new org.glstudio.teleports.modules.command.back.BackCommand(
                    manager, "back", Permissions.BACK_USE, plugin));

            manager.register(new TpOfflineCommand(manager, "tpoffline", Permissions.ADMIN_TPOFFLINE, plugin));

            LoggerUtils.logSuccess("Commands registered.");
            return true;
        } catch (Exception e) {
            Diagnostics.logFailure("StartupService#registerCommands", e);
            return false;
        }
    }

    private void registerApi() {
        Bukkit.getServicesManager().register(GLTeleportsAPI.class, new TeleportsAPI(plugin), plugin,
                org.bukkit.plugin.ServicePriority.Normal);
    }

    private void registerPlaceholders() {
        PlaceholderHooks.registerIfPresent(() -> {
            TeleportsExpansion expansion = new TeleportsExpansion(plugin);
            plugin.setPlaceholderExpansion(expansion);
            return expansion;
        });
    }

    private boolean registerListeners() {
        try {
            registerCoreListeners();

            if (plugin.getCrossServerManager().isEnabled()) {
                registerMultiServerListeners();
            }

            LoggerUtils.logSuccess("Listeners registered.");
            return true;
        } catch (Exception e) {
            Diagnostics.logFailure("StartupService#registerListeners", e);
            return false;
        }
    }
}
