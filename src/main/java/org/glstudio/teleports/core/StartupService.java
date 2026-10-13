package org.glstudio.teleports.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.concurrent.TeleportExecutors;
import org.glstudio.teleports.common.config.ConfigValidator;
import org.glstudio.teleports.common.config.Messages;
import org.glstudio.teleports.common.config.MultiServerSettings;
import org.glstudio.teleports.common.config.Settings;
import org.glstudio.teleports.core.util.Diagnostics;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.database.DatabaseType;
import org.glstudio.teleports.modules.manager.Confirmations;
import org.glstudio.teleports.modules.service.BackupService;
import org.glstudio.teleports.modules.command.home.DelHomeCommand;
import org.glstudio.teleports.modules.command.GLTeleportsCommand;
import org.glstudio.teleports.modules.command.home.HomeAdmin;
import org.glstudio.teleports.modules.command.home.HomeCommand;
import org.glstudio.teleports.modules.command.home.ListHomesCommand;
import org.glstudio.teleports.modules.command.home.MoveHomeCommand;
import org.glstudio.teleports.modules.command.home.RenameHomeCommand;
import org.glstudio.teleports.modules.command.home.SetHomeCommand;
import org.glstudio.teleports.modules.command.spawn.SetSpawnCommand;
import org.glstudio.teleports.modules.command.spawn.SpawnCommand;
import org.glstudio.teleports.modules.command.tpa.TpAcceptCommand;
import org.glstudio.teleports.modules.command.tpa.TpDenyCommand;
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
import org.glstudio.teleports.api.GLTeleportsAPI;
import org.glstudio.teleports.api.TeleportsAPI;
import org.glstudio.nexus.modules.menu.MenuManager;
import org.glstudio.teleports.common.network.PacketHandler;
import org.glstudio.teleports.common.storage.redis.PlayerRegistry;
import org.glstudio.teleports.common.storage.redis.RedisService;
import org.glstudio.teleports.modules.command.home.HomeInviteCommand;
import org.glstudio.teleports.modules.command.home.HomeInvitesCommand;
import org.glstudio.teleports.modules.command.home.HomeUninviteCommand;
import org.glstudio.teleports.modules.command.home.HomesCommand;
import org.glstudio.teleports.modules.command.tpa.TpaBlockCommand;
import org.glstudio.teleports.modules.database.DatabaseManager;
import org.glstudio.teleports.modules.placeholder.TeleportsExpansion;
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
import org.glstudio.teleports.modules.manager.ChatInputManager;
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
import org.glstudio.teleports.modules.service.PlayerWarpPurgeService;
import org.glstudio.teleports.modules.manager.SpawnManager;
import org.glstudio.teleports.modules.manager.TeleportManager;
import org.glstudio.teleports.modules.manager.TeleportQueue;
import org.glstudio.teleports.modules.manager.TeleportSafetyManager;
import org.glstudio.teleports.modules.manager.TpaBlockManager;
import org.glstudio.teleports.modules.manager.TpaManager;
import org.glstudio.teleports.modules.manager.WarpManager;
import org.glstudio.teleports.modules.migration.MigrationManager;
import org.glstudio.teleports.modules.transfer.BungeePlayerTransferService;
import org.glstudio.teleports.modules.transfer.PlayerTransferService;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class StartupService {
    private static final int CONFIG_VERSION = 1;

    private static final long REDIS_STARTUP_TIMEOUT_SECONDS = 15L;

    private final GTeleports plugin;

    public StartupService(GTeleports plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
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

        LoggerUtils.sendEnable(plugin);
        return true;
    }

    private boolean loadConfigs() {
        try {
            plugin.setConfigManager(new ConfigManager(plugin));
            plugin.setMessages(new Messages(plugin));
            plugin.setSettings(new Settings(plugin));
            plugin.setConfirmations(new Confirmations());
            plugin.getMessages().applyToNexus();

            plugin.setMultiServerSettings(new MultiServerSettings(plugin.getConfigManager().getMainConfig()));

            checkConfigVersions();
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

    private void checkConfigVersions() {
        record Tracked(String name, org.glstudio.nexus.utils.ConfigFile file) {
        }

        List<Tracked> files = List.of(
                new Tracked("config.yml", plugin.getConfigManager().getMainConfig()),
                new Tracked("language.yml", plugin.getConfigManager().getLanguageConfig()),
                new Tracked("database.yml", plugin.getConfigManager().getDatabaseConfig()),
                new Tracked("player-warps.yml", plugin.getConfigManager().getPlayerWarpsConfig()));

        for (Tracked tracked : files) {
            int found = tracked.file().getInt("config-version", 0);

            if (found < CONFIG_VERSION) {
                LoggerUtils.logWarn(tracked.name() + " is version " + found + "; this build expects "
                        + CONFIG_VERSION + ". Missing keys fall back to the bundled defaults, but new "
                        + "settings will not appear in your file. Compare it against the copy in the jar.");
            }
        }
    }

    private boolean loadExecutors() {
        try {
            int poolSize = plugin.getConfigManager().getDatabaseConfig().getInt("mysql.pool-size", 10);
            plugin.setExecutors(new TeleportExecutors(poolSize));
            plugin.setMultiServerSupervisor(new MultiServerSupervisor(plugin));
            plugin.setCommandRegistry(new CommandRegistry(plugin));
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
            TeleportExecutors executors = plugin.getExecutors();

            RedisService redisService = new RedisService(executors, settings);

            redisService.initialize().orTimeout(REDIS_STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
            plugin.setRedisService(redisService);

            PacketHandler packetHandler = new PacketHandler(settings, executors, redisService);
            plugin.setPacketHandler(packetHandler);

            PlayerTransferService transferService = new BungeePlayerTransferService(plugin);
            transferService.register();
            plugin.setPlayerTransferService(transferService);

            PlayerRegistry playerRegistry = new PlayerRegistry(plugin, redisService, executors, settings);
            playerRegistry.startHeartbeat();
            plugin.setPlayerRegistry(playerRegistry);

            CrossServerManager crossServerManager =
                    new CrossServerManager(plugin, settings, packetHandler, transferService, playerRegistry);
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
            plugin.getRedisService().close().join();
            plugin.setRedisService(null);
        }
        plugin.setPacketHandler(null);
        plugin.setPlayerTransferService(null);
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

            plugin.setChatInputManager(new ChatInputManager(plugin));
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

            plugin.setMenuManager(new MenuManager(plugin));

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

    private void register(CommandManager manager, Command command) {
        manager.register(command);
        plugin.getCommandRegistry().track(command.getName(), command.aliases());
    }

    public void registerCoreListeners() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        pluginManager.registerEvents(new MoveListener(plugin), plugin);
        pluginManager.registerEvents(new DamageListener(plugin), plugin);
        pluginManager.registerEvents(new QuitListener(plugin), plugin);
        pluginManager.registerEvents(new RespawnListener(plugin), plugin);
        pluginManager.registerEvents(new BedRespawnListener(plugin), plugin);
        pluginManager.registerEvents(new org.glstudio.teleports.modules.listener.ChatInputListener(plugin), plugin);
    }

    public void registerMultiServerListeners() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        pluginManager.registerEvents(new LoginPlacementListener(plugin), plugin);
        pluginManager.registerEvents(new ArrivalListener(plugin), plugin);
        pluginManager.registerEvents(new PresenceListener(plugin), plugin);
    }

    private boolean registerCommands() {
        try {
            CommandManager manager = plugin.getCommandManager();

            register(manager, new GLTeleportsCommand(manager, "glteleports", Permissions.ADMIN_RELOAD, plugin));

            register(manager, new SetHomeCommand(manager, "sethome", Permissions.HOME_SET, plugin));
            register(manager, new HomeCommand(manager, "home", Permissions.HOME_USE, plugin));
            register(manager, new DelHomeCommand(manager, "delhome", Permissions.HOME_DELETE, plugin));

            register(manager, new ListHomesCommand(manager, "listhomes", Permissions.HOME_LIST, plugin));
            register(manager, new RenameHomeCommand(manager, "renamehome", Permissions.HOME_SET, plugin));
            register(manager, new MoveHomeCommand(manager, "movehome", Permissions.HOME_SET, plugin));
            register(manager, new HomesCommand(manager, "homes", Permissions.HOME_LIST, plugin));

            register(manager, new HomeInviteCommand(manager, "homeinvite", Permissions.HOME_SHARE, plugin));
            register(manager, new HomeUninviteCommand(manager, "homeuninvite", Permissions.HOME_SHARE, plugin));
            register(manager, new HomeInvitesCommand(manager, "homeinvites", Permissions.HOME_SHARE, plugin));

            register(manager, new TpaCommand(manager, "tpa", Permissions.TPA_SEND, plugin));
            register(manager, new TpaHereCommand(manager, "tpahere", Permissions.TPA_HERE, plugin));
            register(manager, new TpAcceptCommand(manager, "tpaccept", Permissions.TPA_ACCEPT, plugin));
            register(manager, new TpDenyCommand(manager, "tpdeny", Permissions.TPA_DENY, plugin));
            register(manager, new TpaCancelCommand(manager, "tpacancel", Permissions.TPA_CANCEL, plugin));
            register(manager, new TpaToggleCommand(manager, "tpatoggle", Permissions.TPA_TOGGLE, plugin));
            register(manager, new TpaBlockCommand(manager, "tpablock", Permissions.TPA_BLOCK, plugin));

            register(manager, new SetWarpCommand(manager, "setwarp", Permissions.WARP_SET, plugin));
            register(manager, new WarpCommand(manager, "warp", Permissions.WARP_USE, plugin));
            register(manager, new WarpsCommand(manager, "warps", Permissions.WARP_LIST, plugin));
            register(manager, new DelWarpCommand(manager, "delwarp", Permissions.WARP_DELETE, plugin));
            register(manager, new EditWarpCommand(manager, "editwarp", Permissions.WARP_SET, plugin));

            register(manager, new SetSpawnCommand(manager, "setspawn", Permissions.SPAWN_SET, plugin));
            register(manager, new SpawnCommand(manager, "spawn", Permissions.SPAWN_USE, plugin));

            register(manager, new org.glstudio.teleports.modules.command.playerwarp.PlayerWarpCommand(
                    manager, "pw", Permissions.PLAYERWARP_USE, plugin));

            register(manager, new org.glstudio.teleports.modules.command.back.BackCommand(
                    manager, "back", Permissions.BACK_USE, plugin));

            register(manager, new TpOfflineCommand(manager, "tpoffline", Permissions.ADMIN_TPOFFLINE, plugin));

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
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }

        try {
            new TeleportsExpansion(plugin).register();
            LoggerUtils.logSuccess("PlaceholderAPI expansion registered.");
        } catch (Throwable e) {
            LoggerUtils.logWarn("PlaceholderAPI is installed but the expansion could not register: "
                    + e.getMessage());
        }
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
