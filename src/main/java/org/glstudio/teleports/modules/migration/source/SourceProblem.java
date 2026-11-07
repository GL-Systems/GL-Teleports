package org.glstudio.teleports.modules.migration.source;

/**
 * Something that could not be read from the source.
 *
 * @param origin where it was
 * @param reason why it failed
 */
public record SourceProblem(String origin, String reason) {
}
