package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import org.glstudio.teleports.modules.model.PlayerWarpManagerEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu with the managers of a warp.
 */
public class PlayerWarpManagersMenu extends PaginatedMenu<Button> {
    private final GTeleports plugin;
    private final PlayerWarp warp;

    private volatile List<PlayerWarpManagerEntry> entries;
    private volatile boolean loading = true;

    /**
     * Creates the menu.
     */
    public PlayerWarpManagersMenu(GTeleports plugin, Player viewer, PlayerWarp warp) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.apply(new MenuConfig(plugin.getConfigManager().getPlayerWarpsManagersMenu()).string("title", "&8Managers: %name%"), "%name%", warp.getName())),
                new MenuConfig(plugin.getConfigManager().getPlayerWarpsManagersMenu()).integer("size", 54));
        this.plugin = plugin;
        this.warp = warp;
        setAutoRefresh(true);
        load();
    }

    private void load() {
        Futures.log(plugin.getPlayerWarpManagerRoleManager().list(warp.getId()).thenAccept(list -> {
            entries = list;
            loading = false;
            Bukkit.getScheduler().runTask(plugin, this::update);
        }), "PlayerWarpManagersMenu#load");
    }

    @Override
    protected boolean shouldRefresh() {
        return loading;
    }

    @Override
    protected List<Integer> getContentSlots() {
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsManagersMenu()).intList("content-slots");
    }

    @Override
    protected List<Button> getItems(Player viewer) {
        List<PlayerWarpManagerEntry> current = entries;
        if (current == null) {
            return List.of();
        }

        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsManagersMenu());
        List<Button> items = new ArrayList<>(current.size());

        for (PlayerWarpManagerEntry entry : current) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(entry.player());

            ButtonBuilder builder = cfg.builder("items.entry").material(Material.PLAYER_HEAD);
            builder.skullOwner(target);
            builder.name(MenuItems.apply(cfg.name("items.entry", "&b%player%"),
                    "%player%", entry.playerName()));
            builder.lore(MenuItems.apply(cfg.lore("items.entry"), "%player%", entry.playerName()));
            builder.onClickSimple(() -> Futures.log(plugin.getPlayerWarpManagerRoleManager()
                    .remove(viewer, warp, entry.playerName())
                    .thenRun(this::load), "PlayerWarpManagersMenu#remove"));

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
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsManagersMenu()).builder("items.empty").build();
    }

    @Override
    protected int getEmptySlot(Player viewer) {
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsManagersMenu()).slot("items.empty");
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int currentPage, int maxPage) {
        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsManagersMenu());
        Map<Integer, Button> buttons = new HashMap<>();

        int infoSlot = cfg.slot("items.info");
        if (infoSlot >= 0) {
            int count = entries == null ? 0 : entries.size();
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

        cfg.put(buttons, "items.add", () -> promptAdd(viewer));
        cfg.put(buttons, "items.back", () -> new PlayerWarpManageMenu(plugin, viewer, warp).open());
        cfg.put(buttons, "items.close", viewer::closeInventory);

        return buttons;
    }

    private void promptAdd(Player viewer) {
        viewer.closeInventory();
        plugin.getChatPrompts().request(viewer,
                MenuItems.prompt(plugin, "player-warp.prompts.managers-add", "{name}", warp.getName()),
                name -> Futures.log(plugin.getPlayerWarpManagerRoleManager().add(viewer, warp, name).thenRun(() ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                new PlayerWarpManagersMenu(plugin, viewer, warp).open())),
                        "PlayerWarpManagersMenu#add"),
                () -> new PlayerWarpManagersMenu(plugin, viewer, warp).open());
    }
}
