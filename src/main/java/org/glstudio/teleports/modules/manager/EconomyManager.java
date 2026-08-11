package org.glstudio.teleports.modules.manager;

import lombok.Getter;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.nexus.utils.LoggerUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private final GTeleports plugin;
    @Getter
    private Economy economy;
    @Getter
    private boolean enabled;

    public EconomyManager(GTeleports plugin) {
        this.plugin = plugin;
        this.enabled = setupEconomy();
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            LoggerUtils.logInfo("Vault not found! Economy features disabled.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            LoggerUtils.logInfo("Economy provider not found! Economy features disabled.");
            return false;
        }

        economy = rsp.getProvider();
        LoggerUtils.logSuccess("Economy hooked successfully!");
        return true;
    }

    public boolean hasCost(TeleportType type) {
        if (!enabled) return false;

        String path = "teleport-settings." + type.getConfigKey() + ".cost";
        return plugin.getConfigManager().getMainConfig().getDouble(path, 0.0) > 0;
    }

    public double getCost(TeleportType type) {
        if (!enabled) return 0.0;

        String path = "teleport-settings." + type.getConfigKey() + ".cost";
        return plugin.getConfigManager().getMainConfig().getDouble(path, 0.0);
    }

    public boolean hasEnoughMoney(Player player, TeleportType type) {
        if (!enabled || !hasCost(type)) return true;

        double cost = getCost(type);
        return economy.has(player, cost);
    }

    public boolean takeMoney(Player player, TeleportType type) {
        if (!enabled || !hasCost(type)) return true;

        double cost = getCost(type);
        if (!hasEnoughMoney(player, type)) {
            return false;
        }

        return economy.withdrawPlayer(player, cost).transactionSuccess();
    }

    public String formatCost(TeleportType type) {
        if (!enabled) return "0";

        double cost = getCost(type);
        return economy.format(cost);
    }
}
