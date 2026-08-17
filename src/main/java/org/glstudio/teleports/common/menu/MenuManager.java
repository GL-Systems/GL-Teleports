package org.glstudio.teleports.common.menu;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.glstudio.teleports.common.menu.listener.MenuListener;
import org.glstudio.nexus.utils.LoggerUtils;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class MenuManager {
    private final JavaPlugin plugin;
    private final Map<UUID, Menu> menus = new ConcurrentHashMap<>();

    private final Set<String> reportedWarnings = ConcurrentHashMap.newKeySet();

    private MenuListener menuListener;

    public MenuManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.menuListener = new MenuListener(this);
        plugin.getServer().getPluginManager().registerEvents(menuListener, plugin);
    }

    public void register(UUID playerId, Menu menu) {
        Menu previous = menus.put(playerId, menu);
        if (previous != null && previous != menu) {
            previous.destroy();
        }
    }

    public Menu get(UUID playerId) {
        return menus.get(playerId);
    }

    public void unregister(UUID playerId) {
        Menu menu = menus.remove(playerId);
        if (menu != null) {
            try {
                menu.onClose();
            } catch (Exception e) {
                LoggerUtils.logException("Closing menu for " + playerId, e);
            }
            menu.destroy();
        }
    }

    public void closeAll() {
        for (UUID playerId : new ArrayList<>(menus.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            unregister(playerId);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        menus.clear();
    }

    public void warnOnce(String key, String message) {
        if (reportedWarnings.add(key)) {
            LoggerUtils.logWarn(message);
        }
    }

    public void resetWarnings() {
        reportedWarnings.clear();
    }
}
