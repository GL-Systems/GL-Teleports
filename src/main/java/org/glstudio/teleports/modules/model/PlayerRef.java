package org.glstudio.teleports.modules.model;

import java.util.UUID;

/**
 * A player's id and name.
 */
public record PlayerRef(UUID uuid, String name) {
}
