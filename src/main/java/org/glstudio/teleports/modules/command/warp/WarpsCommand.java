package org.glstudio.teleports.modules.command.warp;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.Failures;
import org.glstudio.teleports.modules.model.Warp;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class WarpsCommand extends Command {
    private static final int PAGE_SIZE = 8;

    private final GTeleports plugin;

    public WarpsCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("warplist");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.warps"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        int requestedPage = parsePage(args);

        Failures.guard(plugin, plugin.getWarpManager().getWarps().thenAccept(all -> {
            List<Warp> visible = new ArrayList<>();
            for (Warp warp : all) {
                if (!warp.hasPermission() || sender.hasPermission(warp.getPermission())) {
                    visible.add(warp);
                }
            }

            if (visible.isEmpty()) {
                plugin.getMessages().send(sender, "errors.no-warps");
                return;
            }

            int pages = (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            int page = Math.min(requestedPage, pages);
            int from = (page - 1) * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, visible.size());

            plugin.getMessages().send(sender, "warp.list-header",
                    "{count}", String.valueOf(visible.size()),
                    "{page}", String.valueOf(page),
                    "{pages}", String.valueOf(pages));

            for (Warp warp : visible.subList(from, to)) {
                plugin.getMessages().send(sender, "warp.list-entry",
                        "{name}", warp.getName(),
                        "{world}", warp.getWorld() != null ? warp.getWorld() : "?",
                        "{x}", String.valueOf(Math.round(warp.getX())),
                        "{y}", String.valueOf(Math.round(warp.getY())),
                        "{z}", String.valueOf(Math.round(warp.getZ())),
                        "{server}", warp.getServer() != null ? warp.getServer() : "?");
            }
        }), "WarpsCommand#list", sender);
    }

    private int parsePage(String[] args) {
        if (args.length == 0) {
            return 1;
        }

        try {
            return Math.max(1, Integer.parseInt(args[0]));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
