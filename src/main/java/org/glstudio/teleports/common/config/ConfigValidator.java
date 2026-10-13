package org.glstudio.teleports.common.config;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.TeleportType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConfigValidator {
    private final GTeleports plugin;
    private final List<String> problems = new ArrayList<>();

    public ConfigValidator(GTeleports plugin) {
        this.plugin = plugin;
    }

    public int validate() {
        problems.clear();

        checkTeleportSettings();
        checkSafety();
        checkEffects();
        checkBackups();
        checkHistory();
        checkQueue();
        checkScopedHomeLimits();
        checkMigrate();

        for (String problem : problems) {
            LoggerUtils.logWarn("config.yml: " + problem);
        }

        if (!problems.isEmpty()) {
            LoggerUtils.logWarn("The settings above are being ignored or clamped. Everything else loaded "
                    + "normally.");
        }

        return problems.size();
    }

    private void checkTeleportSettings() {
        for (TeleportType type : TeleportType.values()) {
            String base = "teleport-settings." + type.getConfigKey();

            atLeast(base + ".delay", 0);
            atLeast(base + ".cooldown", 0);
            atLeast(base + ".immunity", 0);

            double cost = plugin.getSettings().getDouble(base + ".cost", 0);
            if (cost < 0) {
                problems.add(base + ".cost is negative (" + cost + "); it will be treated as free.");
            }
        }

        double threshold = plugin.getSettings().getDouble("teleport-settings.move-threshold", 0.5);
        if (threshold <= 0) {
            problems.add("teleport-settings.move-threshold is " + threshold
                    + "; any movement at all will cancel a teleport.");
        }

        atLeast("teleport-settings.tpa.request-timeout", 1);
        atLeast("teleport-settings.tpahere.request-timeout", 1);
        atLeast("teleport-settings.tpa.max-outgoing", 1);

        String priority = plugin.getSettings().getString("teleport-settings.tpa.priority", "recent");
        if (!priority.equalsIgnoreCase("recent") && !priority.equalsIgnoreCase("oldest")) {
            problems.add("teleport-settings.tpa.priority is '" + priority
                    + "'; expected 'recent' or 'oldest'. Falling back to 'recent'.");
        }
    }

    private void checkSafety() {
        if (plugin.getSettings().isSet("safety.min-height") && plugin.getSettings().isSet("safety.max-height")) {
            int min = plugin.getSettings().getInt("safety.min-height", Integer.MIN_VALUE);
            int max = plugin.getSettings().getInt("safety.max-height", Integer.MAX_VALUE);

            if (min > max) {
                problems.add("safety.min-height (" + min + ") is above safety.max-height (" + max
                        + "); every destination will be rejected.");
            }
        }

        int radius = plugin.getSettings().getInt("safety.check-radius", 1);
        if (radius < 0) {
            problems.add("safety.check-radius is negative; lava will not be checked.");
        } else if (radius > 4) {
            problems.add("safety.check-radius is " + radius + ", which inspects "
                    + ((2 * radius + 1) * (2 * radius + 1) * 4) + " blocks per teleport and will reject "
                    + "destinations merely near lava.");
        }

        for (String cause : plugin.getSettings().getStringList("safety.immunity-causes")) {
            try {
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.valueOf(cause.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                problems.add("safety.immunity-causes lists '" + cause + "', which is not a damage cause.");
            }
        }
    }

    private void checkEffects() {
        for (TeleportType type : TeleportType.values()) {
            for (String stage : new String[]{"delay-tick", "teleport"}) {
                String base = "effects." + type.getConfigKey() + "." + stage;

                checkSound(base + ".sound.type");
                checkEnum(base + ".particle.type", Particle.class);
                checkBossBarColour(base + ".bossbar.color");
            }
        }
    }

    private void checkBossBarColour(String path) {
        String value = plugin.getSettings().getString(path, null);
        if (value == null) {
            return;
        }

        try {
            net.kyori.adventure.bossbar.BossBar.Color.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add(path + " is '" + value + "', which is not a bossbar colour; GREEN will be used.");
        }
    }

    @SuppressWarnings({"deprecation", "removal"})
    private void checkSound(String path) {
        String value = plugin.getSettings().getString(path, null);
        if (value == null) {
            return;
        }

        try {
            Sound.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add(path + " is '" + value + "', which this server version does not know; "
                    + "that sound will not play.");
        }
    }

    private <E extends Enum<E>> void checkEnum(String path, Class<E> type) {
        String value = plugin.getSettings().getString(path, null);
        if (value == null) {
            return;
        }

        try {
            Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add(path + " is '" + value + "', which this server version does not know; "
                    + "that effect will not play.");
        }
    }

    private void checkBackups() {
        atLeast("backups.interval-minutes", 15);
        atLeast("backups.keep", 1);
    }

    private void checkHistory() {
        atLeast("history.retention-days", 1);
        atLeast("home-shares.max-per-home", 1);
        atLeast("tpa-blocks.max-entries", 1);

        boolean yaml = "yaml".equalsIgnoreCase(
                plugin.getConfigManager().getDatabaseConfig().getString("type", "yaml"));

        if (yaml && plugin.getSettings().getBoolean("history.enabled", true)
                && plugin.getSettings().getInt("history.retention-days", 30) > 90) {
            problems.add("history.retention-days is over 90 on the yaml backend, which reads the whole "
                    + "log file on every query. Shorten it, or move to mysql for the log to stay cheap.");
        }
    }

    private void checkQueue() {
        atLeast("queue.max-per-tick", 1);
        atLeast("queue.notice-threshold", 1);
    }

    private void checkScopedHomeLimits() {
        int global = plugin.getSettings().getInt("home-limits.default", 3);

        for (String scope : new String[]{"home-limits.per-world", "home-limits.per-server"}) {
            ConfigurationSection section = plugin.getConfigManager().getMainConfig()
                    .getConfigurationSection(scope);

            if (section == null) {
                continue;
            }

            for (String key : section.getKeys(false)) {
                int value = section.getInt(key);

                if (value < 0) {
                    problems.add(scope + "." + key + " is negative; use 0 to forbid homes there.");
                } else if (global >= 0 && value > global) {
                    problems.add(scope + "." + key + " is " + value + ", above the global limit of "
                            + global + ", so it can never apply. Raise home-limits.default or lower this.");
                }
            }
        }
    }

    private void checkMigrate() {
        String policy = plugin.getSettings().getString("migrate.conflict-policy", "skip");
        if (!List.of("skip", "rename", "replace").contains(policy.toLowerCase(Locale.ROOT))) {
            problems.add("migrate.conflict-policy is '" + policy + "'; expected skip, rename or replace. "
                    + "/glteleports migrate refuses to run until it is fixed.");
        }
    }

    private void atLeast(String path, int minimum) {
        if (!plugin.getSettings().isSet(path)) {
            return;
        }

        int value = plugin.getSettings().getInt(path, minimum);
        if (value < minimum) {
            problems.add(path + " is " + value + "; the lowest usable value is " + minimum + ".");
        }
    }

    public void reportUnknownKeys() {
        ConfigurationSection root = plugin.getConfigManager().getMainConfig().getConfigurationSection("");
        if (root == null) {
            return;
        }

        List<String> known = List.of("config-version", "multi-server", "MULTI_SERVER", "home-limits",
                "teleport-settings", "blacklist", "safety", "effects", "backups", "last-location",
                "menus", "history", "home-shares", "tpa-blocks", "cooldowns", "queue", "performance",
                "spawn", "back", "migrate", "clear-cooldowns-on-quit");

        for (String key : root.getKeys(false)) {
            if (!known.contains(key)) {
                LoggerUtils.logWarn("config.yml has a top-level '" + key + "' that this plugin does not "
                        + "read. Check it for a typo.");
            }
        }
    }
}
