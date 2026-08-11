package org.glstudio.teleports.modules.model;

import lombok.Getter;

@Getter
public enum TeleportType {
    HOME("home"),
    WARP("warp"),
    TPA("tpa"),
    TPAHERE("tpahere"),
    SPAWN("spawn");

    private final String configKey;

    TeleportType(String configKey) {
        this.configKey = configKey;
    }
}
