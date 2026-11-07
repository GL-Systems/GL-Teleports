package org.glstudio.teleports.modules.migration;

import org.glstudio.teleports.core.util.TeleportNames;
import org.glstudio.teleports.modules.manager.PlayerWarpManager;
import org.glstudio.teleports.modules.migration.source.SourceLocation;
import org.glstudio.teleports.modules.migration.source.SourcePlayerWarp;
import org.glstudio.teleports.modules.migration.source.SourcePlayerWarpLink.Kind;
import org.glstudio.teleports.modules.migration.source.SourcePlayerWarpLink;
import org.glstudio.teleports.modules.migration.source.SourcePlayerWarps;
import org.glstudio.teleports.modules.migration.source.SourceProblem;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpBan;
import org.glstudio.teleports.modules.model.PlayerWarpFavourite;
import org.glstudio.teleports.modules.model.PlayerWarpImport;
import org.glstudio.teleports.modules.model.PlayerWarpManagerEntry;
import org.glstudio.teleports.modules.model.PlayerWarpRating;
import org.glstudio.teleports.modules.model.PlayerWarpWhitelistEntry;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Turns the player warps a source provided into the exact warps and related records to write, without touching
 * any storage.
 *
 * Every source warp gets a fixed identity here (see {@link #idFor}), so a repeated run recognises what it already
 * imported by id rather than by name or position: such a warp is left exactly as it is now - even if its owner
 * has renamed, moved or re-rated it since - and so are its favourites, ratings and lists. A warp an earlier run
 * imported and that has been deleted here since is not brought back.
 */
class PlayerWarpPlanner {
    // Column sizes of the player warp tables; MySQL refuses longer values and would roll back the whole import.
    static final int PLAYER_NAME_LENGTH = 16;
    static final int DESCRIPTION_LENGTH = 256;
    static final int COMMENT_LENGTH = 256;
    static final int REASON_LENGTH = 255;
    static final int ICON_LENGTH = 64;
    static final int CATEGORY_LENGTH = 32;

    private static final Pattern COLOR_CODE = Pattern.compile("(?i)[&§][0-9A-FK-ORX]");
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^a-z0-9_-]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private static final double POSITION_TOLERANCE = 1.0E-3;

    private final MigrationOptions options;
    private final WorldDirectory worlds;
    private final MigrationPlanner planner;

    PlayerWarpPlanner(MigrationOptions options, WorldDirectory worlds, MigrationPlanner planner) {
        this.options = options;
        this.worlds = worlds;
        this.planner = planner;
    }

    /**
     * The id a source warp is imported under. It depends only on the source's own id and creation time, which
     * survive a rename, a move and an owner change in the source, while two unrelated PlayerWarps databases
     * would need the same id created in the same millisecond to collide.
     */
    static String idFor(SourcePlayerWarp warp) {
        String key = "PlayerWarps:" + warp.sourceId() + ":" + warp.createdAt();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    PlayerWarpPlan plan(SourcePlayerWarps source, String localServer, List<PlayerWarp> existing,
                        Set<String> previouslyImported, Set<String> knownCategories,
                        List<SourceProblem> problems, int[] unloaded) {
        if (source.isEmpty()) {
            return PlayerWarpPlan.empty();
        }

        Counter counts = new Counter();
        List<SourceProblem> notes = new ArrayList<>(source.notes());
        Map<String, Long> notCarriedOver = new LinkedHashMap<>(source.notCarriedOver());
        Set<String> unknownCategories = new TreeSet<>();

        Map<String, PlayerWarp> existingById = new HashMap<>();
        Map<String, PlayerWarp> taken = new HashMap<>();
        for (PlayerWarp warp : existing) {
            existingById.put(warp.getId(), warp);
            taken.put(TeleportNames.normalize(warp.getName()), warp);
        }

        Set<String> plannedNames = new HashSet<>();
        Map<Long, Planned> planned = new LinkedHashMap<>();
        Set<UUID> owners = new HashSet<>();

        for (SourcePlayerWarp warp : source.warps()) {
            counts.found++;
            if (warp.owner() != null) {
                owners.add(warp.owner());
            }

            String id = idFor(warp);
            if (existingById.containsKey(id)) {
                counts.unchanged++;
                continue;
            }

            if (previouslyImported.contains(id)) {
                counts.unchanged++;
                problems.add(new SourceProblem(warp.origin(), "imported by an earlier run and deleted here since; "
                        + "not recreated"));
                continue;
            }

            if (warp.owner() == null) {
                invalid(counts, problems, warp.origin(), "it has no owner (a PlayerWarps server warp); player "
                        + "warps here always belong to a player");
                continue;
            }

            SourceLocation location = warp.location() == null ? null : options.mapWorld(warp.location());
            String locationProblem = MigrationPlanner.describeProblem(location);
            if (locationProblem != null) {
                invalid(counts, problems, warp.origin(), locationProblem);
                continue;
            }

            if (belongsElsewhere(localServer, location)) {
                counts.elsewhere++;
                continue;
            }

            String name = sanitize(warp.name());
            if (name.isEmpty()) {
                invalid(counts, problems, warp.origin(), "the name '" + warp.name() + "' has no letters or digits "
                        + "this plugin accepts");
                continue;
            }

            String server = planner.serverFor(location);
            String finalName = resolveName(warp, location, name, server, taken, plannedNames, counts, problems);
            if (finalName == null) {
                continue;
            }

            PlayerWarp converted = convert(warp, location, id, finalName, server, knownCategories, unknownCategories,
                    notes);
            taken.put(finalName, converted);
            plannedNames.add(finalName);
            planned.put(warp.sourceId(), new Planned(converted));

            if ((server == null || server.equalsIgnoreCase(options.currentServer()))
                    && !worlds.isLoaded(converted.getWorld())) {
                unloaded[0]++;
            }
        }

        Map<Kind, LinkCounter> linkCounts = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            linkCounts.put(kind, new LinkCounter());
        }

        for (SourcePlayerWarpLink link : source.links()) {
            LinkCounter count = linkCounts.get(link.kind());
            count.found++;

            Planned target = planned.get(link.warpId());
            if (target == null) {
                count.withWarp++;
                continue;
            }

            addLink(target, link, count, problems, notes);
        }

        List<PlayerWarpImport> imports = new ArrayList<>();
        for (Planned entry : planned.values()) {
            imports.add(entry.build());
            linkCounts.get(Kind.FAVOURITE).imported += entry.favourites.size();
            linkCounts.get(Kind.RATING).imported += entry.ratings.size();
            linkCounts.get(Kind.WHITELIST).imported += entry.whitelist.size();
            linkCounts.get(Kind.MANAGER).imported += entry.managers.size();
            linkCounts.get(Kind.BAN).imported += entry.bans.size();
        }

        Map<Kind, PlayerWarpPlan.LinkCounts> links = new EnumMap<>(Kind.class);
        linkCounts.forEach((kind, count) -> links.put(kind, count.toCounts()));

        return new PlayerWarpPlan(List.copyOf(imports), counts.toCounts(), owners.size(), Map.copyOf(links),
                Collections.unmodifiableMap(notCarriedOver), List.copyOf(unknownCategories),
                List.copyOf(notes));
    }

    private String resolveName(SourcePlayerWarp warp, SourceLocation location, String name, String server,
                               Map<String, PlayerWarp> taken,
                               Set<String> plannedNames, Counter counts, List<SourceProblem> problems) {
        boolean sanitized = warp.name() == null || !name.equals(TeleportNames.normalize(warp.name()));
        PlayerWarp current = taken.get(name);

        if (current == null) {
            if (sanitized) {
                counts.renamed++;
                problems.add(new SourceProblem(warp.origin(), "the name '" + warp.name() + "' is not accepted "
                        + "here; imported as '" + name + "'"));
            } else {
                counts.created++;
            }
            return name;
        }

        boolean plannedThisRun = plannedNames.contains(name);
        if (!plannedThisRun && warp.owner().equals(current.getOwner()) && samePlace(current, location, server)) {
            counts.unchanged++;
            problems.add(new SourceProblem(warp.origin(), "'" + name + "' already exists here with the same owner "
                    + "and position; kept the existing one"));
            return null;
        }

        String reason = plannedThisRun
                ? "another PlayerWarps warp already uses the name '" + name + "'"
                : "'" + name + "' already exists here (owned by " + describeOwner(current) + ")";

        if (options.policy() != ConflictPolicy.RENAME) {
            counts.conflicts++;
            problems.add(new SourceProblem(warp.origin(), reason + "; left out"
                    + (options.policy() == ConflictPolicy.REPLACE
                    ? " (player warps are never replaced: that would delete another player's warp; use rename)"
                    : "")));
            return null;
        }

        for (String alternative : MigrationPlanner.renameCandidates(name)) {
            if (!taken.containsKey(alternative)) {
                counts.renamed++;
                problems.add(new SourceProblem(warp.origin(), reason + "; imported as '" + alternative + "'"));
                return alternative;
            }
        }

        counts.conflicts++;
        problems.add(new SourceProblem(warp.origin(), reason + " and no free alternative name was found"));
        return null;
    }

    private PlayerWarp convert(SourcePlayerWarp warp, SourceLocation location, String id, String name, String server,
                               Set<String> knownCategories, Set<String> unknownCategories, List<SourceProblem> notes) {
        PlayerWarp converted = new PlayerWarp();

        converted.setId(id);
        converted.setName(name);
        converted.setOwner(warp.owner());
        converted.setOwnerName(playerName(warp.ownerName(), warp.origin(), notes));
        converted.setWorld(location.world());
        converted.setX(location.x());
        converted.setY(location.y());
        converted.setZ(location.z());
        converted.setYaw(location.yaw());
        converted.setPitch(location.pitch());
        converted.setServer(server);

        converted.setDescription(limit(warp.description(), DESCRIPTION_LENGTH, "description", warp.origin(), notes));

        if (warp.icon() != null) {
            if (warp.icon().length() > ICON_LENGTH) {
                notes.add(new SourceProblem(warp.origin(), "the icon '" + warp.icon() + "' is too long to store; "
                        + "imported with the default icon"));
            } else {
                converted.setIcon(warp.icon());
            }
        }

        if (!warp.categories().isEmpty()) {
            String category = warp.categories().get(0).toLowerCase(Locale.ROOT);
            if (category.length() > CATEGORY_LENGTH) {
                notes.add(new SourceProblem(warp.origin(), "the category '" + category + "' is longer than "
                        + CATEGORY_LENGTH + " characters; imported without a category"));
            } else {
                converted.setCategoryId(category);
                if (!knownCategories.contains(category)) {
                    unknownCategories.add(category);
                }
            }
        }

        converted.setCost(warp.cost());
        converted.setLocked(warp.locked());
        converted.setWhitelistEnabled(warp.whitelistEnabled());

        if (warp.password() != null) {
            String salt = PlayerWarpManager.randomSalt();
            converted.setPasswordSalt(salt);
            converted.setPasswordHash(PlayerWarpManager.hash(warp.password(), salt));
        }

        long created = warp.createdAt() > 0 ? warp.createdAt() : options.now();
        converted.setCreatedAt(created);
        converted.setUpdatedAt(created);
        converted.setLastUsedAt(Math.max(created, warp.lastUsedAt()));

        return converted;
    }

    private void addLink(Planned target, SourcePlayerWarpLink link, LinkCounter count, List<SourceProblem> problems,
                         List<SourceProblem> notes) {
        String warpId = target.warp.getId();
        String playerName = playerName(link.playerName(), link.origin(), notes);
        long now = options.now();

        switch (link.kind()) {
            case FAVOURITE -> {
                if (target.favourites.put(link.player(), new PlayerWarpFavourite(link.player(), warpId, now))
                        != null) {
                    count.duplicates++;
                }
            }
            case RATING -> {
                if (link.stars() < 1 || link.stars() > 5) {
                    count.invalid++;
                    problems.add(new SourceProblem(link.origin(), "the rating " + link.stars()
                            + " is outside 1-5; left out"));
                    return;
                }
                String comment = limit(link.text(), COMMENT_LENGTH, "rating comment", link.origin(), notes);
                if (target.ratings.put(link.player(), new PlayerWarpRating(warpId, link.player(), playerName,
                        link.stars(), comment, now, now)) != null) {
                    count.duplicates++;
                }
            }
            case WHITELIST -> {
                if (target.whitelist.put(link.player(), new PlayerWarpWhitelistEntry(warpId, link.player(),
                        playerName, now)) != null) {
                    count.duplicates++;
                }
            }
            case MANAGER -> {
                if (target.managers.put(link.player(), new PlayerWarpManagerEntry(warpId, link.player(),
                        playerName, now)) != null) {
                    count.duplicates++;
                }
            }
            case BAN -> {
                if (link.expiresAt() > 0 && link.expiresAt() <= now) {
                    count.invalid++;
                    problems.add(new SourceProblem(link.origin(), "the ban had already ended; left out"));
                    return;
                }
                String reason = limit(link.text(), REASON_LENGTH, "ban reason", link.origin(), notes);
                long expires = link.expiresAt() > 0 ? link.expiresAt() : PlayerWarpBan.PERMANENT;
                if (target.bans.put(link.player(), new PlayerWarpBan(warpId, link.player(), playerName, null, null,
                        reason, now, expires)) != null) {
                    count.duplicates++;
                }
            }
        }
    }

    /**
     * Lower case, accents dropped, colour codes removed and every run of other characters turned into one '_',
     * cut to the longest name allowed: "Mansión" becomes "mansion", "&6Gold Shop" becomes "gold_shop".
     */
    static String sanitize(String name) {
        if (name == null) {
            return "";
        }

        String plain = COLOR_CODE.matcher(name).replaceAll("");
        plain = DIACRITICS.matcher(Normalizer.normalize(plain, Normalizer.Form.NFD)).replaceAll("");
        plain = NOT_ALLOWED.matcher(plain.toLowerCase(Locale.ROOT)).replaceAll("_");
        plain = trimUnderscores(plain);

        if (plain.length() > TeleportNames.MAX_LENGTH) {
            plain = trimUnderscores(plain.substring(0, TeleportNames.MAX_LENGTH));
        }

        return TeleportNames.isValid(plain) ? plain : "";
    }

    private static String trimUnderscores(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '_') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '_') {
            end--;
        }
        return value.substring(start, end);
    }

    private boolean belongsElsewhere(String localServer, SourceLocation location) {
        return !options.multiServer()
                && localServer != null && !localServer.isBlank()
                && location.server() != null && !location.server().isBlank()
                && !location.server().equalsIgnoreCase(localServer);
    }

    private static boolean samePlace(PlayerWarp current, SourceLocation location, String server) {
        return current.getWorld() != null && current.getWorld().equalsIgnoreCase(location.world())
                && (current.getServer() == null ? server == null : current.getServer().equalsIgnoreCase(server))
                && Math.abs(current.getX() - location.x()) < POSITION_TOLERANCE
                && Math.abs(current.getY() - location.y()) < POSITION_TOLERANCE
                && Math.abs(current.getZ() - location.z()) < POSITION_TOLERANCE;
    }

    private static String describeOwner(PlayerWarp warp) {
        return warp.getOwnerName() != null ? warp.getOwnerName() : String.valueOf(warp.getOwner());
    }

    private static String playerName(String name, String origin, List<SourceProblem> notes) {
        if (name == null || name.length() <= PLAYER_NAME_LENGTH) {
            return name;
        }
        notes.add(new SourceProblem(origin, "the player name '" + name + "' is longer than " + PLAYER_NAME_LENGTH
                + " characters; stored without a name (the UUID is kept)"));
        return null;
    }

    private static String limit(String text, int length, String label, String origin, List<SourceProblem> notes) {
        if (text == null || text.length() <= length) {
            return text;
        }

        int end = length;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        notes.add(new SourceProblem(origin, "the " + label + " is longer than " + length + " characters; cut to "
                + "fit (" + text.length() + " characters in the source)"));
        return text.substring(0, end);
    }

    private static void invalid(Counter counts, List<SourceProblem> problems, String origin, String reason) {
        counts.invalid++;
        problems.add(new SourceProblem(origin, reason));
    }

    private static final class Planned {
        private final PlayerWarp warp;
        private final Map<UUID, PlayerWarpFavourite> favourites = new LinkedHashMap<>();
        private final Map<UUID, PlayerWarpRating> ratings = new LinkedHashMap<>();
        private final Map<UUID, PlayerWarpWhitelistEntry> whitelist = new LinkedHashMap<>();
        private final Map<UUID, PlayerWarpManagerEntry> managers = new LinkedHashMap<>();
        private final Map<UUID, PlayerWarpBan> bans = new LinkedHashMap<>();

        private Planned(PlayerWarp warp) {
            this.warp = warp;
        }

        private PlayerWarpImport build() {
            warp.setFavouriteCount(favourites.size());
            warp.setRatingCount(ratings.size());
            warp.setRatingAverage(ratings.values().stream().mapToInt(PlayerWarpRating::stars).average()
                    .orElse(0.0D));

            return new PlayerWarpImport(warp, List.copyOf(favourites.values()), List.copyOf(ratings.values()),
                    List.copyOf(whitelist.values()), List.copyOf(managers.values()), List.copyOf(bans.values()));
        }
    }

    private static final class Counter {
        private int found;
        private int created;
        private int renamed;
        private int unchanged;
        private int conflicts;
        private int elsewhere;
        private int invalid;

        private MigrationPlan.Counts toCounts() {
            return new MigrationPlan.Counts(found, created, renamed, 0, unchanged, conflicts, elsewhere, invalid);
        }
    }

    private static final class LinkCounter {
        private int found;
        private int imported;
        private int withWarp;
        private int duplicates;
        private int invalid;

        private PlayerWarpPlan.LinkCounts toCounts() {
            return new PlayerWarpPlan.LinkCounts(found, imported, withWarp, duplicates, invalid);
        }
    }
}
