package org.glstudio.teleports.modules.migration.source;

import java.util.List;

/**
 * Everything one source provided, already in plain values but not yet validated against this plugin's rules.
 *
 * @param storage     human-readable description of where the data was read from, safe to log (no credentials)
 * @param localServer the name the source itself uses for the server the migration runs on, when it records
 *                    servers reliably; null otherwise
 * @param problems    entries the reader could not even parse; they are reported, never imported
 * @param playerWarps the source's player warps; empty for sources that only have homes and warps
 */
public record MigrationData(String storage, String localServer, List<SourceHome> homes, List<SourceWarp> warps,
                            List<SourceProblem> problems, SourcePlayerWarps playerWarps) {

    public MigrationData(String storage, String localServer, List<SourceHome> homes, List<SourceWarp> warps,
                         List<SourceProblem> problems) {
        this(storage, localServer, homes, warps, problems, SourcePlayerWarps.none());
    }
}
