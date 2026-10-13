package org.glstudio.teleports.modules.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.nexus.modules.menu.extra.ConfirmMenu;
import org.glstudio.nexus.modules.menu.paginated.PaginatedMenu;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.modules.manager.HomeManager;
import org.glstudio.teleports.modules.model.Home;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomesMenu extends PaginatedMenu<Button> {
    private final GTeleports plugin;
    private final SimpleDateFormat dateFormat;

    private volatile List<Home> homes;
    private volatile boolean loading = true;

    public HomesMenu(GTeleports plugin, Player viewer) {
        super(plugin.getMenuManager(), viewer,
                CC.t(MenuItems.string(plugin.getConfigManager().getHomesMenu(), "title", "&8Your Homes")),
                MenuItems.integer(plugin.getConfigManager().getHomesMenu(), "size", 54));
        this.plugin = plugin;
        this.dateFormat = new SimpleDateFormat(
                MenuItems.string(plugin.getConfigManager().getHomesMenu(), "date-format", "dd/MM/yyyy"));
        setAutoRefresh(true);
        load();
    }

    private void load() {
        Failures.log(plugin.getHomeManager().getHomes(viewer.getUniqueId()).thenAccept(loaded -> {
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
        List<Integer> configured = MenuItems.intList(plugin.getConfigManager().getHomesMenu(), "content-slots");
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

        ConfigFile cfg = plugin.getConfigManager().getHomesMenu();
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
            return ButtonPresets.loading();
        }
        return MenuItems.builder(plugin.getConfigManager().getHomesMenu(), "items.empty").build();
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int currentPage, int maxPage) {
        ConfigFile cfg = plugin.getConfigManager().getHomesMenu();
        Map<Integer, Button> buttons = new HashMap<>();

        MenuItems.put(buttons, cfg, "items.close", viewer::closeInventory);

        int infoSlot = MenuItems.slot(cfg, "items.info");
        if (infoSlot >= 0) {
            int used = homes == null ? 0 : homes.size();
            int limit = plugin.getHomeManager().getHomeLimit(viewer);
            String limitText = limit == HomeManager.UNLIMITED
                    ? plugin.getMessages().raw("placeholders.unlimited") : String.valueOf(limit);

            ButtonBuilder info = MenuItems.builder(cfg, "items.info");
            info.name(MenuItems.apply(MenuItems.name(cfg, "items.info", "&eHomes"),
                    "%used%", used, "%limit%", limitText));
            info.lore(MenuItems.apply(MenuItems.lore(cfg, "items.info"),
                    "%used%", used, "%limit%", limitText));
            buttons.put(infoSlot, info.build());
        }

        int previousSlot = MenuItems.slot(cfg, "items.previous-page");
        if (previousSlot >= 0 && currentPage > 0) {
            buttons.put(previousSlot,
                    MenuItems.builder(cfg, "items.previous-page").onClickSimple(this::previousPage).build());
        }

        int nextSlot = MenuItems.slot(cfg, "items.next-page");
        if (nextSlot >= 0 && currentPage < maxPage) {
            buttons.put(nextSlot,
                    MenuItems.builder(cfg, "items.next-page").onClickSimple(this::nextPage).build());
        }

        return buttons;
    }

    private Button buildHomeButton(ConfigFile cfg, Home home) {
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

        ButtonBuilder builder = MenuItems.builder(cfg, path);
        builder.material(iconFor(cfg, home));
        builder.name(MenuItems.apply(MenuItems.name(cfg, path, "&a%name%"), placeholders));
        builder.lore(MenuItems.apply(MenuItems.lore(cfg, path), placeholders));

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

    private Material iconFor(ConfigFile cfg, Home home) {
        String world = home.getWorld() == null ? "" : home.getWorld().toLowerCase(java.util.Locale.ROOT);
        String override = cfg.getString("world-icons." + world, null);

        if (override != null) {
            return ButtonBuilder.parseMaterial(override, Material.GRASS_BLOCK, "world-icons." + world);
        }

        return ButtonBuilder.parseMaterial(
                MenuItems.string(cfg, "items.home.material", "GRASS_BLOCK"),
                Material.GRASS_BLOCK, "items.home.material");
    }

    private void confirmDelete(Home home) {
        ConfigFile cfg = plugin.getConfigManager().getHomesMenu();

        Button display = MenuItems.builder(cfg, "items.home")
                .material(iconFor(cfg, home))
                .name(MenuItems.apply(MenuItems.name(cfg, "items.home", "&a%name%"), "%name%", home.getName()))
                .lore(List.of())
                .build();

        ConfirmMenu.confirm(plugin.getMenuManager(), viewer,
                MenuItems.apply(MenuItems.string(cfg, "confirm.title", "&8Delete home?"),
                        "%name%", home.getName()),
                display,
                MenuItems.apply(MenuItems.string(cfg, "confirm.accept", "&a&lDELETE"), "%name%", home.getName()),
                MenuItems.apply(cfg.getStringList("confirm.accept-lore"), "%name%", home.getName()),
                MenuItems.apply(MenuItems.string(cfg, "confirm.cancel", "&c&lKEEP"), "%name%", home.getName()),
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
