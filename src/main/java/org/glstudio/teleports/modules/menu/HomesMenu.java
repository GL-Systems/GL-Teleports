package org.glstudio.teleports.modules.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.nexus.modules.menu.button.ButtonPresets;
import org.glstudio.nexus.modules.menu.config.MenuConfig;
import org.glstudio.nexus.modules.menu.extra.ConfirmMenu;
import org.glstudio.nexus.modules.menu.paginated.PaginatedMenu;
import org.glstudio.nexus.utils.CC;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.manager.HomeManager;
import org.glstudio.teleports.modules.model.Home;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu with a player's homes.
 */
public class HomesMenu extends PaginatedMenu<Button> {
    private final GTeleports plugin;
    private final SimpleDateFormat dateFormat;

    private volatile List<Home> homes;
    private volatile boolean loading = true;

    /**
     * Creates the menu.
     */
    public HomesMenu(GTeleports plugin, Player viewer) {
        super(plugin.getMenuManager(), viewer,
                CC.t(new MenuConfig(plugin.getConfigManager().getHomesMenu()).string("title", "&8Your Homes")),
                new MenuConfig(plugin.getConfigManager().getHomesMenu()).integer("size", 54));
        this.plugin = plugin;
        this.dateFormat = new SimpleDateFormat(
                new MenuConfig(plugin.getConfigManager().getHomesMenu()).string("date-format", "dd/MM/yyyy"));
        setAutoRefresh(true);
        load();
    }

    private void load() {
        Futures.log(plugin.getHomeManager().getHomes(viewer.getUniqueId()).thenAccept(loaded -> {
            homes = loaded;
            loading = false;

            Bukkit.getScheduler().runTask(plugin, this::update);
        }), "HomesMenu#load");
    }

    @Override
    protected boolean shouldRefresh() {
        return loading;
    }

    @Override
    protected List<Integer> getContentSlots() {
        List<Integer> configured = new MenuConfig(plugin.getConfigManager().getHomesMenu()).intList("content-slots");
        if (!configured.isEmpty()) {
            return configured;
        }

        List<Integer> fallback = new ArrayList<>();
        for (int slot = 0; slot < Math.max(0, getSize() - 9); slot++) {
            fallback.add(slot);
        }
        return fallback;
    }

    @Override
    protected List<Button> getItems(Player viewer) {
        List<Home> current = homes;
        if (current == null) {
            return List.of();
        }

        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getHomesMenu());
        List<Button> items = new ArrayList<>(current.size());

        for (Home home : current) {
            items.add(buildHomeButton(cfg, home));
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
        return new MenuConfig(plugin.getConfigManager().getHomesMenu()).builder("items.empty").build();
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int currentPage, int maxPage) {
        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getHomesMenu());
        Map<Integer, Button> buttons = new HashMap<>();

        cfg.put(buttons, "items.close", viewer::closeInventory);

        int infoSlot = cfg.slot("items.info");
        if (infoSlot >= 0) {
            int used = homes == null ? 0 : homes.size();
            int limit = plugin.getHomeManager().getHomeLimit(viewer);
            String limitText = limit == HomeManager.UNLIMITED
                    ? plugin.getMessages().raw("placeholders.unlimited") : String.valueOf(limit);

            ButtonBuilder info = cfg.builder("items.info");
            info.name(MenuItems.apply(cfg.name("items.info", "&eHomes"),
                    "%used%", used, "%limit%", limitText));
            info.lore(MenuItems.apply(cfg.lore("items.info"),
                    "%used%", used, "%limit%", limitText));
            buttons.put(infoSlot, info.build());
        }

        int previousSlot = cfg.slot("items.previous-page");
        if (previousSlot >= 0 && currentPage > 0) {
            buttons.put(previousSlot,
                    cfg.builder("items.previous-page").onClickSimple(this::previousPage).build());
        }

        int nextSlot = cfg.slot("items.next-page");
        if (nextSlot >= 0 && currentPage < maxPage) {
            buttons.put(nextSlot,
                    cfg.builder("items.next-page").onClickSimple(this::nextPage).build());
        }

        return buttons;
    }

    private Button buildHomeButton(MenuConfig cfg, Home home) {
        String path = "items.home";

        Object[] placeholders = {
                "%name%", home.getName(),
                "%world%", home.getWorld() == null ? "?" : home.getWorld(),
                "%server%", home.getServer() == null ? "?" : home.getServer(),
                "%x%", Math.round(home.getX()),
                "%y%", Math.round(home.getY()),
                "%z%", Math.round(home.getZ()),
                "%date%", dateFormat.format(new Date(home.getCreatedAt()))
        };

        ButtonBuilder builder = cfg.builder(path);
        builder.material(iconFor(cfg, home));
        builder.name(MenuItems.apply(cfg.name(path, "&a%name%"), placeholders));
        builder.lore(MenuItems.apply(cfg.lore(path), placeholders));

        builder.onClick(event -> {
            if (event.getClick() == ClickType.RIGHT) {
                confirmDelete(home);
                return;
            }

            viewer.closeInventory();
            plugin.getHomeManager().teleportToHome(viewer, home.getName());
        });

        return builder.build();
    }

    private Material iconFor(MenuConfig cfg, Home home) {
        String world = home.getWorld() == null ? "" : home.getWorld().toLowerCase(java.util.Locale.ROOT);
        String override = cfg.file().getString("world-icons." + world, null);

        if (override != null) {
            return ButtonBuilder.parseMaterial(override, Material.GRASS_BLOCK, "world-icons." + world);
        }

        return ButtonBuilder.parseMaterial(
                cfg.string("items.home.material", "GRASS_BLOCK"),
                Material.GRASS_BLOCK, "items.home.material");
    }

    private void confirmDelete(Home home) {
        MenuConfig cfg = new MenuConfig(plugin.getConfigManager().getHomesMenu());

        Button display = cfg.builder("items.home")
                .material(iconFor(cfg, home))
                .name(MenuItems.apply(cfg.name("items.home", "&a%name%"), "%name%", home.getName()))
                .lore(List.of())
                .build();

        ConfirmMenu.confirm(plugin.getMenuManager(), viewer,
                MenuItems.apply(cfg.string("confirm.title", "&8Delete home?"),
                        "%name%", home.getName()),
                display,
                MenuItems.apply(cfg.string("confirm.accept", "&a&lDELETE"), "%name%", home.getName()),
                MenuItems.apply(cfg.file().getStringList("confirm.accept-lore"), "%name%", home.getName()),
                MenuItems.apply(cfg.string("confirm.cancel", "&c&lKEEP"), "%name%", home.getName()),
                ButtonBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").toItemStack(),
                () -> {
                    plugin.getHomeManager().deleteHome(viewer, home.getName())
                            .thenRun(() -> Bukkit.getScheduler().runTask(plugin,
                                    () -> new HomesMenu(plugin, viewer).open()));
                },
                () -> new HomesMenu(plugin, viewer).open()
        );
    }
}
