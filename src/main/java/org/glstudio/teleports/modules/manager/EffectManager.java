package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.TeleportType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

public class EffectManager {
    private final GTeleports plugin;
    private final Map<UUID, BossBar> activeBossBars;
    private final MiniMessage miniMessage;

    public EffectManager(GTeleports plugin) {
        this.plugin = plugin;
        this.activeBossBars = new ConcurrentHashMap<>();
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void clearBossBar(Player player) {
        BossBar bossBar = activeBossBars.remove(player.getUniqueId());

        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    public enum EffectStage {
        DELAY_START,

        DELAY_TICK,

        TELEPORT,

        TELEPORT_ARRIVE,

        COOLDOWN_START,

        CANCELLED
    }

    public void playTeleportEffects(Player player, TeleportType type, EffectStage stage,
                                    String destinationName) {
        String basePath = basePath(type, stage);

        boolean defaultEnabled = stage == EffectStage.DELAY_TICK || stage == EffectStage.TELEPORT;

        if (!plugin.getSettings().getBoolean(basePath + ".enabled", defaultEnabled)) {
            return;
        }

        playSound(player, basePath);
        playParticles(player, basePath);
        playTitle(player, basePath, destinationName);
        playActionBar(player, basePath, destinationName);
    }

    private static String basePath(TeleportType type, EffectStage stage) {
        return "effects." + type.getConfigKey() + "."
                + stage.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    public void playDelayEffects(Player player, TeleportType type, int remainingSeconds, int totalSeconds,
                                 String destinationName) {
        String basePath = "effects." + type.getConfigKey() + ".delay-tick";

        if (!plugin.getSettings().getBoolean(basePath + ".enabled", true)) {
            return;
        }

        if (plugin.getSettings().getBoolean(basePath + ".sound.enabled", true)) {
            String soundName = plugin.getSettings().getString(basePath + ".sound.type", "BLOCK_NOTE_BLOCK_PLING");
            float volume = (float) plugin.getSettings().getDouble(basePath + ".sound.volume", 1.0);
            float pitch = (float) plugin.getSettings().getDouble(basePath + ".sound.pitch", 1.0);

            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {
            }
        }

        playParticles(player, basePath);

        String actionBarText = plugin.getSettings().getString(basePath + ".actionbar.text", "");
        if (actionBarText != null && !actionBarText.isEmpty()) {
            actionBarText = actionBarText.replace("{time}", String.valueOf(remainingSeconds));
            Component component = miniMessage.deserialize(applyName(actionBarText, destinationName));
            player.sendActionBar(component);
        }

        updateBossBar(player, basePath, remainingSeconds, totalSeconds, destinationName);
    }

    private void playSound(Player player, String basePath) {
        if (!plugin.getSettings().getBoolean(basePath + ".sound.enabled", false)) {
            return;
        }

        String soundName = plugin.getSettings().getString(basePath + ".sound.type", "ENTITY_ENDERMAN_TELEPORT");
        float volume = (float) plugin.getSettings().getDouble(basePath + ".sound.volume", 1.0);
        float pitch = (float) plugin.getSettings().getDouble(basePath + ".sound.pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void playParticles(Player player, String basePath) {
        if (!plugin.getSettings().getBoolean(basePath + ".particle.enabled", false)) {
            return;
        }

        String particleName = plugin.getSettings().getString(basePath + ".particle.type", "PORTAL");
        int count = plugin.getSettings().getInt(basePath + ".particle.count", 20);

        try {
            Particle particle = Particle.valueOf(particleName);
            Location loc = player.getLocation().clone().add(0, 1, 0);
            player.getWorld().spawnParticle(particle, loc, count, 0.5, 0.5, 0.5, 0.1);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void playTitle(Player player, String basePath, String destinationName) {
        if (!plugin.getSettings().getBoolean(basePath + ".title.enabled", false)) {
            return;
        }

        String titleText = applyName(
                plugin.getSettings().getString(basePath + ".title.text", ""), destinationName);
        String subtitleText = applyName(
                plugin.getSettings().getString(basePath + ".title.subtitle", ""), destinationName);

        if (titleText.isEmpty() && subtitleText.isEmpty()) {
            return;
        }

        Component title = titleText.isEmpty() ? Component.empty() : miniMessage.deserialize(titleText);
        Component subtitle = subtitleText.isEmpty() ? Component.empty() : miniMessage.deserialize(subtitleText);

        int fadeIn = plugin.getSettings().getInt(basePath + ".title.fade-in", 10);
        int stay = plugin.getSettings().getInt(basePath + ".title.stay", 70);
        int fadeOut = plugin.getSettings().getInt(basePath + ".title.fade-out", 20);

        Title titleObj = Title.title(
                title,
                subtitle,
                Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L)
                )
        );

        player.showTitle(titleObj);
    }

    private void playActionBar(Player player, String basePath, String destinationName) {
        if (!plugin.getSettings().getBoolean(basePath + ".actionbar.enabled", false)) {
            return;
        }

        String text = applyName(plugin.getSettings().getString(basePath + ".actionbar.text", ""), destinationName);
        if (text.isEmpty()) {
            return;
        }

        Component component = miniMessage.deserialize(text);
        player.sendActionBar(component);
    }

    private void updateBossBar(Player player, String basePath, int remainingSeconds, int totalSeconds,
                               String destinationName) {
        if (!plugin.getSettings().getBoolean(basePath + ".bossbar.enabled", false)) {
            return;
        }

        BossBar bossBar = activeBossBars.get(player.getUniqueId());

        if (remainingSeconds <= 0) {
            clearBossBar(player);
            return;
        }

        String text = plugin.getSettings().getString(basePath + ".bossbar.text", "Teleporting in {time}s");
        text = applyName(text.replace("{time}", String.valueOf(remainingSeconds)), destinationName);

        float progress = totalSeconds <= 0 ? 1.0f
                : Math.max(0.0f, Math.min(1.0f, (float) remainingSeconds / totalSeconds));

        String colorName = plugin.getSettings().getString(basePath + ".bossbar.color", "GREEN");
        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            color = BossBar.Color.GREEN;
        }

        if (bossBar == null) {
            bossBar = BossBar.bossBar(miniMessage.deserialize(text), progress, color, BossBar.Overlay.PROGRESS);
            activeBossBars.put(player.getUniqueId(), bossBar);
            player.showBossBar(bossBar);
        } else {
            bossBar.name(miniMessage.deserialize(text));
            bossBar.progress(progress);
        }
    }

    private static String applyName(String text, String destinationName) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return text.replace("{name}", destinationName == null ? "" : destinationName);
    }

    public void safeDisable() {
        activeBossBars.forEach((uuid, bossBar) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.hideBossBar(bossBar);
            }
        });
        activeBossBars.clear();
    }
}
