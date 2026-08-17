package org.glstudio.teleports.modules.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

@Data
@AllArgsConstructor
public class TpaRequest {
    private UUID id;
    private UUID requester;
    private String requesterName;
    private String requesterServer;
    private UUID target;
    private String targetName;
    private String targetServer;
    private TpaType type;
    private long timestamp;

    private transient BukkitTask expiryTask;

    public TpaRequest(UUID id, UUID requester, String requesterName, String requesterServer,
                      UUID target, String targetName, String targetServer, TpaType type, long timestamp) {
        this(id, requester, requesterName, requesterServer, target, targetName, targetServer,
                type, timestamp, null);
    }

    public enum TpaType {
        TPA,
        TPAHERE
    }

    public boolean isExpired(long timeoutSeconds) {
        return System.currentTimeMillis() - timestamp > timeoutSeconds * 1000L;
    }

    public void cancelExpiry() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }

    public Player getRequesterPlayer() {
        return Bukkit.getPlayer(requester);
    }

    public Player getTargetPlayer() {
        return Bukkit.getPlayer(target);
    }

    public TeleportType toTeleportType() {
        return type == TpaType.TPA ? TeleportType.TPA : TeleportType.TPAHERE;
    }
}
