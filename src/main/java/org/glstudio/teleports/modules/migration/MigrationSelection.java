package org.glstudio.teleports.modules.migration;

import org.glstudio.teleports.modules.migration.source.MigrationData;
import org.glstudio.teleports.modules.migration.source.SourcePlayerWarps;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** The kinds of destinations a single migration run may write. */
public record MigrationSelection(Set<Kind> kinds) {
    /**
     * What can be selected.
     */
    public enum Kind {
        /**
         * Homes.
         */
        HOMES("homes"),
        /**
         * Warps.
         */
        WARPS("warps"),
        /**
         * Player warps.
         */
        PLAYER_WARPS("playerwarps");

        private final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    /** Creates the selection, copying what it holds. */
    public MigrationSelection {
        kinds = Set.copyOf(kinds);
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("At least one migration kind is required");
        }
    }

    /**
     * Selects everything.
     */
    public static MigrationSelection all() {
        return new MigrationSelection(EnumSet.allOf(Kind.class));
    }

    /**
     * Reads a value from its text.
     */
    public static MigrationSelection parse(String value) {
        if (value == null || value.equalsIgnoreCase("all")) {
            return all();
        }

        EnumSet<Kind> selected = EnumSet.noneOf(Kind.class);
        for (String part : value.toLowerCase(Locale.ROOT).split(",", -1)) {
            Kind kind = Arrays.stream(Kind.values())
                    .filter(candidate -> candidate.key.equals(part.trim()))
                    .findFirst().orElse(null);
            if (kind == null) {
                return null;
            }
            selected.add(kind);
        }
        return selected.isEmpty() ? null : new MigrationSelection(selected);
    }

    /**
     * Tells whether a kind is selected.
     */
    public boolean includes(Kind kind) {
        return kinds.contains(kind);
    }

    /**
     * Returns the name of this selection.
     */
    public String key() {
        return kinds.size() == Kind.values().length ? "all" : Arrays.stream(Kind.values())
                .filter(kinds::contains).map(kind -> kind.key).collect(Collectors.joining(","));
    }

    /**
     * Keeps only what is selected.
     */
    public MigrationData filter(MigrationData data) {
        return new MigrationData(data.storage(), data.localServer(),
                includes(Kind.HOMES) ? data.homes() : List.of(),
                includes(Kind.WARPS) ? data.warps() : List.of(),
                data.problems(),
                includes(Kind.PLAYER_WARPS) ? data.playerWarps() : SourcePlayerWarps.none());
    }
}
