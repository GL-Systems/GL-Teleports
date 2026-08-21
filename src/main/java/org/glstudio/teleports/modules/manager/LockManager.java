package org.glstudio.teleports.modules.manager;

import org.bukkit.command.CommandSender;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.modules.model.DestinationLock;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class LockManager {
    private final GTeleports plugin;

    private final Map<String, DestinationLock> locks = new ConcurrentHashMap<>();

    public LockManager(GTeleports plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        Failures.log(plugin.getDatabaseManager().getProvider().getLocks().thenAccept(loaded -> {
            locks.clear();
            for (DestinationLock lock : loaded) {
                locks.put(lock.key(), lock);
            }
        }), "LockManager#refresh");
    }

    public DestinationLock find(String type, String target) {
        DestinationLock lock = locks.get(DestinationLock.key(type, target));

        if (lock == null) {
            return null;
        }

        if (lock.isExpired()) {
            locks.remove(lock.key(), lock);
            Failures.log(plugin.getDatabaseManager().getProvider().deleteLock(type, target),
                    "LockManager#expire");
            return null;
        }

        return lock;
    }

    public boolean isLocked(String type, String target) {
        return find(type, target) != null;
    }

    public boolean isWarpLocked(String warp) {
        return isLocked(DestinationLock.WARP, warp);
    }

    public boolean isServerLocked(String server) {
        return isLocked(DestinationLock.SERVER, server);
    }

    public List<DestinationLock> active() {
        return locks.values().stream().filter(DestinationLock::isActive).toList();
    }

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

        return Failures.guard(plugin, result, "LockManager#lock", staff);
    }

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

        return Failures.guard(plugin, result, "LockManager#unlock", staff);
    }

    public void safeDisable() {
        locks.clear();
    }
}
