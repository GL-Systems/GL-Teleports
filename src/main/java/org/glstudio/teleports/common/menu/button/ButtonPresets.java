package org.glstudio.teleports.common.menu.button;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class ButtonPresets {
    private ButtonPresets() {
    }

    public static Button close() {
        return Button.builder()
                .material(Material.BARRIER)
                .name(ChatColor.RED + "Close")
                .onClickPlayer(Player::closeInventory)
                .build();
    }

    public static Button nextPage(Runnable action) {
        return Button.builder()
                .material(Material.ARROW)
                .name(ChatColor.GREEN + "Next Page")
                .lore(ChatColor.GRAY + "Click to go to the next page")
                .onClickSimple(action)
                .build();
    }

    public static Button previousPage(Runnable action) {
        return Button.builder()
                .material(Material.ARROW)
                .name(ChatColor.GREEN + "Previous Page")
                .lore(ChatColor.GRAY + "Click to go to the previous page")
                .onClickSimple(action)
                .build();
    }

    public static Button confirm(Runnable action) {
        return Button.builder()
                .material(Material.EMERALD_BLOCK)
                .name(ChatColor.GREEN + "" + ChatColor.BOLD + "CONFIRM")
                .lore(ChatColor.GRAY + "Click to confirm")
                .onClickSimple(action)
                .build();
    }

    public static Button cancel(Runnable action) {
        return Button.builder()
                .material(Material.REDSTONE_BLOCK)
                .name(ChatColor.RED + "" + ChatColor.BOLD + "CANCEL")
                .lore(ChatColor.GRAY + "Click to cancel")
                .onClickSimple(action)
                .build();
    }

    public static Button glassFiller(Material paneMaterial) {
        return Button.filler(paneMaterial == null ? Material.BLACK_STAINED_GLASS_PANE : paneMaterial);
    }

    public static Button blackGlass() {
        return glassFiller(Material.BLACK_STAINED_GLASS_PANE);
    }

    public static Button info(String title, String... lore) {
        return Button.builder()
                .material(Material.BOOK)
                .name(ChatColor.AQUA + title)
                .lore(lore)
                .build();
    }

    public static Button loading() {
        return Button.builder()
                .material(Material.CLOCK)
                .name(ChatColor.YELLOW + "Loading...")
                .lore(ChatColor.GRAY + "Please wait")
                .hideAll()
                .build();
    }
}
