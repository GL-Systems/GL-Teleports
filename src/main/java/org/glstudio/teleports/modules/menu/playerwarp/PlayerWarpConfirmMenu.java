package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.nexus.modules.menu.extra.ConfirmMenu;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.menu.MenuItems;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpCategory;

public final class PlayerWarpConfirmMenu {
    private PlayerWarpConfirmMenu() {
    }

    public static void open(GTeleports plugin, Player viewer, PlayerWarp warp, double cost, Runnable onConfirm) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsConfirmMenu();

        PlayerWarpCategory category = plugin.getPlayerWarpCategoryRegistry().get(warp.getCategoryId());
        Object[] placeholders = {
                "%name%", warp.getName(),
                "%owner%", warp.getOwnerName() == null ? "?" : warp.getOwnerName(),
                "%world%", warp.getWorld() == null ? "?" : warp.getWorld(),
                "%category%", category == null ? "-" : category.displayName(),
                "%cost%", plugin.getEconomyManager().formatCost(cost),
                "%balance%", plugin.getEconomyManager().formatCost(
                        plugin.getEconomyManager().getEconomy().getBalance(viewer))
        };

        ConfirmMenu.Builder builder = ConfirmMenu.builder(plugin.getMenuManager(), viewer)
                .title(MenuItems.apply(MenuItems.string(cfg, "title", "&8Teleport for %cost%?"), placeholders))
                .size(MenuItems.integer(cfg, "size", 27));

        int displaySlot = MenuItems.slot(cfg, "items.display");
        if (displaySlot >= 0) {
            ButtonBuilder display = item(cfg, "items.display", Material.GRASS_BLOCK, "&a%name%", placeholders);
            if (warp.getIcon() != null) {
                display.material(ButtonBuilder.parseMaterial(warp.getIcon(), Material.GRASS_BLOCK,
                        "player warp confirm"));
            }
            builder.display(displaySlot, display.toItemStack());
        }

        builder.confirm(MenuItems.slot(cfg, "items.confirm", 11),
                item(cfg, "items.confirm", Material.LIME_CONCRETE, "&a&lTELEPORT", placeholders).toItemStack(), onConfirm);
        builder.cancel(MenuItems.slot(cfg, "items.cancel", 15),
                item(cfg, "items.cancel", Material.RED_CONCRETE, "&c&lCANCEL", placeholders).toItemStack(), null);

        if (cfg.getConfigurationSection("extra") != null) {
            for (String key : cfg.getConfigurationSection("extra").getKeys(false)) {
                String path = "extra." + key;
                int slot = MenuItems.slot(cfg, path);
                if (slot >= 0) {
                    builder.extra(slot, item(cfg, path, Material.GRAY_STAINED_GLASS_PANE, " ", placeholders)
                            .toItemStack());
                }
            }
        }

        builder.open();
    }

    private static ButtonBuilder item(ConfigFile cfg, String path, Material fallbackMaterial, String fallbackName,
                                      Object[] placeholders) {
        if (cfg.getConfigurationSection(path) == null) {
            return ButtonBuilder.of(fallbackMaterial)
                    .name(MenuItems.apply(fallbackName, placeholders));
        }
        return MenuItems.builder(cfg, path)
                .name(MenuItems.apply(MenuItems.name(cfg, path, fallbackName), placeholders))
                .lore(MenuItems.apply(MenuItems.lore(cfg, path), placeholders));
    }
}
