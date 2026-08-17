package org.glstudio.teleports.common.menu.button;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

@Getter
public class Button {
    private final ItemStack itemStack;
    private final Consumer<InventoryClickEvent> clickAction;

    public Button(ItemStack itemStack) {
        this(itemStack, null);
    }

    public Button(ItemStack itemStack, Consumer<InventoryClickEvent> clickAction) {
        this.itemStack = itemStack == null ? new ItemStack(Material.AIR) : itemStack;
        this.clickAction = clickAction;
    }

    public void onClick(InventoryClickEvent e) {
        if (clickAction != null) {
            clickAction.accept(e);
        }
    }

    public boolean isInteractive() {
        return clickAction != null;
    }

    public static ButtonBuilder builder() {
        return new ButtonBuilder();
    }

    public static Button display(ItemStack itemStack) {
        return new Button(itemStack);
    }

    public static Button filler(Material material) {
        return new ButtonBuilder().material(material).name(" ").hideAll().build();
    }
}
