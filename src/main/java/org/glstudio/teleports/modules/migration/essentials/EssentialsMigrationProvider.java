package org.glstudio.teleports.modules.migration.essentials;

import org.glstudio.teleports.modules.migration.MigrationException;
import org.glstudio.teleports.modules.migration.MigrationProvider;
import org.glstudio.teleports.modules.migration.WorldDirectory;
import org.glstudio.teleports.modules.migration.source.MigrationData;
import org.glstudio.teleports.modules.migration.source.SourceHome;
import org.glstudio.teleports.modules.migration.source.SourceLocation;
import org.glstudio.teleports.modules.migration.source.SourceProblem;
import org.glstudio.teleports.modules.migration.source.SourceWarp;
import org.glstudio.teleports.modules.migration.source.YamlValues;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Reads EssentialsX's flat files (2.19 and later, plus the older name-based world format).
 *
 * <pre>
 * plugins/Essentials/userdata/&lt;uuid&gt;.yml   homes.&lt;name&gt;.{world, world-name, x, y, z, yaw, pitch}
 *                                         last-account-name (lastAccountName before 2.19)
 * plugins/Essentials/warps/&lt;file&gt;.yml       name, world, world-name, x, y, z, yaw, pitch, lastowner
 * plugins/Essentials/config.yml            per-warp-permission
 * </pre>
 *
 * Since 2.19 {@code world} holds the world's UUID and {@code world-name} its name at the time; before that
 * {@code world} held the name. Files are only ever opened for reading.
 */
public class EssentialsMigrationProvider implements MigrationProvider {
    private static final String WARP_PERMISSION_PREFIX = "essentials.warps.";

    @Override
    public String id() {
        return "essentials";
    }

    @Override
    public List<String> aliases() {
        return List.of("essentialsx", "ess");
    }

    @Override
    public String displayName() {
        return "EssentialsX";
    }

    @Override
    public String defaultFolder() {
        return "Essentials";
    }

    @Override
    public MigrationData read(Path folder, WorldDirectory worlds) throws MigrationException {
        Path userdata = folder.resolve("userdata");
        Path warpsFolder = folder.resolve("warps");

        if (!Files.isDirectory(userdata) && !Files.isDirectory(warpsFolder)) {
            throw new MigrationException("migrate.errors.not-found",
                    "neither userdata/ nor warps/ exists in " + folder, "{path}", folder.toString());
        }

        List<SourceHome> homes = new ArrayList<>();
        List<SourceWarp> warps = new ArrayList<>();
        List<SourceProblem> problems = new ArrayList<>();

        boolean perWarpPermission = readPerWarpPermission(folder.resolve("config.yml"), problems);

        try {
            for (Path file : yamlFiles(userdata)) {
                readUser(file, worlds, homes, problems);
            }

            for (Path file : yamlFiles(warpsFolder)) {
                readWarp(file, worlds, perWarpPermission, warps, problems);
            }
        } catch (IOException e) {
            throw new MigrationException("migrate.errors.read-failed", "could not list the EssentialsX folders", e,
                    "{error}", String.valueOf(e.getMessage()));
        }

        return new MigrationData("EssentialsX files in " + folder, null, homes, warps, problems);
    }

    private static boolean readPerWarpPermission(Path config, List<SourceProblem> problems) {
        if (!Files.isRegularFile(config)) {
            return false;
        }

        try {
            return YamlValues.bool(YamlValues.get(YamlValues.load(config), "per-warp-permission"), false);
        } catch (IOException | YAMLException e) {
            problems.add(new SourceProblem("config.yml", "could not be read, so per-warp-permission is assumed "
                    + "off: " + firstLine(e)));
            return false;
        }
    }

    private static void readUser(Path file, WorldDirectory worlds, List<SourceHome> homes,
                                 List<SourceProblem> problems) {
        String origin = "userdata/" + file.getFileName();
        Map<?, ?> root;

        try {
            root = YamlValues.load(file);
        } catch (IOException | YAMLException e) {
            problems.add(new SourceProblem(origin, "could not be read: " + firstLine(e)));
            return;
        }

        Map<?, ?> section = YamlValues.section(root, "homes");
        if (section == null || section.isEmpty()) {
            return;
        }

        UUID owner = parseUuid(stripExtension(file.getFileName().toString()));
        if (owner == null) {
            problems.add(new SourceProblem(origin, "the file name is not a UUID. This is EssentialsX's old "
                    + "name-based storage; start EssentialsX once so it converts its userdata, then migrate again"));
            return;
        }

        String ownerName = YamlValues.string(YamlValues.get(root, "last-account-name"));
        if (ownerName == null) {
            ownerName = YamlValues.string(YamlValues.get(root, "lastAccountName"));
        }

        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String name = String.valueOf(entry.getKey());
            String entryOrigin = origin + " homes." + name;

            if (!(entry.getValue() instanceof Map<?, ?> node)) {
                problems.add(new SourceProblem(entryOrigin, "is not a location"));
                continue;
            }

            try {
                homes.add(new SourceHome(owner, ownerName, name, location(node, worlds), 0L, false, entryOrigin));
            } catch (BadEntry e) {
                problems.add(new SourceProblem(entryOrigin, e.getMessage()));
            }
        }
    }

    private static void readWarp(Path file, WorldDirectory worlds, boolean perWarpPermission,
                                 List<SourceWarp> warps, List<SourceProblem> problems) {
        String origin = "warps/" + file.getFileName();
        Map<?, ?> root;

        try {
            root = YamlValues.load(file);
        } catch (IOException | YAMLException e) {
            problems.add(new SourceProblem(origin, "could not be read: " + firstLine(e)));
            return;
        }

        // EssentialsX itself ignores a warp file without a name, so it was never a usable warp.
        String name = YamlValues.string(YamlValues.get(root, "name"));
        if (name == null) {
            problems.add(new SourceProblem(origin, "has no name"));
            return;
        }

        String permission = perWarpPermission ? WARP_PERMISSION_PREFIX + name.toLowerCase(Locale.ROOT) : null;

        try {
            warps.add(new SourceWarp(name, location(root, worlds), permission, 0L, origin));
        } catch (BadEntry e) {
            problems.add(new SourceProblem(origin, e.getMessage()));
        }
    }

    static SourceLocation location(Map<?, ?> node, WorldDirectory worlds) throws BadEntry {
        String world = YamlValues.string(YamlValues.get(node, "world"));
        String worldName = YamlValues.string(YamlValues.get(node, "world-name"));

        if (world == null && worldName == null) {
            throw new BadEntry("no world recorded");
        }

        String resolved;
        UUID worldId = parseUuid(world);

        if (worldId != null) {
            String loaded = worlds.nameOf(worldId);
            resolved = loaded != null ? loaded : worldName;

            if (resolved == null) {
                throw new BadEntry("world " + worldId + " is not loaded on this server and no world-name was "
                        + "recorded");
            }
        } else {
            resolved = world != null ? world : worldName;
        }

        double x = YamlValues.number(YamlValues.get(node, "x"));
        double y = YamlValues.number(YamlValues.get(node, "y"));
        double z = YamlValues.number(YamlValues.get(node, "z"));

        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            throw new BadEntry("x, y or z is missing or not a number");
        }

        return new SourceLocation(resolved, x, y, z, angle(YamlValues.get(node, "yaw")),
                angle(YamlValues.get(node, "pitch")), null);
    }

    private static float angle(Object value) {
        double angle = YamlValues.number(value);
        return Double.isNaN(angle) ? 0F : (float) angle;
    }

    private static List<Path> yamlFiles(Path folder) throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return files;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.yml")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        }

        files.sort(null);
        return files;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.length() != 36) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String stripExtension(String fileName) {
        return fileName.endsWith(".yml") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }

    private static String firstLine(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    static final class BadEntry extends Exception {
        BadEntry(String reason) {
            super(reason);
        }
    }
}
