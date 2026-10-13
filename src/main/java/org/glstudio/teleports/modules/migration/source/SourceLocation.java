package org.glstudio.teleports.modules.migration.source;

/**
 * A position read from another plugin, before it is checked or converted.
 *
 * @param world  the world name, or null when the source only recorded an identifier that could not be resolved
 * @param server the server the source says this position lives on, or null when it does not record one
 */
public record SourceLocation(String world, double x, double y, double z, float yaw, float pitch, String server) {
}
