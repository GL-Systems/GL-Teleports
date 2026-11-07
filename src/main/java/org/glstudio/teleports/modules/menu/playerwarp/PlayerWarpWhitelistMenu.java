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
import org.glstudio.teleports.modules.model.PlayerWarpWhitelistEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu with the whitelist of a warp.
 */
public class PlayerWarpWhitelistMenu extends PaginatedMenu<Button> {
    private final GTeleports plugin;
    private final PlayerWarp warp;

    private volatile List<PlayerWarpWhitelistEntry> entries;
    private volatile boolean loading = true;

    /**
     * Creates the menu.
     */
    public PlayerWarpWhitelistMenu(GTeleports plugin, Player viewer, PlayerWarp warp) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.apply(new MenuConfig(plugin.getConfigManager().getPlayerWarpsWhitelistMenu()).string("title", "&8Whitelist: %name%"), "%name%", warp.getName())),
                new MenuConfig(plugin.getConfigManager().getPlayerWarpsWhitelistMenu()).integer("size", 54));
        this.plugin = plugin;
        this.warp = warp;
        setAutoRefresh(true);
        load();
    }

    private void load() {
        Futures.log(plugin.getPlayerWarpWhitelistManager().list(warp.getId()).thenAccept(list -> {
            entries = list;
            loading = false;
            Bukkit.getScheduler().runTask(plugin, this::update);
        }), "PlayerWarpWhitelistMenu#load");
    }

    @Override
    protected boolean shouldRefresh() {
        return loading;
    }

    @Override
    protected List<Integer> getContentSlots() {
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsWhitelistMenu()).intList("content-slots");
    }

    @Override
    protected List<Button> getItems(Player viewer) {
        List<PlayerWarpWhitelistEntry> current = entries;
        if (current == null) {
            return List.of();
        }

        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsWhitelistMenu());
        List<Button> items = new ArrayList<>(current.size());

        for (PlayerWarpWhitelistEntry entry : current) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(entry.player());

            ButtonBuilder builder = cfg.builder("items.entry").material(Material.PLAYER_HEAD);
            builder.skullOwner(target);
            builder.name(MenuItems.apply(cfg.name("items.entry", "&e%player%"),
                    "%player%", entry.playerName()));
            builder.lore(MenuItems.apply(cfg.lore("items.entry"), "%player%", entry.playerName()));
            builder.onClickSimple(() -> Futures.log(plugin.getPlayerWarpWhitelistManager()
                    .remove(viewer, warp, entry.playerName())
                    .thenRun(this::load), "PlayerWarpWhitelistMenu#remove"));

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
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsWhitelistMenu()).builder("items.empty").build();
    }

    @Override
    protected int getEmptySlot(Player viewer) {
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsWhitelistMenu()).slot("items.empty");
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int currentPage, int maxPage) {
        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsWhitelistMenu());
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

        String togglePath = warp.isWhitelistEnabled() ? "items.toggle-on" : "items.toggle-off";
        int toggleSlot = cfg.slot(togglePath);
        if (toggleSlot >= 0) {
            buttons.put(toggleSlot, cfg.builder(togglePath)
                    .onClickSimple(() -> Futures.log(plugin.getPlayerWarpManager()
                            .setWhitelistEnabled(viewer, warp.getName(), !warp.isWhitelistEnabled())
                            .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () ->
                                    new PlayerWarpWhitelistMenu(plugin, viewer, warp).open())),
                            "PlayerWarpWhitelistMenu#toggle"))
                    .build());
        }

        cfg.put(buttons, "items.back", () -> new PlayerWarpManageMenu(plugin, viewer, warp).open());
        cfg.put(buttons, "items.close", viewer::closeInventory);

        cfg.putExtras(buttons, "extra");

        return buttons;
    }

    private void promptAdd(Player viewer) {
        viewer.closeInventory();
        plugin.getChatPrompts().request(viewer,
                MenuItems.prompt(plugin, "player-warp.prompts.whitelist-add", "{name}", warp.getName()),
                name -> Futures.log(plugin.getPlayerWarpWhitelistManager().add(viewer, warp, name).thenRun(() ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                new PlayerWarpWhitelistMenu(plugin, viewer, warp).open())),
                        "PlayerWarpWhitelistMenu#add"),
                () -> new PlayerWarpWhitelistMenu(plugin, viewer, warp).open());
    }
}
