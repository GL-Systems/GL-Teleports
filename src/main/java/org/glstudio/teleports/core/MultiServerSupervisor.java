package org.glstudio.teleports.core;

import org.bukkit.Bukkit;
import org.glstudio.nexus.network.NetworkSupervisor;
import org.glstudio.nexus.proxy.ProxyMessenger;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.config.MultiServerSettings;
import org.glstudio.teleports.common.network.PacketHandler;
import org.glstudio.teleports.common.storage.redis.PlayerRegistry;
import org.glstudio.teleports.common.storage.redis.RedisService;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Keeps trying to bring multi-server support up when Redis was unreachable at startup.
 */
public class MultiServerSupervisor {
    private static final long CONNECT_TIMEOUT_SECONDS = 15L;
    private static final long NAME_CHECK_DELAY_SECONDS = 60L;
    private static final Duration PROXY_ANSWER_TIMEOUT = Duration.ofSeconds(5);

    private final GTeleports plugin;
    private final NetworkSupervisor supervisor;

    /**
     * Creates the supervisor.
     *
     * @param plugin the owning plugin
     */
    public MultiServerSupervisor(GTeleports plugin) {
        this.plugin = plugin;
        this.supervisor = new NetworkSupervisor(plugin.getExecutors().scheduler(), "Multi-server teleports");
    }

    /** Schedules another attempt, unless multi-server support is already up. */
    public void scheduleRetry() {
        if (plugin.getCrossServerManager().isEnabled()) {
            return;
        }
        supervisor.scheduleRetry(this::attempt);
    }

    /** Stops retrying. */
    public void stop() {
        supervisor.stop();
    }

    private void attempt() {
        if (plugin.getCrossServerManager().isEnabled()) {
            return;
        }

        MultiServerSettings settings = plugin.getMultiServerSettings();
        RedisService redisService = new RedisService(plugin.getExecutors(), settings);

        try {
            redisService.initialize().orTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
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

    private void verifyServerName(ProxyMessenger proxyMessenger, String serverName) {
        plugin.getExecutors().scheduler().schedule(() -> {
            if (supervisor.isStopped() || Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }

            proxyMessenger.servers(PROXY_ANSWER_TIMEOUT).thenAccept(servers -> {
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

            ProxyMessenger proxyMessenger = new ProxyMessenger(plugin);
            proxyMessenger.register();
            plugin.setProxyMessenger(proxyMessenger);

            PlayerRegistry playerRegistry =
                    new PlayerRegistry(plugin, redisService, plugin.getExecutors(), settings);
            playerRegistry.startHeartbeat();
            plugin.setPlayerRegistry(playerRegistry);

            plugin.getCrossServerManager().attach(packetHandler, proxyMessenger, playerRegistry);

            packetHandler.setCrossServerManager(plugin.getCrossServerManager());
            redisService.setPacketHandler(packetHandler);

            plugin.getStartupService().registerMultiServerListeners();

            redisService.startSubscriber();
            redisService.startConsumer();

            plugin.getCrossServerManager().announcePresence(true);

            supervisor.reset();
            LoggerUtils.logSuccess("Multi-server teleports are online on channel '" + settings.getChannel()
                    + "' as '" + settings.getServerName() + "'.");

            verifyServerName(proxyMessenger, settings.getServerName());
        } catch (Exception e) {
            LoggerUtils.logException("MultiServerSupervisor#enable", e);
            scheduleRetry();
        }
    }
}
