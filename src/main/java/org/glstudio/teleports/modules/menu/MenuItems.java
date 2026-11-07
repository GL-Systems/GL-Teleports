package org.glstudio.teleports.modules.menu;

import org.glstudio.nexus.modules.menu.placeholder.Placeholders;
import org.glstudio.nexus.utils.CC;
import org.glstudio.teleports.GTeleports;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills the placeholders of menu texts and translates their colours.
 */
public final class MenuItems {

    private MenuItems() {
    }

    /**
     * Builds the text of a chat prompt.
     *
     * @param plugin       the owning plugin
     * @param languageKey  the key in {@code language.yml}
     * @param placeholders token and value pairs
     * @return the text, empty when the key is missing
     */
    public static String prompt(GTeleports plugin, String languageKey, Object... placeholders) {
        return apply(plugin.getMessages().raw(languageKey), placeholders);
    }

    /**
     * Fills a line.
     *
     * @param line         the line
     * @param placeholders token and value pairs
     * @return the line, empty when there is none
     */
    public static String apply(String line, Object... placeholders) {
        return line == null ? "" : CC.t(Placeholders.apply(line, placeholders));
    }

    /**
     * Fills several lines.
     *
     * @param lines        the lines
     * @param placeholders token and value pairs
     * @return the lines
     */
    public static List<String> apply(List<String> lines, Object... placeholders) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(apply(line, placeholders));
        }
        return result;
    }
}
