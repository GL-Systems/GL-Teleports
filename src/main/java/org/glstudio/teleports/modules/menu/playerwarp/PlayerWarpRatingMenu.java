package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.menu.Menu;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.config.MenuConfig;
import org.glstudio.nexus.utils.CC;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.menu.MenuItems;
import org.glstudio.teleports.modules.model.PlayerWarp;

import java.util.HashMap;
import java.util.Map;

/**
 * Menu to rate a warp.
 */
public class PlayerWarpRatingMenu extends Menu {
    private final GTeleports plugin;
    private final PlayerWarp warp;

    /**
     * Creates the menu.
     */
    public PlayerWarpRatingMenu(GTeleports plugin, Player viewer, PlayerWarp warp) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.apply(new MenuConfig(plugin.getConfigManager().getPlayerWarpsRatingMenu()).string("title", "&8Rate: %name%"), "%name%", warp.getName())),
                new MenuConfig(plugin.getConfigManager().getPlayerWarpsRatingMenu()).integer("size", 27));
        this.plugin = plugin;
        this.warp = warp;
    }

    @Override
    protected void render() {
        clear();
        for (Map.Entry<Integer, Button> entry : getButtons(viewer).entrySet()) {
            place(entry.getKey(), entry.getValue());
        }
    }

    private Map<Integer, Button> getButtons(Player viewer) {
        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsRatingMenu());
        Map<Integer, Button> buttons = new HashMap<>();

        for (int stars = 1; stars <= 5; stars++) {
            int finalStars = stars;
            cfg.put(buttons, "items.star-" + stars, () -> promptComment(viewer, finalStars));
        }

        cfg.put(buttons, "items.back", () -> new PlayerWarpInfoMenu(plugin, viewer, warp).open());
        cfg.put(buttons, "items.close", viewer::closeInventory);

        cfg.putExtras(buttons, "extra");

        return buttons;
    }

    private void promptComment(Player viewer, int stars) {
        viewer.closeInventory();
        plugin.getChatPrompts().request(viewer,
                MenuItems.prompt(plugin, "player-warp.prompts.rating-comment", "{stars}", stars),
                comment -> plugin.getPlayerWarpRatingManager().rate(viewer, warp, stars,
                        comment.equalsIgnoreCase("skip") ? "" : comment));
    }
}
