package org.glstudio.teleports.modules.migration.source;

import java.util.List;
import java.util.Map;

/**
 * The player warps one source provided, with everything that hangs off them.
 *
 * @param notCarriedOver what the source stores that this plugin has no place for, label to amount, in report
 *                       order; nothing in it is written, and it is listed so nothing is lost silently
 * @param notes          per-entry details of the above and of values that were adjusted on the way in; the entry
 *                       itself is still imported
 */
public record SourcePlayerWarps(List<SourcePlayerWarp> warps, List<SourcePlayerWarpLink> links,
                                Map<String, Long> notCarriedOver, List<SourceProblem> notes) {

    /**
     * Returns an empty one.
     */
    public static SourcePlayerWarps none() {
        return new SourcePlayerWarps(List.of(), List.of(), Map.of(), List.of());
    }

    /**
     * Tells whether this holds nothing.
     */
    public boolean isEmpty() {
        return warps.isEmpty() && links.isEmpty();
    }
}
