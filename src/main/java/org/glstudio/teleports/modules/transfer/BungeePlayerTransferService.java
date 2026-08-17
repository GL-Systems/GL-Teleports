package org.glstudio.teleports.modules.transfer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.glstudio.nexus.utils.LoggerUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BungeePlayerTransferService implements PlayerTransferService, PluginMessageListener {
    private static final String CHANNEL = "BungeeCord";
    private static final String SUBCHANNEL_CONNECT = "Connect";
    private static final String SUBCHANNEL_GET_SERVERS = "GetServers";

    private static final long ANSWER_TIMEOUT_SECONDS = 5L;

    private final Plugin plugin;

    private volatile CompletableFuture<List<String>> pendingServerList;

    public BungeePlayerTransferService(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    @Override
    public boolean transfer(Player player, String targetServer) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(SUBCHANNEL_CONNECT);
            out.writeUTF(targetServer);
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
            return true;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            LoggerUtils.logError("Failed to transfer " + player.getName() + " to " + targetServer
                    + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public CompletableFuture<List<String>> knownServers() {
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);

        if (carrier == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        CompletableFuture<List<String>> answer = new CompletableFuture<>();
        pendingServerList = answer;

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(SUBCHANNEL_GET_SERVERS);
            carrier.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            pendingServerList = null;
            return CompletableFuture.completedFuture(List.of());
        }

        return answer
                .completeOnTimeout(List.of(), ANSWER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(error -> List.of());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!SUBCHANNEL_GET_SERVERS.equals(in.readUTF())) {
                return;
            }

            CompletableFuture<List<String>> waiting = pendingServerList;
            pendingServerList = null;

            if (waiting != null) {
                waiting.complete(Arrays.asList(in.readUTF().split(", ")));
            }
        } catch (IOException e) {
            LoggerUtils.logDebug("BungeePlayerTransferService", "Malformed proxy reply: " + e.getMessage());
        }
    }
}
