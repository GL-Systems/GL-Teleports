package org.glstudio.teleports.modules.migration;

import org.glstudio.teleports.modules.migration.source.SourcePlayerWarpLink.Kind;
import org.glstudio.teleports.modules.migration.source.SourceProblem;
import org.glstudio.teleports.modules.model.PlayerWarpImport;

import java.util.List;
import java.util.Map;

/**
 * The player-warp part of a {@link MigrationPlan}.
 *
 * @param imports           the warps to write, each with its related records
 * @param owners            distinct owners among the player warps the source provided
 * @param links             per kind of related record, what happens to it
 * @param notCarriedOver    what the source stores that has no place here, label to amount; never written
 * @param unknownCategories categories the imported warps use that player-warps.yml does not define (yet)
 * @param notes             entries imported with a change worth knowing about (name, truncated text, icon...)
 */
public record PlayerWarpPlan(List<PlayerWarpImport> imports, MigrationPlan.Counts counts, int owners,
                             Map<Kind, LinkCounts> links, Map<String, Long> notCarriedOver,
                             List<String> unknownCategories, List<SourceProblem> notes) {

    public static PlayerWarpPlan empty() {
        return new PlayerWarpPlan(List.of(), new MigrationPlan.Counts(0, 0, 0, 0, 0, 0, 0, 0), 0, Map.of(),
                Map.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return imports.isEmpty();
    }

    public boolean found() {
        return counts.found() > 0 || links.values().stream().anyMatch(count -> count.found() > 0);
    }

    /**
     * @param withWarp   belonged to a warp that is not imported in this run (invalid, conflicting, on another
     *                   server or imported before) and stay behind with it
     * @param duplicates the same player listed twice for the same warp; the last one counts, as in the source
     * @param invalid    unusable, e.g. a rating outside 1-5 or a ban that has already ended
     */
    public record LinkCounts(int found, int imported, int withWarp, int duplicates, int invalid) {
    }
}
