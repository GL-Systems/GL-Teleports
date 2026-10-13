package org.glstudio.teleports.modules.migration.source;

import java.util.UUID;

/**
 * @param origin where the entry came from, for reports: a file name or a table row
 */
public record SourceHome(UUID owner, String ownerName, String name, SourceLocation location, long createdAt,
                         boolean publicHome, String origin) {
}
