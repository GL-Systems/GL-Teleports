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
import java.util.Locale;
import java.util.Map;

public class PlayerWarpInfoMenu extends Menu {
    private final GTeleports plugin;
    private final PlayerWarp warp;

    private volatile boolean favourite;
    private volatile boolean mayManage;
    private volatile boolean loaded;

    public PlayerWarpInfoMenu(GTeleports plugin, Player viewer, PlayerWarp warp) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.apply(MenuItems.string(plugin.getConfigManager().getPlayerWarpsInfoMenu(),
                        "title", "&8%name%"), "%name%", warp.getName())),
                MenuItems.integer(plugin.getConfigManager().getPlayerWarpsInfoMenu(), "size", 27));
        this.plugin = plugin;
        this.warp = warp;
        setAutoRefresh(true);
        load();
    }

    private void load() {
        Failures.log(plugin.getPlayerWarpFavouriteManager().isFavourite(viewer.getUniqueId(), warp.getId())
                .thenCombine(plugin.getPlayerWarpManagerRoleManager().mayManage(warp, viewer.getUniqueId()),
                        (fav, manage) -> {
                            favourite = fav;
                            mayManage = manage || viewer.hasPermission(
                                    org.glstudio.teleports.core.util.Permissions.PLAYERWARP_ADMIN_REMOVEALL);
                            loaded = true;
                            return null;
                        }).thenRun(() -> Bukkit.getScheduler().runTask(plugin, this::update)),
                "PlayerWarpInfoMenu#load");
    }

    @Override
    protected boolean shouldRefresh() {
        return !loaded;
    }

    @Override
    protected void render() {
        clear();
        for (Map.Entry<Integer, Button> entry : getButtons(viewer).entrySet()) {
            place(entry.getKey(), entry.getValue());
        }
    }

    private Map<Integer, Button> getButtons(Player viewer) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsInfoMenu();
        Map<Integer, Button> buttons = new HashMap<>();

        PlayerWarpCategory category = plugin.getPlayerWarpCategoryRegistry().get(warp.getCategoryId());
        String status = statusLine();

        Object[] placeholders = {
                "%name%", warp.getName(),
                "%owner%", warp.getOwnerName() == null ? "?" : warp.getOwnerName(),
                "%world%", warp.getWorld() == null ? "?" : warp.getWorld(),
                "%category%", category == null ? "-" : category.displayName(),
                "%rating%", String.format(Locale.ROOT, "%.1f", warp.getRatingAverage()),
                "%rating-count%", warp.getRatingCount(),
                "%favourites%", warp.getFavouriteCount(),
                "%description%", warp.getDescription() == null ? "-" : warp.getDescription(),
                "%cost%", warp.isFree() ? plugin.getMessages().raw("placeholders.free")
                        : plugin.getEconomyManager().formatCost(warp.getCost()),
                "%status%", status
        };

        int displaySlot = MenuItems.slot(cfg, "items.display");
        if (displaySlot >= 0) {
            Material material = warp.getIcon() != null
                    ? ButtonBuilder.parseMaterial(warp.getIcon(), Material.GRASS_BLOCK, "player warp info")
                    : Material.GRASS_BLOCK;

            ButtonBuilder display = MenuItems.builder(cfg, "items.display").material(material);
            display.name(MenuItems.apply(MenuItems.name(cfg, "items.display", "&a%name%"), placeholders));
            display.lore(MenuItems.apply(MenuItems.lore(cfg, "items.display"), placeholders));
            buttons.put(displaySlot, display.build());
        }

        MenuItems.put(buttons, cfg, "items.teleport", () -> teleport(viewer));

        String favPath = favourite ? "items.favourite-on" : "items.favourite-off";
        int favSlot = MenuItems.slot(cfg, favPath);
        if (favSlot >= 0) {
            buttons.put(favSlot, MenuItems.builder(cfg, favPath)
                    .onClickSimple(() -> plugin.getPlayerWarpFavouriteManager().toggle(viewer, warp)
                            .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () ->
                                    new PlayerWarpInfoMenu(plugin, viewer, warp).open())))
                    .build());
        }

        MenuItems.put(buttons, cfg, "items.rate", () -> new PlayerWarpRatingMenu(plugin, viewer, warp).open());

        if (mayManage) {
            MenuItems.put(buttons, cfg, "items.manage", () -> new PlayerWarpManageMenu(plugin, viewer, warp).open());
        }

        MenuItems.put(buttons, cfg, "items.back",
                () -> new PlayerWarpListMenu(plugin, viewer, PlayerWarpListMenu.Mode.ALL, null).open());
        MenuItems.put(buttons, cfg, "items.close", viewer::closeInventory);

        MenuItems.putExtras(buttons, cfg, "extra");

        return buttons;
    }

    private String statusLine() {
        StringBuilder status = new StringBuilder();
        if (warp.isLocked()) {
            status.append("&c[Locked] ");
        }
        if (warp.isWhitelistEnabled()) {
            status.append("&e[Whitelist] ");
        }
        if (warp.hasPassword()) {
            status.append("&6[Password]");
        }
        return CC.t(status.toString());
    }

    private void teleport(Player viewer) {
        viewer.closeInventory();
        plugin.getPlayerWarpManager().attemptTeleport(viewer, warp.getName(), null);
    }
}
