package org.glstudio.teleports.modules.migration;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.glstudio.nexus.utils.Diagnostics;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.modules.database.DatabaseProvider;
import org.glstudio.teleports.modules.migration.essentials.EssentialsMigrationProvider;
import org.glstudio.teleports.modules.migration.huskhomes.HuskHomesMigrationProvider;
import org.glstudio.teleports.modules.migration.playerwarps.PlayerWarpsMigrationProvider;
import org.glstudio.teleports.modules.migration.source.MigrationData;
import org.glstudio.teleports.modules.migration.source.SourceHome;
import org.glstudio.teleports.modules.migration.source.SourcePlayerWarpLink.Kind;
import org.glstudio.teleports.modules.migration.source.SourceProblem;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpCategory;
import org.glstudio.teleports.modules.model.PlayerWarpImport;
import org.glstudio.teleports.modules.model.Warp;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs /glteleports migrate: a scan that reports what would happen, then - only after a confirmation - the same
 * plan written to this plugin's storage.
 *
 * Reading the source, comparing it against existing data and writing all happen on the IO executor; the main
 * thread only captures the loaded worlds up front and sends the messages. One migration runs at a time.
 */
public class MigrationManager {
    private static final long CONFIRM_WINDOW_MILLIS = 120_000L;
    private static final int PROBLEMS_LOGGED = 10;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final GTeleports plugin;
    private final List<MigrationProvider> providers = List.of(
            new EssentialsMigrationProvider(),
            new HuskHomesMigrationProvider(),
            new PlayerWarpsMigrationProvider());

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, PendingConfirmation> pending = new ConcurrentHashMap<>();

    /**
     * Creates the manager.
     */
    public MigrationManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Lists the available sources.
     */
    public List<String> sourceIds() {
        return providers.stream().map(MigrationProvider::id).toList();
    }

    /**
     * Finds a source by name.
     */
    public MigrationProvider find(String argument) {
        String wanted = argument.toLowerCase(Locale.ROOT);

        for (MigrationProvider provider : providers) {
            if (provider.id().equals(wanted) || provider.aliases().contains(wanted)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * Reads the source and reports what an import would do. Nothing is written.
     */
    public void scan(CommandSender sender, String sourceArgument) {
        scan(sender, sourceArgument, MigrationSelection.all());
    }

    /**
     * Shows what a migration would do.
     */
    public void scan(CommandSender sender, String sourceArgument, MigrationSelection selection) {
        start(sender, sourceArgument, selection, false);
    }

    /**
     * Imports, provided the same sender scanned the same source within the confirmation window. The data is
     * read and planned again, so whatever changed since the scan is taken into account.
     */
    public void confirm(CommandSender sender, String sourceArgument) {
        confirm(sender, sourceArgument, MigrationSelection.all());
    }

    /**
     * Runs a planned migration.
     */
    public void confirm(CommandSender sender, String sourceArgument, MigrationSelection selection) {
        start(sender, sourceArgument, selection, true);
    }

    private void start(CommandSender sender, String sourceArgument, MigrationSelection selection, boolean write) {
        MigrationProvider provider = find(sourceArgument);
        if (provider == null) {
            plugin.getMessages().send(sender, "migrate.errors.unknown-source",
                    "{source}", sourceArgument, "{sources}", String.join(", ", sourceIds()));
            return;
        }

        String policyName = plugin.getSettings().getString("migrate.conflict-policy", "skip");
        ConflictPolicy policy = ConflictPolicy.parse(policyName);
        if (policy == null) {
            plugin.getMessages().send(sender, "migrate.errors.invalid-policy", "{policy}", policyName);
            return;
        }

        Map<String, String> worldMappings;
        try {
            worldMappings = readWorldMappings();
        } catch (IllegalArgumentException e) {
            plugin.getMessages().send(sender, "migrate.errors.invalid-world-mapping", "{error}", e.getMessage());
            return;
        }

        if (running.get()) {
            plugin.getMessages().send(sender, "migrate.errors.already-running");
            return;
        }

        if (write && !consumeConfirmation(sender, provider, selection, policy, worldMappings)) {
            plugin.getMessages().send(sender, "migrate.errors.scan-first", "{source}", provider.id());
            return;
        }

        if (!running.compareAndSet(false, true)) {
            plugin.getMessages().send(sender, "migrate.errors.already-running");
            return;
        }

        try {
            launch(sender, provider, selection, policy, worldMappings, write);
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
    }

    private void launch(CommandSender sender, MigrationProvider provider, MigrationSelection selection,
                        ConflictPolicy policy, Map<String, String> worldMappings, boolean write) {
        WorldDirectory worlds = loadedWorlds();
        MigrationOptions options = new MigrationOptions(
                plugin.getMultiServerSettings().isEnabled(),
                plugin.getCrossServerManager().currentServer(),
                plugin.getSettings().getString("migrate.server", "").trim(),
                plugin.getSettings().getBoolean("migrate.use-source-server", true),
                policy,
                worldMappings,
                System.currentTimeMillis());
        Path folder = folderFor(provider);
        Set<String> categories = plugin.getPlayerWarpCategoryRegistry().all().stream()
                .map(PlayerWarpCategory::id)
                .collect(Collectors.toUnmodifiableSet());
        long started = System.nanoTime();

        plugin.getMessages().send(sender, write ? "migrate.importing" : "migrate.scanning",
                "{source}", provider.displayName());
        LoggerUtils.logInfo((write ? "Importing" : "Scanning") + " " + provider.displayName() + " data from "
                + folder + " (requested by " + sender.getName() + ", kinds " + selection.key()
                + ", conflict policy " + policy.key() + ").");

        DatabaseProvider database = plugin.getDatabaseManager().getProvider();

        CompletableFuture<MigrationPlan> result = CompletableFuture
                .supplyAsync(() -> selection.filter(read(provider, folder, worlds)), plugin.getExecutors().io())
                .thenCompose(data -> existingData(provider, database, data)
                        .thenApply(existing -> plan(data, existing, options, worlds, categories)));

        if (write) {
            result = result.thenCompose(plan -> plan.homes().isEmpty() && plan.warps().isEmpty()
                    ? CompletableFuture.completedFuture(plan)
                    : database.importData(plan.homes(), plan.warps()).thenApply(ignored -> plan));
            result = result.thenCompose(plan -> plan.playerWarps().isEmpty()
                    ? CompletableFuture.completedFuture(plan)
                    : database.importPlayerWarps(plan.playerWarps().imports())
                    .thenApply(ignored -> {
                        recordImported(provider, plan.playerWarps());
                        return plan;
                    }));
        }

        result.whenComplete((plan, error) -> {
            try {
                if (error != null) {
                    fail(sender, provider, error, write);
                    return;
                }

                if (write && !plan.isEmpty()) {
                    refreshCaches(plan);
                }

                Path log = writeLog(provider, plan, write, options, selection);
                double seconds = (System.nanoTime() - started) / 1_000_000_000D;
                logSummary(provider, plan, write, seconds, log);

                if (!write && !plan.isEmpty()) {
                    pending.put(key(sender), new PendingConfirmation(provider.id(), selection, policy, worldMappings,
                            System.currentTimeMillis() + CONFIRM_WINDOW_MILLIS));
                }

                onMain(() -> report(sender, provider, plan, write, options, selection, seconds, log));
            } finally {
                running.set(false);
            }
        });
    }

    private MigrationData read(MigrationProvider provider, Path folder, WorldDirectory worlds) {
        try {
            return provider.read(folder, worlds);
        } catch (MigrationException e) {
            throw new CompletionException(e);
        }
    }

    private MigrationPlan plan(MigrationData data, ExistingData existing, MigrationOptions options,
                               WorldDirectory worlds, Set<String> categories) {
        try {
            return new MigrationPlanner(options, worlds).plan(data, existing.homes(), existing.warps(),
                    existing.playerWarps(), existing.importedPlayerWarps(), categories);
        } catch (MigrationException e) {
            throw new CompletionException(e);
        }
    }

    private CompletableFuture<ExistingData> existingData(MigrationProvider provider, DatabaseProvider database,
                                                         MigrationData data) {
        Set<UUID> owners = data.homes().stream()
                .map(SourceHome::owner)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, List<Home>> homes = new ConcurrentHashMap<>();
        CompletableFuture<?>[] loads = owners.stream()
                .map(owner -> database.getHomes(owner).thenAccept(list -> homes.put(owner, list)))
                .toArray(CompletableFuture[]::new);

        CompletableFuture<List<PlayerWarp>> playerWarps = data.playerWarps().isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : database.getAllPlayerWarps();
        Set<String> imported = data.playerWarps().isEmpty() ? Set.of() : readImported(provider);

        return CompletableFuture.allOf(loads)
                .thenCombine(database.getAllWarps(), (ignored, warps) -> warps)
                .thenCombine(playerWarps, (warps, current) -> new ExistingData(homes, warps, current, imported));
    }

    /**
     * The ids of every player warp this source ever imported, kept next to the migration logs. With it a warp
     * the owner deleted after the import is not brought back by a later run.
     */
    private Path importedFile(MigrationProvider provider) {
        return plugin.getDataFolder().toPath().resolve("migrations").resolve(provider.id() + "-imported.txt");
    }

    private Set<String> readImported(MigrationProvider provider) {
        Path file = importedFile(provider);
        if (!Files.isRegularFile(file)) {
            return Set.of();
        }

        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new CompletionException(new MigrationException("migrate.errors.read-failed",
                    "could not read " + file, e, "{error}", "could not read " + file.getFileName()
                    + ": " + e.getMessage()));
        }
    }

    private void recordImported(MigrationProvider provider, PlayerWarpPlan plan) {
        Path file = importedFile(provider);

        try {
            Files.createDirectories(file.getParent());
            boolean fresh = !Files.exists(file);

            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                if (fresh) {
                    writer.write("# Player warps imported from " + provider.displayName() + ", one id per line. "
                            + "Ids listed here are never imported again,\n# even if the warp has been deleted "
                            + "since. Delete this file to let a new run recreate deleted warps.\n");
                }
                for (PlayerWarpImport entry : plan.imports()) {
                    writer.write(entry.warp().getId() + "\n");
                }
            }
        } catch (IOException e) {
            // The import itself succeeded; without this file a later run still never duplicates a warp that
            // exists, it would only recreate one that was deleted in the meantime.
            LoggerUtils.logWarn("Could not record the imported player warps in " + file + ": " + e.getMessage());
        }
    }

    private void refreshCaches(MigrationPlan plan) {
        plugin.getHomeManager().invalidate(null);
        plugin.getWarpManager().invalidateCache();
        plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.HOME, null);
        plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.WARP, null);

        if (!plan.playerWarps().isEmpty()) {
            plugin.getPlayerWarpManager().invalidateCache();
            plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.PLAYER_WARP, null);
        }
    }

    private void fail(CommandSender sender, MigrationProvider provider, Throwable error, boolean write) {
        Throwable cause = Diagnostics.rootCause(error);
        MigrationException migrationError = findMigrationException(error);

        if (migrationError != null) {
            // The detail is ours and never carries credentials; the driver's own exception might, so it is
            // deliberately not logged here.
            LoggerUtils.logWarn(provider.displayName() + " migration stopped: " + migrationError.getMessage());
            onMain(() -> plugin.getMessages().send(sender, migrationError.messageKey(),
                    withSource(provider, migrationError.placeholders())));
            return;
        }

        Diagnostics.logFailure("MigrationManager#" + (write ? "import" : "scan"), cause);
        onMain(() -> plugin.getMessages().send(sender,
                write ? "migrate.errors.write-failed" : "migrate.errors.read-failed",
                "{source}", provider.displayName(), "{error}", Diagnostics.describeRootCause(error)));
    }

    private static MigrationException findMigrationException(Throwable error) {
        Throwable current = error;

        for (int depth = 0; current != null && depth < 10; depth++) {
            if (current instanceof MigrationException migration) {
                return migration;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String[] withSource(MigrationProvider provider, String[] placeholders) {
        String[] all = new String[placeholders.length + 2];
        all[0] = "{source}";
        all[1] = provider.displayName();
        System.arraycopy(placeholders, 0, all, 2, placeholders.length);
        return all;
    }

    private void report(CommandSender sender, MigrationProvider provider, MigrationPlan plan, boolean write,
                        MigrationOptions options, MigrationSelection selection, double seconds, Path log) {
        String source = provider.displayName();

        plugin.getMessages().send(sender, write ? "migrate.done-header" : "migrate.scan-header",
                "{source}", source, "{storage}", plan.storage(), "{duration}", duration(seconds));
        plugin.getMessages().send(sender, "migrate.selection", "{selection}", selection.key());
        if (!options.worldMappings().isEmpty()) {
            String mappings = options.worldMappings().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + " → " + entry.getValue())
                    .collect(Collectors.joining(", "));
            plugin.getMessages().send(sender, "migrate.world-mappings", "{mappings}", mappings);
        }

        PlayerWarpPlan playerWarps = plan.playerWarps();
        boolean onlyPlayerWarps = playerWarps.found() && plan.homeCounts().found() == 0
                && plan.warpCounts().found() == 0;

        if (!onlyPlayerWarps) {
            plugin.getMessages().send(sender, "migrate.summary-players", "{players}", number(plan.players()));
            sendCounts(sender, write ? "migrate.done-homes" : "migrate.summary-homes", plan.homeCounts());
            sendCounts(sender, write ? "migrate.done-warps" : "migrate.summary-warps", plan.warpCounts());
        }

        if (playerWarps.found()) {
            reportPlayerWarps(sender, playerWarps, write);
        }

        if (plan.unreadable() > 0) {
            plugin.getMessages().send(sender, "migrate.summary-unreadable", "{count}", number(plan.unreadable()));
        }
        if (plan.publicHomes() > 0) {
            plugin.getMessages().send(sender, "migrate.summary-public", "{count}", number(plan.publicHomes()));
        }
        if (plan.restrictedWarps() > 0) {
            plugin.getMessages().send(sender, "migrate.summary-restricted",
                    "{count}", number(plan.restrictedWarps()));
        }
        if (plan.unloadedWorlds() > 0) {
            plugin.getMessages().send(sender, "migrate.summary-unloaded-worlds",
                    "{count}", number(plan.unloadedWorlds()));
        }

        if (!options.multiServer()) {
            plugin.getMessages().send(sender, "migrate.target-local", "{server}", options.currentServer());
        } else if (options.useSourceServer()) {
            plugin.getMessages().send(sender, "migrate.target-source", "{server}",
                    options.fallbackServer().isEmpty() ? "-" : options.fallbackServer());
        } else {
            plugin.getMessages().send(sender, "migrate.target-server", "{server}", options.fallbackServer());
        }

        if (log != null) {
            plugin.getMessages().send(sender, "migrate.log-written", "{file}",
                    plugin.getDataFolder().toPath().relativize(log).toString());
        }

        if (write) {
            return;
        }

        if (plan.isEmpty()) {
            plugin.getMessages().send(sender, "migrate.nothing-to-import", "{source}", source);
            return;
        }

        String command = "/glteleports migrate " + provider.id()
                + (selection.key().equals("all") ? "" : " " + selection.key()) + " confirm";
        plugin.getMessages().send(sender, "migrate.confirm", "{policy}", options.policy().key(),
                "{command}", command, "{seconds}", String.valueOf(CONFIRM_WINDOW_MILLIS / 1000));
    }

    private void reportPlayerWarps(CommandSender sender, PlayerWarpPlan plan, boolean write) {
        plugin.getMessages().send(sender, "migrate.summary-player-warp-owners", "{players}", number(plan.owners()));
        sendCounts(sender, write ? "migrate.done-player-warps" : "migrate.summary-player-warps", plan.counts());

        PlayerWarpPlan.LinkCounts favourites = links(plan, Kind.FAVOURITE);
        PlayerWarpPlan.LinkCounts ratings = links(plan, Kind.RATING);
        PlayerWarpPlan.LinkCounts whitelist = links(plan, Kind.WHITELIST);
        PlayerWarpPlan.LinkCounts managers = links(plan, Kind.MANAGER);
        PlayerWarpPlan.LinkCounts bans = links(plan, Kind.BAN);

        plugin.getMessages().send(sender, write ? "migrate.done-player-warp-links" : "migrate.summary-player-warp-links",
                "{favourites}", number(favourites.imported()), "{favourites-found}", number(favourites.found()),
                "{ratings}", number(ratings.imported()), "{ratings-found}", number(ratings.found()),
                "{whitelist}", number(whitelist.imported()), "{whitelist-found}", number(whitelist.found()),
                "{managers}", number(managers.imported()), "{managers-found}", number(managers.found()),
                "{bans}", number(bans.imported()), "{bans-found}", number(bans.found()));

        int withWarp = 0;
        int duplicates = 0;
        int invalid = 0;
        for (PlayerWarpPlan.LinkCounts counts : plan.links().values()) {
            withWarp += counts.withWarp();
            duplicates += counts.duplicates();
            invalid += counts.invalid();
        }

        if (withWarp + duplicates + invalid > 0) {
            plugin.getMessages().send(sender, "migrate.summary-player-warp-links-left",
                    "{with-warp}", number(withWarp), "{duplicates}", number(duplicates), "{invalid}", number(invalid));
        }

        if (!plan.unknownCategories().isEmpty()) {
            plugin.getMessages().send(sender, "migrate.summary-unknown-categories",
                    "{categories}", String.join(", ", plan.unknownCategories()));
        }

        if (!plan.notes().isEmpty()) {
            plugin.getMessages().send(sender, "migrate.summary-adjusted", "{count}", number(plan.notes().size()));
        }

        if (!plan.notCarriedOver().isEmpty()) {
            plugin.getMessages().send(sender, "migrate.not-carried-over-header");
            plan.notCarriedOver().forEach((label, amount) -> plugin.getMessages().send(sender,
                    "migrate.not-carried-over-entry", "{what}", label, "{amount}", number(amount)));
        }
    }

    private static PlayerWarpPlan.LinkCounts links(PlayerWarpPlan plan, Kind kind) {
        return plan.links().getOrDefault(kind, new PlayerWarpPlan.LinkCounts(0, 0, 0, 0, 0));
    }

    private void sendCounts(CommandSender sender, String key, MigrationPlan.Counts counts) {
        plugin.getMessages().send(sender, key,
                "{found}", number(counts.found()),
                "{imported}", number(counts.imported()),
                "{new}", number(counts.created()),
                "{renamed}", number(counts.renamed()),
                "{replaced}", number(counts.replaced()),
                "{unchanged}", number(counts.unchanged()),
                "{conflicts}", number(counts.conflicts()),
                "{elsewhere}", number(counts.elsewhere()),
                "{skipped}", number(counts.skipped()),
                "{invalid}", number(counts.invalid()));
    }

    private void logSummary(MigrationProvider provider, MigrationPlan plan, boolean write, double seconds,
                            Path log) {
        MigrationPlan.Counts homes = plan.homeCounts();
        MigrationPlan.Counts warps = plan.warpCounts();

        LoggerUtils.logInfo(provider.displayName() + (write ? " import finished" : " scan finished") + " in "
                + duration(seconds) + " (" + plan.storage() + "): " + plan.players() + " players; homes "
                + homes.found() + " found, " + homes.imported() + (write ? " imported" : " to import") + ", "
                + homes.skipped() + " skipped, " + homes.invalid() + " invalid; warps " + warps.found()
                + " found, " + warps.imported() + (write ? " imported" : " to import") + ", " + warps.skipped()
                + " skipped, " + warps.invalid() + " invalid; " + plan.unreadable() + " unreadable records.");

        PlayerWarpPlan playerWarps = plan.playerWarps();
        if (playerWarps.found()) {
            MigrationPlan.Counts counts = playerWarps.counts();
            LoggerUtils.logInfo("  Player warps: " + counts.found() + " found, " + counts.imported()
                    + (write ? " imported" : " to import") + ", " + counts.skipped() + " skipped, "
                    + counts.invalid() + " invalid; " + playerWarps.notCarriedOver().size()
                    + " kinds of data have no equivalent here (listed in the log).");
        }

        List<SourceProblem> problems = plan.problems();
        for (int index = 0; index < Math.min(PROBLEMS_LOGGED, problems.size()); index++) {
            SourceProblem problem = problems.get(index);
            LoggerUtils.logWarn("  " + problem.origin() + ": " + problem.reason());
        }

        if (problems.size() > PROBLEMS_LOGGED) {
            LoggerUtils.logWarn("  ... and " + (problems.size() - PROBLEMS_LOGGED) + " more"
                    + (log == null ? "." : ", listed in " + log + "."));
        }
    }

    private Path writeLog(MigrationProvider provider, MigrationPlan plan, boolean write, MigrationOptions options,
                          MigrationSelection selection) {
        try {
            Path folder = plugin.getDataFolder().toPath().resolve("migrations");
            Files.createDirectories(folder);

            Path file = folder.resolve(provider.id() + "-" + (write ? "import" : "scan") + "-"
                    + LocalDateTime.now().format(STAMP) + ".log");

            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(provider.displayName() + (write ? " import" : " scan (nothing written)") + "\n");
                writer.write("Source: " + plan.storage() + "\n");
                writer.write("Selected kinds: " + selection.key() + "\n");
                writer.write("Conflict policy: " + options.policy().key() + "\n");
                writer.write("World mappings: " + options.worldMappings() + "\n");
                writer.write("Multi-server: " + options.multiServer() + ", this server: " + options.currentServer()
                        + ", migrate.server: " + (options.fallbackServer().isEmpty() ? "-" : options.fallbackServer())
                        + "\n");
                writer.write("Players: " + plan.players() + "\n");
                writeCounts(writer, "Homes", plan.homeCounts());
                writeCounts(writer, "Warps", plan.warpCounts());
                writer.write("Unreadable records: " + plan.unreadable() + "\n");
                writePlayerWarps(writer, plan.playerWarps());
                writer.write("\n");

                writer.write("Entries left out, renamed or replaced (" + plan.problems().size() + "):\n");
                for (SourceProblem problem : plan.problems()) {
                    writer.write("  " + problem.origin() + ": " + problem.reason() + "\n");
                }
            }

            return file;
        } catch (IOException e) {
            LoggerUtils.logWarn("Could not write the migration log: " + e.getMessage());
            return null;
        }
    }

    private static void writePlayerWarps(Writer writer, PlayerWarpPlan plan) throws IOException {
        if (!plan.found()) {
            return;
        }

        writer.write("Player warp owners: " + plan.owners() + "\n");
        writeCounts(writer, "Player warps", plan.counts());

        for (Kind kind : Kind.values()) {
            PlayerWarpPlan.LinkCounts counts = links(plan, kind);
            writer.write("  " + kind.name().toLowerCase(Locale.ROOT) + ": found " + counts.found() + ", imported "
                    + counts.imported() + ", left behind with their warp " + counts.withWarp()
                    + ", duplicates merged " + counts.duplicates() + ", invalid " + counts.invalid() + "\n");
        }

        if (!plan.unknownCategories().isEmpty()) {
            writer.write("Categories not defined in player-warps.yml (kept on the warps; add them to show them): "
                    + String.join(", ", plan.unknownCategories()) + "\n");
        }

        writer.write("\nNot carried over, no equivalent in GL-Teleports (" + plan.notCarriedOver().size() + "):\n");
        for (Map.Entry<String, Long> entry : plan.notCarriedOver().entrySet()) {
            writer.write("  " + entry.getKey() + ": " + entry.getValue() + "\n");
        }

        writer.write("\nImported with changes (" + plan.notes().size() + "):\n");
        for (SourceProblem note : plan.notes()) {
            writer.write("  " + note.origin() + ": " + note.reason() + "\n");
        }
    }

    private static void writeCounts(Writer writer, String label, MigrationPlan.Counts counts) throws IOException {
        writer.write(label + ": found " + counts.found() + ", new " + counts.created() + ", renamed "
                + counts.renamed() + ", replaced " + counts.replaced() + ", already present " + counts.unchanged()
                + ", conflicts skipped " + counts.conflicts() + ", on another server " + counts.elsewhere()
                + ", invalid " + counts.invalid() + "\n");
    }

    private Path folderFor(MigrationProvider provider) {
        String configured = plugin.getSettings().getString("migrate.folders." + provider.id(), "").trim();

        if (!configured.isEmpty()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }

        return plugin.getDataFolder().toPath().toAbsolutePath().getParent().resolve(provider.defaultFolder());
    }

    private Map<String, String> readWorldMappings() {
        String path = "migrate.world-mappings";
        ConfigurationSection section = plugin.getConfigManager().getMainConfig().getConfigurationSection(path);
        if (section == null) {
            if (plugin.getConfigManager().getMainConfig().isSet(path)) {
                throw new IllegalArgumentException(path + " must be a map of source world to destination world");
            }
            return Map.of();
        }

        Map<String, String> mappings = new HashMap<>();
        for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
            String source = entry.getKey().trim();
            if (!(entry.getValue() instanceof String value)) {
                throw new IllegalArgumentException(path + "." + source + " must be a world name");
            }
            String target = value.trim();
            if (source.isEmpty() || target.isEmpty() || source.length() > 64 || target.length() > 64) {
                throw new IllegalArgumentException(path + " needs non-empty world names of at most 64 characters");
            }
            if (mappings.putIfAbsent(source.toLowerCase(Locale.ROOT), target) != null) {
                throw new IllegalArgumentException(path + " contains the same source world more than once: "
                        + source);
            }
        }
        return Map.copyOf(mappings);
    }

    private static WorldDirectory loadedWorlds() {
        Map<UUID, String> worlds = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            worlds.put(world.getUID(), world.getName());
        }
        return new WorldDirectory(worlds);
    }

    private boolean consumeConfirmation(CommandSender sender, MigrationProvider provider, MigrationSelection selection,
                                        ConflictPolicy policy, Map<String, String> worldMappings) {
        PendingConfirmation confirmation = pending.remove(key(sender));

        return confirmation != null
                && confirmation.source().equals(provider.id())
                && confirmation.selection().equals(selection)
                && confirmation.policy() == policy
                && confirmation.worldMappings().equals(worldMappings)
                && confirmation.expiresAt() >= System.currentTimeMillis();
    }

    private static String key(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : "console:" + sender.getName();
    }

    private void onMain(Runnable action) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String duration(double seconds) {
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }

    /**
     * Stops this on shutdown.
     */
    public void safeDisable() {
        pending.clear();
    }

    private record PendingConfirmation(String source, MigrationSelection selection, ConflictPolicy policy,
                                       Map<String, String> worldMappings, long expiresAt) {
    }

    private record ExistingData(Map<UUID, List<Home>> homes, List<Warp> warps, List<PlayerWarp> playerWarps,
                                Set<String> importedPlayerWarps) {
    }
}
