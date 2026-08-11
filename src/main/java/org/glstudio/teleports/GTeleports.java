package org.glstudio.teleports;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;
import org.glstudio.nexus.api.NexusAPI;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.common.network.PacketHandler;
import org.glstudio.teleports.common.storage.redis.PlayerRegistry;
import org.glstudio.teleports.common.storage.redis.RedisService;
import org.glstudio.teleports.core.ConfigManager;
import org.glstudio.teleports.core.MultiServerSettings;
import org.glstudio.teleports.core.ShutdownService;
import org.glstudio.teleports.core.StartupService;
import org.glstudio.teleports.core.TeleportExecutors;
import org.glstudio.teleports.modules.database.DatabaseManager;
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
import org.glstudio.teleports.modules.transfer.PlayerTransferService;

@Getter
@Setter
public final class GTeleports extends JavaPlugin {

    private StartupService startupService;
    private ShutdownService shutdownService;

    private ConfigManager configManager;
    private CommandManager commandManager;

    // Database
    private DatabaseManager databaseManager;

    // Multi-server infrastructure. All null except the settings when MULTI_SERVER.ENABLED is false.
    private MultiServerSettings multiServerSettings;
    private TeleportExecutors executors;
    private RedisService redisService;
    private PacketHandler packetHandler;
    private PlayerTransferService playerTransferService;
    private PlayerRegistry playerRegistry;

    // Managers
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
