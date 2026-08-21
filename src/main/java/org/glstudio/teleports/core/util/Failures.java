package org.glstudio.teleports.core.util;

import org.bukkit.command.CommandSender;
import org.glstudio.teleports.GTeleports;

import java.util.concurrent.CompletableFuture;

public final class Failures {
    private Failures() {
    }

    public static <T> CompletableFuture<T> guard(GTeleports plugin, CompletableFuture<T> future,
                                                 String context, CommandSender sender) {
        return future.whenComplete((result, error) -> {
            if (error == null) {
                return;
            }

            Diagnostics.logFailure(context, error);

            if (sender != null) {
                plugin.getMessages().send(sender, "errors.database-error");
            }
        });
    }

    public static <T> CompletableFuture<T> log(CompletableFuture<T> future, String context) {
        return future.whenComplete((result, error) -> {
            if (error != null) {
                Diagnostics.logFailure(context, error);
            }
        });
    }
}
