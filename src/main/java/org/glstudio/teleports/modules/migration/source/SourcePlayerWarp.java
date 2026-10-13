package org.glstudio.teleports.modules.migration.source;

import java.util.List;
import java.util.UUID;

/**
 * A player warp read from another plugin, before it is checked or converted.
 *
 * @param sourceId   the source's own identifier, which its related records point at and which makes the import
 *                   repeatable: the same source warp always becomes the same warp here
 * @param categories every category the source filed the warp under, in the source's order
 * @param icon       the icon's material name, or null when the source used its default icon or the icon could
 *                   not be decoded (the reader reports the latter)
 * @param cost       the price to use the warp, or null when it is free
 * @param password   the plain-text password the source stored, or null; it is hashed before it is written
 * @param lastUsedAt the last time anyone visited it according to the source, or 0 when unknown
 */
public record SourcePlayerWarp(long sourceId, UUID owner, String ownerName, String name, SourceLocation location,
                               String description, List<String> categories, String icon, Double cost,
                               boolean locked, String password, boolean whitelistEnabled, long createdAt,
                               long lastUsedAt, String origin) {
}
