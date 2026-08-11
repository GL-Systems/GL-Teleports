package org.glstudio.teleports.modules.transfer;

import org.bukkit.entity.Player;

public interface PlayerTransferService {

    void register();

    boolean transfer(Player player, String targetServer);
}
