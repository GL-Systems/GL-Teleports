package org.glstudio.teleports.modules.migration;

import org.glstudio.teleports.modules.migration.source.MigrationData;

import java.nio.file.Path;
import java.util.List;

/**
 * Reads another plugin's homes and warps.
 *
 * An implementation only interprets the foreign format: it never writes anywhere, neither to its source nor to
 * this plugin's storage. Validation, conflict handling and writing are shared, in {@link MigrationPlanner} and
 * {@link MigrationManager}. Reads run on a worker thread, so implementations must not touch the Bukkit API.
 */
public interface MigrationProvider {
    /** The argument of /glteleports migrate, lower case. */
    String id();

    /** Other arguments accepted for this source. */
    List<String> aliases();

    /**
     * Returns the name to show.
     */
    String displayName();

    /** The folder name the source plugin uses under plugins/. */
    String defaultFolder();

    /**
     * Reads the source data.
     */
    MigrationData read(Path folder, WorldDirectory worlds) throws MigrationException;
}
