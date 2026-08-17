package org.glstudio.teleports.common.menu.listener;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.glstudio.teleports.common.menu.Menu;
import org.glstudio.teleports.common.menu.MenuManager;
import org.glstudio.teleports.common.menu.button.Button;
import org.glstudio.nexus.utils.LoggerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuListener implements Listener {
    private static final long CLICK_COOLDOWN_MS = 200L;

    private final MenuManager menuManager;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public MenuListener(MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }

        Menu menu = menuManager.get(p.getUniqueId());
        if (menu == null || menu.isDestroyed()) {
            return;
        }

        if (!menu.getInventory().equals(e.getView().getTopInventory())) {
            menuManager.unregister(p.getUniqueId());
            return;
        }

        if (!menu.isAllowInteract()) {
            e.setCancelled(true);
        }

        if (e.isCancelled() && menu.isAllowInteract()) {
            return;
        }

        menu.onClick(e);

        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(menu.getInventory())) {
            return;
        }

        Map<Integer, Button> buttons = menu.getButtons();
        if (buttons == null) {
            return;
        }
        Button button = buttons.get(e.getRawSlot());
        if (button == null) {
            return;
        }

        if (isClickingTooFast(p.getUniqueId())) {
            return;
        }

        try {
            button.onClick(e);
        } catch (Exception ex) {
            LoggerUtils.logException("Handling menu click for " + p.getName(), ex);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        Menu menu = menuManager.get(p.getUniqueId());
        if (menu == null || menu.isDestroyed() || menu.isAllowInteract()) {
            return;
        }
        if (menu.getInventory().equals(e.getView().getTopInventory())) {
            e.setCancelled(true);
        }
    }

    private boolean isClickingTooFast(UUID playerId) {
        long now = System.currentTimeMillis();
        Long previous = lastClick.put(playerId, now);
        return previous != null && now - previous < CLICK_COOLDOWN_MS;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        menuManager.unregister(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        menuManager.unregister(e.getPlayer().getUniqueId());
        lastClick.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        HumanEntity human = e.getPlayer();
        if (!(human instanceof Player p)) {
            return;
        }
        Menu menu = menuManager.get(p.getUniqueId());
        if (menu != null && menu.getInventory().equals(e.getInventory())) {
            menuManager.unregister(p.getUniqueId());
        }
    }
}
