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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EffectManager {

    private final GTeleports plugin;
    private final Map<UUID, BossBar> activeBossBars;
    private final MiniMessage miniMessage;

    public EffectManager(GTeleports plugin) {
        this.plugin = plugin;
        this.activeBossBars = new HashMap<>();
        this.miniMessage = MiniMessage.miniMessage();
    }

    public enum EffectStage {
        DELAY_START,
        DELAY_TICK,
        TELEPORT,
        COOLDOWN_START
    }

    public void playTeleportEffects(Player player, TeleportType type, EffectStage stage) {
        String basePath = "effects." + type.getConfigKey() + "." + stage.name().toLowerCase().replace("_", "-");

        if (!plugin.getConfigManager().getMainConfig().getBoolean(basePath + ".enabled", true)) {
            return;
        }

        playSound(player, basePath);
        playParticles(player, basePath);
        playTitle(player, basePath);
        playActionBar(player, basePath);

        if (stage == EffectStage.DELAY_TICK) {
            updateBossBar(player, basePath, 0);
        }
    }

    public void playDelayEffects(Player player, TeleportType type, int remainingSeconds) {
        String basePath = "effects." + type.getConfigKey() + ".delay-tick";

        if (!plugin.getConfigManager().getMainConfig().getBoolean(basePath + ".enabled", true)) {
            return;
        }

        if (plugin.getConfigManager().getMainConfig().getBoolean(basePath + ".sound.enabled", true)) {
            String soundName = plugin.getConfigManager().getMainConfig().getString(basePath + ".sound.type", "BLOCK_NOTE_BLOCK_PLING");
            float volume = (float) plugin.getConfigManager().getMainConfig().getDouble(basePath + ".sound.volume", 1.0);
            float pitch = (float) plugin.getConfigManager().getMainConfig().getDouble(basePath + ".sound.pitch", 1.0);

            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {
            }
        }

        playParticles(player, basePath);

        String actionBarText = plugin.getConfigManager().getMainConfig().getString(basePath + ".actionbar.text");
        if (actionBarText != null && !actionBarText.isEmpty()) {
            actionBarText = actionBarText.replace("{time}", String.valueOf(remainingSeconds));
            Component component = miniMessage.deserialize(actionBarText);
            player.sendActionBar(component);
        }

        updateBossBar(player, basePath, remainingSeconds);
    }

    private void playSound(Player player, String basePath) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean(basePath + ".sound.enabled", false)) {
            return;
        }

        String soundName = plugin.getConfigManager().getMainConfig().getString(basePath + ".sound.type", "ENTITY_ENDERMAN_TELEPORT");
        float volume = (float) plugin.getConfigManager().getMainConfig().getDouble(basePath + ".sound.volume", 1.0);
        float pitch = (float) plugin.getConfigManager().getMainConfig().getDouble(basePath + ".sound.pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void playParticles(Player player, String basePath) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean(basePath + ".particle.enabled", false)) {
            return;
        }

        String particleName = plugin.getConfigManager().getMainConfig().getString(basePath + ".particle.type", "PORTAL");
        int count = plugin.getConfigManager().getMainConfig().getInt(basePath + ".particle.count", 20);

        try {
            Particle particle = Particle.valueOf(particleName);
            Location loc = player.getLocation().clone().add(0, 1, 0);
            player.getWorld().spawnParticle(particle, loc, count, 0.5, 0.5, 0.5, 0.1);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void playTitle(Player player, String basePath) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean(basePath + ".title.enabled", false)) {
            return;
        }

        String titleText = plugin.getConfigManager().getMainConfig().getString(basePath + ".title.text", "");
        String subtitleText = plugin.getConfigManager().getMainConfig().getString(basePath + ".title.subtitle", "");

        if (titleText.isEmpty() && subtitleText.isEmpty()) {
            return;
        }

        Component title = titleText.isEmpty() ? Component.empty() : miniMessage.deserialize(titleText);
        Component subtitle = subtitleText.isEmpty() ? Component.empty() : miniMessage.deserialize(subtitleText);

        int fadeIn = plugin.getConfigManager().getMainConfig().getInt(basePath + ".title.fade-in", 10);
        int stay = plugin.getConfigManager().getMainConfig().getInt(basePath + ".title.stay", 70);
        int fadeOut = plugin.getConfigManager().getMainConfig().getInt(basePath + ".title.fade-out", 20);

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

    private void playActionBar(Player player, String basePath) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean(basePath + ".actionbar.enabled", false)) {
            return;
        }

        String text = plugin.getConfigManager().getMainConfig().getString(basePath + ".actionbar.text", "");
        if (text.isEmpty()) {
            return;
        }

        Component component = miniMessage.deserialize(text);
        player.sendActionBar(component);
    }

    private void updateBossBar(Player player, String basePath, int remainingSeconds) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean(basePath + ".bossbar.enabled", false)) {
            return;
        }

        BossBar bossBar = activeBossBars.get(player.getUniqueId());

        if (remainingSeconds <= 0) {
            if (bossBar != null) {
                player.hideBossBar(bossBar);
                activeBossBars.remove(player.getUniqueId());
            }
            return;
        }

        String text = plugin.getConfigManager().getMainConfig().getString(basePath + ".bossbar.text", "Teleporting in {time}s");
        text = text.replace("{time}", String.valueOf(remainingSeconds));

        String colorName = plugin.getConfigManager().getMainConfig().getString(basePath + ".bossbar.color", "GREEN");
        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            color = BossBar.Color.GREEN;
        }

        if (bossBar == null) {
            bossBar = BossBar.bossBar(
                    miniMessage.deserialize(text),
                    1.0f,
                    color,
                    BossBar.Overlay.PROGRESS
            );
            activeBossBars.put(player.getUniqueId(), bossBar);
            player.showBossBar(bossBar);
        } else {
            bossBar.name(miniMessage.deserialize(text));
        }
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
