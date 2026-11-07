package org.glstudio.teleports.modules.model;

import lombok.Getter;

/**
 * The kinds of teleport, each with its own settings.
 */
@Getter
public enum TeleportType {
    /**
     * Home.
     */
    HOME("home"),
    /**
     * Warp.
     */
    WARP("warp"),
    /**
     * Player warp.
     */
    PLAYER_WARP("player-warp"),
    /**
     * Back.
     */
    BACK("back"),
    /**
     * Tpa.
     */
    TPA("tpa"),
    /**
     * Tpahere.
     */
    TPAHERE("tpahere"),
    /**
     * Spawn.
     */
    SPAWN("spawn");

    private final String configKey;

    TeleportType(String configKey) {
        this.configKey = configKey;
    }
}
