package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;
import org.glstudio.nexus.modules.menu.Menu;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.nexus.modules.menu.extra.ConfirmMenu;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.menu.MenuItems;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpCategory;

import java.util.HashMap;
import java.util.Map;

public class PlayerWarpManageMenu extends Menu {
    private final GTeleports plugin;
    private final PlayerWarp warp;

    public PlayerWarpManageMenu(GTeleports plugin, Player viewer, PlayerWarp warp) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.apply(MenuItems.string(plugin.getConfigManager().getPlayerWarpsManageMenu(),
                        "title", "&8Manage: %name%"), "%name%", warp.getName())),
                MenuItems.integer(plugin.getConfigManager().getPlayerWarpsManageMenu(), "size", 45));
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
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsManageMenu();
        Map<Integer, Button> buttons = new HashMap<>();

        Object[] placeholders = {
                "%name%", warp.getName(),
                "%description%", warp.getDescription() == null ? "-" : warp.getDescription(),
                "%icon%", warp.getIcon() == null ? "default" : warp.getIcon(),
                "%category%", categoryName(),
                "%cost%", warp.isFree() ? plugin.getMessages().raw("placeholders.free")
                        : plugin.getEconomyManager().formatCost(warp.getCost()),
                "%lock-status%", warp.isLocked() ? "&clocked" : "&aunlocked",
                "%password-status%", warp.hasPassword() ? "&6set" : "&7none",
                "%whitelist-status%", warp.isWhitelistEnabled() ? "&eenabled" : "&7disabled"
        };

        put(buttons, cfg, "items.description", placeholders, event -> promptDescription(viewer));
        put(buttons, cfg, "items.icon", placeholders, event -> promptIcon(viewer));
        put(buttons, cfg, "items.category", placeholders,
                event -> new PlayerWarpCategoryMenu(plugin, viewer, warp).open());

        put(buttons, cfg, "items.cost", placeholders, event -> {
            if (event.getClick() == ClickType.RIGHT) {
                Failures.log(plugin.getPlayerWarpManager().setCost(viewer, warp.getName(), null)
                        .thenRun(() -> reopen(viewer)), "PlayerWarpManageMenu#removeCost");
                return;
            }
            promptCost(viewer);
        });

        put(buttons, cfg, "items.lock", placeholders, event ->
                Failures.log(plugin.getPlayerWarpManager().setLocked(viewer, warp.getName(), !warp.isLocked())
                        .thenRun(() -> reopen(viewer)), "PlayerWarpManageMenu#lock"));

        put(buttons, cfg, "items.password", placeholders, event -> {
            if (event.getClick() == ClickType.RIGHT) {
                Failures.log(plugin.getPlayerWarpManager().removePassword(viewer, warp.getName())
                        .thenRun(() -> reopen(viewer)), "PlayerWarpManageMenu#removePassword");
                return;
            }
            promptPassword(viewer);
        });

        put(buttons, cfg, "items.whitelist", placeholders,
                event -> new PlayerWarpWhitelistMenu(plugin, viewer, warp).open());
        put(buttons, cfg, "items.bans", placeholders, event -> new PlayerWarpBansMenu(plugin, viewer, warp).open());

        if (viewer.hasPermission(Permissions.PLAYERWARP_ADMIN_MANAGERS)) {
            put(buttons, cfg, "items.managers", placeholders,
                    event -> new PlayerWarpManagersMenu(plugin, viewer, warp).open());
        }

        if (warp.isOwner(viewer.getUniqueId()) || viewer.hasPermission(Permissions.PLAYERWARP_ADMIN_REMOVEALL)) {
            put(buttons, cfg, "items.transfer", placeholders,
                    event -> new PlayerWarpTransferMenu(plugin, viewer, warp).open());
        }

        put(buttons, cfg, "items.rename", placeholders, event -> promptRename(viewer));
        put(buttons, cfg, "items.reset", placeholders, event -> confirmReset(viewer, placeholders));
        put(buttons, cfg, "items.delete", placeholders, event -> confirmDelete(viewer, placeholders));

        MenuItems.put(buttons, cfg, "items.back",
                () -> new PlayerWarpListMenu(plugin, viewer, PlayerWarpListMenu.Mode.MINE, null).open());
        MenuItems.put(buttons, cfg, "items.close", viewer::closeInventory);

        MenuItems.putExtras(buttons, cfg, "extra");

        return buttons;
    }

    private String categoryName() {
        PlayerWarpCategory category = plugin.getPlayerWarpCategoryRegistry().get(warp.getCategoryId());
        return category == null ? "-" : category.displayName();
    }

    private void put(Map<Integer, Button> buttons, ConfigFile cfg, String path, Object[] placeholders,
                     java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> onClick) {
        int slot = MenuItems.slot(cfg, path);
        if (slot < 0) {
            return;
        }

        ButtonBuilder builder = MenuItems.builder(cfg, path);
        builder.name(MenuItems.apply(MenuItems.name(cfg, path, "&f?"), placeholders));
        builder.lore(MenuItems.apply(MenuItems.lore(cfg, path), placeholders));
        builder.onClick(onClick::accept);

        buttons.put(slot, builder.build());
    }

    private void reopen(Player viewer) {
        Failures.log(plugin.getPlayerWarpManager().getWarp(warp.getId()).thenAccept(fresh ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    if (fresh == null) {
                        viewer.closeInventory();
                        return;
                    }
                    new PlayerWarpManageMenu(plugin, viewer, fresh).open();
                })), "PlayerWarpManageMenu#reopen");
    }

    private void promptDescription(Player viewer) {
        viewer.closeInventory();
        plugin.getChatInputManager().request(viewer,
                MenuItems.prompt(plugin, "player-warp.prompts.description", "{name}", warp.getName()),
                text -> Failures.log(plugin.getPlayerWarpManager().setDescription(viewer, warp.getName(), text)
                        .thenRun(() -> reopen(viewer)), "PlayerWarpManageMenu#desc"),
                () -> reopen(viewer));
    }

    private void promptIcon(Player viewer) {
        viewer.closeInventory();
        plugin.getChatInputManager().request(viewer,
                MenuItems.prompt(plugin, "player-warp.prompts.icon", "{name}", warp.getName()),
                text -> Failures.log(plugin.getPlayerWarpManager().setIcon(viewer, warp.getName(), text)
                        .thenRun(() -> reopen(viewer)), "PlayerWarpManageMenu#icon"),
                () -> reopen(viewer));
    }

    private void promptCost(Player viewer) {
        viewer.closeInventory();
        plugin.getChatInputManager().request(viewer,
                MenuItems.prompt(plugin, "player-warp.prompts.cost", "{name}", warp.getName()), text -> {
            try {
                double cost = Double.parseDouble(text.trim());
                Failures.log(plugin.getPlayerWarpManager().setCost(viewer, warp.getName(), cost)
                        .thenRun(() -> reopen(viewer)), "PlayerWarpManageMenu#cost");
            } catch (NumberFormatException e) {
                plugin.getMessages().send(viewer, "errors.invalid-amount", "{value}", text);
                reopen(viewer);
            }
        }, () -> reopen(viewer));
    }

    private void promptPassword(Player viewer) {
        viewer.closeInventory();
        plugin.getChatInputManager().request(viewer,
                MenuItems.prompt(plugin, "player-warp.prompts.password", "{name}", warp.getName()),
                text -> Failures.log(plugin.getPlayerWarpManager().setPassword(viewer, warp.getName(), text)
                        .thenRun(() -> reopen(viewer)), "PlayerWarpManageMenu#password"),
                () -> reopen(viewer));
    }

    private void promptRename(Player viewer) {
        viewer.closeInventory();
        plugin.getChatInputManager().request(viewer,
                MenuItems.prompt(plugin, "player-warp.prompts.rename", "{name}", warp.getName()),
                text -> Failures.log(plugin.getPlayerWarpManager().renameWarp(viewer, warp.getName(), text)
                        .thenRun(() -> reopen(viewer)), "PlayerWarpManageMenu#rename"),
                () -> reopen(viewer));
    }

    private void confirmReset(Player viewer, Object[] placeholders) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsManageMenu();

        ConfirmMenu.confirm(plugin.getMenuManager(), viewer,
                MenuItems.apply(MenuItems.string(cfg, "items.confirm-reset.title", "&8Move here?"), placeholders),
                new ButtonBuilder().material(Material.COMPASS).name(warp.getName()).build(),
                MenuItems.apply(MenuItems.string(cfg, "items.confirm-reset.accept", "&a&lMOVE"), placeholders),
                MenuItems.apply(cfg.getStringList("items.confirm-reset.accept-lore"), placeholders),
                MenuItems.apply(MenuItems.string(cfg, "items.confirm-reset.cancel", "&c&lCANCEL"), placeholders),
                ButtonBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").toItemStack(),
                () -> Failures.log(plugin.getPlayerWarpManager().resetLocation(viewer, warp.getName())
                        .thenRun(() -> reopen(viewer)), "PlayerWarpManageMenu#reset"),
                () -> reopen(viewer)
        );
    }

    private void confirmDelete(Player viewer, Object[] placeholders) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsManageMenu();

        ConfirmMenu.confirm(plugin.getMenuManager(), viewer,
                MenuItems.apply(MenuItems.string(cfg, "items.confirm-delete.title", "&8Delete?"), placeholders),
                new ButtonBuilder().material(Material.GRASS_BLOCK).name(warp.getName()).build(),
                MenuItems.apply(MenuItems.string(cfg, "items.confirm-delete.accept", "&c&lDELETE"), placeholders),
                MenuItems.apply(cfg.getStringList("items.confirm-delete.accept-lore"), placeholders),
                MenuItems.apply(MenuItems.string(cfg, "items.confirm-delete.cancel", "&a&lKEEP IT"), placeholders),
                ButtonBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").toItemStack(),
                () -> plugin.getPlayerWarpManager().removeWarp(viewer, warp.getName())
                        .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> viewer.closeInventory())),
                () -> reopen(viewer)
        );
    }
}
