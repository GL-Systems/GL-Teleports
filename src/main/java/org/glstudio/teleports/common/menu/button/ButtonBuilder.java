package org.glstudio.teleports.common.menu.button;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.LoggerUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class ButtonBuilder {
    private ItemStack itemStack;
    private Consumer<InventoryClickEvent> clickAction;

    public ButtonBuilder() {
        this.itemStack = new ItemStack(Material.STONE);
    }

    public ButtonBuilder material(Material material) {
        this.itemStack.setType(material == null ? Material.STONE : material);
        return this;
    }

    public ButtonBuilder amount(int amount) {
        this.itemStack.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public ButtonBuilder name(String name) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ButtonBuilder lore(List<String> lore) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setLore(lore == null ? new ArrayList<>() : new ArrayList<>(lore));
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ButtonBuilder lore(String... lore) {
        return lore(Arrays.asList(lore));
    }

    public ButtonBuilder addLore(String line) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();
            lore.add(line);
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ButtonBuilder glow() {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return this;
        }
        try {
            meta.setEnchantmentGlintOverride(true);
        } catch (Throwable ignored) {
            org.bukkit.enchantments.Enchantment enchantment =
                    org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            if (enchantment != null) {
                meta.addEnchant(enchantment, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        itemStack.setItemMeta(meta);
        return this;
    }

    public ButtonBuilder flags(ItemFlag... flags) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(flags);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ButtonBuilder hideAll() {
        return flags(ItemFlag.values());
    }

    public ButtonBuilder customModelData(int data) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(data);
            itemStack.setItemMeta(meta);
        }
        return this;
    }

    public ButtonBuilder onClick(Consumer<InventoryClickEvent> action) {
        this.clickAction = action;
        return this;
    }

    public ButtonBuilder onClickPlayer(Consumer<Player> action) {
        this.clickAction = e -> action.accept((Player) e.getWhoClicked());
        return this;
    }

    public ButtonBuilder onClickSimple(Runnable action) {
        this.clickAction = e -> action.run();
        return this;
    }

    public ButtonBuilder skullOwner(OfflinePlayer owner) {
        if (itemStack.getType() != Material.PLAYER_HEAD || owner == null) {
            return this;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(owner);
            itemStack.setItemMeta(skullMeta);
        }
        return this;
    }

    public Button build() {
        return new Button(itemStack.clone(), clickAction);
    }

    public static ButtonBuilder fromConfig(ConfigurationSection section) {
        ButtonBuilder builder = new ButtonBuilder();
        if (section == null) {
            return builder;
        }

        builder.material(parseMaterial(section.getString("MATERIAL", "STONE"), Material.STONE,
                section.getCurrentPath()));
        builder.amount(section.getInt("AMOUNT", 1));

        String name = section.getString("NAME");
        if (name != null) {
            builder.name(CC.t(name));
        }

        List<String> lore = section.getStringList("LORE");
        if (!lore.isEmpty()) {
            builder.lore(CC.t(lore));
        }

        if (section.contains("CUSTOM_MODEL_DATA")) {
            builder.customModelData(section.getInt("CUSTOM_MODEL_DATA"));
        }

        if (section.getBoolean("GLOW", false)) {
            builder.glow();
        }

        return builder;
    }

    public static Material parseMaterial(String name, Material fallback, String context) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LoggerUtils.logWarn("Unknown material '" + name + "'"
                    + (context == null ? "" : " at " + context) + "; using " + fallback + ".");
            return fallback;
        }
    }
}
