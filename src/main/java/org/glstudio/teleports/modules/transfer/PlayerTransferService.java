package org.glstudio.teleports.modules.transfer;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PlayerTransferService {
    void register();

    boolean transfer(Player player, String targetServer);

    CompletableFuture<List<String>> knownServers();
}
