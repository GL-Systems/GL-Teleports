package org.glstudio.teleports.modules.migration;

import org.glstudio.teleports.modules.migration.source.SourceLocation;

import java.util.Locale;
import java.util.Map;

/**
 * @param multiServer     whether multi-server is enabled; when off every location belongs to {@code currentServer}
 * @param currentServer   this server's multi-server.server-name, which is what locally created homes are saved with
 * @param fallbackServer  migrate.server: the server for entries whose source does not say which one they live on
 * @param useSourceServer migrate.use-source-server: trust the server a source recorded, when it records one
 * @param worldMappings  lower-case source world name to destination world name, only for this migration
 */
public record MigrationOptions(boolean multiServer, String currentServer, String fallbackServer,
                               boolean useSourceServer, ConflictPolicy policy, Map<String, String> worldMappings,
                               long now) {
    /** Creates the options, copying what it holds. */
    public MigrationOptions {
        worldMappings = Map.copyOf(worldMappings);
    }

    /**
     * Renames the world of a location as configured.
     */
    public SourceLocation mapWorld(SourceLocation source) {
        String world = source.world();
        String target = world == null ? null : worldMappings.get(world.toLowerCase(Locale.ROOT));
        if (target == null || target.equals(world)) {
            return source;
        }
        return new SourceLocation(target, source.x(), source.y(), source.z(), source.yaw(), source.pitch(),
                source.server());
    }
}
