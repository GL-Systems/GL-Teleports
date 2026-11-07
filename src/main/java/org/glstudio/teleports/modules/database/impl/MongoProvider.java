package org.glstudio.teleports.modules.database.impl;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.BackLocation;
import org.glstudio.teleports.modules.model.DestinationLock;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.HomeShare;
import org.glstudio.teleports.modules.model.PlayerRef;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpBan;
import org.glstudio.teleports.modules.model.PlayerWarpFavourite;
import org.glstudio.teleports.modules.model.PlayerWarpManagerEntry;
import org.glstudio.teleports.modules.model.PlayerWarpRating;
import org.glstudio.teleports.modules.model.PlayerWarpSponsoredSlot;
import org.glstudio.teleports.modules.model.PlayerWarpWhitelistEntry;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.StoredLocation;
import org.glstudio.teleports.modules.model.TeleportRecord;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.teleports.modules.model.Warp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Stores the data in MongoDB.
 */
public class MongoProvider extends AbstractProvider {
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> homesCollection;
    private MongoCollection<Document> warpsCollection;
    private MongoCollection<Document> spawnCollection;
    private MongoCollection<Document> settingsCollection;
    private MongoCollection<Document> playersCollection;
    private MongoCollection<Document> lastLocationsCollection;
    private MongoCollection<Document> bedRespawnPointsCollection;
    private MongoCollection<Document> teleportLogCollection;
    private MongoCollection<Document> homeSharesCollection;
    private MongoCollection<Document> tpaBlocksCollection;
    private MongoCollection<Document> locksCollection;
    private MongoCollection<Document> playerWarpsCollection;
    private MongoCollection<Document> playerWarpWhitelistCollection;
    private MongoCollection<Document> playerWarpBansCollection;
    private MongoCollection<Document> playerWarpManagersCollection;
    private MongoCollection<Document> playerWarpRatingsCollection;
    private MongoCollection<Document> playerWarpFavouritesCollection;
    private MongoCollection<Document> playerWarpSponsoredSlotsCollection;
    private MongoCollection<Document> backLocationsCollection;

    /**
     * Creates the provider.
     */
    public MongoProvider(GTeleports plugin) {
        super(plugin);
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return run(() -> {
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
                settingsCollection = database.getCollection("player_settings");
                playersCollection = database.getCollection("players");
                lastLocationsCollection = database.getCollection("last_locations");
                bedRespawnPointsCollection = database.getCollection("bed_respawn_points");
                teleportLogCollection = database.getCollection("teleport_log");
                homeSharesCollection = database.getCollection("home_shares");
                tpaBlocksCollection = database.getCollection("tpa_blocks");
                locksCollection = database.getCollection("destination_locks");
                playerWarpsCollection = database.getCollection("player_warps");
                playerWarpWhitelistCollection = database.getCollection("player_warp_whitelist");
                playerWarpBansCollection = database.getCollection("player_warp_bans");
                playerWarpManagersCollection = database.getCollection("player_warp_managers");
                playerWarpRatingsCollection = database.getCollection("player_warp_ratings");
                playerWarpFavouritesCollection = database.getCollection("player_warp_favourites");
                playerWarpSponsoredSlotsCollection = database.getCollection("player_warp_sponsored_slots");
                backLocationsCollection = database.getCollection("back_locations");

                teleportLogCollection.createIndex(new Document("player", 1).append("created_at", -1));
                teleportLogCollection.createIndex(new Document("destination", 1).append("created_at", -1));
                teleportLogCollection.createIndex(new Document("created_at", 1));
                homeSharesCollection.createIndex(new Document("invitee", 1));
                playerWarpsCollection.createIndex(new Document("id", 1));
                playerWarpsCollection.createIndex(new Document("name", 1));
                playerWarpsCollection.createIndex(new Document("owner", 1));
                playerWarpsCollection.createIndex(new Document("category_id", 1));
                playerWarpWhitelistCollection.createIndex(new Document("warp_id", 1).append("player", 1));
                playerWarpBansCollection.createIndex(new Document("warp_id", 1).append("player", 1));
                playerWarpManagersCollection.createIndex(new Document("warp_id", 1).append("player", 1));
                playerWarpRatingsCollection.createIndex(new Document("warp_id", 1).append("player", 1));
                playerWarpFavouritesCollection.createIndex(new Document("player", 1).append("warp_id", 1));
                playerWarpSponsoredSlotsCollection.createIndex(new Document("slot_index", 1));
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize MongoDB provider", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return run(() -> {
            if (mongoClient != null) {
                mongoClient.close();
            }
        });
    }

    @Override
    public CompletableFuture<List<Home>> getHomes(UUID owner) {
        return supply(() -> {
            List<Home> homes = new ArrayList<>();

            homesCollection.find(Filters.eq("owner", owner.toString())).forEach(doc -> {
                Home home = documentToHome(doc);
                if (home != null) {
                    homes.add(home);
                }
            });

            return homes;
        });
    }

    @Override
    public CompletableFuture<Home> getHome(UUID owner, String name) {
        return supply(() -> {
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
        return run(() -> {
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
        return run(() -> {
            homesCollection.deleteOne(
                    Filters.and(
                            Filters.eq("owner", owner.toString()),
                            Filters.eq("name", name)
                    )
            );
        });
    }

    @Override
    public CompletableFuture<List<Warp>> getAllWarps() {
        return supply(() -> {
            List<Warp> warps = new ArrayList<>();

            warpsCollection.find().forEach(doc -> {
                Warp warp = documentToWarp(doc);
                if (warp != null) {
                    warps.add(warp);
                }
            });

            return warps;
        });
    }

    @Override
    public CompletableFuture<Warp> getWarp(String name) {
        return supply(() -> {
            Document doc = warpsCollection.find(Filters.eq("name", name)).first();

            return doc != null ? documentToWarp(doc) : null;
        });
    }

    @Override
    public CompletableFuture<Void> saveWarp(Warp warp) {
        return run(() -> {
            Document doc = warpToDocument(warp);

            warpsCollection.replaceOne(
                    Filters.eq("name", warp.getName()),
                    doc,
                    new ReplaceOptions().upsert(true)
            );
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteWarp(String name) {
        return supply(() -> warpsCollection.deleteOne(Filters.eq("name", name)).getDeletedCount() > 0);
    }

    @Override
    public CompletableFuture<Void> importData(List<Home> homes, List<Warp> warps) {
        return run(() -> {
            // Unordered so one rejected document does not stop the rest; multi-document transactions would
            // need a replica set, which a standalone MongoDB does not have.
            BulkWriteOptions options = new BulkWriteOptions().ordered(false);

            List<WriteModel<Document>> homeWrites = new ArrayList<>();
            for (Home home : homes) {
                homeWrites.add(new ReplaceOneModel<>(
                        Filters.and(
                                Filters.eq("owner", home.getOwner().toString()),
                                Filters.eq("name", home.getName())),
                        homeToDocument(home),
                        new ReplaceOptions().upsert(true)));
            }

            List<WriteModel<Document>> warpWrites = new ArrayList<>();
            for (Warp warp : warps) {
                warpWrites.add(new ReplaceOneModel<>(Filters.eq("name", warp.getName()), warpToDocument(warp),
                        new ReplaceOptions().upsert(true)));
            }

            if (!homeWrites.isEmpty()) {
                homesCollection.bulkWrite(homeWrites, options);
            }
            if (!warpWrites.isEmpty()) {
                warpsCollection.bulkWrite(warpWrites, options);
            }
        });
    }

    @Override
    public CompletableFuture<SpawnLocation> getSpawn() {
        return supply(() -> {
            Document doc = spawnCollection.find().first();

            return doc != null ? documentToSpawn(doc) : null;
        });
    }

    @Override
    public CompletableFuture<Void> saveSpawn(SpawnLocation spawn) {
        return run(() -> {

            spawnCollection.deleteMany(Filters.ne("server", spawn.getServer()));

            spawnCollection.replaceOne(
                    Filters.eq("server", spawn.getServer()),
                    spawnToDocument(spawn),
                    new ReplaceOptions().upsert(true));
        });
    }

    @Override
    public CompletableFuture<Void> rememberPlayer(UUID uuid, String name) {
        return run(() -> playersCollection.replaceOne(
                Filters.eq("uuid", uuid.toString()),
                new Document().append("uuid", uuid.toString())
                        .append("name", name)
                        .append("name_lower", name.toLowerCase(Locale.ROOT))
                        .append("last_seen", System.currentTimeMillis()),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<PlayerRef> findPlayer(String name) {
        return supply(() -> {
            Document doc = playersCollection.find(
                    Filters.eq("name_lower", name.toLowerCase(Locale.ROOT))).first();

            if (doc == null || doc.getString("uuid") == null) {
                return null;
            }

            return new PlayerRef(UUID.fromString(doc.getString("uuid")), doc.getString("name"));
        });
    }

    @Override
    public CompletableFuture<Void> saveLastLocation(UUID uuid, String server, StoredLocation location) {
        return run(() -> lastLocationsCollection.replaceOne(
                Filters.and(Filters.eq("uuid", uuid.toString()), Filters.eq("server", server)),
                new Document().append("uuid", uuid.toString())
                        .append("server", server)
                        .append("world", location.world())
                        .append("x", location.x())
                        .append("y", location.y())
                        .append("z", location.z())
                        .append("yaw", (double) location.yaw())
                        .append("pitch", (double) location.pitch())
                        .append("updated_at", System.currentTimeMillis()),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<StoredLocation> getLastLocation(UUID uuid, String server) {
        return supply(() -> {
            Document doc = lastLocationsCollection.find(
                    Filters.and(Filters.eq("uuid", uuid.toString()), Filters.eq("server", server))).first();

            if (doc == null || doc.getString("world") == null) {
                return null;
            }

            return new StoredLocation(doc.getString("world"), number(doc, "x"), number(doc, "y"),
                    number(doc, "z"), (float) number(doc, "yaw"), (float) number(doc, "pitch"));
        });
    }

    @Override
    public CompletableFuture<Void> saveBedRespawnPoint(UUID uuid, StoredLocation location) {
        return run(() -> bedRespawnPointsCollection.replaceOne(
                Filters.eq("uuid", uuid.toString()),
                new Document().append("uuid", uuid.toString())
                        .append("world", location.world())
                        .append("x", location.x())
                        .append("y", location.y())
                        .append("z", location.z())
                        .append("yaw", (double) location.yaw())
                        .append("pitch", (double) location.pitch())
                        .append("updated_at", System.currentTimeMillis()),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<StoredLocation> getBedRespawnPoint(UUID uuid) {
        return supply(() -> {
            Document doc = bedRespawnPointsCollection.find(Filters.eq("uuid", uuid.toString())).first();

            if (doc == null || doc.getString("world") == null) {
                return null;
            }

            return new StoredLocation(doc.getString("world"), number(doc, "x"), number(doc, "y"),
                    number(doc, "z"), (float) number(doc, "yaw"), (float) number(doc, "pitch"));
        });
    }

    @Override
    public CompletableFuture<Void> clearBedRespawnPoint(UUID uuid) {
        return run(() -> bedRespawnPointsCollection.deleteOne(Filters.eq("uuid", uuid.toString())));
    }

    @Override
    public CompletableFuture<Boolean> isTpaBlocked(UUID uuid) {
        return supply(() -> {
            Document doc = settingsCollection.find(Filters.eq("uuid", uuid.toString())).first();
            return doc != null && doc.getBoolean("tpa_blocked", false);
        });
    }

    @Override
    public CompletableFuture<Void> setTpaBlocked(UUID uuid, boolean blocked) {
        return run(() -> settingsCollection.replaceOne(
                Filters.eq("uuid", uuid.toString()),
                new Document().append("uuid", uuid.toString()).append("tpa_blocked", blocked),
                new ReplaceOptions().upsert(true)));
    }

    private Home documentToHome(Document doc) {
        String owner = doc.getString("owner");
        String world = doc.getString("world");

        if (owner == null || world == null) {
            LoggerUtils.logWarn("Skipping malformed home document: " + doc.toJson());
            return null;
        }

        try {
            return new Home(
                    UUID.fromString(owner),
                    doc.getString("name"),
                    world,
                    number(doc, "x"),
                    number(doc, "y"),
                    number(doc, "z"),
                    (float) number(doc, "yaw"),
                    (float) number(doc, "pitch"),
                    (long) number(doc, "created_at"),
                    doc.getString("server")
            );
        } catch (IllegalArgumentException e) {
            LoggerUtils.logWarn("Skipping malformed home document: " + e.getMessage());
            return null;
        }
    }

    private static double number(Document doc, String field) {
        Object value = doc.get(field);
        return value instanceof Number n ? n.doubleValue() : 0.0D;
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
        String world = doc.getString("world");

        if (world == null) {
            LoggerUtils.logWarn("Skipping malformed warp document: " + doc.toJson());
            return null;
        }

        Object cost = doc.get("cost");
        Object cooldown = doc.get("cooldown");
        Object delay = doc.get("delay");

        return new Warp(
                doc.getString("name"),
                world,
                number(doc, "x"),
                number(doc, "y"),
                number(doc, "z"),
                (float) number(doc, "yaw"),
                (float) number(doc, "pitch"),
                doc.getString("permission"),
                (long) number(doc, "created_at"),
                doc.getString("server"),
                cost instanceof Number n ? n.doubleValue() : null,
                cooldown instanceof Number n ? n.intValue() : null,
                delay instanceof Number n ? n.intValue() : null
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
                .append("server", warp.getServer())
                .append("cost", warp.getCost())
                .append("cooldown", warp.getCooldown())
                .append("delay", warp.getDelay());
    }

    @Override
    public CompletableFuture<Void> logTeleport(TeleportRecord record) {
        return run(() -> teleportLogCollection.insertOne(new Document()
                .append("player", record.player().toString())
                .append("player_name", record.playerName())
                .append("type", record.type().name())
                .append("destination", record.destination())
                .append("from_server", record.fromServer())
                .append("from_world", record.fromWorld())
                .append("from_x", record.fromX())
                .append("from_y", record.fromY())
                .append("from_z", record.fromZ())
                .append("to_server", record.toServer())
                .append("to_world", record.toWorld())
                .append("to_x", record.toX())
                .append("to_y", record.toY())
                .append("to_z", record.toZ())
                .append("cost", record.cost())
                .append("created_at", record.timestamp())
                .append("result", record.result())));
    }

    @Override
    public CompletableFuture<List<TeleportRecord>> getTeleportHistory(UUID player, int limit) {
        return supply(() -> readLog(Filters.eq("player", player.toString()), limit));
    }

    @Override
    public CompletableFuture<List<TeleportRecord>> getWarpHistory(String warp, int limit) {
        return supply(() -> readLog(Filters.and(
                Filters.eq("type", TeleportType.WARP.name()),
                Filters.regex("destination", "^" + java.util.regex.Pattern.quote(warp) + "$", "i")), limit));
    }

    private List<TeleportRecord> readLog(org.bson.conversions.Bson filter, int limit) {
        List<TeleportRecord> records = new ArrayList<>();

        teleportLogCollection.find(filter)
                .sort(Sorts.descending("created_at"))
                .limit(Math.max(1, limit))
                .forEach(doc -> {
                    TeleportRecord record = documentToRecord(doc);
                    if (record != null) {
                        records.add(record);
                    }
                });

        return records;
    }

    private TeleportRecord documentToRecord(Document doc) {
        String player = doc.getString("player");
        if (player == null) {
            return null;
        }

        TeleportType type;
        try {
            type = TeleportType.valueOf(doc.getString("type"));
        } catch (IllegalArgumentException | NullPointerException e) {
            type = TeleportType.WARP;
        }

        try {
            return new TeleportRecord(0L, UUID.fromString(player), doc.getString("player_name"), type,
                    doc.getString("destination"),
                    doc.getString("from_server"), doc.getString("from_world"),
                    number(doc, "from_x"), number(doc, "from_y"), number(doc, "from_z"),
                    doc.getString("to_server"), doc.getString("to_world"),
                    number(doc, "to_x"), number(doc, "to_y"), number(doc, "to_z"),
                    number(doc, "cost"), (long) number(doc, "created_at"),
                    doc.getString("result"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public CompletableFuture<Integer> purgeTeleportHistory(long cutoff) {
        return supply(() -> (int) teleportLogCollection
                .deleteMany(Filters.lt("created_at", cutoff)).getDeletedCount());
    }

    @Override
    public CompletableFuture<Void> shareHome(HomeShare share) {
        return run(() -> homeSharesCollection.replaceOne(
                shareFilter(share.owner(), share.home(), share.invitee()),
                new Document()
                        .append("owner", share.owner().toString())
                        .append("owner_name", share.ownerName())
                        .append("home", share.home())
                        .append("invitee", share.invitee().toString())
                        .append("invitee_name", share.inviteeName())
                        .append("granted_at", share.grantedAt())
                        .append("expires_at", share.expiresAt()),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<Boolean> unshareHome(UUID owner, String home, UUID invitee) {
        return supply(() -> homeSharesCollection
                .deleteOne(shareFilter(owner, home, invitee)).getDeletedCount() > 0);
    }

    private static org.bson.conversions.Bson shareFilter(UUID owner, String home, UUID invitee) {
        return Filters.and(
                Filters.eq("owner", owner.toString()),
                Filters.eq("home", home),
                Filters.eq("invitee", invitee.toString()));
    }

    @Override
    public CompletableFuture<List<HomeShare>> getSharesByOwner(UUID owner) {
        return supply(() -> readShares(Filters.eq("owner", owner.toString())));
    }

    @Override
    public CompletableFuture<List<HomeShare>> getSharesForInvitee(UUID invitee) {
        return supply(() -> readShares(Filters.eq("invitee", invitee.toString())));
    }

    private List<HomeShare> readShares(org.bson.conversions.Bson filter) {
        List<HomeShare> shares = new ArrayList<>();

        homeSharesCollection.find(filter).forEach(doc -> {
            String owner = doc.getString("owner");
            String invitee = doc.getString("invitee");

            if (owner == null || invitee == null) {
                return;
            }

            try {
                shares.add(new HomeShare(UUID.fromString(owner), doc.getString("owner_name"),
                        doc.getString("home"), UUID.fromString(invitee), doc.getString("invitee_name"),
                        (long) number(doc, "granted_at"), (long) number(doc, "expires_at")));
            } catch (IllegalArgumentException ignored) {
            }
        });

        return shares;
    }

    @Override
    public CompletableFuture<Void> clearSharesOf(UUID owner, String home) {
        return run(() -> homeSharesCollection.deleteMany(Filters.and(
                Filters.eq("owner", owner.toString()),
                Filters.eq("home", home))));
    }

    @Override
    public CompletableFuture<Set<UUID>> getTpaBlocks(UUID owner) {
        return supply(() -> {
            Set<UUID> blocked = new HashSet<>();

            tpaBlocksCollection.find(Filters.eq("owner", owner.toString())).forEach(doc -> {
                String value = doc.getString("blocked");
                if (value == null) {
                    return;
                }
                try {
                    blocked.add(UUID.fromString(value));
                } catch (IllegalArgumentException ignored) {
                }
            });

            return blocked;
        });
    }

    @Override
    public CompletableFuture<Void> addTpaBlock(UUID owner, UUID blocked) {
        return run(() -> tpaBlocksCollection.replaceOne(
                blockFilter(owner, blocked),
                new Document().append("owner", owner.toString())
                        .append("blocked", blocked.toString())
                        .append("created_at", System.currentTimeMillis()),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<Boolean> removeTpaBlock(UUID owner, UUID blocked) {
        return supply(() -> tpaBlocksCollection
                .deleteOne(blockFilter(owner, blocked)).getDeletedCount() > 0);
    }

    private static org.bson.conversions.Bson blockFilter(UUID owner, UUID blocked) {
        return Filters.and(
                Filters.eq("owner", owner.toString()),
                Filters.eq("blocked", blocked.toString()));
    }

    @Override
    public CompletableFuture<List<DestinationLock>> getLocks() {
        return supply(() -> {
            List<DestinationLock> locks = new ArrayList<>();

            locksCollection.find().forEach(doc -> {
                String type = doc.getString("lock_type");
                String target = doc.getString("target");

                if (type == null || target == null) {
                    return;
                }

                locks.add(new DestinationLock(type, target, doc.getString("reason"),
                        doc.getString("locked_by"),
                        (long) number(doc, "locked_at"), (long) number(doc, "until")));
            });

            return locks;
        });
    }

    @Override
    public CompletableFuture<Void> saveLock(DestinationLock lock) {
        return run(() -> locksCollection.replaceOne(
                lockFilter(lock.type(), lock.target()),
                new Document().append("lock_type", lock.type())
                        .append("target", lock.target())
                        .append("reason", lock.reason())
                        .append("locked_by", lock.lockedBy())
                        .append("locked_at", lock.lockedAt())
                        .append("until", lock.until()),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<Boolean> deleteLock(String type, String target) {
        return supply(() -> locksCollection
                .deleteOne(lockFilter(DestinationLock.normalize(type), DestinationLock.normalize(target)))
                .getDeletedCount() > 0);
    }

    private static org.bson.conversions.Bson lockFilter(String type, String target) {
        return Filters.and(Filters.eq("lock_type", type), Filters.eq("target", target));
    }

    private SpawnLocation documentToSpawn(Document doc) {
        String world = doc.getString("world");

        if (world == null) {
            LoggerUtils.logWarn("Skipping malformed spawn document: " + doc.toJson());
            return null;
        }

        return new SpawnLocation(
                world,
                number(doc, "x"),
                number(doc, "y"),
                number(doc, "z"),
                (float) number(doc, "yaw"),
                (float) number(doc, "pitch"),
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

    @Override
    public CompletableFuture<List<PlayerWarp>> getAllPlayerWarps() {
        return supply(() -> {
            List<PlayerWarp> warps = new ArrayList<>();
            playerWarpsCollection.find().forEach(doc -> {
                PlayerWarp warp = documentToPlayerWarp(doc);
                if (warp != null) {
                    warps.add(warp);
                }
            });
            return warps;
        });
    }

    @Override
    public CompletableFuture<PlayerWarp> getPlayerWarp(String id) {
        return supply(() -> {
            Document doc = playerWarpsCollection.find(Filters.eq("id", id)).first();
            return doc != null ? documentToPlayerWarp(doc) : null;
        });
    }

    @Override
    public CompletableFuture<PlayerWarp> findPlayerWarpByName(String name) {
        return supply(() -> {
            Document doc = playerWarpsCollection.find(
                    Filters.regex("name", "^" + java.util.regex.Pattern.quote(name) + "$", "i")).first();
            return doc != null ? documentToPlayerWarp(doc) : null;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerWarp(PlayerWarp warp) {
        return run(() -> playerWarpsCollection.replaceOne(
                Filters.eq("id", warp.getId()),
                playerWarpToDocument(warp),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<Boolean> deletePlayerWarp(String id) {
        return supply(() -> {
            playerWarpWhitelistCollection.deleteMany(Filters.eq("warp_id", id));
            playerWarpBansCollection.deleteMany(Filters.eq("warp_id", id));
            playerWarpManagersCollection.deleteMany(Filters.eq("warp_id", id));
            playerWarpRatingsCollection.deleteMany(Filters.eq("warp_id", id));
            playerWarpFavouritesCollection.deleteMany(Filters.eq("warp_id", id));
            return playerWarpsCollection.deleteOne(Filters.eq("id", id)).getDeletedCount() > 0;
        });
    }

    private PlayerWarp documentToPlayerWarp(Document doc) {
        String world = doc.getString("world");
        String owner = doc.getString("owner");

        if (world == null || owner == null) {
            LoggerUtils.logWarn("Skipping malformed player warp document: " + doc.toJson());
            return null;
        }

        Object cost = doc.get("cost");
        Object cooldown = doc.get("cooldown");
        Object delay = doc.get("delay");

        try {
            return new PlayerWarp(
                    doc.getString("id"),
                    doc.getString("name"),
                    UUID.fromString(owner),
                    doc.getString("owner_name"),
                    world,
                    number(doc, "x"),
                    number(doc, "y"),
                    number(doc, "z"),
                    (float) number(doc, "yaw"),
                    (float) number(doc, "pitch"),
                    doc.getString("server"),
                    doc.getString("description"),
                    doc.getString("icon"),
                    doc.getBoolean("use_owner_head", false),
                    doc.getString("category_id"),
                    cost instanceof Number n ? n.doubleValue() : null,
                    cooldown instanceof Number n ? n.intValue() : null,
                    delay instanceof Number n ? n.intValue() : null,
                    doc.getBoolean("locked", false),
                    doc.getString("password_hash"),
                    doc.getString("password_salt"),
                    doc.getBoolean("whitelist_enabled", false),
                    number(doc, "rating_average"),
                    (int) number(doc, "rating_count"),
                    (int) number(doc, "favourite_count"),
                    (long) number(doc, "created_at"),
                    (long) number(doc, "updated_at"),
                    (long) number(doc, "last_used_at"));
        } catch (IllegalArgumentException e) {
            LoggerUtils.logWarn("Skipping malformed player warp document: " + e.getMessage());
            return null;
        }
    }

    private Document playerWarpToDocument(PlayerWarp warp) {
        return new Document()
                .append("id", warp.getId())
                .append("name", warp.getName())
                .append("owner", warp.getOwner().toString())
                .append("owner_name", warp.getOwnerName())
                .append("world", warp.getWorld())
                .append("x", warp.getX())
                .append("y", warp.getY())
                .append("z", warp.getZ())
                .append("yaw", (double) warp.getYaw())
                .append("pitch", (double) warp.getPitch())
                .append("server", warp.getServer())
                .append("description", warp.getDescription())
                .append("icon", warp.getIcon())
                .append("use_owner_head", warp.isUseOwnerHead())
                .append("category_id", warp.getCategoryId())
                .append("cost", warp.getCost())
                .append("cooldown", warp.getCooldown())
                .append("delay", warp.getDelay())
                .append("locked", warp.isLocked())
                .append("password_hash", warp.getPasswordHash())
                .append("password_salt", warp.getPasswordSalt())
                .append("whitelist_enabled", warp.isWhitelistEnabled())
                .append("rating_average", warp.getRatingAverage())
                .append("rating_count", warp.getRatingCount())
                .append("favourite_count", warp.getFavouriteCount())
                .append("created_at", warp.getCreatedAt())
                .append("updated_at", warp.getUpdatedAt())
                .append("last_used_at", warp.getLastUsedAt());
    }

    @Override
    public CompletableFuture<List<PlayerWarpWhitelistEntry>> getPlayerWarpWhitelist(String warpId) {
        return supply(() -> {
            List<PlayerWarpWhitelistEntry> entries = new ArrayList<>();
            playerWarpWhitelistCollection.find(Filters.eq("warp_id", warpId)).forEach(doc -> {
                String player = doc.getString("player");
                if (player == null) {
                    return;
                }
                entries.add(new PlayerWarpWhitelistEntry(warpId, UUID.fromString(player),
                        doc.getString("player_name"), (long) number(doc, "added_at")));
            });
            return entries;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerWarpWhitelistEntry(PlayerWarpWhitelistEntry entry) {
        return run(() -> playerWarpWhitelistCollection.replaceOne(
                warpPlayerFilter(entry.warpId(), entry.player()),
                new Document().append("warp_id", entry.warpId())
                        .append("player", entry.player().toString())
                        .append("player_name", entry.playerName())
                        .append("added_at", entry.addedAt()),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<Boolean> removePlayerWarpWhitelistEntry(String warpId, UUID player) {
        return supply(() -> playerWarpWhitelistCollection
                .deleteOne(warpPlayerFilter(warpId, player)).getDeletedCount() > 0);
    }

    @Override
    public CompletableFuture<List<PlayerWarpBan>> getPlayerWarpBans(String warpId) {
        return supply(() -> {
            List<PlayerWarpBan> bans = new ArrayList<>();
            playerWarpBansCollection.find(Filters.eq("warp_id", warpId)).forEach(doc -> {
                String player = doc.getString("player");
                if (player == null) {
                    return;
                }
                bans.add(new PlayerWarpBan(warpId, UUID.fromString(player), doc.getString("player_name"),
                        doc.getString("banned_by"), doc.getString("banned_by_name"), doc.getString("reason"),
                        (long) number(doc, "banned_at"), (long) number(doc, "expires_at")));
            });
            return bans;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerWarpBan(PlayerWarpBan ban) {
        return run(() -> playerWarpBansCollection.replaceOne(
                warpPlayerFilter(ban.warpId(), ban.player()),
                new Document().append("warp_id", ban.warpId())
                        .append("player", ban.player().toString())
                        .append("player_name", ban.playerName())
                        .append("banned_by", ban.bannedBy())
                        .append("banned_by_name", ban.bannedByName())
                        .append("reason", ban.reason())
                        .append("banned_at", ban.bannedAt())
                        .append("expires_at", ban.expiresAt()),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<Boolean> removePlayerWarpBan(String warpId, UUID player) {
        return supply(() -> playerWarpBansCollection
                .deleteOne(warpPlayerFilter(warpId, player)).getDeletedCount() > 0);
    }

    @Override
    public CompletableFuture<List<PlayerWarpManagerEntry>> getPlayerWarpManagers(String warpId) {
        return supply(() -> {
            List<PlayerWarpManagerEntry> entries = new ArrayList<>();
            playerWarpManagersCollection.find(Filters.eq("warp_id", warpId)).forEach(doc -> {
                String player = doc.getString("player");
                if (player == null) {
                    return;
                }
                entries.add(new PlayerWarpManagerEntry(warpId, UUID.fromString(player),
                        doc.getString("player_name"), (long) number(doc, "added_at")));
            });
            return entries;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerWarpManagerEntry(PlayerWarpManagerEntry entry) {
        return run(() -> playerWarpManagersCollection.replaceOne(
                warpPlayerFilter(entry.warpId(), entry.player()),
                new Document().append("warp_id", entry.warpId())
                        .append("player", entry.player().toString())
                        .append("player_name", entry.playerName())
                        .append("added_at", entry.addedAt()),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<Boolean> removePlayerWarpManagerEntry(String warpId, UUID player) {
        return supply(() -> playerWarpManagersCollection
                .deleteOne(warpPlayerFilter(warpId, player)).getDeletedCount() > 0);
    }

    @Override
    public CompletableFuture<List<PlayerWarpRating>> getPlayerWarpRatings(String warpId) {
        return supply(() -> {
            List<PlayerWarpRating> ratings = new ArrayList<>();
            playerWarpRatingsCollection.find(Filters.eq("warp_id", warpId)).forEach(doc -> {
                String player = doc.getString("player");
                if (player == null) {
                    return;
                }
                ratings.add(new PlayerWarpRating(warpId, UUID.fromString(player), doc.getString("player_name"),
                        (int) number(doc, "stars"), doc.getString("comment"),
                        (long) number(doc, "created_at"), (long) number(doc, "updated_at")));
            });
            return ratings;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerWarpRating(PlayerWarpRating rating) {
        return run(() -> playerWarpRatingsCollection.replaceOne(
                warpPlayerFilter(rating.warpId(), rating.player()),
                new Document().append("warp_id", rating.warpId())
                        .append("player", rating.player().toString())
                        .append("player_name", rating.playerName())
                        .append("stars", rating.stars())
                        .append("comment", rating.comment())
                        .append("created_at", rating.createdAt())
                        .append("updated_at", rating.updatedAt()),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<List<PlayerWarpFavourite>> getPlayerWarpFavourites(UUID player) {
        return supply(() -> {
            List<PlayerWarpFavourite> favourites = new ArrayList<>();
            playerWarpFavouritesCollection.find(Filters.eq("player", player.toString())).forEach(doc ->
                    favourites.add(new PlayerWarpFavourite(player, doc.getString("warp_id"),
                            (long) number(doc, "added_at"))));
            return favourites;
        });
    }

    @Override
    public CompletableFuture<Void> addPlayerWarpFavourite(PlayerWarpFavourite favourite) {
        return run(() -> playerWarpFavouritesCollection.replaceOne(
                Filters.and(Filters.eq("player", favourite.player().toString()),
                        Filters.eq("warp_id", favourite.warpId())),
                new Document().append("player", favourite.player().toString())
                        .append("warp_id", favourite.warpId())
                        .append("added_at", favourite.addedAt()),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<Boolean> removePlayerWarpFavourite(UUID player, String warpId) {
        return supply(() -> playerWarpFavouritesCollection.deleteOne(
                Filters.and(Filters.eq("player", player.toString()), Filters.eq("warp_id", warpId)))
                .getDeletedCount() > 0);
    }

    private static org.bson.conversions.Bson warpPlayerFilter(String warpId, UUID player) {
        return Filters.and(Filters.eq("warp_id", warpId), Filters.eq("player", player.toString()));
    }

    @Override
    public CompletableFuture<List<PlayerWarpSponsoredSlot>> getSponsoredSlots() {
        return supply(() -> {
            List<PlayerWarpSponsoredSlot> slots = new ArrayList<>();
            playerWarpSponsoredSlotsCollection.find().forEach(doc -> {
                String owner = doc.getString("owner");
                if (owner == null) {
                    return;
                }
                slots.add(new PlayerWarpSponsoredSlot(
                        (int) number(doc, "slot_index"),
                        doc.getString("warp_id"),
                        UUID.fromString(owner),
                        doc.getString("owner_name"),
                        (long) number(doc, "started_at"),
                        (long) number(doc, "expires_at"),
                        number(doc, "price_paid")));
            });
            return slots;
        });
    }

    @Override
    public CompletableFuture<Void> saveSponsoredSlot(PlayerWarpSponsoredSlot slot) {
        return run(() -> playerWarpSponsoredSlotsCollection.replaceOne(
                Filters.eq("slot_index", slot.slotIndex()),
                new Document().append("slot_index", slot.slotIndex())
                        .append("warp_id", slot.warpId())
                        .append("owner", slot.owner().toString())
                        .append("owner_name", slot.ownerName())
                        .append("started_at", slot.startedAt())
                        .append("expires_at", slot.expiresAt())
                        .append("price_paid", slot.pricePaid()),
                new ReplaceOptions().upsert(true)));
    }

    @Override
    public CompletableFuture<Boolean> clearSponsoredSlot(int slotIndex) {
        return supply(() -> playerWarpSponsoredSlotsCollection
                .deleteOne(Filters.eq("slot_index", slotIndex)).getDeletedCount() > 0);
    }

    @Override
    public CompletableFuture<BackLocation> getBackLocation(UUID owner) {
        return supply(() -> {
            Document doc = backLocationsCollection.find(Filters.eq("owner", owner.toString())).first();

            if (doc == null || doc.getString("world") == null) {
                return null;
            }

            return new BackLocation(owner, doc.getString("world"), number(doc, "x"), number(doc, "y"),
                    number(doc, "z"), (float) number(doc, "yaw"), (float) number(doc, "pitch"),
                    doc.getString("server"), (long) number(doc, "saved_at"));
        });
    }

    @Override
    public CompletableFuture<Void> saveBackLocation(BackLocation location) {
        return run(() -> backLocationsCollection.replaceOne(
                Filters.eq("owner", location.getOwner().toString()),
                new Document().append("owner", location.getOwner().toString())
                        .append("world", location.getWorld())
                        .append("x", location.getX())
                        .append("y", location.getY())
                        .append("z", location.getZ())
                        .append("yaw", (double) location.getYaw())
                        .append("pitch", (double) location.getPitch())
                        .append("server", location.getServer())
                        .append("saved_at", location.getSavedAt()),
                new ReplaceOptions().upsert(true)));
    }
}
