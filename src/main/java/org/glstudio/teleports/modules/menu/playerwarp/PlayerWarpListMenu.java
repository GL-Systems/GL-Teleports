package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.teleports.modules.menu.ButtonPresets;
import org.glstudio.nexus.modules.menu.extra.ConfirmMenu;
import org.glstudio.nexus.modules.menu.paginated.PaginatedMenu;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.modules.menu.MenuItems;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpCategory;
import org.glstudio.teleports.modules.model.PlayerWarpFavourite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PlayerWarpListMenu extends PaginatedMenu<Button> {
    public enum Mode {
        ALL, CATEGORY, SEARCH, FAVOURITES, MINE, SPONSOR
    }

    private final GTeleports plugin;
    private final Mode mode;
    private final String query;
    private final Integer sponsorSlot;

    private volatile List<PlayerWarp> warps;
    private volatile Set<String> favouriteIds = Set.of();
    private volatile boolean loading = true;

    public PlayerWarpListMenu(GTeleports plugin, Player viewer, Mode mode, String query) {
        this(plugin, viewer, mode, query, null);
    }

    public PlayerWarpListMenu(GTeleports plugin, Player viewer, Mode mode, String query, Integer sponsorSlot) {
        super(plugin.getMenuManager(), viewer, title(plugin, mode, query),
                MenuItems.integer(plugin.getConfigManager().getPlayerWarpsListMenu(), "size", 54));
        this.plugin = plugin;
        this.mode = mode;
        this.query = query;
        this.sponsorSlot = sponsorSlot;
        setAutoRefresh(true);
        load();
    }

    private static String title(GTeleports plugin, Mode mode, String query) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsListMenu();
        String raw = cfg.getString("title." + mode.name().toLowerCase(Locale.ROOT), "&8Player Warps");

        if (mode == Mode.CATEGORY) {
            PlayerWarpCategory category = plugin.getPlayerWarpCategoryRegistry().get(query);
            raw = raw.replace("%category%", category == null ? String.valueOf(query) : category.displayName());
        } else if (mode == Mode.SEARCH) {
            raw = raw.replace("%query%", String.valueOf(query));
        }

        return CC.t(raw);
    }

    private void load() {
        Failures.log(plugin.getPlayerWarpManager().getWarps().thenCombine(
                plugin.getPlayerWarpFavouriteManager().list(viewer.getUniqueId()),
                (all, favs) -> filter(all, favs)
        ).thenAccept(result -> {
            warps = result.warps();
            favouriteIds = result.favouriteIds();
            loading = false;
            Bukkit.getScheduler().runTask(plugin, this::update);
        }), "PlayerWarpListMenu#load");
    }

    private record FilterResult(List<PlayerWarp> warps, Set<String> favouriteIds) {
    }

    private FilterResult filter(List<PlayerWarp> all, List<PlayerWarpFavourite> favs) {
        Set<String> favIds = favs.stream().map(PlayerWarpFavourite::warpId).collect(Collectors.toSet());

        List<PlayerWarp> filtered = switch (mode) {
            case ALL -> new ArrayList<>(all);
            case CATEGORY -> all.stream()
                    .filter(w -> query != null && query.equalsIgnoreCase(w.getCategoryId()))
                    .collect(Collectors.toCollection(ArrayList::new));
            case SEARCH -> {
                String needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
                yield all.stream()
                        .filter(w -> w.getName().toLowerCase(Locale.ROOT).contains(needle)
                                || (w.getDescription() != null
                                && w.getDescription().toLowerCase(Locale.ROOT).contains(needle)))
                        .collect(Collectors.toCollection(ArrayList::new));
            }
            case FAVOURITES -> all.stream()
                    .filter(w -> favIds.contains(w.getId()))
                    .collect(Collectors.toCollection(ArrayList::new));
            case MINE, SPONSOR -> all.stream()
                    .filter(w -> w.isOwner(viewer.getUniqueId()))
                    .collect(Collectors.toCollection(ArrayList::new));
        };

        filtered.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return new FilterResult(filtered, favIds);
    }

    @Override
    protected boolean shouldRefresh() {
        return loading;
    }

    @Override
    protected List<Integer> getContentSlots() {
        List<Integer> configured = MenuItems.intList(plugin.getConfigManager().getPlayerWarpsListMenu(),
                "content-slots");
        return configured.isEmpty() ? List.of() : configured;
    }

    @Override
    protected List<Button> getItems(Player viewer) {
        List<PlayerWarp> current = warps;
        if (current == null) {
            return List.of();
        }

        List<Button> items = new ArrayList<>(current.size());
        for (PlayerWarp warp : current) {
            items.add(buildButton(warp));
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
        return MenuItems.builder(plugin.getConfigManager().getPlayerWarpsListMenu(), "items.empty").build();
    }

    @Override
    protected int getEmptySlot(Player viewer) {
        return MenuItems.slot(plugin.getConfigManager().getPlayerWarpsListMenu(), "items.empty");
    }

    @Override
    protected Map<Integer, Button> getStaticButtons(Player viewer, int currentPage, int maxPage) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsListMenu();
        Map<Integer, Button> buttons = new HashMap<>();

        int infoSlot = MenuItems.slot(cfg, "items.info");
        if (infoSlot >= 0) {
            int count = warps == null ? 0 : warps.size();
            ButtonBuilder info = MenuItems.builder(cfg, "items.info");
            info.lore(MenuItems.apply(MenuItems.lore(cfg, "items.info"),
                    "%count%", count, "%page%", currentPage + 1, "%pages%", maxPage + 1));
            buttons.put(infoSlot, info.build());
        }

        if (currentPage > 0) {
            MenuItems.put(buttons, cfg, "items.previous-page", this::previousPage);
        }
        if (currentPage < maxPage) {
            MenuItems.put(buttons, cfg, "items.next-page", this::nextPage);
        }

        MenuItems.put(buttons, cfg, "items.back", () -> new PlayerWarpMainMenu(plugin, viewer).open());
        MenuItems.put(buttons, cfg, "items.close", viewer::closeInventory);

        MenuItems.putExtras(buttons, cfg, "extra");

        return buttons;
    }

    private Button buildButton(PlayerWarp warp) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsListMenu();
        String path = "items.warp";

        PlayerWarpCategory category = plugin.getPlayerWarpCategoryRegistry().get(warp.getCategoryId());
        String status = "";
        if (warp.isLocked()) {
            status += MenuItems.string(cfg, "items.locked-suffix", " &c[Locked]");
        }
        if (warp.isWhitelistEnabled()) {
            status += MenuItems.string(cfg, "items.whitelist-suffix", " &e[Whitelist]");
        }
        if (warp.hasPassword()) {
            status += MenuItems.string(cfg, "items.password-suffix", " &6[Password]");
        }

        boolean favourite = favouriteIds.contains(warp.getId());
        String favouriteSuffix = favourite ? MenuItems.string(cfg, "items.favourite-suffix", " &d★") : "";

        Object[] placeholders = {
                "%name%", warp.getName(),
                "%owner%", warp.getOwnerName() == null ? "?" : warp.getOwnerName(),
                "%world%", warp.getWorld() == null ? "?" : warp.getWorld(),
                "%category%", category == null ? "-" : category.displayName(),
                "%rating%", String.format(Locale.ROOT, "%.1f", warp.getRatingAverage()),
                "%rating-count%", warp.getRatingCount(),
                "%cost%", warp.isFree() ? plugin.getMessages().raw("placeholders.free")
                        : plugin.getEconomyManager().formatCost(warp.getCost()),
                "%status%", CC.t(status)
        };

        Material material = resolveIcon(warp);

        ButtonBuilder builder = MenuItems.builder(cfg, path).material(material);
        builder.name(MenuItems.apply(MenuItems.name(cfg, path, "&a%name%") + favouriteSuffix, placeholders));
        builder.lore(MenuItems.apply(MenuItems.lore(cfg, path), placeholders));

        if (warp.isUseOwnerHead() && material == Material.PLAYER_HEAD) {
            builder.skullOwner(Bukkit.getOfflinePlayer(warp.getOwner()));
        }

        builder.onClick(event -> {
            boolean rightClick = event.getClick() == ClickType.RIGHT;

            if (mode == Mode.SPONSOR) {
                confirmSponsor(warp);
                return;
            }

            if (mode == Mode.MINE && !rightClick) {
                plugin.getPlayerWarpManagerRoleManager().mayManage(warp, viewer.getUniqueId()).thenAccept(allowed -> {
                    if (!allowed) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> new PlayerWarpManageMenu(plugin, viewer, warp).open());
                });
                return;
            }

            if (rightClick) {
                new PlayerWarpInfoMenu(plugin, viewer, warp).open();
                return;
            }

            viewer.closeInventory();
            plugin.getPlayerWarpManager().attemptTeleport(viewer, warp.getName(), null);
        });

        return builder.build();
    }

    private void confirmSponsor(PlayerWarp warp) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsListMenu();
        int slotIndex = sponsorSlot;

        Object[] placeholders = {
                "%name%", warp.getName(),
                "%cost%", plugin.getEconomyManager().formatCost(plugin.getPlayerWarpSponsorManager().cost()),
                "%hours%", plugin.getPlayerWarpSponsorManager().durationHours()
        };

        ConfirmMenu.confirm(plugin.getMenuManager(), viewer,
                MenuItems.apply(MenuItems.string(cfg, "items.confirm-sponsor.title", "&8Sponsor?"), placeholders),
                new ButtonBuilder().material(resolveIcon(warp)).name(warp.getName()).build(),
                MenuItems.apply(MenuItems.string(cfg, "items.confirm-sponsor.accept", "&a&lSPONSOR"), placeholders),
                MenuItems.apply(cfg.getStringList("items.confirm-sponsor.accept-lore"), placeholders),
                MenuItems.apply(MenuItems.string(cfg, "items.confirm-sponsor.cancel", "&c&lCANCEL"), placeholders),
                ButtonBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").toItemStack(),
                () -> plugin.getPlayerWarpSponsorManager().sponsor(viewer, slotIndex, warp)
                        .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> new PlayerWarpMainMenu(plugin, viewer).open())),
                () -> new PlayerWarpListMenu(plugin, viewer, Mode.SPONSOR, null, slotIndex).open()
        );
    }

    private Material resolveIcon(PlayerWarp warp) {
        if (warp.getIcon() != null) {
            return ButtonBuilder.parseMaterial(warp.getIcon(), Material.GRASS_BLOCK, "player warp " + warp.getName());
        }
        if (warp.isUseOwnerHead()) {
            return Material.PLAYER_HEAD;
        }
        return Material.GRASS_BLOCK;
    }
}
