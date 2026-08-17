package org.glstudio.teleports.modules.manager;

import lombok.Getter;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.Permissions;
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

    public static final String BYPASS_PERMISSION = Permissions.BYPASS_COST;

    public boolean hasCost(TeleportType type) {
        if (!enabled) return false;

        String path = "teleport-settings." + type.getConfigKey() + ".cost";
        return plugin.getSettings().getDouble(path, 0.0) > 0;
    }

    public double getCost(TeleportType type) {
        if (!enabled) return 0.0;

        String path = "teleport-settings." + type.getConfigKey() + ".cost";
        return plugin.getSettings().getDouble(path, 0.0);
    }

    public boolean hasEnoughMoney(Player player, TeleportType type) {
        return hasEnoughMoney(player, getCost(type));
    }

    public boolean takeMoney(Player player, TeleportType type) {
        return takeMoney(player, getCost(type));
    }

    public boolean refund(Player player, TeleportType type) {
        return refund(player, getCost(type));
    }

    public String formatCost(TeleportType type) {
        return formatCost(getCost(type));
    }

    private boolean chargeable(Player player, double cost) {
        return enabled && cost > 0 && !player.hasPermission(BYPASS_PERMISSION);
    }

    public boolean hasEnoughMoney(Player player, double cost) {
        if (!chargeable(player, cost)) return true;

        return economy.has(player, cost);
    }

    public boolean takeMoney(Player player, double cost) {
        if (!chargeable(player, cost)) return true;

        if (!economy.has(player, cost)) {
            return false;
        }

        return economy.withdrawPlayer(player, cost).transactionSuccess();
    }

    public boolean refund(Player player, double cost) {
        if (!chargeable(player, cost)) return false;

        return economy.depositPlayer(player, cost).transactionSuccess();
    }

    public String formatCost(double cost) {
        if (!enabled) return "0";

        return economy.format(cost);
    }
}
