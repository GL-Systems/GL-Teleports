package org.glstudio.teleports.modules.database.impl;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.database.DatabaseProvider;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Runs the storage work on the plugin's I/O threads.
 */
public abstract class AbstractProvider implements DatabaseProvider {
    protected final GTeleports plugin;

    /**
     * Creates the provider.
     */
    protected AbstractProvider(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the I/O executor.
     */
    protected Executor io() {
        return plugin.getExecutors().io();
    }

    /**
     * Runs a task on the I/O threads.
     */
    protected CompletableFuture<Void> run(Runnable task) {
        return CompletableFuture.runAsync(task, io());
    }

    /**
     * Computes a value on the I/O threads.
     */
    protected <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, io());
    }
}
