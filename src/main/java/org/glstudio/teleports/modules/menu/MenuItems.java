package org.glstudio.teleports.modules.menu;

import org.bukkit.configuration.ConfigurationSection;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.common.menu.button.Button;
import org.glstudio.teleports.common.menu.button.ButtonBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MenuItems {
    public static final int NO_SLOT = -1;

    private MenuItems() {
    }

    public static void put(Map<Integer, Button> buttons, ConfigFile config, String path, Runnable onClick) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return;
        }
        int slot = section.getInt("SLOT", NO_SLOT);
        if (slot < 0) {
            return;
        }
        ButtonBuilder builder = ButtonBuilder.fromConfig(section);
        if (onClick != null) {
            builder.onClickSimple(onClick);
        }
        buttons.put(slot, builder.build());
    }

    public static ButtonBuilder builder(ConfigFile config, String path) {
        return ButtonBuilder.fromConfig(config.getConfigurationSection(path));
    }

    public static int slot(ConfigFile config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section == null ? NO_SLOT : section.getInt("SLOT", NO_SLOT);
    }

    public static int slot(ConfigFile config, String path, int fallback) {
        int slot = slot(config, path);
        return slot < 0 ? fallback : slot;
    }

    public static String name(ConfigFile config, String path, String fallback) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return fallback;
        }
        String name = section.getString("NAME");
        return name == null ? fallback : name;
    }

    public static List<String> lore(ConfigFile config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section == null ? new ArrayList<>() : section.getStringList("LORE");
    }

    public static List<Integer> intList(ConfigFile config, String path) {
        return config.contains(path) ? config.getIntegerList(path) : new ArrayList<>();
    }

    public static String string(ConfigFile config, String path, String fallback) {
        return config.contains(path) ? config.getString(path, fallback) : fallback;
    }

    public static int integer(ConfigFile config, String path, int fallback) {
        return config.contains(path) ? config.getInt(path, fallback) : fallback;
    }

    public static String apply(String line, Object... placeholders) {
        if (line == null) {
            return "";
        }

        String result = line;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace(String.valueOf(placeholders[i]), String.valueOf(placeholders[i + 1]));
        }
        return org.glstudio.nexus.utils.CC.t(result);
    }

    public static List<String> apply(List<String> lines, Object... placeholders) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(apply(line, placeholders));
        }
        return result;
    }
}
