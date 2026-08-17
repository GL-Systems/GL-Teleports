package org.glstudio.teleports.modules.model;

import java.util.Locale;

public record DestinationLock(String type,
                              String target,
                              String reason,
                              String lockedBy,
                              long lockedAt,
                              long until) {
    public static final String WARP = "warp";
    public static final String SERVER = "server";

    public static final long INDEFINITE = 0L;

    public static DestinationLock of(String type, String target, String reason, String lockedBy, long until) {
        return new DestinationLock(normalize(type), normalize(target), reason, lockedBy,
                System.currentTimeMillis(), until);
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isExpired() {
        return until != INDEFINITE && System.currentTimeMillis() >= until;
    }

    public boolean isActive() {
        return !isExpired();
    }

    public boolean matches(String otherType, String otherTarget) {
        return type.equals(normalize(otherType)) && target.equals(normalize(otherTarget));
    }

    public String key() {
        return type + ":" + target;
    }

    public static String key(String type, String target) {
        return normalize(type) + ":" + normalize(target);
    }
}
