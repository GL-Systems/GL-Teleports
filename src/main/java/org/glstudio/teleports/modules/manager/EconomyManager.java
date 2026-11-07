package org.glstudio.teleports.modules.manager;

import org.bukkit.entity.Player;
import org.glstudio.nexus.hooks.economy.VaultEconomy;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.model.TeleportType;

/**
 * Charges and refunds teleport costs.
 */
public class EconomyManager {
    private final GTeleports plugin;
    private final VaultEconomy economy = VaultEconomy.get();

    /**
     * Creates the manager.
     */
    public EconomyManager(GTeleports plugin) {
        this.plugin = plugin;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            LoggerUtils.logInfo("Vault not found! Economy features disabled.");
        } else if (!economy.isAvailable()) {
            LoggerUtils.logInfo("Economy provider not found! Economy features disabled.");
        } else {
            LoggerUtils.logSuccess("Economy hooked successfully!");
        }
    }

    /**
     * Tells whether an economy provider is available.
     */
    public boolean isEnabled() {
        return economy.isAvailable();
    }

    /**
     * Returns the balance.
     */
    public double getBalance(Player player) {
        return economy.balance(player);
    }

    public static final String BYPASS_PERMISSION = Permissions.BYPASS_COST;

    /**
     * Tells whether a teleport type costs money.
     */
    public boolean hasCost(TeleportType type) {
        if (!isEnabled()) return false;

        String path = "teleport-settings." + type.getConfigKey() + ".cost";
        return plugin.getSettings().getDouble(path, 0.0) > 0;
    }

    /**
     * Returns the cost.
     */
    public double getCost(TeleportType type) {
        if (!isEnabled()) return 0.0;

        String path = "teleport-settings." + type.getConfigKey() + ".cost";
        return plugin.getSettings().getDouble(path, 0.0);
    }

    /**
     * Tells whether a player can pay.
     */
    public boolean hasEnoughMoney(Player player, TeleportType type) {
        return hasEnoughMoney(player, getCost(type));
    }

    /**
     * Takes the money.
     */
    public boolean takeMoney(Player player, TeleportType type) {
        return takeMoney(player, getCost(type));
    }

    /**
     * Gives back what a teleport cost.
     */
    public boolean refund(Player player, TeleportType type) {
        return refund(player, getCost(type));
    }

    /**
     * Formats the cost.
     */
    public String formatCost(TeleportType type) {
        return formatCost(getCost(type));
    }

    /**
     * Tells whether a player would pay this cost.
     */
    public boolean chargeable(Player player, double cost) {
        return isEnabled() && cost > 0 && !player.hasPermission(BYPASS_PERMISSION);
    }

    /**
     * Tells whether a player can pay.
     */
    public boolean hasEnoughMoney(Player player, double cost) {
        if (!chargeable(player, cost)) return true;

        return economy.has(player, cost);
    }

    /**
     * Takes the money.
     */
    public boolean takeMoney(Player player, double cost) {
        if (!chargeable(player, cost)) return true;

        if (!economy.has(player, cost)) {
            return false;
        }

        return economy.withdraw(player, cost).success();
    }

    /**
     * Gives back what a teleport cost.
     */
    public boolean refund(Player player, double cost) {
        if (!chargeable(player, cost)) return false;

        return economy.deposit(player, cost).success();
    }

    /**
     * Formats the cost.
     */
    public String formatCost(double cost) {
        if (!isEnabled()) return "0";

        return economy.format(cost);
    }
}
