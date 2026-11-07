package org.glstudio.teleports.modules.command.warp;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.Warp;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code /editwarp} command.
 */
public class EditWarpCommand extends Command {
    private static final List<String> FIELDS = List.of("cost", "cooldown", "delay");

    private final GTeleports plugin;

    /**
     * Creates the command.
     */
    public EditWarpCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("warpedit");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.editwarp"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return;
        }

        String field = args[1].toLowerCase(java.util.Locale.ROOT);

        if (field.equals("permission")) {
            plugin.getWarpManager().setWarpPermission(player, args[0], args.length > 2 ? args[2] : null);
            return;
        }

        if (!FIELDS.contains(field)) {
            sendUsage(sender);
            return;
        }

        if (args.length < 3 || args[2].equalsIgnoreCase("default") || args[2].equalsIgnoreCase("reset")) {
            plugin.getWarpManager().setWarpOverride(player, args[0], field, null);
            return;
        }

        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessages().send(player, "errors.invalid-duration", "{value}", args[2]);
            return;
        }

        if (value < 0) {
            plugin.getMessages().send(player, "errors.invalid-duration", "{value}", args[2]);
            return;
        }

        plugin.getWarpManager().setWarpOverride(player, args[0], field, value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Warp warp : plugin.getWarpManager().getCachedWarps()) {
                names.add(warp.getName());
            }
            return filterTabCompletion(names, args[0]);
        }

        if (args.length == 2) {
            List<String> fields = new ArrayList<>(FIELDS);
            fields.add("permission");
            return filterTabCompletion(fields, args[1]);
        }

        if (args.length == 3 && FIELDS.contains(args[1].toLowerCase(java.util.Locale.ROOT))) {
            return filterTabCompletion(List.of("default", "0", "5", "30", "100"), args[2]);
        }

        return List.of();
    }
}
