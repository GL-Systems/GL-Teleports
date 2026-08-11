package org.glstudio.teleports.modules.database;

import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.Warp;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DatabaseProvider {

    CompletableFuture<Void> initialize();
    CompletableFuture<Void> shutdown();

    CompletableFuture<List<Home>> getHomes(UUID owner);
    CompletableFuture<Home> getHome(UUID owner, String name);
    CompletableFuture<Void> saveHome(Home home);
    CompletableFuture<Void> deleteHome(UUID owner, String name);
    CompletableFuture<Integer> getHomeCount(UUID owner);

    CompletableFuture<List<Warp>> getAllWarps();
    CompletableFuture<Warp> getWarp(String name);
    CompletableFuture<Void> saveWarp(Warp warp);
    CompletableFuture<Void> deleteWarp(String name);
    CompletableFuture<Boolean> warpExists(String name);

    CompletableFuture<SpawnLocation> getSpawn();
    CompletableFuture<Void> saveSpawn(SpawnLocation spawn);
}
