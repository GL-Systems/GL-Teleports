package org.glstudio.teleports.modules.manager;

import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Captures a player's next chat message as free-text input for a GUI flow (a description, a
 * password, a rating comment, ...) instead of the message being broadcast. There is no inventory
 * variant of "type some text" in this plugin's menu framework, so every such flow closes its
 * inventory, prompts here, and gets a callback on the main thread with what the player typed.
 */
public class ChatInputManager {
    public record PendingInput(String prompt, Consumer<String> onSubmit, Runnable onCancel) {
    }

    private final GTeleports plugin;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public ChatInputManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, String prompt, Consumer<String> onSubmit) {
        request(player, prompt, onSubmit, null);
    }

    public void request(Player player, String prompt, Consumer<String> onSubmit, Runnable onCancel) {
        pending.put(player.getUniqueId(), new PendingInput(prompt, onSubmit, onCancel));
        plugin.getMessages().send(player, "player-warp.input.prompt", "{prompt}", prompt);
    }

    public boolean isAwaiting(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public PendingInput poll(UUID uuid) {
        return pending.remove(uuid);
    }

    public void cancel(UUID uuid) {
        PendingInput input = pending.remove(uuid);
        if (input != null && input.onCancel() != null) {
            input.onCancel().run();
        }
    }

    public void safeDisable() {
        pending.clear();
    }
}
