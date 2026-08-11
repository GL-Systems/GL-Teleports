package org.glstudio.teleports.modules.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.entity.Player;

import java.util.UUID;

@Data
@AllArgsConstructor
public class TpaRequest {
    private UUID requester;
    private String requesterName;
    private String requesterServer;
    private UUID target;
    private String targetName;
    private String targetServer;
    private TpaType type;
    private long timestamp;

    public enum TpaType {
        TPA,
        TPAHERE
    }

    public boolean isExpired(long timeout) {
        return System.currentTimeMillis() - timestamp > timeout * 1000;
    }

    public Player getRequesterPlayer() {
        return org.bukkit.Bukkit.getPlayer(requester);
    }

    public Player getTargetPlayer() {
        return org.bukkit.Bukkit.getPlayer(target);
    }

    public TeleportType toTeleportType() {
        return type == TpaType.TPA ? TeleportType.TPA : TeleportType.TPAHERE;
    }
}
