package org.glstudio.teleports.core;

import java.util.Locale;

public final class Durations {
    public static final long INVALID = -1L;

    private Durations() {
    }

    public static long parseSeconds(String input) {
        if (input == null || input.isBlank()) {
            return INVALID;
        }

        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        char last = trimmed.charAt(trimmed.length() - 1);

        long multiplier = switch (last) {
            case 's' -> 1L;
            case 'm' -> 60L;
            case 'h' -> 3600L;
            case 'd' -> 86400L;
            default -> 0L;
        };

        String number = multiplier == 0L ? trimmed : trimmed.substring(0, trimmed.length() - 1);

        try {
            long value = Long.parseLong(number);

            return value <= 0 ? INVALID : value * (multiplier == 0L ? 1L : multiplier);
        } catch (NumberFormatException e) {
            return INVALID;
        }
    }
}
