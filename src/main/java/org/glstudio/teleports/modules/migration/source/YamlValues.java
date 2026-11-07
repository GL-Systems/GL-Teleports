package org.glstudio.teleports.modules.migration.source;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Plain SnakeYAML access for other plugins' files.
 *
 * Bukkit's YamlConfiguration would also work at runtime, but it logs through the server on a parse error and
 * the readers must stay usable off the main thread and in tests. Values are read leniently because hand-edited
 * files hold numbers as strings and keys as integers.
 */
public final class YamlValues {
    private YamlValues() {
    }

    /**
     * Reads a YAML file.
     */
    public static Map<?, ?> load(Path file) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(true);

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object root = new Yaml(new SafeConstructor(options)).load(reader);
            return root instanceof Map<?, ?> map ? map : Map.of();
        }
    }

    /**
     * Reads a dotted path.
     */
    public static Object path(Map<?, ?> root, String path) {
        Object current = root;

        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = get(map, part);
        }

        return current;
    }

    /**
     * Reads a key.
     */
    public static Object get(Map<?, ?> map, String key) {
        if (map.containsKey(key)) {
            return map.get(key);
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (String.valueOf(entry.getKey()).equals(key)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Reads a section.
     */
    public static Map<?, ?> section(Map<?, ?> root, String path) {
        Object value = path(root, path);
        return value instanceof Map<?, ?> map ? map : null;
    }

    /**
     * Reads a text.
     */
    public static String string(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * NaN when the value is missing or not a number, so the caller can tell "0" from "absent".
     */
    public static double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }

        return Double.NaN;
    }

    /**
     * Reads a flag.
     */
    public static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) {
            return flag;
        }

        if (value instanceof String text) {
            if (text.equalsIgnoreCase("true")) {
                return true;
            }
            if (text.equalsIgnoreCase("false")) {
                return false;
            }
        }

        return fallback;
    }
}
