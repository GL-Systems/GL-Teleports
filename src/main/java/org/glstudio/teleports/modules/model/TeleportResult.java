package org.glstudio.teleports.modules.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TeleportResult {
    private final boolean success;
    private final String message;

    public static TeleportResult success() {
        return new TeleportResult(true, null);
    }

    public static TeleportResult failure(String message) {
        return new TeleportResult(false, message);
    }
}
