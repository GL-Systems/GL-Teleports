package org.glstudio.teleports.modules.command.tpa;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.storage.redis.PlayerRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class TpaTargets {
    private TpaTargets() {
    }

    static List<String> suggest(GTeleports plugin, Player sender) {
        Set<String> names = new LinkedHashSet<>();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (sender.canSee(online)) {
                names.add(online.getName());
            }
        }

        PlayerRegistry registry = plugin.getCrossServerManager().getPlayerRegistry();
        if (registry != null) {
            names.addAll(registry.networkPlayerNames());
        }

        names.remove(sender.getName());

        return new ArrayList<>(names);
    }
}
