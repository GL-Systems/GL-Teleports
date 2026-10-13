package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.entity.Player;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;
import org.glstudio.nexus.modules.menu.Menu;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.teleports.modules.menu.MenuItems;
import org.glstudio.teleports.modules.model.PlayerWarp;

import java.util.HashMap;
import java.util.Map;

public class PlayerWarpRatingMenu extends Menu {
    private final GTeleports plugin;
    private final PlayerWarp warp;

    public PlayerWarpRatingMenu(GTeleports plugin, Player viewer, PlayerWarp warp) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.apply(MenuItems.string(plugin.getConfigManager().getPlayerWarpsRatingMenu(),
                        "title", "&8Rate: %name%"), "%name%", warp.getName())),
                MenuItems.integer(plugin.getConfigManager().getPlayerWarpsRatingMenu(), "size", 27));
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
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsRatingMenu();
        Map<Integer, Button> buttons = new HashMap<>();

        for (int stars = 1; stars <= 5; stars++) {
            int finalStars = stars;
            MenuItems.put(buttons, cfg, "items.star-" + stars, () -> promptComment(viewer, finalStars));
        }

        MenuItems.put(buttons, cfg, "items.back", () -> new PlayerWarpInfoMenu(plugin, viewer, warp).open());
        MenuItems.put(buttons, cfg, "items.close", viewer::closeInventory);

        MenuItems.putExtras(buttons, cfg, "extra");

        return buttons;
    }

    private void promptComment(Player viewer, int stars) {
        viewer.closeInventory();
        plugin.getChatInputManager().request(viewer,
                MenuItems.prompt(plugin, "player-warp.prompts.rating-comment", "{stars}", stars),
                comment -> plugin.getPlayerWarpRatingManager().rate(viewer, warp, stars,
                        comment.equalsIgnoreCase("skip") ? "" : comment));
    }
}
