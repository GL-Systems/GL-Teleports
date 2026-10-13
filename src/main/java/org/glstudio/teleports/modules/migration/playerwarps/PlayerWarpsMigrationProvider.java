package org.glstudio.teleports.modules.migration.playerwarps;

import org.glstudio.teleports.modules.migration.MigrationException;
import org.glstudio.teleports.modules.migration.MigrationProvider;
import org.glstudio.teleports.modules.migration.WorldDirectory;
import org.glstudio.teleports.modules.migration.source.MigrationData;
import org.glstudio.teleports.modules.migration.source.SourceLocation;
import org.glstudio.teleports.modules.migration.source.SourcePlayerWarp;
import org.glstudio.teleports.modules.migration.source.SourcePlayerWarpLink;
import org.glstudio.teleports.modules.migration.source.SourcePlayerWarpLink.Kind;
import org.glstudio.teleports.modules.migration.source.SourcePlayerWarps;
import org.glstudio.teleports.modules.migration.source.SourceProblem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reads PlayerWarps (Olzie) 7.x player warps straight from its database, MySQL/MariaDB or SQLite.
 *
 * <pre>
 * {prefix}warps                     id, name, uuid (owner), world, x, y, z, pitch, yaw, description,
 *                                   visits "total:purged", date (ms), icon (Base64 item), category "a, b",
 *                                   locked (non-null = locked), cost, password (plain text), last_rent,
 *                                   whitelist_enabled, sponsor, sponsor_cooldown, type NORMAL|SIGN,
 *                                   random_sort, earned_rate_rewards, set_prices, tags, server (MySQL mode)
 * {prefix}players                   uuid, name, ... (profile data with no equivalent here)
 * {prefix}players_favourite_warps   player_uuid, warp_id -> warps.id
 * {prefix}players_visited_warps     player_uuid, warp_id
 * {prefix}rates                     warp_id, uuid, rate, description
 * {prefix}warps_visits              warp_id, player_uuid, amount, time (ms), boosted
 * {prefix}warps_banned              warp_id, player_uuid, time (ms the ban ends; 0 or -1 = permanent), reason
 * {prefix}warps_whitelisted         warp_id, player_uuid
 * {prefix}warps_managers            warp_id, player_uuid
 * {prefix}warps_paid                warp_id, player_uuid, time
 * {prefix}leaderboards, servers, updater
 * </pre>
 *
 * PlayerWarps declares its numeric columns as LONG, which MySQL turns into text, so every number is read as text
 * and parsed. Tables are read one by one with plain SELECTs and joined in memory, which keeps the SQL portable and
 * lets a related row whose warp is gone be counted instead of disappearing in a join. Connections are opened
 * read-only, so PlayerWarps' data cannot be changed.
 */
public class PlayerWarpsMigrationProvider implements MigrationProvider {
    static final String SQLITE_FILE = "data/database.db";

    private static final int CONNECT_TIMEOUT_SECONDS = 15;
    private static final Pattern COLOR_CODE = Pattern.compile("(?i)§[0-9A-FK-ORX]");

    static final String VISIT_COUNTERS = "Visit counters (sum of every warp's total visits)";
    static final String VISIT_RECORDS = "Per-player visit records (warps_visits; only the latest visit is kept, "
            + "as the warp's last use)";
    static final String VISITED_HISTORY = "Visited-warps history (players_visited_warps)";
    static final String PAYMENTS = "Pay-once payments (warps_paid)";
    static final String SPONSORS = "Sponsorships (warps.sponsor)";
    static final String RENT = "Rent due dates (warps.last_rent)";
    static final String TAGS = "Warp tags (warps.tags)";
    static final String SET_PRICES = "Per-currency prices (warps.set_prices)";
    static final String RATE_REWARDS = "Earned rating rewards (earned_rate_rewards)";
    static final String SIGN_WARPS = "Sign warps, imported as ordinary warps without their sign (type SIGN)";
    static final String EXTRA_CATEGORIES = "Categories beyond the first one of a warp";
    static final String ICON_DETAILS = "Icon details beyond the material (name, lore, enchantments, head skin)";
    static final String ICONS_UNREADABLE = "Icons that could not be decoded (imported with the default icon)";
    static final String LEADERBOARDS = "Leaderboard entries (leaderboards)";
    static final String PROFILES = "Player profiles (players: messages, logs, language, cooldowns; only the "
            + "names are used)";
    static final String ORPHANS = "Related records pointing at a warp that no longer exists";
    static final String MISSING_TABLES = "Tables that do not exist in this PlayerWarps database";

    @Override
    public String id() {
        return "playerwarps";
    }

    @Override
    public List<String> aliases() {
        return List.of("pwarps", "playerwarp");
    }

    @Override
    public String displayName() {
        return "PlayerWarps";
    }

    @Override
    public String defaultFolder() {
        return "PlayerWarps";
    }

    @Override
    public MigrationData read(Path folder, WorldDirectory worlds) throws MigrationException {
        if (!Files.isDirectory(folder)) {
            throw new MigrationException("migrate.errors.not-found", "no PlayerWarps folder at " + folder,
                    "{path}", folder.toString());
        }

        PlayerWarpsConfig config = PlayerWarpsConfig.load(folder);
        String storage = describe(config, folder);

        try (Connection connection = open(config, folder)) {
            return read(connection, config, storage);
        } catch (SQLException e) {
            throw new MigrationException("migrate.errors.read-failed", "could not query PlayerWarps (" + storage
                    + ")", e, "{error}", sanitize(e, config));
        }
    }

    static MigrationData read(Connection connection, PlayerWarpsConfig config, String storage)
            throws SQLException, MigrationException {
        Reader reader = new Reader(connection, config);
        reader.readAll();

        // Only MySQL mode records servers; that is also when PlayerWarps knows its own proxy name.
        String localServer = config.type() == PlayerWarpsConfig.DatabaseType.SQLITE ? null : config.serverName();

        return new MigrationData(storage, localServer, List.of(), List.of(), List.copyOf(reader.problems),
                new SourcePlayerWarps(List.copyOf(reader.warps), List.copyOf(reader.links),
                        reader.notCarriedOver(), List.copyOf(reader.notes)));
    }

    private static final class Reader {
        private final Connection connection;
        private final PlayerWarpsConfig config;

        private final Map<UUID, String> names = new HashMap<>();
        private final Map<Long, Long> lastVisits = new HashMap<>();
        private final Set<Long> warpIds = new HashSet<>();

        private final List<SourcePlayerWarp> warps = new ArrayList<>();
        private final List<SourcePlayerWarpLink> links = new ArrayList<>();
        private final List<SourceProblem> problems = new ArrayList<>();
        private final List<SourceProblem> notes = new ArrayList<>();
        private final Map<String, Long> counts = new LinkedHashMap<>();

        private Reader(Connection connection, PlayerWarpsConfig config) {
            this.connection = connection;
            this.config = config;
        }

        void readAll() throws SQLException, MigrationException {
            String warpsTable = config.table("warps");
            if (!exists(warpsTable)) {
                throw new MigrationException("migrate.errors.read-failed", "PlayerWarps table " + warpsTable
                        + " does not exist", "{error}", "table " + warpsTable + " does not exist; check "
                        + "settings.database.table-prefix in PlayerWarps' config.yml");
            }

            readPlayers();
            readVisits();
            readWarps();

            readLinks("players_favourite_warps", Kind.FAVOURITE, "player_uuid");
            readLinks("rates", Kind.RATING, "uuid");
            readLinks("warps_whitelisted", Kind.WHITELIST, "player_uuid");
            readLinks("warps_managers", Kind.MANAGER, "player_uuid");
            readLinks("warps_banned", Kind.BAN, "player_uuid");

            countRows("players_visited_warps", VISITED_HISTORY);
            countRows("warps_paid", PAYMENTS);
            countRows("leaderboards", LEADERBOARDS);
        }

        private void readPlayers() throws SQLException {
            String table = config.table("players");
            if (!exists(table)) {
                missing(table);
                return;
            }

            long profiles = 0;
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table);
                 ResultSet rows = statement.executeQuery()) {
                Row row = new Row(rows);
                while (rows.next()) {
                    profiles++;
                    UUID uuid = uuid(row.string("uuid"));
                    String name = row.string("name");
                    if (uuid != null && name != null) {
                        names.put(uuid, name);
                    }
                    if (row.string("earned_rate_rewards") != null) {
                        add(RATE_REWARDS, 1);
                    }
                }
            }
            add(PROFILES, profiles);
        }

        private void readVisits() throws SQLException {
            String table = config.table("warps_visits");
            if (!exists(table)) {
                missing(table);
                return;
            }

            long records = 0;
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table);
                 ResultSet rows = statement.executeQuery()) {
                Row row = new Row(rows);
                while (rows.next()) {
                    records++;
                    Long warp = number(row.string("warp_id"));
                    Long time = number(row.string("time"));
                    if (warp != null && time != null && time > 0) {
                        lastVisits.merge(warp, time, Math::max);
                    }
                }
            }
            add(VISIT_RECORDS, records);
        }

        private void readWarps() throws SQLException {
            String table = config.table("warps");
            boolean serverColumn = config.type() != PlayerWarpsConfig.DatabaseType.SQLITE;

            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table
                    + " ORDER BY id");
                 ResultSet rows = statement.executeQuery()) {
                Row row = new Row(rows);

                while (rows.next()) {
                    Long id = number(row.string("id"));
                    String name = row.string("name");
                    String origin = table + " #" + row.string("id") + (name == null ? "" : " '" + name + "'");

                    if (id == null) {
                        problems.add(new SourceProblem(origin, "the warp id is not a number"));
                        continue;
                    }
                    warpIds.add(id);

                    String ownerText = row.string("uuid");
                    UUID owner = uuid(ownerText);
                    if (ownerText != null && owner == null) {
                        problems.add(new SourceProblem(origin, "the owner '" + ownerText + "' is not a UUID"));
                        continue;
                    }

                    Double x = row.decimal("x");
                    Double y = row.decimal("y");
                    Double z = row.decimal("z");
                    if (x == null || y == null || z == null) {
                        problems.add(new SourceProblem(origin, "its position is missing or incomplete"));
                        continue;
                    }

                    Float yaw = row.single("yaw");
                    Float pitch = row.single("pitch");
                    SourceLocation location = new SourceLocation(row.string("world"), x, y, z,
                            yaw == null ? 0F : yaw, pitch == null ? 0F : pitch,
                            serverColumn ? row.string("server") : null);

                    List<String> categories = categories(row.string("category"));
                    if (categories.size() > 1) {
                        add(EXTRA_CATEGORIES, 1);
                        notes.add(new SourceProblem(origin, "filed under " + categories + "; only '"
                                + categories.get(0) + "' is kept, a warp has one category here"));
                    }

                    PlayerWarpsIcon.Result icon = PlayerWarpsIcon.decode(row.string("icon"));
                    if (icon.problem() != null) {
                        add(ICONS_UNREADABLE, 1);
                        notes.add(new SourceProblem(origin, icon.problem() + "; imported with the default icon"));
                    } else if (icon.customised()) {
                        add(ICON_DETAILS, 1);
                        notes.add(new SourceProblem(origin, "only the icon's material (" + icon.material()
                                + ") is kept, not its name, lore, enchantments or skin"));
                    }

                    Double cost = null;
                    String costText = row.string("cost");
                    if (costText != null) {
                        Double parsed = parseDecimal(costText);
                        if (parsed == null || !Double.isFinite(parsed)) {
                            notes.add(new SourceProblem(origin, "the cost '" + costText + "' is not a number; "
                                    + "imported as free"));
                        } else if (parsed > 0) {
                            cost = parsed;
                        }
                    }

                    String type = row.string("type");
                    if (type != null && type.equalsIgnoreCase("SIGN")) {
                        add(SIGN_WARPS, 1);
                        notes.add(new SourceProblem(origin, "a sign warp; imported as an ordinary warp"));
                    }

                    countIfPresent(row, "visits", null);
                    countIfPresent(row, "sponsor", SPONSORS);
                    countIfPresent(row, "last_rent", RENT);
                    countIfPresent(row, "tags", TAGS);
                    countIfPresent(row, "set_prices", SET_PRICES);
                    countIfPresent(row, "earned_rate_rewards", RATE_REWARDS);

                    Long date = number(row.string("date"));
                    long lastVisit = lastVisits.getOrDefault(id, 0L);

                    warps.add(new SourcePlayerWarp(id, owner, owner == null ? null : names.get(owner), name,
                            location, row.string("description"), categories, icon.material(), cost,
                            row.string("locked") != null, password(row.string("password")),
                            row.bool("whitelist_enabled"), date == null ? 0L : date, lastVisit, origin));
                }
            }
        }

        private void countIfPresent(Row row, String column, String label) throws SQLException {
            String value = row.string(column);
            if (value == null) {
                return;
            }

            if (column.equals("visits")) {
                // "total:purged"; only the total is a count of real visits.
                int colon = value.indexOf(':');
                Long total = number(colon < 0 ? value : value.substring(0, colon));
                if (total != null && total > 0) {
                    add(VISIT_COUNTERS, total);
                }
                return;
            }

            add(label, 1);
        }

        private void readLinks(String suffix, Kind kind, String playerColumn) throws SQLException {
            String table = config.table(suffix);
            if (!exists(table)) {
                missing(table);
                return;
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table
                    + " ORDER BY id");
                 ResultSet rows = statement.executeQuery()) {
                Row row = new Row(rows);

                while (rows.next()) {
                    String origin = table + " #" + row.string("id");
                    Long warp = number(row.string("warp_id"));

                    if (warp == null) {
                        problems.add(new SourceProblem(origin, "warp_id '" + row.string("warp_id")
                                + "' is not a number"));
                        continue;
                    }

                    if (!warpIds.contains(warp)) {
                        add(ORPHANS, 1);
                        continue;
                    }

                    String playerText = row.string(playerColumn);
                    UUID player = uuid(playerText);
                    if (player == null) {
                        problems.add(new SourceProblem(origin, "the player '" + playerText + "' is not a UUID"));
                        continue;
                    }

                    int stars = 0;
                    String text = null;
                    long expiresAt = -1L;

                    if (kind == Kind.RATING) {
                        Long rate = number(row.string("rate"));
                        if (rate == null) {
                            problems.add(new SourceProblem(origin, "the rating '" + row.string("rate")
                                    + "' is not a number"));
                            continue;
                        }
                        stars = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, rate));
                        text = nullText(row.string("description"));
                    } else if (kind == Kind.BAN) {
                        Long time = number(row.string("time"));
                        // PlayerWarps reads 0 as "never ends", exactly like -1.
                        expiresAt = time == null || time <= 0 ? -1L : time;
                        text = nullText(row.string("reason"));
                    }

                    links.add(new SourcePlayerWarpLink(kind, warp, player, names.get(player), stars, text,
                            expiresAt, origin));
                }
            }
        }

        private void countRows(String suffix, String label) throws SQLException {
            String table = config.table(suffix);
            if (!exists(table)) {
                missing(table);
                return;
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
                 ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    add(label, rows.getLong(1));
                }
            }
        }

        private boolean exists(String table) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table
                    + " WHERE 1 = 0");
                 ResultSet ignored = statement.executeQuery()) {
                return true;
            } catch (SQLException e) {
                return false;
            }
        }

        private void missing(String table) {
            add(MISSING_TABLES, 1);
            notes.add(new SourceProblem(table, "the table does not exist; nothing read from it"));
        }

        private void add(String label, long amount) {
            if (amount > 0) {
                counts.merge(label, amount, Long::sum);
            }
        }

        Map<String, Long> notCarriedOver() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
        }
    }

    /** Column access by name that tolerates columns older PlayerWarps versions did not have yet. */
    private static final class Row {
        private final ResultSet rows;
        private final Set<String> columns = new HashSet<>();

        private Row(ResultSet rows) throws SQLException {
            this.rows = rows;
            ResultSetMetaData metaData = rows.getMetaData();
            for (int index = 1; index <= metaData.getColumnCount(); index++) {
                columns.add(metaData.getColumnLabel(index).toLowerCase(Locale.ROOT));
            }
        }

        String string(String column) throws SQLException {
            return columns.contains(column) ? rows.getString(column) : null;
        }

        /**
         * Read natively, never through text: SQLite turns a REAL into text with only 15 significant digits, which
         * would move a warp by up to a few nanometres - not much, but not the value PlayerWarps stored.
         */
        Double decimal(String column) throws SQLException {
            if (!columns.contains(column)) {
                return null;
            }

            double value = rows.getDouble(column);
            if (!rows.wasNull()) {
                return value;
            }

            // Connector/J answers 0 + wasNull for text it cannot parse; give text one more chance.
            String text = rows.getString(column);
            return text == null ? null : parseDecimal(text);
        }

        Float single(String column) throws SQLException {
            if (!columns.contains(column)) {
                return null;
            }

            float value = rows.getFloat(column);
            return rows.wasNull() ? null : value;
        }

        boolean bool(String column) throws SQLException {
            String value = string(column);
            return value != null && (value.trim().equals("1") || value.trim().equalsIgnoreCase("true"));
        }
    }

    static List<String> categories(String value) {
        List<String> categories = new ArrayList<>();
        if (value == null) {
            return categories;
        }

        for (String category : value.split(", ")) {
            String trimmed = category.trim();
            if (!trimmed.isEmpty()) {
                categories.add(trimmed);
            }
        }
        return categories;
    }

    /**
     * PlayerWarps compares passwords after stripping colour codes from both sides, so that is the password a
     * player actually types.
     */
    static String password(String stored) {
        if (stored == null) {
            return null;
        }
        String stripped = COLOR_CODE.matcher(stored).replaceAll("");
        return stripped.isEmpty() ? null : stripped;
    }

    private static String nullText(String value) {
        return value == null || value.equals("null") || value.isBlank() ? null : value;
    }

    static Long number(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            Double decimal = parseDecimal(trimmed);
            return decimal == null || !Double.isFinite(decimal) ? null : (long) decimal.doubleValue();
        }
    }

    private static Double parseDecimal(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID uuid(String value) {
        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Connection open(PlayerWarpsConfig config, Path folder) throws MigrationException {
        PlayerWarpsConfig.Credentials credentials = config.credentials();
        Properties properties = new Properties();
        String driver;
        String url;

        switch (config.type()) {
            case SQLITE -> {
                Path file = folder.resolve(SQLITE_FILE);
                if (!Files.isRegularFile(file)) {
                    throw new MigrationException("migrate.errors.not-found", "PlayerWarps database file " + file
                            + " does not exist", "{path}", file.toString());
                }
                driver = "org.sqlite.JDBC";
                url = "jdbc:sqlite:" + file.toAbsolutePath();
                // SQLITE_OPEN_READONLY: the driver refuses every write and never creates the file.
                properties.setProperty("open_mode", "1");
            }
            case MYSQL, MARIADB -> {
                // Connector/J speaks to MariaDB as well, and is the driver this plugin already ships.
                driver = "com.mysql.cj.jdbc.Driver";
                url = "jdbc:mysql://" + credentials.host() + ":" + credentials.port() + "/"
                        + credentials.database() + arguments(credentials.arguments());
                properties.setProperty("user", credentials.username());
                properties.setProperty("password", credentials.password());
                properties.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS * 1000));
            }
            case POSTGRESQL -> {
                driver = "org.postgresql.Driver";
                url = "jdbc:postgresql://" + credentials.host() + ":" + credentials.port() + "/"
                        + credentials.database() + arguments(credentials.arguments());
                properties.setProperty("user", credentials.username());
                properties.setProperty("password", credentials.password());
                properties.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
                properties.setProperty("loginTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
            }
            default -> throw new IllegalStateException("Unhandled database type " + config.type());
        }

        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new MigrationException("migrate.errors.driver-missing", "JDBC driver " + driver
                    + " is not available", e, "{driver}", driver);
        }

        try {
            Connection connection = DriverManager.getConnection(url, properties);
            connection.setReadOnly(true);
            return connection;
        } catch (SQLException e) {
            throw new MigrationException("migrate.errors.connect-failed", "could not connect to PlayerWarps' "
                    + config.type() + " database (" + describe(config, folder) + ")", e,
                    "{error}", sanitize(e, config));
        }
    }

    private static String arguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return "";
        }
        return arguments.startsWith("?") ? arguments : "?" + arguments;
    }

    static String describe(PlayerWarpsConfig config, Path folder) {
        return config.type() == PlayerWarpsConfig.DatabaseType.SQLITE
                ? "PlayerWarps SQLite, " + folder.resolve(SQLITE_FILE)
                : "PlayerWarps " + config.type() + ", " + config.credentials() + ", tables "
                + config.tablePrefix() + "*";
    }

    /**
     * Driver messages may echo the user name back ("Access denied for user ..."); neither it nor the password
     * is allowed to reach a chat message or the log.
     */
    static String sanitize(SQLException error, PlayerWarpsConfig config) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        PlayerWarpsConfig.Credentials credentials = config.credentials();

        if (credentials != null) {
            if (!credentials.password().isEmpty()) {
                message = message.replace(credentials.password(), "***");
            }
            if (!credentials.username().isEmpty()) {
                message = message.replace(credentials.username(), "***");
            }
        }

        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
