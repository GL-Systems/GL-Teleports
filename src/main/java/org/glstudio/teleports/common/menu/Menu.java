package org.glstudio.teleports.common.menu;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.glstudio.teleports.common.menu.button.Button;
import org.glstudio.nexus.utils.LoggerUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
public abstract class Menu {
    private static final int MIN_SIZE = 9;
    private static final int MAX_SIZE = 54;
    private static final long REFRESH_PERIOD_TICKS = 10L;

    private final JavaPlugin plugin;
    private final MenuManager manager;

    protected Map<Integer, Button> buttons = Collections.emptyMap();
    protected Player p;
    protected Inventory inventory;
    protected BukkitTask updater;
    protected String title;
    protected int size;

    protected ItemStack filler;
    protected boolean fillEnabled;
    protected boolean allowInteract;

    private final boolean autoRefresh;
    private boolean destroyed;

    public Menu(MenuManager manager, Player p, String title, int size, boolean autoRefresh) {
        this(manager, manager.getPlugin(), p, title, size, autoRefresh);
    }

    public Menu(MenuManager manager, JavaPlugin plugin, Player p, String title, int size, boolean autoRefresh) {
        this.manager = manager;
        this.plugin = plugin;
        this.p = p;
        this.title = title == null ? "" : title;
        this.size = normaliseSize(size);
        this.autoRefresh = autoRefresh;
        this.fillEnabled = false;
        this.allowInteract = false;
        this.inventory = Bukkit.createInventory(null, this.size, this.title);
    }

    private int normaliseSize(int configured) {
        int clamped = Math.min(MAX_SIZE, Math.max(MIN_SIZE, configured));
        if (clamped % 9 != 0) {
            clamped = Math.min(MAX_SIZE, ((clamped / 9) + 1) * 9);
        }
        if (clamped != configured) {
            LoggerUtils.logWarn("Menu size " + configured + " is not a valid chest size; using " + clamped + ".");
        }
        return clamped;
    }

    public void open() {
        if (p == null || !p.isOnline()) {
            return;
        }
        this.destroyed = false;
        render();
        p.openInventory(inventory);
        manager.register(p.getUniqueId(), this);
        startUpdater();
    }

    protected boolean shouldRefresh() {
        return true;
    }

    private void startUpdater() {
        if (!autoRefresh || updater != null) {
            return;
        }
        updater = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (destroyed || p == null || !p.isOnline()) {
                destroy();
                return;
            }
            if (shouldRefresh()) {
                update();
            }
        }, REFRESH_PERIOD_TICKS, REFRESH_PERIOD_TICKS);
    }

    public void update() {
        if (destroyed || p == null || !p.isOnline()) {
            return;
        }
        render();
    }

    private void render() {
        Map<Integer, Button> resolved;
        try {
            resolved = getButtons(p);
        } catch (Exception e) {
            LoggerUtils.logException("Building menu " + getClass().getSimpleName(), e);
            return;
        }

        this.buttons = sanitise(resolved);

        inventory.clear();
        for (Map.Entry<Integer, Button> entry : buttons.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue().getItemStack());
        }

        if (fillEnabled && filler != null) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack item = inventory.getItem(slot);
                if (item == null || item.getType() == Material.AIR) {
                    inventory.setItem(slot, filler);
                }
            }
        }
    }

    private Map<Integer, Button> sanitise(Map<Integer, Button> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, Button> valid = new HashMap<>(source.size());
        for (Map.Entry<Integer, Button> entry : source.entrySet()) {
            Integer slot = entry.getKey();
            if (slot == null || slot < 0 || slot >= size || entry.getValue() == null) {
                manager.warnOnce(getClass().getSimpleName() + "#" + slot,
                        "Menu " + getClass().getSimpleName() + " tried to place an item in slot " + slot
                                + ", which is outside its " + size + "-slot inventory. Check the SLOT values "
                                + "in the matching menus/*.yml file.");
                continue;
            }
            valid.put(slot, entry.getValue());
        }
        return valid;
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        buttons = Collections.emptyMap();
        if (updater != null) {
            updater.cancel();
            updater = null;
        }
        onDestroy();
    }

    protected void onDestroy() {
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void onClick(InventoryClickEvent e) {
    }

    public void onClose() {
    }

    public abstract Map<Integer, Button> getButtons(Player p);

    protected void setFiller(ItemStack filler) {
        this.filler = filler;
        this.fillEnabled = filler != null;
    }

    protected void setAllowInteract(boolean allowInteract) {
        this.allowInteract = allowInteract;
    }

    public int[] getCenterSlots() {
        int rows = size / 9;
        int interior = Math.max(0, (rows - 2) * 7);
        int[] slots = new int[interior];
        if (interior == 0) {
            return slots;
        }
        int index = 0;
        for (int row = 1; row < rows - 1; row++) {
            for (int column = 1; column < 8; column++) {
                slots[index++] = row * 9 + column;
            }
        }
        return slots;
    }
}
