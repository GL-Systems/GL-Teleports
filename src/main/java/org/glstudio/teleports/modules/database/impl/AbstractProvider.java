package org.glstudio.teleports.modules.database.impl;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.database.DatabaseProvider;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public abstract class AbstractProvider implements DatabaseProvider {
    protected final GTeleports plugin;

    protected AbstractProvider(GTeleports plugin) {
        this.plugin = plugin;
    }

    protected Executor io() {
        return plugin.getExecutors().io();
    }

    protected CompletableFuture<Void> run(Runnable task) {
        return CompletableFuture.runAsync(task, io());
    }

    protected <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, io());
    }
}
