package org.glstudio.teleports.core;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class Messages {
    private final GTeleports plugin;

    private final Map<String, Optional<String>> resolvedFallbacks = new ConcurrentHashMap<>();

    public Messages(GTeleports plugin) {
        this.plugin = plugin;
    }

    public void send(CommandSender who, String path, String... placeholders) {
        if (who == null) {
            return;
        }

        String raw = raw(path);
        if (raw == null || raw.isBlank()) {
            return;
        }

        who.sendMessage(CC.miniMessage(prefix() + apply(raw, placeholders)));
    }

    private String prefix() {
        String prefix = plugin.getConfigManager().getLanguageConfig().getString("prefix", "");
        return prefix == null ? "" : prefix;
    }

    public Component component(String path, String... placeholders) {
        String raw = raw(path);
        return raw == null ? Component.empty() : CC.miniMessage(apply(raw, placeholders));
    }

    public String raw(String path) {
        ConfigFile language = plugin.getConfigManager().getLanguageConfig();

        String value = language.getString(path, null);
        if (value != null) {
            return value;
        }

        return resolvedFallbacks
                .computeIfAbsent(path, key -> Optional.ofNullable(language.getString(key)))
                .orElse(null);
    }

    public void reset() {
        resolvedFallbacks.clear();
    }

    private static String apply(String message, String... placeholders) {
        if (placeholders.length == 0) {
            return message;
        }

        String result = message;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace(placeholders[i], String.valueOf(placeholders[i + 1]));
        }
        return result;
    }
}
