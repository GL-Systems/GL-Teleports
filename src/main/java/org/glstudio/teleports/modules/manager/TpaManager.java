package org.glstudio.teleports.modules.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.packets.TpaRequestPacket;
import org.glstudio.teleports.common.network.packets.TpaResponse;
import org.glstudio.teleports.common.network.packets.TpaResponsePacket;
import org.glstudio.teleports.common.network.packets.TpaTravelPacket;
import org.glstudio.teleports.common.storage.redis.NetworkPlayer;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.teleports.modules.model.TpaRequest;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TpaManager {

    private final GTeleports plugin;
    private final Map<UUID, Queue<TpaRequest>> pendingRequests;
    private final Map<UUID, Queue<TpaRequest>> outgoingRequests;
    private final Map<UUID, Boolean> tpaToggle;
    private final MiniMessage miniMessage;

    public TpaManager(GTeleports plugin) {
        this.plugin = plugin;
        this.pendingRequests = new ConcurrentHashMap<>();
        this.outgoingRequests = new ConcurrentHashMap<>();
        this.tpaToggle = new ConcurrentHashMap<>();
        this.miniMessage = MiniMessage.miniMessage();

        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanExpiredRequests, 20L, 20L);
    }

    public void sendRequest(Player requester, String targetName, TpaRequest.TpaType type) {
        if (requester.getName().equalsIgnoreCase(targetName)) {
            send(requester, message("errors.cannot-teleport-self"));
            return;
        }

        Player local = Bukkit.getPlayer(targetName);
        if (local != null && local.isOnline()) {
            sendLocalRequest(requester, local, type);
            return;
        }

        CrossServerManager crossServer = plugin.getCrossServerManager();
        if (!crossServer.isEnabled()) {
            send(requester, message("errors.player-offline"));
            return;
        }

        UUID requesterId = requester.getUniqueId();
        crossServer.getPlayerRegistry().lookup(targetName).thenAccept(found ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player stillOnline = Bukkit.getPlayer(requesterId);
                    if (stillOnline == null || !stillOnline.isOnline()) {
                        return;
                    }
                    if (found == null) {
                        send(stillOnline, message("errors.player-offline"));
                        return;
                    }
                    sendRemoteRequest(stillOnline, found, type);
                }));
    }

    private void sendLocalRequest(Player requester, Player target, TpaRequest.TpaType type) {
        if (tpaToggle.getOrDefault(target.getUniqueId(), false)) {
            send(requester, message("errors.tpa-disabled").replace("{player}", target.getName()));
            return;
        }

        String server = plugin.getCrossServerManager().currentServer();
        TpaRequest request = new TpaRequest(
                requester.getUniqueId(), requester.getName(), server,
                target.getUniqueId(), target.getName(), server,
                type, System.currentTimeMillis()
        );

        pendingRequests.computeIfAbsent(target.getUniqueId(), k -> new LinkedList<>()).add(request);

        send(requester, message("tpa.request-sent").replace("{player}", target.getName()));
        showRequest(target, requester.getName());

        scheduleExpiry(request);
    }

    private void sendRemoteRequest(Player requester, NetworkPlayer target, TpaRequest.TpaType type) {
        CrossServerManager crossServer = plugin.getCrossServerManager();
        String server = crossServer.currentServer();

        TpaRequest request = new TpaRequest(
                requester.getUniqueId(), requester.getName(), server,
                target.getUuid(), target.getName(), target.getServer(),
                type, System.currentTimeMillis()
        );

        outgoingRequests.computeIfAbsent(requester.getUniqueId(), k -> new LinkedList<>()).add(request);

        crossServer.getPacketHandler().send(new TpaRequestPacket(
                crossServer.getSettings().getInstanceId(), target.getServer(),
                requester.getUniqueId(), requester.getName(), server,
                target.getUuid(), target.getName(), type));

        send(requester, message("tpa.request-sent").replace("{player}", target.getName()));
    }

    public void handleRemoteRequest(TpaRequestPacket packet) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player target = Bukkit.getPlayer(packet.getTargetUuid());
            if (target == null || !target.isOnline()) {
                respond(TpaResponse.EXPIRED, packet.getRequesterServer(), packet.getRequesterUuid(),
                        packet.getRequesterName(), packet.getTargetUuid(), packet.getTargetName(),
                        plugin.getCrossServerManager().currentServer(), packet.getTpaType(), null);
                return;
            }

            if (tpaToggle.getOrDefault(target.getUniqueId(), false)) {
                respond(TpaResponse.DISABLED, packet.getRequesterServer(), packet.getRequesterUuid(),
                        packet.getRequesterName(), target.getUniqueId(), target.getName(),
                        plugin.getCrossServerManager().currentServer(), packet.getTpaType(), null);
                return;
            }

            TpaRequest request = new TpaRequest(
                    packet.getRequesterUuid(), packet.getRequesterName(), packet.getRequesterServer(),
                    target.getUniqueId(), target.getName(), plugin.getCrossServerManager().currentServer(),
                    packet.getTpaType(), System.currentTimeMillis()
            );

            pendingRequests.computeIfAbsent(target.getUniqueId(), k -> new LinkedList<>()).add(request);

            showRequest(target, packet.getRequesterName());
            scheduleExpiry(request);
        });
    }

    public void acceptRequest(Player player, String requesterName) {
        TpaRequest request = getRequest(player, requesterName);

        if (request == null) {
            send(player, message("errors.no-pending-requests"));
            return;
        }

        removeRequest(player.getUniqueId(), request.getRequester());

        if (isLocal(request.getRequesterServer())) {
            acceptLocally(player, request);
            return;
        }

        respond(TpaResponse.ACCEPTED, request.getRequesterServer(), request.getRequester(),
                request.getRequesterName(), player.getUniqueId(), player.getName(),
                request.getTargetServer(), request.getType(), player.getLocation());

        send(player, message("tpa.request-accepted").replace("{player}", request.getRequesterName()));
    }

    private void acceptLocally(Player player, TpaRequest request) {
        Player requester = request.getRequesterPlayer();
        if (requester == null || !requester.isOnline()) {
            send(player, message("errors.player-offline"));
            return;
        }

        Player teleporter = request.getType() == TpaRequest.TpaType.TPA ? requester : player;
        Player destination = request.getType() == TpaRequest.TpaType.TPA ? player : requester;

        plugin.getTeleportManager().requestTeleport(teleporter, destination.getLocation(),
                request.toTeleportType(), success -> {
                    if (success) {
                        String message = message("tpa.request-accepted");
                        player.sendMessage(message.replace("{player}", requester.getName()));
                        requester.sendMessage(message.replace("{player}", player.getName()));
                    }
                });
    }

    public void denyRequest(Player player, String requesterName) {
        TpaRequest request = getRequest(player, requesterName);

        if (request == null) {
            send(player, message("errors.no-pending-requests"));
            return;
        }

        removeRequest(player.getUniqueId(), request.getRequester());

        String message = message("tpa.request-denied");
        send(player, message.replace("{player}", request.getRequesterName()));

        if (isLocal(request.getRequesterServer())) {
            Player requester = request.getRequesterPlayer();
            if (requester != null && requester.isOnline()) {
                requester.sendMessage(message.replace("{player}", player.getName()));
            }
            return;
        }

        respond(TpaResponse.DENIED, request.getRequesterServer(), request.getRequester(),
                request.getRequesterName(), player.getUniqueId(), player.getName(),
                request.getTargetServer(), request.getType(), null);
    }

    public void cancelRequest(Player player, String targetName) {
        TpaRequest request = findOutgoing(player, targetName);

        if (request == null) {
            send(player, message("errors.no-outgoing-requests"));
            return;
        }

        String message = message("tpa.request-cancelled");
        send(player, message);

        if (isLocal(request.getTargetServer())) {
            removeRequest(request.getTarget(), player.getUniqueId());

            Player target = request.getTargetPlayer();
            if (target != null && target.isOnline()) {
                target.sendMessage(message.replace("{player}", player.getName()));
            }
            return;
        }

        dropOutgoing(player.getUniqueId(), request.getTarget());
        respond(TpaResponse.CANCELLED, request.getTargetServer(), request.getRequester(),
                request.getRequesterName(), request.getTarget(), request.getTargetName(),
                request.getTargetServer(), request.getType(), null);
    }

    public void toggleTpa(Player player) {
        boolean current = tpaToggle.getOrDefault(player.getUniqueId(), false);
        tpaToggle.put(player.getUniqueId(), !current);

        send(player, message(!current ? "tpa.disabled" : "tpa.enabled"));
    }

    public void handleRemoteResponse(TpaResponsePacket packet) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (packet.getResponse() == TpaResponse.CANCELLED) {
                removeRequest(packet.getTargetUuid(), packet.getRequesterUuid());

                Player target = Bukkit.getPlayer(packet.getTargetUuid());
                if (target != null && target.isOnline()) {
                    target.sendMessage(message("tpa.request-cancelled")
                            .replace("{player}", packet.getRequesterName()));
                }
                return;
            }

            dropOutgoing(packet.getRequesterUuid(), packet.getTargetUuid());

            Player requester = Bukkit.getPlayer(packet.getRequesterUuid());
            if (requester == null || !requester.isOnline()) {
                return;
            }

            switch (packet.getResponse()) {
                case DENIED -> send(requester, message("tpa.request-denied")
                        .replace("{player}", packet.getTargetName()));
                case EXPIRED -> send(requester, message("tpa.request-expired")
                        .replace("{player}", packet.getTargetName()));
                case DISABLED -> send(requester, message("errors.tpa-disabled")
                        .replace("{player}", packet.getTargetName()));
                case ACCEPTED -> handleAccepted(requester, packet);
                default -> {
                }
            }
        });
    }

    private void handleAccepted(Player requester, TpaResponsePacket packet) {
        send(requester, message("tpa.request-accepted").replace("{player}", packet.getTargetName()));

        if (packet.getTpaType() == TpaRequest.TpaType.TPA) {
            plugin.getCrossServerManager().sendTo(requester, TeleportType.TPA, packet.toDestination());
            return;
        }

        Location destination = requester.getLocation();
        plugin.getCrossServerManager().getPacketHandler().send(new TpaTravelPacket(
                plugin.getCrossServerManager().getSettings().getInstanceId(),
                packet.getTargetServer(), packet.getTargetUuid(), packet.getTargetName(),
                plugin.getCrossServerManager().currentServer(), destination, TeleportType.TPAHERE));
    }

    public void handleRemoteTravel(TpaTravelPacket packet) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player traveller = Bukkit.getPlayer(packet.getTravellerUuid());
            if (traveller == null || !traveller.isOnline()) {
                return;
            }

            plugin.getCrossServerManager().sendTo(traveller, packet.getTeleportType(),
                    packet.toDestination());
        });
    }

    private void showRequest(Player target, String requesterName) {
        send(target, message("tpa.request-received").replace("{player}", requesterName));

        Component acceptButton = miniMessage.deserialize(
                "<green><click:run_command:/tpaccept " + requesterName + ">[ACEPTAR]</click></green>");
        Component denyButton = miniMessage.deserialize(
                "<red><click:run_command:/tpdeny " + requesterName + ">[RECHAZAR]</click></red>");

        target.sendMessage(Component.text("  ").append(acceptButton)
                .append(Component.text(" ")).append(denyButton));
    }

    private void scheduleExpiry(TpaRequest request) {
        int timeout = requestTimeout();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!removeRequest(request.getTarget(), request.getRequester())) {
                return;
            }

            String expired = message("tpa.request-expired");

            Player target = request.getTargetPlayer();
            if (target != null && target.isOnline()) {
                target.sendMessage(expired.replace("{player}", request.getRequesterName()));
            }

            if (isLocal(request.getRequesterServer())) {
                Player requester = request.getRequesterPlayer();
                if (requester != null && requester.isOnline()) {
                    requester.sendMessage(expired.replace("{player}", request.getTargetName()));
                }
                return;
            }

            respond(TpaResponse.EXPIRED, request.getRequesterServer(), request.getRequester(),
                    request.getRequesterName(), request.getTarget(), request.getTargetName(),
                    request.getTargetServer(), request.getType(), null);
        }, timeout * 20L);
    }

    private void respond(TpaResponse response, String handlerServer, UUID requesterUuid,
                         String requesterName, UUID targetUuid, String targetName,
                         String targetServer, TpaRequest.TpaType type, Location accepterLocation) {
        CrossServerManager crossServer = plugin.getCrossServerManager();
        if (!crossServer.isEnabled()) {
            return;
        }

        crossServer.getPacketHandler().send(new TpaResponsePacket(
                crossServer.getSettings().getInstanceId(), handlerServer, response,
                requesterUuid, requesterName, targetUuid, targetName,
                targetServer, type, accepterLocation));
    }

    private TpaRequest getRequest(Player target, String requesterName) {
        Queue<TpaRequest> requests = pendingRequests.get(target.getUniqueId());
        if (requests == null || requests.isEmpty()) {
            return null;
        }

        if (requesterName != null) {
            return requests.stream()
                    .filter(request -> request.getRequesterName().equalsIgnoreCase(requesterName))
                    .findFirst()
                    .orElse(null);
        }

        String priority = plugin.getConfigManager().getMainConfig()
                .getString("teleport-settings.tpa.priority", "recent");

        if (priority.equalsIgnoreCase("oldest")) {
            return requests.peek();
        }

        TpaRequest last = null;
        for (TpaRequest request : requests) {
            last = request;
        }
        return last;
    }

    private TpaRequest findOutgoing(Player player, String targetName) {
        Queue<TpaRequest> outgoing = outgoingRequests.get(player.getUniqueId());
        if (outgoing != null) {
            for (TpaRequest request : outgoing) {
                if (targetName == null || request.getTargetName().equalsIgnoreCase(targetName)) {
                    return request;
                }
            }
        }

        for (Queue<TpaRequest> queue : pendingRequests.values()) {
            for (TpaRequest request : queue) {
                if (!request.getRequester().equals(player.getUniqueId())) {
                    continue;
                }
                if (targetName == null || request.getTargetName().equalsIgnoreCase(targetName)) {
                    return request;
                }
            }
        }

        return null;
    }

    private boolean removeRequest(UUID target, UUID requester) {
        Queue<TpaRequest> requests = pendingRequests.get(target);
        if (requests == null) {
            return false;
        }

        boolean removed = requests.removeIf(request -> request.getRequester().equals(requester));

        if (requests.isEmpty()) {
            pendingRequests.remove(target);
        }

        return removed;
    }

    private void dropOutgoing(UUID requester, UUID target) {
        Queue<TpaRequest> outgoing = outgoingRequests.get(requester);
        if (outgoing == null) {
            return;
        }

        outgoing.removeIf(request -> request.getTarget().equals(target));

        if (outgoing.isEmpty()) {
            outgoingRequests.remove(requester);
        }
    }

    private void cleanExpiredRequests() {
        int timeout = requestTimeout();

        pendingRequests.values().forEach(queue -> queue.removeIf(request -> request.isExpired(timeout)));
        pendingRequests.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        outgoingRequests.values().forEach(queue -> queue.removeIf(request -> request.isExpired(timeout)));
        outgoingRequests.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private int requestTimeout() {
        return plugin.getConfigManager().getMainConfig()
                .getInt("teleport-settings.tpa.request-timeout", 30);
    }

    private boolean isLocal(String server) {
        return plugin.getCrossServerManager().isLocal(server);
    }

    private String message(String path) {
        String message = plugin.getConfigManager().getLanguageConfig().getString(path);
        return message == null ? "" : message;
    }

    private void send(Player player, String message) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(message);
        }
    }

    public void safeDisable() {
        pendingRequests.clear();
        outgoingRequests.clear();
        tpaToggle.clear();
    }
}
