package org.glstudio.teleports.modules.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.api.Events;
import org.glstudio.teleports.api.event.TpaRequestEvent;
import org.glstudio.teleports.common.network.packets.TpaRequestPacket;
import org.glstudio.teleports.common.network.packets.TpaResponse;
import org.glstudio.teleports.common.network.packets.TpaResponsePacket;
import org.glstudio.teleports.common.network.packets.TpaTravelPacket;
import org.glstudio.teleports.common.storage.redis.NetworkPlayer;
import org.glstudio.teleports.core.Failures;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.teleports.modules.model.TpaRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TpaManager {
    private final GTeleports plugin;
    private final Map<UUID, Queue<TpaRequest>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Queue<TpaRequest>> outgoingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> tpaToggle = new ConcurrentHashMap<>();

    private final TpaRequestStore store;

    public TpaManager(GTeleports plugin) {
        this.plugin = plugin;
        this.store = new TpaRequestStore(plugin);
    }

    private boolean isRefused(Player requester, UUID targetId, String targetName,
                              TpaRequest.TpaType type) {
        if (plugin.getTpaBlockManager().blocks(targetId, requester.getUniqueId())) {
            plugin.getMessages().send(requester, "errors.tpa-disabled", "{player}", targetName);
            return true;
        }

        TpaRequestEvent event = new TpaRequestEvent(requester, targetId, targetName, type);
        return !Events.callAndExplain(event, requester, event.getCancelMessage());
    }

    public void sendRequest(Player requester, String targetName, TpaRequest.TpaType type) {
        if (requester.getName().equalsIgnoreCase(targetName)) {
            plugin.getMessages().send(requester, "errors.cannot-teleport-self");
            return;
        }

        Player local = Bukkit.getPlayerExact(targetName);
        if (local != null && local.isOnline() && requester.canSee(local)) {
            sendLocalRequest(requester, local, type);
            return;
        }

        CrossServerManager crossServer = plugin.getCrossServerManager();
        if (!crossServer.isEnabled()) {
            plugin.getMessages().send(requester, "errors.player-offline");
            return;
        }

        UUID requesterId = requester.getUniqueId();
        Failures.log(crossServer.getPlayerRegistry().lookup(targetName).thenAccept(found ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player stillOnline = Bukkit.getPlayer(requesterId);
                    if (stillOnline == null || !stillOnline.isOnline()) {
                        return;
                    }
                    if (found == null) {
                        plugin.getMessages().send(stillOnline, "errors.player-offline");
                        return;
                    }
                    sendRemoteRequest(stillOnline, found, type);
                })), "TpaManager#sendRequest");
    }

    private void sendLocalRequest(Player requester, Player target, TpaRequest.TpaType type) {
        if (tpaToggle.getOrDefault(target.getUniqueId(), false)) {
            plugin.getMessages().send(requester, "errors.tpa-disabled", "{player}", target.getName());
            return;
        }

        if (isRefused(requester, target.getUniqueId(), target.getName(), type)) {
            return;
        }

        if (rejectDuplicate(requester, target.getUniqueId(), target.getName())) {
            return;
        }

        String server = plugin.getCrossServerManager().currentServer();
        TpaRequest request = new TpaRequest(
                UUID.randomUUID(),
                requester.getUniqueId(), requester.getName(), server,
                target.getUniqueId(), target.getName(), server,
                type, System.currentTimeMillis()
        );

        queueFor(pendingRequests, target.getUniqueId()).add(request);
        queueFor(outgoingRequests, requester.getUniqueId()).add(request);

        store.open(request.getId(), requestTimeout(type));

        plugin.getMessages().send(requester, "tpa.request-sent", "{player}", target.getName());
        showRequest(target, request);

        scheduleExpiry(request);
    }

    private void sendRemoteRequest(Player requester, NetworkPlayer target, TpaRequest.TpaType type) {
        CrossServerManager crossServer = plugin.getCrossServerManager();
        String server = crossServer.currentServer();

        TpaRequestEvent event = new TpaRequestEvent(requester, target.getUuid(), target.getName(), type);
        if (!Events.callAndExplain(event, requester, event.getCancelMessage())) {
            return;
        }

        if (rejectDuplicate(requester, target.getUuid(), target.getName())) {
            return;
        }

        TpaRequest request = new TpaRequest(
                UUID.randomUUID(),
                requester.getUniqueId(), requester.getName(), server,
                target.getUuid(), target.getName(), target.getServer(),
                type, System.currentTimeMillis()
        );

        queueFor(outgoingRequests, requester.getUniqueId()).add(request);
        store.open(request.getId(), requestTimeout(type));

        crossServer.getPacketHandler().send(new TpaRequestPacket(
                        request.getId(), crossServer.getSettings().getInstanceId(), target.getServer(),
                        requester.getUniqueId(), requester.getName(), server,
                        target.getUuid(), target.getName(), type))
                .exceptionally(error -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        dropOutgoing(requester.getUniqueId(), request.getId());
                        plugin.getMessages().send(requester, "errors.network-error");
                    });
                    return null;
                });

        plugin.getMessages().send(requester, "tpa.request-sent", "{player}", target.getName());

        scheduleExpiry(request);
    }

    private boolean rejectDuplicate(Player requester, UUID targetId, String targetName) {
        Queue<TpaRequest> outgoing = outgoingRequests.get(requester.getUniqueId());
        if (outgoing == null) {
            return false;
        }

        for (TpaRequest existing : outgoing) {
            if (existing.getTarget().equals(targetId)) {
                plugin.getMessages().send(requester, "errors.request-already-sent", "{player}", targetName);
                return true;
            }
        }

        int maxOutgoing = plugin.getSettings()
                .getInt("teleport-settings.tpa.max-outgoing", 5);

        if (outgoing.size() >= maxOutgoing) {
            plugin.getMessages().send(requester, "errors.too-many-requests",
                    "{limit}", String.valueOf(maxOutgoing));
            return true;
        }

        return false;
    }

    public void handleRemoteRequest(TpaRequestPacket packet) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player target = Bukkit.getPlayer(packet.getTargetUuid());
            String here = plugin.getCrossServerManager().currentServer();

            if (target == null || !target.isOnline()) {
                respond(packet.getId(), TpaResponse.EXPIRED, packet.getRequesterServer(),
                        packet.getRequesterUuid(), packet.getRequesterName(), packet.getTargetUuid(),
                        packet.getTargetName(), here, packet.getTpaType(), null);
                return;
            }

            if (tpaToggle.getOrDefault(target.getUniqueId(), false)
                    || plugin.getTpaBlockManager().blocks(target.getUniqueId(), packet.getRequesterUuid())) {
                respond(packet.getId(), TpaResponse.DISABLED, packet.getRequesterServer(),
                        packet.getRequesterUuid(), packet.getRequesterName(), target.getUniqueId(),
                        target.getName(), here, packet.getTpaType(), null);
                return;
            }

            TpaRequest request = new TpaRequest(
                    packet.getId(),
                    packet.getRequesterUuid(), packet.getRequesterName(), packet.getRequesterServer(),
                    target.getUniqueId(), target.getName(), here,
                    packet.getTpaType(), System.currentTimeMillis()
            );

            queueFor(pendingRequests, target.getUniqueId()).add(request);

            showRequest(target, request);
            scheduleExpiry(request);
        });
    }

    public void acceptRequest(Player player, String requesterName) {
        TpaRequest request = findPending(player, requesterName);

        if (request == null) {
            plugin.getMessages().send(player, "errors.no-pending-requests");
            return;
        }

        removeRequest(player.getUniqueId(), request.getId());

        claimThen(player, request, () -> {
            if (isLocal(request.getRequesterServer())) {
                acceptLocally(player, request);
                return;
            }

            respond(request.getId(), TpaResponse.ACCEPTED, request.getRequesterServer(), request.getRequester(),
                    request.getRequesterName(), player.getUniqueId(), player.getName(),
                    request.getTargetServer(), request.getType(), player.getLocation());

            plugin.getMessages().send(player, "tpa.request-accepted", "{player}", request.getRequesterName());
        });
    }

    private void claimThen(Player player, TpaRequest request, Runnable action) {
        Failures.log(store.claim(request.getId()).thenAccept(won ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (!won) {
                        plugin.getMessages().send(player, "errors.no-pending-requests");
                        return;
                    }
                    action.run();
                })), "TpaManager#claim");
    }

    private void acceptLocally(Player player, TpaRequest request) {
        dropOutgoing(request.getRequester(), request.getId());

        Player requester = request.getRequesterPlayer();
        if (requester == null || !requester.isOnline()) {
            plugin.getMessages().send(player, "errors.player-offline");
            return;
        }

        Player teleporter = request.getType() == TpaRequest.TpaType.TPA ? requester : player;
        Player destination = request.getType() == TpaRequest.TpaType.TPA ? player : requester;

        plugin.getTeleportManager().requestTeleport(teleporter, destination.getLocation(),
                request.toTeleportType(), null, destination.getName(), success -> {
                    if (success) {
                        plugin.getMessages().send(player, "tpa.request-accepted",
                                "{player}", requester.getName());
                        plugin.getMessages().send(requester, "tpa.request-accepted",
                                "{player}", player.getName());
                    }
                });
    }

    public void denyRequest(Player player, String requesterName) {
        TpaRequest request = findPending(player, requesterName);

        if (request == null) {
            plugin.getMessages().send(player, "errors.no-pending-requests");
            return;
        }

        removeRequest(player.getUniqueId(), request.getId());
        store.discard(request.getId());
        plugin.getMessages().send(player, "tpa.request-denied", "{player}", request.getRequesterName());

        if (isLocal(request.getRequesterServer())) {
            dropOutgoing(request.getRequester(), request.getId());

            Player requester = request.getRequesterPlayer();
            if (requester != null && requester.isOnline()) {
                plugin.getMessages().send(requester, "tpa.request-denied", "{player}", player.getName());
            }
            return;
        }

        respond(request.getId(), TpaResponse.DENIED, request.getRequesterServer(), request.getRequester(),
                request.getRequesterName(), player.getUniqueId(), player.getName(),
                request.getTargetServer(), request.getType(), null);
    }

    public void cancelRequest(Player player, String targetName) {
        TpaRequest request = findOutgoing(player, targetName);

        if (request == null) {
            plugin.getMessages().send(player, "errors.no-outgoing-requests");
            return;
        }

        dropOutgoing(player.getUniqueId(), request.getId());
        store.discard(request.getId());
        plugin.getMessages().send(player, "tpa.request-cancelled");

        if (isLocal(request.getTargetServer())) {
            removeRequest(request.getTarget(), request.getId());

            Player target = request.getTargetPlayer();
            if (target != null && target.isOnline()) {
                plugin.getMessages().send(target, "tpa.request-cancelled", "{player}", player.getName());
            }
            return;
        }

        respond(request.getId(), TpaResponse.CANCELLED, request.getTargetServer(), request.getRequester(),
                request.getRequesterName(), request.getTarget(), request.getTargetName(),
                request.getTargetServer(), request.getType(), null);
    }

    public void toggleTpa(Player player) {
        boolean enabled = tpaToggle.getOrDefault(player.getUniqueId(), false);
        tpaToggle.put(player.getUniqueId(), !enabled);

        plugin.getMessages().send(player, !enabled ? "tpa.disabled" : "tpa.enabled");
        plugin.getPlayerSettings().setTpaBlocked(player.getUniqueId(), !enabled);
    }

    public void applyStoredToggle(UUID uuid, boolean blocked) {
        if (blocked) {
            tpaToggle.put(uuid, true);
        } else {
            tpaToggle.remove(uuid);
        }
    }

    public void handleRemoteResponse(TpaResponsePacket packet) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (packet.getResponse() == TpaResponse.CANCELLED) {
                removeRequest(packet.getTargetUuid(), packet.getId());

                Player target = Bukkit.getPlayer(packet.getTargetUuid());
                if (target != null && target.isOnline()) {
                    plugin.getMessages().send(target, "tpa.request-cancelled",
                            "{player}", packet.getRequesterName());
                }
                return;
            }

            dropOutgoing(packet.getRequesterUuid(), packet.getId());

            Player requester = Bukkit.getPlayer(packet.getRequesterUuid());
            if (requester == null || !requester.isOnline()) {
                return;
            }

            switch (packet.getResponse()) {
                case DENIED -> plugin.getMessages().send(requester, "tpa.request-denied",
                        "{player}", packet.getTargetName());
                case EXPIRED -> plugin.getMessages().send(requester, "tpa.request-expired",
                        "{player}", packet.getTargetName());
                case DISABLED -> plugin.getMessages().send(requester, "errors.tpa-disabled",
                        "{player}", packet.getTargetName());
                case ACCEPTED -> handleAccepted(requester, packet);
                default -> {
                }
            }
        });
    }

    private void handleAccepted(Player requester, TpaResponsePacket packet) {
        CrossServerManager crossServer = plugin.getCrossServerManager();

        if (!crossServer.isEnabled()) {
            plugin.getMessages().send(requester, "errors.network-error");
            return;
        }

        plugin.getMessages().send(requester, "tpa.request-accepted", "{player}", packet.getTargetName());

        if (packet.getTpaType() == TpaRequest.TpaType.TPA) {
            crossServer.sendTo(requester, TeleportType.TPA, packet.toDestination());
            return;
        }

        Location destination = requester.getLocation();
        crossServer.getPacketHandler().send(new TpaTravelPacket(
                        packet.getId(), crossServer.getSettings().getInstanceId(),
                        packet.getTargetServer(), packet.getTargetUuid(), packet.getTargetName(),
                        crossServer.currentServer(), destination, TeleportType.TPAHERE))
                .exceptionally(error -> {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getMessages().send(requester, "errors.network-error"));
                    return null;
                });
    }

    public void handleRemoteTravel(TpaTravelPacket packet) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player traveller = Bukkit.getPlayer(packet.getTravellerUuid());
            if (traveller == null || !traveller.isOnline()) {
                return;
            }

            plugin.getCrossServerManager().sendTo(traveller, packet.getTeleportType(), packet.toDestination());
        });
    }

    private void showRequest(Player target, TpaRequest request) {
        String requesterName = request.getRequesterName();
        boolean sameServer = isLocal(request.getRequesterServer());

        plugin.getMessages().send(target, sameServer ? "tpa.request-received" : "tpa.request-received-remote",
                "{player}", requesterName,
                "{server}", String.valueOf(request.getRequesterServer()),
                "{time}", String.valueOf(requestTimeout(request.getType())),
                "{direction}", plugin.getMessages().raw(request.getType() == TpaRequest.TpaType.TPA
                        ? "tpa.direction-to-you" : "tpa.direction-to-them"));

        Component accept = plugin.getMessages().component("tpa.button-accept")
                .clickEvent(ClickEvent.runCommand("/tpaccept " + requesterName));
        Component deny = plugin.getMessages().component("tpa.button-deny")
                .clickEvent(ClickEvent.runCommand("/tpdeny " + requesterName));

        target.sendMessage(Component.text("  ").append(accept).append(Component.text(" ")).append(deny));
    }

    private void scheduleExpiry(TpaRequest request) {
        int timeout = requestTimeout(request.getType());

        request.setExpiryTask(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean removed = removeRequest(request.getTarget(), request.getId());
            boolean dropped = dropOutgoing(request.getRequester(), request.getId());

            if (!removed && !dropped) {
                return;
            }

            store.discard(request.getId());

            Player target = request.getTargetPlayer();
            if (target != null && target.isOnline() && isLocal(request.getTargetServer())) {
                plugin.getMessages().send(target, "tpa.request-expired", "{player}", request.getRequesterName());
            }

            if (isLocal(request.getRequesterServer())) {
                Player requester = request.getRequesterPlayer();
                if (requester != null && requester.isOnline()) {
                    plugin.getMessages().send(requester, "tpa.request-expired",
                            "{player}", request.getTargetName());
                }
                return;
            }

            respond(request.getId(), TpaResponse.EXPIRED, request.getRequesterServer(), request.getRequester(),
                    request.getRequesterName(), request.getTarget(), request.getTargetName(),
                    request.getTargetServer(), request.getType(), null);
        }, timeout * 20L));
    }

    private void respond(UUID id, TpaResponse response, String handlerServer, UUID requesterUuid,
                         String requesterName, UUID targetUuid, String targetName,
                         String targetServer, TpaRequest.TpaType type, Location accepterLocation) {
        CrossServerManager crossServer = plugin.getCrossServerManager();
        if (!crossServer.isEnabled()) {
            return;
        }

        crossServer.getPacketHandler().send(new TpaResponsePacket(
                        id, crossServer.getSettings().getInstanceId(), handlerServer, response,
                        requesterUuid, requesterName, targetUuid, targetName,
                        targetServer, type, accepterLocation))
                .exceptionally(error -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player affected = Bukkit.getPlayer(targetUuid);
                        if (affected != null && affected.isOnline()) {
                            plugin.getMessages().send(affected, "errors.network-error");
                        }
                    });
                    return null;
                });
    }

    private TpaRequest findPending(Player target, String requesterName) {
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

        String priority = plugin.getSettings()
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
        if (outgoing == null) {
            return null;
        }

        for (TpaRequest request : outgoing) {
            if (targetName == null || request.getTargetName().equalsIgnoreCase(targetName)) {
                return request;
            }
        }

        return null;
    }

    public List<String> pendingRequesterNames(Player target) {
        Queue<TpaRequest> requests = pendingRequests.get(target.getUniqueId());
        if (requests == null) {
            return List.of();
        }

        List<String> names = new ArrayList<>(requests.size());
        for (TpaRequest request : requests) {
            names.add(request.getRequesterName());
        }
        return names;
    }

    public List<String> outgoingTargetNames(Player requester) {
        Queue<TpaRequest> requests = outgoingRequests.get(requester.getUniqueId());
        if (requests == null) {
            return List.of();
        }

        List<String> names = new ArrayList<>(requests.size());
        for (TpaRequest request : requests) {
            names.add(request.getTargetName());
        }
        return names;
    }

    private boolean removeRequest(UUID target, UUID requestId) {
        return removeFrom(pendingRequests, target, requestId);
    }

    private boolean dropOutgoing(UUID requester, UUID requestId) {
        return removeFrom(outgoingRequests, requester, requestId);
    }

    private boolean removeFrom(Map<UUID, Queue<TpaRequest>> index, UUID owner, UUID requestId) {
        Queue<TpaRequest> queue = index.get(owner);
        if (queue == null) {
            return false;
        }

        boolean removed = queue.removeIf(request -> {
            if (!request.getId().equals(requestId)) {
                return false;
            }
            request.cancelExpiry();
            return true;
        });

        if (queue.isEmpty()) {
            index.remove(owner, queue);
        }

        return removed;
    }

    public void forget(UUID uuid) {
        Queue<TpaRequest> pending = pendingRequests.remove(uuid);
        if (pending != null) {
            pending.forEach(TpaRequest::cancelExpiry);
        }

        Queue<TpaRequest> outgoing = outgoingRequests.remove(uuid);
        if (outgoing != null) {
            outgoing.forEach(TpaRequest::cancelExpiry);
        }

        tpaToggle.remove(uuid);
    }

    private static Queue<TpaRequest> queueFor(Map<UUID, Queue<TpaRequest>> index, UUID owner) {
        return index.computeIfAbsent(owner, key -> new ConcurrentLinkedQueue<>());
    }

    private int requestTimeout(TpaRequest.TpaType type) {
        String key = type == TpaRequest.TpaType.TPAHERE ? "tpahere" : "tpa";
        return plugin.getSettings()
                .getInt("teleport-settings." + key + ".request-timeout", 30);
    }

    private boolean isLocal(String server) {
        return plugin.getCrossServerManager().isLocal(server);
    }

    public void safeDisable() {
        pendingRequests.values().forEach(queue -> queue.forEach(TpaRequest::cancelExpiry));
        outgoingRequests.values().forEach(queue -> queue.forEach(TpaRequest::cancelExpiry));

        pendingRequests.clear();
        outgoingRequests.clear();
        tpaToggle.clear();
    }
}
