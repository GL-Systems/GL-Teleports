package org.glstudio.teleports.core;

import org.bukkit.Bukkit;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.PacketHandler;
import org.glstudio.teleports.common.storage.redis.PlayerRegistry;
import org.glstudio.teleports.common.storage.redis.RedisService;
import org.glstudio.teleports.modules.transfer.BungeePlayerTransferService;
import org.glstudio.teleports.modules.transfer.PlayerTransferService;

import java.util.concurrent.TimeUnit;

public class MultiServerSupervisor {
    private static final long INITIAL_RETRY_SECONDS = 30L;
    private static final long MAX_RETRY_SECONDS = 300L;

    private static final long NAME_CHECK_DELAY_SECONDS = 60L;

    private final GTeleports plugin;

    private volatile boolean stopped;
    private volatile long retrySeconds = INITIAL_RETRY_SECONDS;

    public MultiServerSupervisor(GTeleports plugin) {
        this.plugin = plugin;
    }

    public void scheduleRetry() {
        if (stopped || plugin.getCrossServerManager().isEnabled()) {
            return;
        }

        long delay = retrySeconds;
        LoggerUtils.logInfo("Multi-server teleports are offline; retrying in " + delay + "s.");

        plugin.getExecutors().scheduler().schedule(this::attempt, delay, TimeUnit.SECONDS);
        retrySeconds = Math.min(retrySeconds * 2, MAX_RETRY_SECONDS);
    }

    public void stop() {
        stopped = true;
    }

    private void attempt() {
        if (stopped || plugin.getCrossServerManager().isEnabled()) {
            return;
        }

        MultiServerSettings settings = plugin.getMultiServerSettings();
        RedisService redisService = new RedisService(plugin.getExecutors(), settings);

        try {
            redisService.initialize().orTimeout(15, TimeUnit.SECONDS).join();
        } catch (Exception e) {
            LoggerUtils.logDebug("MultiServerSupervisor", "Redis still unreachable: " + e.getMessage());
            scheduleRetry();
            return;
        }

        try {
            Bukkit.getScheduler().runTask(plugin, () -> enable(settings, redisService));
        } catch (Exception e) {
            LoggerUtils.logException("MultiServerSupervisor#attempt", e);
            scheduleRetry();
        }
    }

    private void verifyServerName(PlayerTransferService transferService, String serverName) {
        plugin.getExecutors().scheduler().schedule(() -> {
            if (stopped || Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }

            transferService.knownServers().thenAccept(servers -> {
                if (servers.isEmpty() || servers.stream().anyMatch(serverName::equalsIgnoreCase)) {
                    return;
                }

                LoggerUtils.logError("The proxy does not know a server called '" + serverName
                        + "'. It knows: " + String.join(", ", servers) + ". Cross-server teleports to "
                        + "this server will not arrive until multi-server.server-name matches one of "
                        + "those names.");
            });
        }, NAME_CHECK_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void enable(MultiServerSettings settings, RedisService redisService) {
        try {
            plugin.setRedisService(redisService);

            PacketHandler packetHandler = new PacketHandler(settings, plugin.getExecutors(), redisService);
            plugin.setPacketHandler(packetHandler);

            PlayerTransferService transferService = new BungeePlayerTransferService(plugin);
            transferService.register();
            plugin.setPlayerTransferService(transferService);

            PlayerRegistry playerRegistry =
                    new PlayerRegistry(plugin, redisService, plugin.getExecutors(), settings);
            playerRegistry.startHeartbeat();
            plugin.setPlayerRegistry(playerRegistry);

            plugin.getCrossServerManager().attach(packetHandler, transferService, playerRegistry);

            packetHandler.setCrossServerManager(plugin.getCrossServerManager());
            redisService.setPacketHandler(packetHandler);

            plugin.getStartupService().registerMultiServerListeners();

            redisService.startSubscriber();
            redisService.startConsumer();

            plugin.getCrossServerManager().announcePresence(true);

            retrySeconds = INITIAL_RETRY_SECONDS;
            LoggerUtils.logSuccess("Multi-server teleports are online on channel '" + settings.getChannel()
                    + "' as '" + settings.getServerName() + "'.");

            verifyServerName(transferService, settings.getServerName());
        } catch (Exception e) {
            LoggerUtils.logException("MultiServerSupervisor#enable", e);
            scheduleRetry();
        }
    }
}
