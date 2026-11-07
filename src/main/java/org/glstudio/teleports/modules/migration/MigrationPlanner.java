package org.glstudio.teleports.modules.migration;

import org.glstudio.teleports.core.util.TeleportNames;
import org.glstudio.teleports.modules.migration.source.MigrationData;
import org.glstudio.teleports.modules.migration.source.SourceHome;
import org.glstudio.teleports.modules.migration.source.SourceLocation;
import org.glstudio.teleports.modules.migration.source.SourceProblem;
import org.glstudio.teleports.modules.migration.source.SourceWarp;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.Warp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Turns what a source provided into the exact homes and warps to write, without touching any storage.
 *
 * Running it twice over the same data gives the same answer, and an entry already present here at the same
 * position is always recognised as such - that is what makes a repeated migration harmless whatever the policy.
 */
public class MigrationPlanner {
    // Vanilla's hard limit is 30,000,000; anything past it is a corrupt record, not a real place.
    private static final double MAX_HORIZONTAL = 30_000_000D;
    private static final int MAX_COLUMN_LENGTH = 64;
    private static final int MAX_RENAME_ATTEMPTS = 99;

    private static final double POSITION_TOLERANCE = 1.0E-3;
    private static final float ANGLE_TOLERANCE = 0.01F;

    private final MigrationOptions options;
    private final WorldDirectory worlds;

    /**
     * Creates the migration planner.
     */
    public MigrationPlanner(MigrationOptions options, WorldDirectory worlds) {
        this.options = options;
        this.worlds = worlds;
    }

    /**
     * Works out what to import.
     */
    public MigrationPlan plan(MigrationData data, Map<UUID, List<Home>> existingHomes, List<Warp> existingWarps)
            throws MigrationException {
        return plan(data, existingHomes, existingWarps, List.of(), Set.of(), Set.of());
    }

    /**
     * @param existingPlayerWarps every player warp stored here now
     * @param importedPlayerWarps ids of player warps earlier runs of this source imported, whether or not they
     *                            still exist
     * @param knownCategories     the player warp category ids player-warps.yml defines
     */
    public MigrationPlan plan(MigrationData data, Map<UUID, List<Home>> existingHomes, List<Warp> existingWarps,
                              List<PlayerWarp> existingPlayerWarps, Set<String> importedPlayerWarps,
                              Set<String> knownCategories) throws MigrationException {
        requireServerForEveryEntry(data);

        List<SourceProblem> problems = new ArrayList<>(data.problems());
        Counter homeCounts = new Counter();
        Counter warpCounts = new Counter();
        int[] unloaded = {0};

        List<Home> homes = planHomes(data, existingHomes, homeCounts, problems, unloaded);
        List<Warp> warps = planWarps(data, existingWarps, warpCounts, problems, unloaded);
        PlayerWarpPlan playerWarps = new PlayerWarpPlanner(options, worlds, this).plan(data.playerWarps(),
                data.localServer(), existingPlayerWarps, importedPlayerWarps, knownCategories, problems, unloaded);

        Set<UUID> owners = new HashSet<>();
        int publicHomes = 0;
        for (SourceHome home : data.homes()) {
            if (home.owner() != null) {
                owners.add(home.owner());
            }
            if (home.publicHome()) {
                publicHomes++;
            }
        }

        int restricted = (int) warps.stream().filter(Warp::hasPermission).count();

        return new MigrationPlan(data.storage(), List.copyOf(homes), List.copyOf(warps), homeCounts.toCounts(),
                warpCounts.toCounts(), owners.size(), publicHomes, unloaded[0], restricted,
                data.problems().size(), List.copyOf(problems), playerWarps);
    }

    private void requireServerForEveryEntry(MigrationData data) throws MigrationException {
        if (!options.multiServer() || !isBlank(options.fallbackServer())) {
            return;
        }

        boolean anyWithoutServer = data.homes().stream().anyMatch(home -> needsFallback(home.location()))
                || data.warps().stream().anyMatch(warp -> needsFallback(warp.location()))
                || data.playerWarps().warps().stream().anyMatch(warp -> needsFallback(warp.location()));

        if (anyWithoutServer) {
            throw new MigrationException("migrate.errors.server-required",
                    "multi-server is enabled, the source does not say which server its entries belong to, "
                            + "and migrate.server is empty");
        }
    }

    private boolean needsFallback(SourceLocation location) {
        return location == null || !options.useSourceServer() || isBlank(location.server());
    }

    private List<Home> planHomes(MigrationData data, Map<UUID, List<Home>> existingHomes, Counter counts,
                                 List<SourceProblem> problems, int[] unloaded) {
        List<Home> planned = new ArrayList<>();
        Map<UUID, Map<String, Home>> takenByOwner = new HashMap<>();
        Map<UUID, Set<String>> plannedByOwner = new HashMap<>();

        for (SourceHome source : data.homes()) {
            counts.found++;

            if (source.owner() == null) {
                invalid(counts, problems, source.origin(), "no owner UUID");
                continue;
            }

            String name = source.name() == null ? "" : TeleportNames.normalize(source.name());
            if (!TeleportNames.isValid(name)) {
                invalid(counts, problems, source.origin(), "the name '" + source.name() + "' is not accepted "
                        + "(letters, digits, '-' and '_', at most " + TeleportNames.MAX_LENGTH + " characters)");
                continue;
            }

            SourceLocation location = source.location() == null ? null : options.mapWorld(source.location());
            String locationProblem = describeProblem(location);
            if (locationProblem != null) {
                invalid(counts, problems, source.origin(), locationProblem);
                continue;
            }

            if (belongsElsewhere(data, location)) {
                counts.elsewhere++;
                continue;
            }

            String server = serverFor(location);
            Home candidate = new Home(source.owner(), name, location.world(), location.x(), location.y(),
                    location.z(), location.yaw(), location.pitch(), timestamp(source.createdAt()), server);

            Map<String, Home> taken = takenByOwner.computeIfAbsent(source.owner(),
                    owner -> index(existingHomes.getOrDefault(owner, List.of()), Home::getName));
            Set<String> plannedNames = plannedByOwner.computeIfAbsent(source.owner(), owner -> new HashSet<>());

            Home current = taken.get(name);
            String finalName = resolveName(name, candidate, current, plannedNames.contains(name), taken,
                    MigrationPlanner::samePlace, counts, problems, source.origin());

            if (finalName == null) {
                continue;
            }

            candidate.setName(finalName);
            taken.put(finalName, candidate);
            plannedNames.add(finalName);
            planned.add(candidate);

            if (isLocal(server) && !worlds.isLoaded(candidate.getWorld())) {
                unloaded[0]++;
            }
        }

        return planned;
    }

    private List<Warp> planWarps(MigrationData data, List<Warp> existingWarps, Counter counts,
                                 List<SourceProblem> problems, int[] unloaded) {
        List<Warp> planned = new ArrayList<>();
        Map<String, Warp> taken = index(existingWarps, Warp::getName);
        Set<String> plannedNames = new HashSet<>();

        for (SourceWarp source : data.warps()) {
            counts.found++;

            String name = source.name() == null ? "" : TeleportNames.normalize(source.name());
            if (!TeleportNames.isValid(name)) {
                invalid(counts, problems, source.origin(), "the name '" + source.name() + "' is not accepted "
                        + "(letters, digits, '-' and '_', at most " + TeleportNames.MAX_LENGTH + " characters)");
                continue;
            }

            SourceLocation location = source.location() == null ? null : options.mapWorld(source.location());
            String locationProblem = describeProblem(location);
            if (locationProblem != null) {
                invalid(counts, problems, source.origin(), locationProblem);
                continue;
            }

            if (belongsElsewhere(data, location)) {
                counts.elsewhere++;
                continue;
            }

            String server = serverFor(location);
            Warp candidate = new Warp(name, location.world(), location.x(), location.y(), location.z(),
                    location.yaw(), location.pitch(), source.permission(), timestamp(source.createdAt()), server);

            Warp current = taken.get(name);
            String finalName = resolveName(name, candidate, current, plannedNames.contains(name), taken,
                    MigrationPlanner::samePlace, counts, problems, source.origin());

            if (finalName == null) {
                continue;
            }

            if (current != null && finalName.equals(name)) {
                // Replacing keeps what an admin tuned here, exactly as /setwarp over an existing warp does.
                candidate.setCost(current.getCost());
                candidate.setCooldown(current.getCooldown());
                candidate.setDelay(current.getDelay());
                candidate.setCreatedAt(current.getCreatedAt());
            }

            candidate.setName(finalName);
            taken.put(finalName, candidate);
            plannedNames.add(finalName);
            planned.add(candidate);

            if (isLocal(server) && !worlds.isLoaded(candidate.getWorld())) {
                unloaded[0]++;
            }
        }

        return planned;
    }

    /**
     * Decides the name an entry is written under, or null when it is not written at all.
     */
    private <T> String resolveName(String name, T candidate, T current, boolean plannedThisRun,
                                   Map<String, T> taken, SamePlace<T> samePlace, Counter counts,
                                   List<SourceProblem> problems, String origin) {
        if (current == null) {
            counts.created++;
            return name;
        }

        if (samePlace.test(current, candidate)) {
            counts.unchanged++;
            return null;
        }

        // Two source entries that only differ in case collapse onto one name here; the first one read wins
        // unless the policy is to rename, and an earlier entry from this same run is never overwritten.
        if (options.policy() == ConflictPolicy.REPLACE && !plannedThisRun) {
            counts.replaced++;
            problems.add(new SourceProblem(origin, "'" + name + "' already existed here and was replaced"));
            return name;
        }

        if (options.policy() != ConflictPolicy.RENAME) {
            counts.conflicts++;
            problems.add(new SourceProblem(origin, plannedThisRun
                    ? "another entry from the same source already uses the name '" + name + "'"
                    : "'" + name + "' already exists here at a different position; kept the existing one"));
            return null;
        }

        for (String alternative : renameCandidates(name)) {
            T occupant = taken.get(alternative);
            if (occupant != null && samePlace.test(occupant, candidate)) {
                counts.unchanged++;
                return null;
            }
        }

        for (String alternative : renameCandidates(name)) {
            if (!taken.containsKey(alternative)) {
                counts.renamed++;
                problems.add(new SourceProblem(origin, "'" + name + "' already exists here; imported as '"
                        + alternative + "'"));
                return alternative;
            }
        }

        counts.conflicts++;
        problems.add(new SourceProblem(origin, "'" + name + "' already exists here and no free alternative name "
                + "was found"));
        return null;
    }

    static List<String> renameCandidates(String name) {
        List<String> candidates = new ArrayList<>();

        for (int index = 2; index <= MAX_RENAME_ATTEMPTS; index++) {
            String suffix = "-" + index;
            int keep = Math.min(name.length(), TeleportNames.MAX_LENGTH - suffix.length());
            candidates.add(name.substring(0, keep) + suffix);
        }

        return candidates;
    }

    private boolean belongsElsewhere(MigrationData data, SourceLocation location) {
        return !options.multiServer()
                && data.localServer() != null
                && !isBlank(location.server())
                && !location.server().equalsIgnoreCase(data.localServer());
    }

    String serverFor(SourceLocation location) {
        if (!options.multiServer()) {
            return options.currentServer();
        }

        if (options.useSourceServer() && !isBlank(location.server())) {
            return location.server();
        }

        return options.fallbackServer();
    }

    private boolean isLocal(String server) {
        return server == null || server.equalsIgnoreCase(options.currentServer());
    }

    static String describeProblem(SourceLocation location) {
        if (location == null) {
            return "no location recorded";
        }

        if (isBlank(location.world())) {
            return "no world recorded";
        }

        if (location.world().length() > MAX_COLUMN_LENGTH) {
            return "the world name '" + location.world() + "' is longer than " + MAX_COLUMN_LENGTH + " characters";
        }

        if (location.server() != null && location.server().length() > MAX_COLUMN_LENGTH) {
            return "the server name '" + location.server() + "' is longer than " + MAX_COLUMN_LENGTH
                    + " characters";
        }

        if (!Double.isFinite(location.x()) || !Double.isFinite(location.y()) || !Double.isFinite(location.z())
                || !Float.isFinite(location.yaw()) || !Float.isFinite(location.pitch())) {
            return "the coordinates are not numbers";
        }

        if (Math.abs(location.x()) > MAX_HORIZONTAL || Math.abs(location.z()) > MAX_HORIZONTAL) {
            return "the coordinates are outside the world (" + location.x() + ", " + location.z() + ")";
        }

        return null;
    }

    private long timestamp(long createdAt) {
        return createdAt > 0 ? createdAt : options.now();
    }

    private static boolean samePlace(Home a, Home b) {
        return samePlace(a.getWorld(), a.getServer(), a.getX(), a.getY(), a.getZ(), a.getYaw(), a.getPitch(),
                b.getWorld(), b.getServer(), b.getX(), b.getY(), b.getZ(), b.getYaw(), b.getPitch());
    }

    private static boolean samePlace(Warp a, Warp b) {
        return samePlace(a.getWorld(), a.getServer(), a.getX(), a.getY(), a.getZ(), a.getYaw(), a.getPitch(),
                b.getWorld(), b.getServer(), b.getX(), b.getY(), b.getZ(), b.getYaw(), b.getPitch());
    }

    private static boolean samePlace(String worldA, String serverA, double xA, double yA, double zA, float yawA,
                                     float pitchA, String worldB, String serverB, double xB, double yB, double zB,
                                     float yawB, float pitchB) {
        return worldA != null && worldA.equalsIgnoreCase(worldB)
                && (serverA == null ? serverB == null : serverA.equalsIgnoreCase(serverB))
                && Math.abs(xA - xB) < POSITION_TOLERANCE
                && Math.abs(yA - yB) < POSITION_TOLERANCE
                && Math.abs(zA - zB) < POSITION_TOLERANCE
                && Math.abs(yawA - yawB) < ANGLE_TOLERANCE
                && Math.abs(pitchA - pitchB) < ANGLE_TOLERANCE;
    }

    private static <T> Map<String, T> index(List<T> entries, Function<T, String> name) {
        Map<String, T> indexed = new LinkedHashMap<>();
        for (T entry : entries) {
            indexed.put(TeleportNames.normalize(name.apply(entry)), entry);
        }
        return indexed;
    }

    private static void invalid(Counter counts, List<SourceProblem> problems, String origin, String reason) {
        counts.invalid++;
        problems.add(new SourceProblem(origin, reason));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    private interface SamePlace<T> {
        boolean test(T a, T b);
    }

    private static final class Counter {
        private int found;
        private int created;
        private int renamed;
        private int replaced;
        private int unchanged;
        private int conflicts;
        private int elsewhere;
        private int invalid;

        private MigrationPlan.Counts toCounts() {
            return new MigrationPlan.Counts(found, created, renamed, replaced, unchanged, conflicts, elsewhere,
                    invalid);
        }
    }
}
