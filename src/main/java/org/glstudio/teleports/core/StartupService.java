package org.glstudio.teleports.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.command.home.DelHomeCommand;
import org.glstudio.teleports.modules.command.GLTeleportsCommand;
import org.glstudio.teleports.modules.command.home.HomeAdmin;
import org.glstudio.teleports.modules.command.home.HomeCommand;
import org.glstudio.teleports.modules.command.home.ListHomesCommand;
import org.glstudio.teleports.modules.command.home.SetHomeCommand;
import org.glstudio.teleports.modules.command.spawn.SetSpawnCommand;
import org.glstudio.teleports.modules.command.spawn.SpawnCommand;
import org.glstudio.teleports.modules.command.tpa.TpAcceptCommand;
import org.glstudio.teleports.modules.command.tpa.TpDenyCommand;
import org.glstudio.teleports.modules.command.tpa.TpaCancelCommand;
import org.glstudio.teleports.modules.command.tpa.TpaCommand;
import org.glstudio.teleports.modules.command.tpa.TpaHereCommand;
import org.glstudio.teleports.modules.command.tpa.TpaToggleCommand;
import org.glstudio.teleports.modules.command.warp.DelWarpCommand;
import org.glstudio.teleports.modules.command.warp.SetWarpCommand;
import org.glstudio.teleports.modules.command.warp.WarpCommand;
import org.glstudio.teleports.common.network.PacketHandler;
import org.glstudio.teleports.common.storage.redis.PlayerRegistry;
import org.glstudio.teleports.common.storage.redis.RedisService;
import org.glstudio.teleports.modules.database.DatabaseManager;
import org.glstudio.teleports.modules.listener.ArrivalListener;
import org.glstudio.teleports.modules.listener.DamageListener;
import org.glstudio.teleports.modules.listener.MoveListener;
import org.glstudio.teleports.modules.listener.PresenceListener;
import org.glstudio.teleports.modules.listener.QuitListener;
import org.glstudio.teleports.modules.manager.BlacklistWorldManager;
import org.glstudio.teleports.modules.manager.CooldownManager;
import org.glstudio.teleports.modules.manager.CrossServerManager;
import org.glstudio.teleports.modules.manager.DelayManager;
import org.glstudio.teleports.modules.manager.EconomyManager;
import org.glstudio.teleports.modules.manager.EffectManager;
import org.glstudio.teleports.modules.manager.HomeManager;
import org.glstudio.teleports.modules.manager.SpawnManager;
import org.glstudio.teleports.modules.manager.TeleportManager;
import org.glstudio.teleports.modules.manager.TeleportSafetyManager;
import org.glstudio.teleports.modules.manager.TpaManager;
import org.glstudio.teleports.modules.manager.WarpManager;
import org.glstudio.teleports.modules.transfer.BungeePlayerTransferService;
import org.glstudio.teleports.modules.transfer.PlayerTransferService;

public class StartupService {

    private final GTeleports plugin;

    public StartupService(GTeleports plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        if (!loadConfigs()) return false;
        if (!loadDatabase()) return false;
        if (!loadMultiServer()) return false;
        if (!loadManagers()) return false;
        if (!registerCommands()) return false;
        if (!registerListeners()) return false;

        LoggerUtils.sendEnable(plugin);
        return true;
    }

    private boolean loadConfigs() {
        try {
            plugin.setConfigManager(new ConfigManager(plugin));
            LoggerUtils.logSuccess("Configs loaded.");
            return true;
        } catch (Exception e) {
            LoggerUtils.logException("StartupService#loadConfigs", e);
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
            LoggerUtils.logException("StartupService#loadDatabase", e);
            return false;
        }
    }

    private boolean loadMultiServer() {
        MultiServerSettings settings = new MultiServerSettings(plugin.getConfigManager().getMainConfig());
        plugin.setMultiServerSettings(settings);

        if (!settings.isEnabled()) {
            plugin.setCrossServerManager(new CrossServerManager(plugin, settings, null, null, null));
            LoggerUtils.logInfo("Multi-server teleports disabled.");
            return true;
        }

        try {
            TeleportExecutors executors = new TeleportExecutors(4);
            plugin.setExecutors(executors);

            RedisService redisService = new RedisService(executors, settings);
            redisService.initialize().join();
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
            LoggerUtils.logException("StartupService#loadMultiServer", e);

            shutdownPartialMultiServer();
            plugin.setCrossServerManager(new CrossServerManager(plugin, settings, null, null, null));
            return true;
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
        if (plugin.getExecutors() != null) {
            plugin.getExecutors().shutdown();
            plugin.setExecutors(null);
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

            plugin.setHomeManager(new HomeManager(plugin));
            plugin.setTpaManager(new TpaManager(plugin));
            plugin.setWarpManager(new WarpManager(plugin));
            plugin.setSpawnManager(new SpawnManager(plugin));

            LoggerUtils.logSuccess("Managers loaded.");
            return true;
        } catch (Exception e) {
            LoggerUtils.logException("StartupService#loadManagers", e);
            return false;
        }
    }

    private boolean registerCommands() {
        try {
            CommandManager manager = plugin.getCommandManager();

            manager.register(new GLTeleportsCommand(manager, "glteleports", "golden.teleports.admin.reload", plugin));

            manager.register(new SetHomeCommand(manager, "sethome", "golden.teleports.sethome", plugin));
            manager.register(new HomeCommand(manager, "home", "golden.teleports.home", plugin));
            manager.register(new DelHomeCommand(manager, "delhome", "golden.teleports.delhome", plugin));
            manager.register(new ListHomesCommand(manager, "listhomes", HomeAdmin.PERMISSION, plugin));

            manager.register(new TpaCommand(manager, "tpa", "golden.teleports.tpa", plugin));
            manager.register(new TpaHereCommand(manager, "tpahere", "golden.teleports.tpahere", plugin));
            manager.register(new TpAcceptCommand(manager, "tpaccept", "golden.teleports.tpaccept", plugin));
            manager.register(new TpDenyCommand(manager, "tpdeny", "golden.teleports.tpdeny", plugin));
            manager.register(new TpaCancelCommand(manager, "tpacancel", "golden.teleports.tpacancel", plugin));
            manager.register(new TpaToggleCommand(manager, "tpatoggle", "golden.teleports.tpatoggle", plugin));

            manager.register(new SetWarpCommand(manager, "setwarp", "golden.teleports.setwarp", plugin));
            manager.register(new WarpCommand(manager, "warp", "golden.teleports.warp", plugin));
            manager.register(new DelWarpCommand(manager, "delwarp", "golden.teleports.delwarp", plugin));

            manager.register(new SetSpawnCommand(manager, "setspawn", "golden.teleports.setspawn", plugin));
            manager.register(new SpawnCommand(manager, "spawn", "golden.teleports.spawn", plugin));

            LoggerUtils.logSuccess("Commands registered.");
            return true;
        } catch (Exception e) {
            LoggerUtils.logException("StartupService#registerCommands", e);
            return false;
        }
    }

    private boolean registerListeners() {
        try {
            PluginManager pluginManager = Bukkit.getPluginManager();

            pluginManager.registerEvents(new MoveListener(plugin), plugin);
            pluginManager.registerEvents(new DamageListener(plugin), plugin);
            pluginManager.registerEvents(new QuitListener(plugin), plugin);

            if (plugin.getCrossServerManager().isEnabled()) {
                pluginManager.registerEvents(new ArrivalListener(plugin), plugin);
                pluginManager.registerEvents(new PresenceListener(plugin), plugin);
            }

            LoggerUtils.logSuccess("Listeners registered.");
            return true;
        } catch (Exception e) {
            LoggerUtils.logException("StartupService#registerListeners", e);
            return false;
        }
    }
}
