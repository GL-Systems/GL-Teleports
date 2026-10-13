package org.glstudio.teleports.modules.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;

/** The "still loading" placeholder shown by every paginated player-warp screen while it fetches. */
public final class ButtonPresets {
    private ButtonPresets() {
    }

    public static Button loading() {
        return ButtonBuilder.of(Material.CLOCK)
                .name(ChatColor.YELLOW + "Loading...")
                .lore(ChatColor.GRAY + "Please wait")
                .hideAll()
                .build();
    }
}
