package org.glstudio.teleports.modules.manager;

import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.lang.LanguageFile;
import org.glstudio.nexus.modules.menu.chat.ChatInputManager;

import java.util.function.Consumer;

/**
 * Asks a player to type something, with this plugin's own texts and the word {@code cancel}.
 */
public class ChatPrompts {
    private static final String CANCEL_WORD = "cancel";

    private final ChatInputManager manager;
    private final LanguageFile messages;

    /**
     * Creates the service.
     *
     * @param manager  takes the player's next chat line
     * @param messages the language file
     */
    public ChatPrompts(ChatInputManager manager, LanguageFile messages) {
        this.manager = manager;
        this.messages = messages;
    }

    /**
     * Asks a question; cancelling only says so.
     *
     * @param player   who answers
     * @param prompt   the question
     * @param onSubmit receives the answer on the main thread
     */
    public void request(Player player, String prompt, Consumer<String> onSubmit) {
        request(player, prompt, onSubmit, null);
    }

    /**
     * Asks a question.
     *
     * @param player   who answers
     * @param prompt   the question
     * @param onSubmit receives the answer on the main thread
     * @param onCancel runs when the player types the cancel word, {@code null} to only say so
     */
    public void request(Player player, String prompt, Consumer<String> onSubmit, Runnable onCancel) {
        messages.send(player, "player-warp.input.prompt", "{prompt}", prompt);
        manager.request(player)
                .cancelWords(CANCEL_WORD)
                .onInput(onSubmit)
                .onCancel(onCancel != null ? onCancel : () -> messages.send(player, "player-warp.input.cancelled"))
                .start();
    }
}
