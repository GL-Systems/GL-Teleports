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
import org.glstudio.teleports.modules.model.PlayerWarpBan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu with the players banned from a warp.
 */
public class PlayerWarpBansMenu extends PaginatedMenu<Button> {
    private final GTeleports plugin;
    private final PlayerWarp warp;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");

    private volatile List<PlayerWarpBan> bans;
    private volatile boolean loading = true;

    /**
     * Creates the menu.
     */
    public PlayerWarpBansMenu(GTeleports plugin, Player viewer, PlayerWarp warp) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.apply(new MenuConfig(plugin.getConfigManager().getPlayerWarpsBansMenu()).string("title", "&8Bans: %name%"), "%name%", warp.getName())),
                new MenuConfig(plugin.getConfigManager().getPlayerWarpsBansMenu()).integer("size", 54));
        this.plugin = plugin;
        this.warp = warp;
        setAutoRefresh(true);
        load();
    }

    private void load() {
        Futures.log(plugin.getPlayerWarpBanManager().list(warp.getId()).thenAccept(list -> {
            bans = list;
            loading = false;
            Bukkit.getScheduler().runTask(plugin, this::update);
        }), "PlayerWarpBansMenu#load");
    }

    @Override
    protected boolean shouldRefresh() {
        return loading;
    }

    @Override
    protected List<Integer> getContentSlots() {
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsBansMenu()).intList("content-slots");
    }

    @Override
    protected List<Button> getItems(Player viewer) {
        List<PlayerWarpBan> current = bans;
        if (current == null) {
            return List.of();
        }

        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsBansMenu());
        List<Button> items = new ArrayList<>(current.size());

        for (PlayerWarpBan ban : current) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(ban.player());
            String expires = ban.isPermanent() ? "never" : dateFormat.format(new Date(ban.expiresAt()));

            Object[] placeholders = {
                    "%player%", ban.playerName(),
                    "%reason%", ban.reason() == null || ban.reason().isBlank() ? "-" : ban.reason(),
                    "%expires%", expires
            };

            ButtonBuilder builder = cfg.builder("items.entry").material(Material.PLAYER_HEAD);
            builder.skullOwner(target);
            builder.name(MenuItems.apply(cfg.name("items.entry", "&c%player%"), placeholders));
            builder.lore(MenuItems.apply(cfg.lore("items.entry"), placeholders));
            builder.onClickSimple(() -> Futures.log(plugin.getPlayerWarpBanManager()
                    .remove(viewer, warp, ban.playerName())
                    .thenRun(this::load), "PlayerWarpBansMenu#remove"));

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
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsBansMenu()).builder("items.empty").build();
    }

    @Override
    protected int getEmptySlot(Player viewer) {
        return new MenuConfig(plugin.getConfigManager().getPlayerWarpsBansMenu()).slot("items.empty");
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int currentPage, int maxPage) {
        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getPlayerWarpsBansMenu());
        Map<Integer, Button> buttons = new HashMap<>();

        int infoSlot = cfg.slot("items.info");
        if (infoSlot >= 0) {
            int count = bans == null ? 0 : bans.size();
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

        cfg.putExtras(buttons, "extra");

        return buttons;
    }

    private void promptAdd(Player viewer) {
        viewer.closeInventory();
        plugin.getChatPrompts().request(viewer,
                MenuItems.prompt(plugin, "player-warp.prompts.ban-add", "{name}", warp.getName()),
                name -> promptReason(viewer, name),
                () -> new PlayerWarpBansMenu(plugin, viewer, warp).open());
    }

    private void promptReason(Player viewer, String targetName) {
        plugin.getChatPrompts().request(viewer,
                MenuItems.prompt(plugin, "player-warp.prompts.ban-reason", "{player}", targetName),
                reason -> Futures.log(plugin.getPlayerWarpBanManager().add(viewer, warp, targetName, 0L,
                        reason.equalsIgnoreCase("skip") ? "" : reason).thenRun(() ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                new PlayerWarpBansMenu(plugin, viewer, warp).open())),
                        "PlayerWarpBansMenu#add"),
                () -> new PlayerWarpBansMenu(plugin, viewer, warp).open());
    }
}
