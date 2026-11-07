package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.nexus.modules.menu.button.ButtonPresets;
import org.glstudio.nexus.modules.menu.config.MenuConfig;
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
 * Menu with the staff options of a player warp.
 */
public class PlayerWarpAdminMenu extends PaginatedMenu<Button> {
    private final GTeleports plugin;

    private volatile List<PlayerWarp> warps;
    private volatile boolean loading = true;

    /**
     * Creates the menu.
     */
    public PlayerWarpAdminMenu(GTeleports plugin, Player viewer) {
        super(plugin.getMenuManager(), viewer,
                CC.t(new MenuConfig(plugin.getConfigManager().getPlayerWarpsAdminMenu()).string("title", "&8Player Warps - Admin")),
                new MenuConfig(plugin.getConfigManager().getPlayerWarpsAdminMenu()).integer("size", 54));
        this.plugin = plugin;
        setAutoRefresh(true);
        load();
    }

    private void load() {
        Futures.log(plugin.getPlayerWarpManager().getWarps().thenAccept(all -> {
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
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsAdminMenu()).intList("content-slots");
    }

    @Override
    protected List<Button> getItems(Player viewer) {
        List<PlayerWarp> current = warps;
        if (current == null) {
            return List.of();
        }

        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsAdminMenu());
        List<Button> items = new ArrayList<>(current.size());

        for (PlayerWarp warp : current) {
            Object[] placeholders = {
                    "%name%", warp.getName(),
                    "%owner%", warp.getOwnerName() == null ? "?" : warp.getOwnerName(),
                    "%world%", warp.getWorld() == null ? "?" : warp.getWorld()
            };

            ButtonBuilder builder = cfg.builder("items.warp");
            builder.name(MenuItems.apply(cfg.name("items.warp", "&a%name%"), placeholders));
            builder.lore(MenuItems.apply(cfg.lore("items.warp"), placeholders));
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
            return ButtonPresets.loading("&eLoading...", "&7Please wait");
        }
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsAdminMenu()).builder("items.empty").build();
    }

    @Override
    protected int getEmptySlot(Player viewer) {
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsAdminMenu()).slot("items.empty");
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int currentPage, int maxPage) {
        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsAdminMenu());
        Map<Integer, Button> buttons = new HashMap<>();

        int infoSlot = cfg.slot("items.info");
        if (infoSlot >= 0) {
            int count = warps == null ? 0 : warps.size();
            ButtonBuilder info = cfg.builder("items.info");
            info.lore(MenuItems.apply(cfg.lore("items.info"), "%count%", count));
            buttons.put(infoSlot, info.build());
        }

        if (currentPage > 0) {
            cfg.put(buttons, "items.previous-page", this::previousPage);
        }
        if (currentPage < maxPage) {
            cfg.put(buttons, "items.next-page", this::nextPage);
        }

        cfg.put(buttons, "items.purge", () -> {
            plugin.getPlayerWarpPurgeService().purgeNow();
            plugin.getMessages().send(viewer, "reload.success");
        });

        cfg.put(buttons, "items.reload", () -> {
            plugin.getConfigManager().getPlayerWarpsConfig().reload();
            plugin.getConfigManager().reload();
            plugin.getPlayerWarpCategoryRegistry().load();
            plugin.getPlayerWarpManager().invalidateCache();
            plugin.getMessages().send(viewer, "reload.success");
            load();
        });

        cfg.put(buttons, "items.close", viewer::closeInventory);

        cfg.putExtras(buttons, "extra");

        return buttons;
    }
}
