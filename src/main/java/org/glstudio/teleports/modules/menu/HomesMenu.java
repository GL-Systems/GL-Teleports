package org.glstudio.teleports.modules.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.menu.button.Button;
import org.glstudio.teleports.common.menu.button.ButtonBuilder;
import org.glstudio.teleports.common.menu.button.ButtonPresets;
import org.glstudio.teleports.common.menu.extra.ConfirmMenu;
import org.glstudio.teleports.common.menu.paginated.PaginatedMenu;
import org.glstudio.teleports.core.Failures;
import org.glstudio.teleports.modules.manager.HomeManager;
import org.glstudio.teleports.modules.model.Home;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomesMenu extends PaginatedMenu {
    private final GTeleports plugin;
    private final SimpleDateFormat dateFormat;

    private volatile List<Home> homes;
    private volatile boolean loading = true;

    public HomesMenu(GTeleports plugin, Player viewer) {
        super(plugin.getMenuManager(), plugin, viewer,
                CC.t(MenuItems.string(plugin.getConfigManager().getHomesMenu(), "TITLE", "&8Your Homes")),
                MenuItems.integer(plugin.getConfigManager().getHomesMenu(), "SIZE", 54),
                true);
        this.plugin = plugin;
        this.dateFormat = new SimpleDateFormat(
                MenuItems.string(plugin.getConfigManager().getHomesMenu(), "DATE_FORMAT", "dd/MM/yyyy"));
        load();
    }

    private void load() {
        Failures.log(plugin.getHomeManager().getHomes(p.getUniqueId()).thenAccept(loaded -> {
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
        List<Integer> configured = MenuItems.intList(plugin.getConfigManager().getHomesMenu(), "CONTENT_SLOTS");
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
    protected List<Button> getPageItems(Player viewer) {
        List<Home> current = homes;
        if (current == null) {
            return List.of();
        }

        ConfigFile cfg = plugin.getConfigManager().getHomesMenu();
        List<Button> items = new ArrayList<>(current.size());

        for (Home home : current) {
            items.add(buildHomeButton(cfg, home));
        }

        return items;
    }

    @Override
    protected Button getEmptyButton(Player viewer) {
        if (loading) {
            return ButtonPresets.loading();
        }
        return MenuItems.builder(plugin.getConfigManager().getHomesMenu(), "ITEMS.EMPTY").build();
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int currentPage, int maxPage) {
        ConfigFile cfg = plugin.getConfigManager().getHomesMenu();
        Map<Integer, Button> buttons = new HashMap<>();

        MenuItems.put(buttons, cfg, "ITEMS.CLOSE", viewer::closeInventory);

        int infoSlot = MenuItems.slot(cfg, "ITEMS.INFO");
        if (infoSlot >= 0) {
            int used = homes == null ? 0 : homes.size();
            int limit = plugin.getHomeManager().getHomeLimit(viewer);
            String limitText = limit == HomeManager.UNLIMITED
                    ? plugin.getMessages().raw("placeholders.unlimited") : String.valueOf(limit);

            ButtonBuilder info = MenuItems.builder(cfg, "ITEMS.INFO");
            info.name(MenuItems.apply(MenuItems.name(cfg, "ITEMS.INFO", "&eHomes"),
                    "%used%", used, "%limit%", limitText));
            info.lore(MenuItems.apply(MenuItems.lore(cfg, "ITEMS.INFO"),
                    "%used%", used, "%limit%", limitText));
            buttons.put(infoSlot, info.build());
        }

        int previousSlot = MenuItems.slot(cfg, "ITEMS.PREVIOUS_PAGE");
        if (previousSlot >= 0 && currentPage > 0) {
            buttons.put(previousSlot,
                    MenuItems.builder(cfg, "ITEMS.PREVIOUS_PAGE").onClickSimple(this::previousPage).build());
        }

        int nextSlot = MenuItems.slot(cfg, "ITEMS.NEXT_PAGE");
        if (nextSlot >= 0 && currentPage < maxPage) {
            buttons.put(nextSlot,
                    MenuItems.builder(cfg, "ITEMS.NEXT_PAGE").onClickSimple(this::nextPage).build());
        }

        return buttons;
    }

    private Button buildHomeButton(ConfigFile cfg, Home home) {
        String path = "ITEMS.HOME";

        Object[] placeholders = {
                "%name%", home.getName(),
                "%world%", home.getWorld() == null ? "?" : home.getWorld(),
                "%server%", home.getServer() == null ? "?" : home.getServer(),
                "%x%", Math.round(home.getX()),
                "%y%", Math.round(home.getY()),
                "%z%", Math.round(home.getZ()),
                "%date%", dateFormat.format(new Date(home.getCreatedAt()))
        };

        ButtonBuilder builder = MenuItems.builder(cfg, path);
        builder.material(iconFor(cfg, home));
        builder.name(MenuItems.apply(MenuItems.name(cfg, path, "&a%name%"), placeholders));
        builder.lore(MenuItems.apply(MenuItems.lore(cfg, path), placeholders));

        builder.onClick(event -> {
            if (event.getClick() == ClickType.RIGHT) {
                confirmDelete(home);
                return;
            }

            p.closeInventory();
            plugin.getHomeManager().teleportToHome(p, home.getName());
        });

        return builder.build();
    }

    private Material iconFor(ConfigFile cfg, Home home) {
        String world = home.getWorld() == null ? "" : home.getWorld().toLowerCase(java.util.Locale.ROOT);
        String override = cfg.getString("WORLD_ICONS." + world, null);

        if (override != null) {
            return ButtonBuilder.parseMaterial(override, Material.GRASS_BLOCK, "WORLD_ICONS." + world);
        }

        return ButtonBuilder.parseMaterial(
                MenuItems.string(cfg, "ITEMS.HOME.MATERIAL", "GRASS_BLOCK"),
                Material.GRASS_BLOCK, "ITEMS.HOME.MATERIAL");
    }

    private void confirmDelete(Home home) {
        ConfigFile cfg = plugin.getConfigManager().getHomesMenu();

        Button display = MenuItems.builder(cfg, "ITEMS.HOME")
                .material(iconFor(cfg, home))
                .name(MenuItems.apply(MenuItems.name(cfg, "ITEMS.HOME", "&a%name%"), "%name%", home.getName()))
                .lore(List.of())
                .build();

        new ConfirmMenu(plugin.getMenuManager(), plugin, p,
                MenuItems.apply(MenuItems.string(cfg, "CONFIRM.TITLE", "&8Delete home?"),
                        "%name%", home.getName()),
                display,
                MenuItems.apply(MenuItems.string(cfg, "CONFIRM.ACCEPT", "&a&lDELETE"), "%name%", home.getName()),
                MenuItems.apply(cfg.getStringList("CONFIRM.ACCEPT_LORE"), "%name%", home.getName()),
                MenuItems.apply(MenuItems.string(cfg, "CONFIRM.CANCEL", "&c&lKEEP"), "%name%", home.getName()),
                () -> {
                    plugin.getHomeManager().deleteHome(p, home.getName())
                            .thenRun(() -> Bukkit.getScheduler().runTask(plugin,
                                    () -> new HomesMenu(plugin, p).open()));
                },
                () -> new HomesMenu(plugin, p).open()
        ).open();
    }
}
