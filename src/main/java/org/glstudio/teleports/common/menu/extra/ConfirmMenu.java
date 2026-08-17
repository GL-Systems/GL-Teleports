package org.glstudio.teleports.common.menu.extra;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.glstudio.teleports.common.menu.Menu;
import org.glstudio.teleports.common.menu.MenuManager;
import org.glstudio.teleports.common.menu.button.Button;
import org.glstudio.teleports.common.menu.button.ButtonBuilder;
import org.glstudio.teleports.common.menu.button.ButtonPresets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfirmMenu extends Menu {
    private static final int DISPLAY_SLOT = 13;
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    private final Button displayItem;
    private final String confirmName;
    private final List<String> confirmLore;
    private final String cancelName;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    private boolean decided;

    public ConfirmMenu(MenuManager manager, JavaPlugin plugin, Player p, String title, Button displayItem,
                       String confirmName, List<String> confirmLore, String cancelName,
                       Runnable onConfirm, Runnable onCancel) {
        super(manager, plugin, p, title, 27, false);
        this.displayItem = displayItem;
        this.confirmName = confirmName;
        this.confirmLore = confirmLore;
        this.cancelName = cancelName;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    public Map<Integer, Button> getButtons(Player p) {
        Map<Integer, Button> buttons = new HashMap<>();

        Button pane = ButtonPresets.glassFiller(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 27; slot++) {
            if (slot < 9 || slot >= 18 || slot % 9 == 0 || slot % 9 == 8) {
                buttons.put(slot, pane);
            }
        }

        if (displayItem != null) {
            buttons.put(DISPLAY_SLOT, displayItem);
        }

        ButtonBuilder confirm = new ButtonBuilder()
                .material(Material.LIME_CONCRETE)
                .name(confirmName)
                .onClickSimple(() -> decide(onConfirm));
        if (confirmLore != null && !confirmLore.isEmpty()) {
            confirm.lore(confirmLore);
        }
        buttons.put(CONFIRM_SLOT, confirm.build());

        buttons.put(CANCEL_SLOT, new ButtonBuilder()
                .material(Material.RED_CONCRETE)
                .name(cancelName)
                .onClickSimple(() -> decide(onCancel))
                .build());

        return buttons;
    }

    private void decide(Runnable action) {
        if (decided) {
            return;
        }
        decided = true;
        if (action != null) {
            action.run();
        } else if (p != null && p.isOnline()) {
            p.closeInventory();
        }
    }
}
