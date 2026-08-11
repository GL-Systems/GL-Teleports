package org.glstudio.teleports.modules.database.impl;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.database.DatabaseProvider;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.Warp;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MongoProvider implements DatabaseProvider {

    private final GTeleports plugin;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> homesCollection;
    private MongoCollection<Document> warpsCollection;
    private MongoCollection<Document> spawnCollection;

    public MongoProvider(GTeleports plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                var dbConfig = plugin.getConfigManager().getDatabaseConfig();

                String connectionString;
                if (dbConfig.getBoolean("mongo.auth.enabled")) {
                    connectionString = String.format(
                            "mongodb://%s:%s@%s:%d/%s",
                            dbConfig.getString("mongo.auth.username"),
                            dbConfig.getString("mongo.auth.password"),
                            dbConfig.getString("mongo.host"),
                            dbConfig.getInt("mongo.port"),
                            dbConfig.getString("mongo.database")
                    );
                } else {
                    connectionString = String.format(
                            "mongodb://%s:%d",
                            dbConfig.getString("mongo.host"),
                            dbConfig.getInt("mongo.port")
                    );
                }

                mongoClient = MongoClients.create(connectionString);
                database = mongoClient.getDatabase(dbConfig.getString("mongo.database"));

                homesCollection = database.getCollection("homes");
                warpsCollection = database.getCollection("warps");
                spawnCollection = database.getCollection("spawn");

            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize MongoDB provider", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            if (mongoClient != null) {
                mongoClient.close();
            }
        });
    }

    @Override
    public CompletableFuture<List<Home>> getHomes(UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
            List<Home> homes = new ArrayList<>();

            homesCollection.find(Filters.eq("owner", owner.toString()))
                    .forEach(doc -> homes.add(documentToHome(doc)));

            return homes;
        });
    }

    @Override
    public CompletableFuture<Home> getHome(UUID owner, String name) {
        return CompletableFuture.supplyAsync(() -> {
            Document doc = homesCollection.find(
                    Filters.and(
                            Filters.eq("owner", owner.toString()),
                            Filters.eq("name", name)
                    )
            ).first();

            return doc != null ? documentToHome(doc) : null;
        });
    }

    @Override
    public CompletableFuture<Void> saveHome(Home home) {
        return CompletableFuture.runAsync(() -> {
            Document doc = homeToDocument(home);

            homesCollection.replaceOne(
                    Filters.and(
                            Filters.eq("owner", home.getOwner().toString()),
                            Filters.eq("name", home.getName())
                    ),
                    doc,
                    new ReplaceOptions().upsert(true)
            );
        });
    }

    @Override
    public CompletableFuture<Void> deleteHome(UUID owner, String name) {
        return CompletableFuture.runAsync(() -> {
            homesCollection.deleteOne(
                    Filters.and(
                            Filters.eq("owner", owner.toString()),
                            Filters.eq("name", name)
                    )
            );
        });
    }

    @Override
    public CompletableFuture<Integer> getHomeCount(UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
            return (int) homesCollection.countDocuments(Filters.eq("owner", owner.toString()));
        });
    }

    @Override
    public CompletableFuture<List<Warp>> getAllWarps() {
        return CompletableFuture.supplyAsync(() -> {
            List<Warp> warps = new ArrayList<>();

            warpsCollection.find().forEach(doc -> warps.add(documentToWarp(doc)));

            return warps;
        });
    }

    @Override
    public CompletableFuture<Warp> getWarp(String name) {
        return CompletableFuture.supplyAsync(() -> {
            Document doc = warpsCollection.find(Filters.eq("name", name)).first();

            return doc != null ? documentToWarp(doc) : null;
        });
    }

    @Override
    public CompletableFuture<Void> saveWarp(Warp warp) {
        return CompletableFuture.runAsync(() -> {
            Document doc = warpToDocument(warp);

            warpsCollection.replaceOne(
                    Filters.eq("name", warp.getName()),
                    doc,
                    new ReplaceOptions().upsert(true)
            );
        });
    }

    @Override
    public CompletableFuture<Void> deleteWarp(String name) {
        return CompletableFuture.runAsync(() -> {
            warpsCollection.deleteOne(Filters.eq("name", name));
        });
    }

    @Override
    public CompletableFuture<Boolean> warpExists(String name) {
        return CompletableFuture.supplyAsync(() -> {
            return warpsCollection.countDocuments(Filters.eq("name", name)) > 0;
        });
    }

    @Override
    public CompletableFuture<SpawnLocation> getSpawn() {
        return CompletableFuture.supplyAsync(() -> {
            Document doc = spawnCollection.find().first();

            return doc != null ? documentToSpawn(doc) : null;
        });
    }

    @Override
    public CompletableFuture<Void> saveSpawn(SpawnLocation spawn) {
        return CompletableFuture.runAsync(() -> {
            Document doc = spawnToDocument(spawn);

            spawnCollection.deleteMany(new Document());
            spawnCollection.insertOne(doc);
        });
    }

    private Home documentToHome(Document doc) {
        return new Home(
                UUID.fromString(doc.getString("owner")),
                doc.getString("name"),
                doc.getString("world"),
                doc.getDouble("x"),
                doc.getDouble("y"),
                doc.getDouble("z"),
                doc.getDouble("yaw").floatValue(),
                doc.getDouble("pitch").floatValue(),
                doc.getLong("created_at"),
                doc.getString("server")
        );
    }

    private Document homeToDocument(Home home) {
        return new Document()
                .append("owner", home.getOwner().toString())
                .append("name", home.getName())
                .append("world", home.getWorld())
                .append("x", home.getX())
                .append("y", home.getY())
                .append("z", home.getZ())
                .append("yaw", (double) home.getYaw())
                .append("pitch", (double) home.getPitch())
                .append("created_at", home.getCreatedAt())
                .append("server", home.getServer());
    }

    private Warp documentToWarp(Document doc) {
        return new Warp(
                doc.getString("name"),
                doc.getString("world"),
                doc.getDouble("x"),
                doc.getDouble("y"),
                doc.getDouble("z"),
                doc.getDouble("yaw").floatValue(),
                doc.getDouble("pitch").floatValue(),
                doc.getString("permission"),
                doc.getLong("created_at"),
                doc.getString("server")
        );
    }

    private Document warpToDocument(Warp warp) {
        return new Document()
                .append("name", warp.getName())
                .append("world", warp.getWorld())
                .append("x", warp.getX())
                .append("y", warp.getY())
                .append("z", warp.getZ())
                .append("yaw", (double) warp.getYaw())
                .append("pitch", (double) warp.getPitch())
                .append("permission", warp.getPermission())
                .append("created_at", warp.getCreatedAt())
                .append("server", warp.getServer());
    }

    private SpawnLocation documentToSpawn(Document doc) {
        return new SpawnLocation(
                doc.getString("world"),
                doc.getDouble("x"),
                doc.getDouble("y"),
                doc.getDouble("z"),
                doc.getDouble("yaw").floatValue(),
                doc.getDouble("pitch").floatValue(),
                doc.getString("server")
        );
    }

    private Document spawnToDocument(SpawnLocation spawn) {
        return new Document()
                .append("world", spawn.getWorld())
                .append("x", spawn.getX())
                .append("y", spawn.getY())
                .append("z", spawn.getZ())
                .append("yaw", (double) spawn.getYaw())
                .append("pitch", (double) spawn.getPitch())
                .append("server", spawn.getServer());
    }
}
