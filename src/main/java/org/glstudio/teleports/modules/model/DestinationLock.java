package org.glstudio.teleports.modules.model;

import java.util.Locale;

/**
 * A warp or server locked by staff.
 */
public record DestinationLock(String type,
                              String target,
                              String reason,
                              String lockedBy,
                              long lockedAt,
                              long until) {
    public static final String WARP = "warp";
    public static final String SERVER = "server";

    public static final long INDEFINITE = 0L;

    /**
     * Creates a lock.
     */
    public static DestinationLock of(String type, String target, String reason, String lockedBy, long until) {
        return new DestinationLock(normalize(type), normalize(target), reason, lockedBy,
                System.currentTimeMillis(), until);
    }

    /**
     * Returns the form names are compared in.
     */
    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Tells whether this has run out.
     */
    public boolean isExpired() {
        return until != INDEFINITE && System.currentTimeMillis() >= until;
    }

    /**
     * Tells whether this is in force.
     */
    public boolean isActive() {
        return !isExpired();
    }

    /**
     * Tells whether this is the given lock.
     */
    public boolean matches(String otherType, String otherTarget) {
        return type.equals(normalize(otherType)) && target.equals(normalize(otherTarget));
    }

    /**
     * Returns the key a lock is stored under.
     */
    public String key() {
        return type + ":" + target;
    }

    /**
     * Returns the key a lock is stored under.
     */
    public static String key(String type, String target) {
        return normalize(type) + ":" + normalize(target);
    }
}
