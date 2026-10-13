package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.teleports.modules.menu.ButtonPresets;
import org.glstudio.nexus.modules.menu.paginated.PaginatedMenu;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.modules.menu.MenuItems;
import org.glstudio.teleports.modules.model.PlayerWarp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerWarpAdminMenu extends PaginatedMenu<Button> {
    private final GTeleports plugin;

    private volatile List<PlayerWarp> warps;
    private volatile boolean loading = true;

    public PlayerWarpAdminMenu(GTeleports plugin, Player viewer) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.string(plugin.getConfigManager().getPlayerWarpsAdminMenu(),
                        "title", "&8Player Warps - Admin")),
                MenuItems.integer(plugin.getConfigManager().getPlayerWarpsAdminMenu(), "size", 54));
        this.plugin = plugin;
        setAutoRefresh(true);
        load();
    }

    private void load() {
        Failures.log(plugin.getPlayerWarpManager().getWarps().thenAccept(all -> {
            warps = new ArrayList<>(all);
            warps.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            loading = false;
            Bukkit.getScheduler().runTask(plugin, this::update);
        }), "PlayerWarpAdminMenu#load");
    }

    @Override
    protected boolean shouldRefresh() {
        return loading;
    }

    @Override
    protected List<Integer> getContentSlots() {
        return MenuItems.intList(plugin.getConfigManager().getPlayerWarpsAdminMenu(), "content-slots");
    }

    @Override
    protected List<Button> getItems(Player viewer) {
        List<PlayerWarp> current = warps;
        if (current == null) {
            return List.of();
        }

        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsAdminMenu();
        List<Button> items = new ArrayList<>(current.size());

        for (PlayerWarp warp : current) {
            Object[] placeholders = {
                    "%name%", warp.getName(),
                    "%owner%", warp.getOwnerName() == null ? "?" : warp.getOwnerName(),
                    "%world%", warp.getWorld() == null ? "?" : warp.getWorld()
            };

            ButtonBuilder builder = MenuItems.builder(cfg, "items.warp");
            builder.name(MenuItems.apply(MenuItems.name(cfg, "items.warp", "&a%name%"), placeholders));
            builder.lore(MenuItems.apply(MenuItems.lore(cfg, "items.warp"), placeholders));
            builder.onClickSimple(() -> new PlayerWarpManageMenu(plugin, viewer, warp).open());

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
        if (loading) {
            return ButtonPresets.loading();
        }
        return MenuItems.builder(plugin.getConfigManager().getPlayerWarpsAdminMenu(), "items.empty").build();
    }

    @Override
    protected int getEmptySlot(Player viewer) {
        return MenuItems.slot(plugin.getConfigManager().getPlayerWarpsAdminMenu(), "items.empty");
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int currentPage, int maxPage) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsAdminMenu();
        Map<Integer, Button> buttons = new HashMap<>();

        int infoSlot = MenuItems.slot(cfg, "items.info");
        if (infoSlot >= 0) {
            int count = warps == null ? 0 : warps.size();
            ButtonBuilder info = MenuItems.builder(cfg, "items.info");
            info.lore(MenuItems.apply(MenuItems.lore(cfg, "items.info"), "%count%", count));
            buttons.put(infoSlot, info.build());
        }

        if (currentPage > 0) {
            MenuItems.put(buttons, cfg, "items.previous-page", this::previousPage);
        }
        if (currentPage < maxPage) {
            MenuItems.put(buttons, cfg, "items.next-page", this::nextPage);
        }

        MenuItems.put(buttons, cfg, "items.purge", () -> {
            plugin.getPlayerWarpPurgeService().purgeNow();
            plugin.getMessages().send(viewer, "reload.success");
        });

        MenuItems.put(buttons, cfg, "items.reload", () -> {
            plugin.getConfigManager().getPlayerWarpsConfig().reload();
            plugin.getConfigManager().reload();
            plugin.getPlayerWarpCategoryRegistry().load();
            plugin.getPlayerWarpManager().invalidateCache();
            plugin.getMessages().send(viewer, "reload.success");
            load();
        });

        MenuItems.put(buttons, cfg, "items.close", viewer::closeInventory);

        MenuItems.putExtras(buttons, cfg, "extra");

        return buttons;
    }
}
