package org.glstudio.teleports.modules.migration.source;

/**
 * @param permission the node the source required to use this warp, or null when it was open to everyone
 */
public record SourceWarp(String name, SourceLocation location, String permission, long createdAt, String origin) {
}
