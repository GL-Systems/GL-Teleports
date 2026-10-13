package org.glstudio.teleports.modules.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.manager.ChatInputManager;

public class ChatInputListener implements Listener {
    private final GTeleports plugin;

    public ChatInputListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getChatInputManager().isAwaiting(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        ChatInputManager.PendingInput input = plugin.getChatInputManager().poll(player.getUniqueId());
        if (input == null) {
            return;
        }

        if (message.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (input.onCancel() != null) {
                    input.onCancel().run();
                } else {
                    plugin.getMessages().send(player, "player-warp.input.cancelled");
                }
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> input.onSubmit().accept(message));
    }
}
