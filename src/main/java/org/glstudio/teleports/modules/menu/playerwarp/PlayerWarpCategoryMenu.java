package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;
import org.glstudio.nexus.modules.menu.Menu;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.modules.menu.MenuItems;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpCategory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Doubles as the "browse by category" screen and, when {@code assignTarget} is set, the
 * "choose a category for this warp" screen opened from {@link PlayerWarpManageMenu}.
 */
public class PlayerWarpCategoryMenu extends Menu {
    private final GTeleports plugin;
    private final PlayerWarp assignTarget;

    public PlayerWarpCategoryMenu(GTeleports plugin, Player viewer, PlayerWarp assignTarget) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.string(plugin.getConfigManager().getPlayerWarpsCategoriesMenu(),
                        "title." + (assignTarget == null ? "browse" : "assign"), "&8Categories")),
                MenuItems.integer(plugin.getConfigManager().getPlayerWarpsCategoriesMenu(), "size", 27));
        this.plugin = plugin;
        this.assignTarget = assignTarget;
    }

    @Override
    protected void render() {
        clear();
        for (Map.Entry<Integer, Button> entry : getButtons(viewer).entrySet()) {
            place(entry.getKey(), entry.getValue());
        }
    }

    private Map<Integer, Button> getButtons(Player viewer) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsCategoriesMenu();
        Map<Integer, Button> buttons = new HashMap<>();

        List<Integer> slots = MenuItems.intList(cfg, "content-slots");
        List<PlayerWarpCategory> categories = plugin.getPlayerWarpCategoryRegistry().all();

        if (assignTarget != null) {
            int noneSlot = MenuItems.slot(cfg, "items.none");
            if (noneSlot >= 0) {
                buttons.put(noneSlot, MenuItems.builder(cfg, "items.none")
                        .onClickSimple(() -> assign(viewer, null))
                        .build());
            }
        }

        int index = 0;
        for (PlayerWarpCategory category : categories) {
            if (index >= slots.size()) {
                break;
            }

            int slot = slots.get(index++);
            Object[] placeholders = {"%name%", category.displayName(), "%description%", category.description()};

            Material material = ButtonBuilder.parseMaterial(category.icon(), Material.PAPER,
                    "player-warps.yml categories." + category.id());

            ButtonBuilder builder = MenuItems.builder(cfg, "items.category").material(material);
            builder.name(MenuItems.apply(category.displayName()));
            builder.lore(MenuItems.apply(MenuItems.lore(cfg, "items.category"), placeholders));

            builder.onClickSimple(assignTarget == null
                    ? () -> new PlayerWarpListMenu(plugin, viewer, PlayerWarpListMenu.Mode.CATEGORY,
                            category.id()).open()
                    : () -> assign(viewer, category.id()));

            buttons.put(slot, builder.build());
        }

        MenuItems.put(buttons, cfg, "items.back", () -> {
            if (assignTarget != null) {
                new PlayerWarpManageMenu(plugin, viewer, assignTarget).open();
            } else {
                new PlayerWarpMainMenu(plugin, viewer).open();
            }
        });
        MenuItems.put(buttons, cfg, "items.close", viewer::closeInventory);

        MenuItems.putExtras(buttons, cfg, "extra");

        return buttons;
    }

    private void assign(Player viewer, String categoryId) {
        Failures.log(plugin.getPlayerWarpManager().setCategory(viewer, assignTarget.getName(), categoryId)
                .thenCompose(ignored -> plugin.getPlayerWarpManager().getWarp(assignTarget.getId()))
                .thenAccept(fresh -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    new PlayerWarpManageMenu(plugin, viewer, fresh == null ? assignTarget : fresh).open();
                })), "PlayerWarpCategoryMenu#assign");
    }
}
