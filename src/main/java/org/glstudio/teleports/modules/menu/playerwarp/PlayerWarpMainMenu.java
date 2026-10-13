package org.glstudio.teleports.modules.menu.playerwarp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;
import org.glstudio.nexus.modules.menu.Menu;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.modules.menu.button.ButtonBuilder;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.modules.menu.MenuItems;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpSponsoredSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PlayerWarpMainMenu extends Menu {
    private record SponsoredEntry(PlayerWarpSponsoredSlot slot, PlayerWarp warp) {
    }

    private final GTeleports plugin;

    private volatile List<SponsoredEntry> sponsoredEntries = List.of();
    private volatile boolean loading = true;

    public PlayerWarpMainMenu(GTeleports plugin, Player viewer) {
        super(plugin.getMenuManager(), viewer,
                org.glstudio.nexus.utils.CC.t(MenuItems.string(plugin.getConfigManager().getPlayerWarpsMainMenu(),
                        "title", "&8Player Warps")),
                MenuItems.integer(plugin.getConfigManager().getPlayerWarpsMainMenu(), "size", 36));
        this.plugin = plugin;
        setAutoRefresh(true);
        load();
    }

    private void load() {
        if (!plugin.getPlayerWarpSponsorManager().isEnabled()) {
            loading = false;
            return;
        }

        Failures.log(plugin.getPlayerWarpSponsorManager().getActiveSlots().thenCompose(this::resolveEntries)
                .thenAccept(entries -> {
                    sponsoredEntries = entries;
                    loading = false;
                    Bukkit.getScheduler().runTask(plugin, this::update);
                }), "PlayerWarpMainMenu#load");
    }

    private CompletableFuture<List<SponsoredEntry>> resolveEntries(List<PlayerWarpSponsoredSlot> slots) {
        List<CompletableFuture<SponsoredEntry>> resolved = new ArrayList<>(slots.size());

        for (PlayerWarpSponsoredSlot slot : slots) {
            resolved.add(plugin.getPlayerWarpManager().getWarp(slot.warpId())
                    .thenApply(warp -> warp == null ? null : new SponsoredEntry(slot, warp)));
        }

        return CompletableFuture.allOf(resolved.toArray(new CompletableFuture[0])).thenApply(ignored -> {
            List<SponsoredEntry> entries = new ArrayList<>(resolved.size());
            for (CompletableFuture<SponsoredEntry> future : resolved) {
                SponsoredEntry entry = future.join();
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return entries;
        });
    }

    @Override
    protected boolean shouldRefresh() {
        return loading;
    }

    @Override
    protected void render() {
        clear();
        for (Map.Entry<Integer, Button> entry : getButtons(viewer).entrySet()) {
            place(entry.getKey(), entry.getValue());
        }
    }

    private Map<Integer, Button> getButtons(Player p) {
        ConfigFile cfg = plugin.getConfigManager().getPlayerWarpsMainMenu();
        Map<Integer, Button> buttons = new HashMap<>();

        if (plugin.getPlayerWarpSponsorManager().isEnabled()) {
            putSponsoredSlots(buttons, cfg, p);
        }

        MenuItems.put(buttons, cfg, "items.browse",
                () -> new PlayerWarpListMenu(plugin, p, PlayerWarpListMenu.Mode.ALL, null).open());
        MenuItems.put(buttons, cfg, "items.categories",
                () -> new PlayerWarpCategoryMenu(plugin, p, null).open());
        MenuItems.put(buttons, cfg, "items.favourites",
                () -> new PlayerWarpListMenu(plugin, p, PlayerWarpListMenu.Mode.FAVOURITES, null).open());
        MenuItems.put(buttons, cfg, "items.my-warps",
                () -> new PlayerWarpListMenu(plugin, p, PlayerWarpListMenu.Mode.MINE, null).open());

        MenuItems.put(buttons, cfg, "items.search", () -> {
            p.closeInventory();
            plugin.getChatInputManager().request(p,
                    MenuItems.prompt(plugin, "player-warp.prompts.search"),
                    query -> new PlayerWarpListMenu(plugin, p, PlayerWarpListMenu.Mode.SEARCH, query).open(),
                    () -> new PlayerWarpMainMenu(plugin, p).open());
        });

        MenuItems.put(buttons, cfg, "items.close", p::closeInventory);

        MenuItems.putExtras(buttons, cfg, "extra");

        return buttons;
    }

    private void putSponsoredSlots(Map<Integer, Button> buttons, ConfigFile cfg, Player viewer) {
        List<Integer> guiSlots = MenuItems.intList(cfg, "sponsored-slots");
        int slotCount = Math.min(guiSlots.size(), plugin.getPlayerWarpSponsorManager().slotCount());

        for (int index = 0; index < slotCount; index++) {
            int guiSlot = guiSlots.get(index);
            int slotIndex = index;

            SponsoredEntry entry = sponsoredEntries.stream()
                    .filter(e -> e.slot().slotIndex() == slotIndex)
                    .findFirst()
                    .orElse(null);

            buttons.put(guiSlot, entry == null
                    ? buildEmptySlot(cfg, viewer, slotIndex)
                    : buildFilledSlot(cfg, viewer, entry));
        }
    }

    private Button buildEmptySlot(ConfigFile cfg, Player viewer, int slotIndex) {
        Object[] placeholders = {
                "%cost%", plugin.getEconomyManager().formatCost(plugin.getPlayerWarpSponsorManager().cost()),
                "%hours%", plugin.getPlayerWarpSponsorManager().durationHours()
        };

        ButtonBuilder builder = MenuItems.builder(cfg, "items.sponsored-empty");
        builder.name(MenuItems.apply(MenuItems.name(cfg, "items.sponsored-empty", "&6Sponsored Slot"),
                placeholders));
        builder.lore(MenuItems.apply(MenuItems.lore(cfg, "items.sponsored-empty"), placeholders));
        builder.onClickSimple(() -> {
            viewer.closeInventory();
            new PlayerWarpListMenu(plugin, viewer, PlayerWarpListMenu.Mode.SPONSOR, null, slotIndex).open();
        });

        return builder.build();
    }

    private Button buildFilledSlot(ConfigFile cfg, Player viewer, SponsoredEntry entry) {
        PlayerWarp warp = entry.warp();
        long remainingSeconds = entry.slot().remainingSeconds();
        String remaining = (remainingSeconds / 3600) + "h " + ((remainingSeconds % 3600) / 60) + "m";

        Object[] placeholders = {
                "%name%", warp.getName(),
                "%owner%", entry.slot().ownerName() == null ? "?" : entry.slot().ownerName(),
                "%remaining%", remaining
        };

        Material material = warp.getIcon() != null
                ? ButtonBuilder.parseMaterial(warp.getIcon(), Material.GOLD_BLOCK, "sponsored slot")
                : Material.GOLD_BLOCK;

        ButtonBuilder builder = MenuItems.builder(cfg, "items.sponsored-filled").material(material);
        builder.name(MenuItems.apply(MenuItems.name(cfg, "items.sponsored-filled", "&6%name%"), placeholders));
        builder.lore(MenuItems.apply(MenuItems.lore(cfg, "items.sponsored-filled"), placeholders));
        builder.onClickSimple(() -> {
            viewer.closeInventory();
            plugin.getPlayerWarpManager().attemptTeleport(viewer, warp.getName(), null);
        });

        return builder.build();
    }
}
