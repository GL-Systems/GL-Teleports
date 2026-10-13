package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.nexus.modules.menu.extra.ConfirmMenu;
import org.glstudio.nexus.modules.menu.paginated.PaginatedMenu;
import org.glstudio.teleports.modules.menu.MenuItems;
import org.glstudio.teleports.modules.model.PlayerWarp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerWarpTransferMenu extends PaginatedMenu<Button> {
    private final GTeleports plugin;
    private final PlayerWarp warp;

    public PlayerWarpTransferMenu(GTeleports plugin, Player viewer, PlayerWarp warp) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.apply(MenuItems.string(plugin.getConfigManager().getPlayerWarpsTransferMenu(),
                        "title", "&8Transfer: %name%"), "%name%", warp.getName())),
                MenuItems.integer(plugin.getConfigManager().getPlayerWarpsTransferMenu(), "size", 54));
        this.plugin = plugin;
        this.warp = warp;
        setAutoRefresh(true);
    }

    @Override
    protected List<Integer> getContentSlots() {
        return MenuItems.intList(plugin.getConfigManager().getPlayerWarpsTransferMenu(), "content-slots");
    }

    @Override
    protected List<Button> getItems(Player viewer) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsTransferMenu();
        List<Button> items = new ArrayList<>();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewer.getUniqueId()) || !viewer.canSee(online)) {
                continue;
            }

            ButtonBuilder builder = MenuItems.builder(cfg, "items.player");
            builder.skullOwner(online);
            builder.name(MenuItems.apply(MenuItems.name(cfg, "items.player", "&e%player%"),
                    "%player%", online.getName()));
            builder.lore(MenuItems.apply(MenuItems.lore(cfg, "items.player"), "%player%", online.getName()));
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
        return MenuItems.builder(plugin.getConfigManager().getPlayerWarpsTransferMenu(), "items.empty").build();
    }

    @Override
    protected int getEmptySlot(Player viewer) {
        return MenuItems.slot(plugin.getConfigManager().getPlayerWarpsTransferMenu(), "items.empty");
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int currentPage, int maxPage) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsTransferMenu();
        Map<Integer, Button> buttons = new HashMap<>();

        if (currentPage > 0) {
            MenuItems.put(buttons, cfg, "items.previous-page", this::previousPage);
        }
        if (currentPage < maxPage) {
            MenuItems.put(buttons, cfg, "items.next-page", this::nextPage);
        }

        MenuItems.put(buttons, cfg, "items.back", () -> new PlayerWarpManageMenu(plugin, viewer, warp).open());
        MenuItems.put(buttons, cfg, "items.close", viewer::closeInventory);

        MenuItems.putExtras(buttons, cfg, "extra");

        return buttons;
    }

    private void confirm(Player viewer, String targetName) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsTransferMenu();
        Object[] placeholders = {"%name%", warp.getName(), "%player%", targetName};

        ConfirmMenu.confirm(plugin.getMenuManager(), viewer,
                MenuItems.apply(MenuItems.string(cfg, "items.confirm.title", "&8Transfer?"), placeholders),
                new ButtonBuilder().material(org.bukkit.Material.NAME_TAG).name(warp.getName()).build(),
                MenuItems.apply(MenuItems.string(cfg, "items.confirm.accept", "&a&lTRANSFER"), placeholders),
                MenuItems.apply(cfg.getStringList("items.confirm.accept-lore"), placeholders),
                MenuItems.apply(MenuItems.string(cfg, "items.confirm.cancel", "&c&lCANCEL"), placeholders),
                ButtonBuilder.of(org.bukkit.Material.GRAY_STAINED_GLASS_PANE).name(" ").toItemStack(),
                () -> plugin.getPlayerWarpManager().transferOwner(viewer, warp.getName(), targetName)
                        .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> viewer.closeInventory())),
                () -> new PlayerWarpTransferMenu(plugin, viewer, warp).open()
        );
    }
}
