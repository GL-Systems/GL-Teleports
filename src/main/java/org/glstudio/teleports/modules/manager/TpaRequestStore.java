package org.glstudio.teleports.modules.manager;

import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TpaRequestStore {
    private final GTeleports plugin;

    private final Set<UUID> localRequests = ConcurrentHashMap.newKeySet();

    public TpaRequestStore(GTeleports plugin) {
        this.plugin = plugin;
    }

    public void open(UUID requestId, int timeoutSeconds) {
        if (!shared()) {
            localRequests.add(requestId);
            return;
        }

        CompletableFuture.runAsync(
                        () -> plugin.getRedisService().set(key(requestId), "1", timeoutSeconds),
                        plugin.getExecutors().io())
                .exceptionally(error -> {
                    LoggerUtils.logWarn("Could not record teleport request " + requestId
                            + "; it will be resolved locally only: " + error.getMessage());
                    localRequests.add(requestId);
                    return null;
                });
    }

    public CompletableFuture<Boolean> claim(UUID requestId) {
        if (!shared()) {
            return CompletableFuture.completedFuture(localRequests.remove(requestId));
        }

        return CompletableFuture.supplyAsync(
                        () -> plugin.getRedisService().getAndDelete(key(requestId)) != null,
                        plugin.getExecutors().io())
                .exceptionally(error -> {
                    LoggerUtils.logWarn("Could not claim teleport request " + requestId
                            + ": " + error.getMessage());

                    return localRequests.remove(requestId);
                });
    }

    public void discard(UUID requestId) {
        localRequests.remove(requestId);

        if (!shared()) {
            return;
        }

        CompletableFuture.runAsync(() -> plugin.getRedisService().delete(key(requestId)),
                plugin.getExecutors().io()).exceptionally(error -> null);
    }

    private boolean shared() {
        return plugin.getCrossServerManager().isEnabled() && plugin.getRedisService() != null;
    }

    private String key(UUID requestId) {
        return plugin.getMultiServerSettings().getChannel() + ":tpa:" + requestId;
    }
}
