package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.nexus.modules.menu.config.MenuConfig;
import org.glstudio.nexus.modules.menu.extra.ConfirmMenu;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.menu.MenuItems;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpCategory;

/**
 * Asks to confirm paying to enter a warp.
 */
public final class PlayerWarpConfirmMenu {
    private PlayerWarpConfirmMenu() {
    }

    /**
     * Asks a player to confirm paying to enter a warp.
     */
    public static void open(GTeleports plugin, Player viewer, PlayerWarp warp, double cost, Runnable onConfirm) {
        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsConfirmMenu());

        PlayerWarpCategory category = plugin.getPlayerWarpCategoryRegistry().get(warp.getCategoryId());
        Object[] placeholders = {
                "%name%", warp.getName(),
                "%owner%", warp.getOwnerName() == null ? "?" : warp.getOwnerName(),
                "%world%", warp.getWorld() == null ? "?" : warp.getWorld(),
                "%category%", category == null ? "-" : category.displayName(),
                "%cost%", plugin.getEconomyManager().formatCost(cost),
                "%balance%", plugin.getEconomyManager().formatCost(
                        plugin.getEconomyManager().getBalance(viewer))
        };

        ConfirmMenu.Builder builder = ConfirmMenu.builder(plugin.getMenuManager(), viewer)
                .title(MenuItems.apply(cfg.string("title", "&8Teleport for %cost%?"), placeholders))
                .size(cfg.integer("size", 27));

        int displaySlot = cfg.slot("items.display");
        if (displaySlot >= 0) {
            ButtonBuilder display = item(cfg, "items.display", Material.GRASS_BLOCK, "&a%name%", placeholders);
            if (warp.getIcon() != null) {
                display.material(ButtonBuilder.parseMaterial(warp.getIcon(), Material.GRASS_BLOCK,
                        "player warp confirm"));
            }
            builder.display(displaySlot, display.toItemStack());
        }

        builder.confirm(cfg.slot("items.confirm", 11),
                item(cfg, "items.confirm", Material.LIME_CONCRETE, "&a&lTELEPORT", placeholders).toItemStack(), onConfirm);
        builder.cancel(cfg.slot("items.cancel", 15),
                item(cfg, "items.cancel", Material.RED_CONCRETE, "&c&lCANCEL", placeholders).toItemStack(), null);

        if (cfg.file().getConfigurationSection("extra") != null) {
            for (String key : cfg.file().getConfigurationSection("extra").getKeys(false)) {
                String path = "extra." + key;
                int slot = cfg.slot(path);
                if (slot >= 0) {
                    builder.extra(slot, item(cfg, path, Material.GRAY_STAINED_GLASS_PANE, " ", placeholders)
                            .toItemStack());
                }
            }
        }

        builder.open();
    }

    private static ButtonBuilder item(MenuConfig cfg, String path, Material fallbackMaterial, String fallbackName,
                                      Object[] placeholders) {
        if (cfg.file().getConfigurationSection(path) == null) {
            return ButtonBuilder.of(fallbackMaterial)
                    .name(MenuItems.apply(fallbackName, placeholders));
        }
        return cfg.builder(path)
                .name(MenuItems.apply(cfg.name(path, fallbackName), placeholders))
                .lore(MenuItems.apply(cfg.lore(path), placeholders));
    }
}
