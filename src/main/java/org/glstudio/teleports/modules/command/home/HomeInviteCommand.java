package org.glstudio.teleports.modules.command.home;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.Home;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeInviteCommand extends Command {
    private static final long INVALID_DURATION = -1L;

    private final GTeleports plugin;

    public HomeInviteCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("sharehome");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.homeinvite"));
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

        long duration = 0L;

        if (args.length >= 3) {
            duration = parseSeconds(args[2]);

            if (duration == INVALID_DURATION) {
                plugin.getMessages().send(player, "errors.invalid-duration", "{value}", args[2]);
                return;
            }
        }

        plugin.getHomeShareManager().invite(player, args[0], args[1], duration);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            return filterTabCompletion(HomeAdmin.completePlayerArgument(player), args[0]);
        }

        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Home home : plugin.getHomeManager().getCachedHomes(player.getUniqueId())) {
                names.add(home.getName());
            }
            return filterTabCompletion(names, args[1]);
        }

        if (args.length == 3) {
            return filterTabCompletion(List.of("30m", "1h", "12h", "7d"), args[2]);
        }

        return List.of();
    }

    private static long parseSeconds(String input) {
        if (input == null || input.isBlank()) {
            return INVALID_DURATION;
        }

        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        char last = trimmed.charAt(trimmed.length() - 1);

        long multiplier = switch (last) {
            case 's' -> 1L;
            case 'm' -> 60L;
            case 'h' -> 3600L;
            case 'd' -> 86400L;
            default -> 0L;
        };

        String number = multiplier == 0L ? trimmed : trimmed.substring(0, trimmed.length() - 1);

        try {
            long value = Long.parseLong(number);

            return value <= 0 ? INVALID_DURATION : value * (multiplier == 0L ? 1L : multiplier);
        } catch (NumberFormatException e) {
            return INVALID_DURATION;
        }
    }
}
