package org.glstudio.teleports.modules.migration;

import org.glstudio.teleports.modules.migration.source.SourceProblem;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.Warp;

import java.util.List;

/**
 * What a migration would write, and why everything else is left out.
 *
 * @param homes          the homes to write, already named, validated and assigned a server
 * @param players        distinct owners among the homes the source provided
 * @param publicHomes    source homes that were public; they are imported as ordinary homes
 * @param unloadedWorlds entries bound for this server whose world is not loaded here right now
 * @param restrictedWarps imported warps that keep a permission requirement from the source
 * @param unreadable     records the source reader could not parse at all
 * @param playerWarps    the player warps to write; empty for sources without them
 */
public record MigrationPlan(String storage, List<Home> homes, List<Warp> warps, Counts homeCounts,
                            Counts warpCounts, int players, int publicHomes, int unloadedWorlds,
                            int restrictedWarps, int unreadable, List<SourceProblem> problems,
                            PlayerWarpPlan playerWarps) {

    /**
     * @param unchanged already present here with the same name and position, typically from an earlier run
     * @param conflicts present here under the same name at a different position, left out under the skip policy
     * @param elsewhere recorded by the source on another server, while multi-server is off here
     * @param invalid   unusable: no world, bad coordinates or a name this plugin does not accept
     */
    public record Counts(int found, int created, int renamed, int replaced, int unchanged, int conflicts,
                         int elsewhere, int invalid) {
        public int imported() {
            return created + renamed + replaced;
        }

        public int skipped() {
            return unchanged + conflicts + elsewhere;
        }
    }

    public boolean isEmpty() {
        return homes.isEmpty() && warps.isEmpty() && playerWarps.isEmpty();
    }
}
