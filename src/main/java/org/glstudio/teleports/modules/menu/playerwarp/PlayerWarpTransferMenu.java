package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.nexus.modules.menu.config.MenuConfig;
import org.glstudio.nexus.modules.menu.extra.ConfirmMenu;
import org.glstudio.nexus.modules.menu.paginated.PaginatedMenu;
import org.glstudio.nexus.utils.CC;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.menu.MenuItems;
import org.glstudio.teleports.modules.model.PlayerWarp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu to hand a warp to another player.
 */
public class PlayerWarpTransferMenu extends PaginatedMenu<Button> {
    private final GTeleports plugin;
    private final PlayerWarp warp;

    /**
     * Creates the menu.
     */
    public PlayerWarpTransferMenu(GTeleports plugin, Player viewer, PlayerWarp warp) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.apply(new MenuConfig(plugin.getConfigManager().getPlayerWarpsTransferMenu()).string("title", "&8Transfer: %name%"), "%name%", warp.getName())),
                new MenuConfig(plugin.getConfigManager().getPlayerWarpsTransferMenu()).integer("size", 54));
        this.plugin = plugin;
        this.warp = warp;
        setAutoRefresh(true);
    }

    @Override
    protected List<Integer> getContentSlots() {
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsTransferMenu()).intList("content-slots");
    }

    @Override
    protected List<Button> getItems(Player viewer) {
        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsTransferMenu());
        List<Button> items = new ArrayList<>();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewer.getUniqueId()) || !viewer.canSee(online)) {
                continue;
            }

            ButtonBuilder builder = cfg.builder("items.player");
            builder.skullOwner(online);
            builder.name(MenuItems.apply(cfg.name("items.player", "&e%player%"),
                    "%player%", online.getName()));
            builder.lore(MenuItems.apply(cfg.lore("items.player"), "%player%", online.getName()));
            builder.onClickSimple(() -> confirm(viewer, online.getName()));

            items.add(builder.build());
        }

        return items;
    }

    @Override
    protected Button toButton(Button item) {
        return item;
    }

    @Override
    protected Button getEmptyButton(Player viewer) {
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsTransferMenu()).builder("items.empty").build();
    }

    @Override
    protected int getEmptySlot(Player viewer) {
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsTransferMenu()).slot("items.empty");
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int currentPage, int maxPage) {
        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsTransferMenu());
        Map<Integer, Button> buttons = new HashMap<>();

        if (currentPage > 0) {
            cfg.put(buttons, "items.previous-page", this::previousPage);
        }
        if (currentPage < maxPage) {
            cfg.put(buttons, "items.next-page", this::nextPage);
        }

        cfg.put(buttons, "items.back", () -> new PlayerWarpManageMenu(plugin, viewer, warp).open());
        cfg.put(buttons, "items.close", viewer::closeInventory);

        cfg.putExtras(buttons, "extra");

        return buttons;
    }

    private void confirm(Player viewer, String targetName) {
        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsTransferMenu());
        Object[] placeholders = {"%name%", warp.getName(), "%player%", targetName};

        ConfirmMenu.confirm(plugin.getMenuManager(), viewer,
                MenuItems.apply(cfg.string("items.confirm.title", "&8Transfer?"), placeholders),
                new ButtonBuilder().material(org.bukkit.Material.NAME_TAG).name(warp.getName()).build(),
                MenuItems.apply(cfg.string("items.confirm.accept", "&a&lTRANSFER"), placeholders),
                MenuItems.apply(cfg.file().getStringList("items.confirm.accept-lore"), placeholders),
                MenuItems.apply(cfg.string("items.confirm.cancel", "&c&lCANCEL"), placeholders),
                ButtonBuilder.of(org.bukkit.Material.GRAY_STAINED_GLASS_PANE).name(" ").toItemStack(),
                () -> plugin.getPlayerWarpManager().transferOwner(viewer, warp.getName(), targetName)
                        .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> viewer.closeInventory())),
                () -> new PlayerWarpTransferMenu(plugin, viewer, warp).open()
        );
    }
}
