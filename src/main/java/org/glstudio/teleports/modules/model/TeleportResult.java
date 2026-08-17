package org.glstudio.teleports.modules.model;

import lombok.Getter;

@Getter
public class TeleportResult {
    private static final String[] NO_PLACEHOLDERS = new String[0];

    private final boolean success;
    private final String message;

    private final String[] placeholders;

    private TeleportResult(boolean success, String message, String[] placeholders) {
        this.success = success;
        this.message = message;
        this.placeholders = placeholders;
    }

    public static TeleportResult success() {
        return new TeleportResult(true, null, NO_PLACEHOLDERS);
    }

    public static TeleportResult failure(String message, String... placeholders) {
        return new TeleportResult(false, message, placeholders);
    }
}
