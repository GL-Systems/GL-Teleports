package org.glstudio.teleports.modules.manager;

import org.bukkit.command.CommandSender;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.modules.model.DestinationLock;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the warps and servers locked by staff.
 */
public class LockManager {
    private final GTeleports plugin;

    private final Map<String, DestinationLock> locks = new ConcurrentHashMap<>();

    /**
     * Creates the manager.
     */
    public LockManager(GTeleports plugin) {
        this.plugin = plugin;
        refresh();
    }

    /**
     * Reads the locks again.
     */
    public void refresh() {
        Futures.log(plugin.getDatabaseManager().getProvider().getLocks().thenAccept(loaded -> {
            locks.clear();
            for (DestinationLock lock : loaded) {
                locks.put(lock.key(), lock);
            }
        }), "LockManager#refresh");
    }

    /**
     * Finds the lock of a warp or a server.
     */
    public DestinationLock find(String type, String target) {
        DestinationLock lock = locks.get(DestinationLock.key(type, target));

        if (lock == null) {
            return null;
        }

        if (lock.isExpired()) {
            locks.remove(lock.key(), lock);
            Futures.log(plugin.getDatabaseManager().getProvider().deleteLock(type, target),
                    "LockManager#expire");
            return null;
        }

        return lock;
    }

    /**
     * Tells whether a warp or a server is locked.
     */
    public boolean isLocked(String type, String target) {
        return find(type, target) != null;
    }

    /**
     * Tells whether a warp is locked.
     */
    public boolean isWarpLocked(String warp) {
        return isLocked(DestinationLock.WARP, warp);
    }

    /**
     * Tells whether a server is locked.
     */
    public boolean isServerLocked(String server) {
        return isLocked(DestinationLock.SERVER, server);
    }

    /**
     * Lists the locks in force.
     */
    public List<DestinationLock> active() {
        return locks.values().stream().filter(DestinationLock::isActive).toList();
    }

    /**
     * Locks a warp or a server.
     */
    public CompletableFuture<Void> lock(CommandSender staff, String type, String target,
                                        String reason, long until) {
        DestinationLock lock = DestinationLock.of(type, target, reason, staff.getName(), until);
        locks.put(lock.key(), lock);

        CompletableFuture<Void> result = plugin.getDatabaseManager().getProvider().saveLock(lock)
                .thenRun(() -> {
                    plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.LOCK, null);
                    plugin.getMessages().send(staff, "lock.placed",
                            "{type}", lock.type(), "{target}", lock.target(),
                            "{reason}", lock.reason() == null ? "-" : lock.reason());
                });

        return Futures.guard(result, "LockManager#lock", staff);
    }

    /**
     * Unlocks a warp or a server.
     */
    public CompletableFuture<Void> unlock(CommandSender staff, String type, String target) {
        String key = DestinationLock.key(type, target);
        boolean cached = locks.remove(key) != null;

        CompletableFuture<Void> result = plugin.getDatabaseManager().getProvider()
                .deleteLock(type, target)
                .thenAccept(removed -> {
                    if (!removed && !cached) {
                        plugin.getMessages().send(staff, "lock.not-found",
                                "{type}", DestinationLock.normalize(type),
                                "{target}", DestinationLock.normalize(target));
                        return;
                    }

                    plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.LOCK, null);
                    plugin.getMessages().send(staff, "lock.lifted",
                            "{type}", DestinationLock.normalize(type),
                            "{target}", DestinationLock.normalize(target));
                });

        return Futures.guard(result, "LockManager#unlock", staff);
    }

    /**
     * Stops this on shutdown.
     */
    public void safeDisable() {
        locks.clear();
    }
}
