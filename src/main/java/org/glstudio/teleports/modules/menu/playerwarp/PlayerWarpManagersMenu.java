package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import org.glstudio.teleports.modules.model.PlayerWarpManagerEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerWarpManagersMenu extends PaginatedMenu<Button> {
    private final GTeleports plugin;
    private final PlayerWarp warp;

    private volatile List<PlayerWarpManagerEntry> entries;
    private volatile boolean loading = true;

    public PlayerWarpManagersMenu(GTeleports plugin, Player viewer, PlayerWarp warp) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.apply(MenuItems.string(plugin.getConfigManager().getPlayerWarpsManagersMenu(),
                        "title", "&8Managers: %name%"), "%name%", warp.getName())),
                MenuItems.integer(plugin.getConfigManager().getPlayerWarpsManagersMenu(), "size", 54));
        this.plugin = plugin;
        this.warp = warp;
        setAutoRefresh(true);
        load();
    }

    private void load() {
        Failures.log(plugin.getPlayerWarpManagerRoleManager().list(warp.getId()).thenAccept(list -> {
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
        return MenuItems.intList(plugin.getConfigManager().getPlayerWarpsManagersMenu(), "content-slots");
    }

    @Override
    protected List<Button> getItems(Player viewer) {
        List<PlayerWarpManagerEntry> current = entries;
        if (current == null) {
            return List.of();
        }

        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsManagersMenu();
        List<Button> items = new ArrayList<>(current.size());

        for (PlayerWarpManagerEntry entry : current) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(entry.player());

            ButtonBuilder builder = MenuItems.builder(cfg, "items.entry").material(Material.PLAYER_HEAD);
            builder.skullOwner(target);
            builder.name(MenuItems.apply(MenuItems.name(cfg, "items.entry", "&b%player%"),
                    "%player%", entry.playerName()));
            builder.lore(MenuItems.apply(MenuItems.lore(cfg, "items.entry"), "%player%", entry.playerName()));
            builder.onClickSimple(() -> Failures.log(plugin.getPlayerWarpManagerRoleManager()
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
            return ButtonPresets.loading();
        }
        return MenuItems.builder(plugin.getConfigManager().getPlayerWarpsManagersMenu(), "items.empty").build();
    }

    @Override
    protected int getEmptySlot(Player viewer) {
        return MenuItems.slot(plugin.getConfigManager().getPlayerWarpsManagersMenu(), "items.empty");
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int currentPage, int maxPage) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsManagersMenu();
        Map<Integer, Button> buttons = new HashMap<>();

        int infoSlot = MenuItems.slot(cfg, "items.info");
        if (infoSlot >= 0) {
            int count = entries == null ? 0 : entries.size();
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

        MenuItems.put(buttons, cfg, "items.add", () -> promptAdd(viewer));
        MenuItems.put(buttons, cfg, "items.back", () -> new PlayerWarpManageMenu(plugin, viewer, warp).open());
        MenuItems.put(buttons, cfg, "items.close", viewer::closeInventory);

        return buttons;
    }

    private void promptAdd(Player viewer) {
        viewer.closeInventory();
        plugin.getChatInputManager().request(viewer,
                MenuItems.prompt(plugin, "player-warp.prompts.managers-add", "{name}", warp.getName()),
                name -> Failures.log(plugin.getPlayerWarpManagerRoleManager().add(viewer, warp, name).thenRun(() ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                new PlayerWarpManagersMenu(plugin, viewer, warp).open())),
                        "PlayerWarpManagersMenu#add"),
                () -> new PlayerWarpManagersMenu(plugin, viewer, warp).open());
    }
}
