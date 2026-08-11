package org.glstudio.teleports.modules.command.home;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class ListHomesCommand extends Command {

    private final GTeleports plugin;

    public ListHomesCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public List<String> usage() {
        return List.of("/listhomes [usuario]");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendUsage(sender);
                return;
            }
            list(sender, player.getUniqueId(), player.getName());
            return;
        }

        OfflinePlayer owner = HomeAdmin.resolve(args[0]);
        if (owner == null) {
            sendMessage(sender, plugin.getConfigManager().getLanguageConfig().getString("errors.player-never-played")
                    .replace("{player}", args[0]));
            return;
        }

        list(sender, owner.getUniqueId(), owner.getName() != null ? owner.getName() : args[0]);
    }

    private void list(CommandSender sender, UUID owner, String ownerName) {
        plugin.getHomeManager().getHomes(owner).thenAccept(homes -> {
            if (homes.isEmpty()) {
                sendMessage(sender, plugin.getConfigManager().getLanguageConfig().getString("errors.no-homes-other")
                        .replace("{player}", ownerName));
                return;
            }

            sendMessage(sender, plugin.getConfigManager().getLanguageConfig().getString("home.list-header")
                    .replace("{player}", ownerName)
                    .replace("{count}", String.valueOf(homes.size())));

            String entry = plugin.getConfigManager().getLanguageConfig().getString("home.list-entry");
            for (Home home : homes) {
                sendMessage(sender, entry
                        .replace("{name}", home.getName())
                        .replace("{world}", home.getWorld())
                        .replace("{x}", String.valueOf(Math.round(home.getX())))
                        .replace("{y}", String.valueOf(Math.round(home.getY())))
                        .replace("{z}", String.valueOf(Math.round(home.getZ())))
                        .replace("{server}", home.getServer() != null ? home.getServer() : "?"));
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        return filterTabCompletion(HomeAdmin.completePlayerArgument(player), args[0]);
    }
}
