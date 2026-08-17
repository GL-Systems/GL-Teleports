package org.glstudio.teleports.common.menu.paginated;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.glstudio.teleports.common.menu.Menu;
import org.glstudio.teleports.common.menu.MenuManager;
import org.glstudio.teleports.common.menu.button.Button;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public abstract class PaginatedMenu extends Menu {
    protected int page;

    public PaginatedMenu(MenuManager manager, JavaPlugin plugin, Player p, String title, int size,
                         boolean autoRefresh) {
        super(manager, plugin, p, title, size, autoRefresh);
    }

    protected abstract List<Integer> getContentSlots();

    protected abstract List<Button> getPageItems(Player p);

    protected abstract Map<Integer, Button> getStaticButtons(Player p, int currentPage, int maxPage);

    protected Button getEmptyButton(Player p) {
        return null;
    }

    @Override
    public Map<Integer, Button> getButtons(Player p) {
        List<Integer> contentSlots = getContentSlots();
        List<Button> items = getPageItems(p);

        int perPage = Math.max(1, contentSlots.size());
        int maxPage = items.isEmpty() ? 0 : (items.size() - 1) / perPage;
        int currentPage = Math.min(Math.max(0, page), maxPage);

        Map<Integer, Button> buttons = new HashMap<>(getStaticButtons(p, currentPage, maxPage));

        if (items.isEmpty()) {
            Button empty = getEmptyButton(p);
            if (empty != null && !contentSlots.isEmpty()) {
                buttons.put(contentSlots.get(0), empty);
            }
            return buttons;
        }

        int start = currentPage * perPage;
        int end = Math.min(start + perPage, items.size());
        for (int i = start; i < end; i++) {
            buttons.put(contentSlots.get(i - start), items.get(i));
        }
        return buttons;
    }

    public int getMaxPage(int itemCount) {
        int perPage = Math.max(1, getContentSlots().size());
        return itemCount == 0 ? 0 : (itemCount - 1) / perPage;
    }

    public void goToPage(int target) {
        this.page = Math.max(0, target);
        update();
    }

    public void nextPage() {
        goToPage(page + 1);
    }

    public void previousPage() {
        goToPage(page - 1);
    }
}
