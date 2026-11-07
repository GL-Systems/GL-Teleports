package org.glstudio.teleports.modules.migration;

import java.util.Locale;

/**
 * What to do when an imported home or warp has the same name as one that already exists here.
 */
public enum ConflictPolicy {
    /** Keep what is already here and leave the imported entry out. */
    SKIP,
    /** Import under a free name such as base-2, keeping both. */
    RENAME,
    /** Overwrite what is already here with the imported entry. */
    REPLACE;

    /**
     * Reads a policy from its name.
     */
    public static ConflictPolicy parse(String value) {
        if (value == null) {
            return null;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns the name of this policy.
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
