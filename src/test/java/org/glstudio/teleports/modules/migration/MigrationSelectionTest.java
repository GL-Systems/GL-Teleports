package org.glstudio.teleports.modules.migration;

import org.glstudio.teleports.modules.migration.source.MigrationData;
import org.glstudio.teleports.modules.migration.source.SourceHome;
import org.glstudio.teleports.modules.migration.source.SourceLocation;
import org.glstudio.teleports.modules.migration.source.SourcePlayerWarp;
import org.glstudio.teleports.modules.migration.source.SourcePlayerWarps;
import org.glstudio.teleports.modules.migration.source.SourceWarp;
import org.glstudio.teleports.modules.model.Home;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationSelectionTest {
    private static final UUID OWNER = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final SourceLocation SURVIVAL = new SourceLocation("Survival", 10, 70, 20, 0, 0, null);

    @Test
    void homesOnlyKeepsPreviousHomeAndDoesNotImportHuskHomesWarps() throws Exception {
        MigrationData source = new MigrationData("HuskHomes", null,
                List.of(new SourceHome(OWNER, "Player", "base", SURVIVAL, 123, false, "home row")),
                List.of(new SourceWarp("market", SURVIVAL, null, 123, "warp row")), List.of());
        Home previous = new Home(OWNER, "base", "Survival", 10, 70, 20, 0, 0, 100, "server-1");
        MigrationOptions options = new MigrationOptions(false, "server-1", "", true, ConflictPolicy.RENAME,
                Map.of("survival", "Survival-2"), 1000);

        MigrationPlan plan = new MigrationPlanner(options,
                new WorldDirectory(Map.of(UUID.randomUUID(), "Survival-2"))).plan(
                MigrationSelection.parse("homes").filter(source), Map.of(OWNER, List.of(previous)), List.of());

        assertEquals("Survival", previous.getWorld());
        assertEquals(1, plan.homes().size());
        assertEquals("base-2", plan.homes().getFirst().getName());
        assertEquals("Survival-2", plan.homes().getFirst().getWorld());
        assertEquals(0, plan.warpCounts().found());
        assertTrue(plan.warps().isEmpty());
        assertEquals(0, plan.unloadedWorlds());
    }

    @Test
    void playerWarpUsesMappedWorldAndMixedSelectionExcludesOrdinaryWarps() throws Exception {
        SourcePlayerWarp playerWarp = new SourcePlayerWarp(1, OWNER, "Player", "shop", SURVIVAL, null,
                List.of(), null, null, false, null, false, 123, 0, "player warp row");
        MigrationData source = new MigrationData("PlayerWarps", null, List.of(),
                List.of(new SourceWarp("market", SURVIVAL, null, 123, "warp row")), List.of(),
                new SourcePlayerWarps(List.of(playerWarp), List.of(), Map.of(), List.of()));
        MigrationOptions options = new MigrationOptions(false, "server-1", "", true, ConflictPolicy.RENAME,
                Map.of("survival", "Survival-2"), 1000);

        MigrationPlan plan = new MigrationPlanner(options, WorldDirectory.empty()).plan(
                MigrationSelection.parse("homes,playerwarps").filter(source), Map.of(), List.of());

        assertEquals(0, plan.warpCounts().found());
        assertEquals("Survival-2", plan.playerWarps().imports().getFirst().warp().getWorld());
        assertFalse(plan.playerWarps().isEmpty());
    }

    @Test
    void selectionHasStableNameForConfirmation() {
        assertEquals("homes,playerwarps", MigrationSelection.parse("playerwarps,homes").key());
        assertEquals(Set.of(MigrationSelection.Kind.HOMES), MigrationSelection.parse("homes").kinds());
    }
}
