package org.glstudio.teleports.modules.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SafetyCheckResult {
    private final boolean safe;
    private final String reason;

    public static SafetyCheckResult safe() {
        return new SafetyCheckResult(true, null);
    }

    public static SafetyCheckResult unsafe(String reason) {
        return new SafetyCheckResult(false, reason);
    }
}
