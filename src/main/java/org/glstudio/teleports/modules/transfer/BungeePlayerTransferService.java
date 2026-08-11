package org.glstudio.teleports.modules.transfer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.glstudio.nexus.utils.LoggerUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class BungeePlayerTransferService implements PlayerTransferService {

    private static final String CHANNEL = "BungeeCord";
    private static final String SUBCHANNEL_CONNECT = "Connect";

    private final Plugin plugin;

    public BungeePlayerTransferService(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
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
}
