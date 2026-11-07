package org.glstudio.teleports.core.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rules for the names of homes and warps.
 */
public final class TeleportNames {
    public static final int MAX_LENGTH = 24;

    private static final Pattern ALLOWED = Pattern.compile("[a-zA-Z0-9_-]+");

    private TeleportNames() {
    }

    /**
     * Tells whether a name is acceptable.
     */
    public static boolean isValid(String name) {
        return name != null
                && !name.isEmpty()
                && name.length() <= MAX_LENGTH
                && ALLOWED.matcher(name).matches();
    }

    /**
     * Returns the form names are compared in.
     */
    public static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
