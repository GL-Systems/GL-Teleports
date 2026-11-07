package org.glstudio.teleports.modules.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Whether a destination is safe, and why not.
 */
@Getter
@AllArgsConstructor
public class SafetyCheckResult {
    private final boolean safe;
    private final String reason;

    /**
     * Creates a safe result.
     */
    public static SafetyCheckResult safe() {
        return new SafetyCheckResult(true, null);
    }

    /**
     * Creates an unsafe result.
     */
    public static SafetyCheckResult unsafe(String reason) {
        return new SafetyCheckResult(false, reason);
    }
}
