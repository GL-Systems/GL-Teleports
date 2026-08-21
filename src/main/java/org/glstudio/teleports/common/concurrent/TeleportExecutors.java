package org.glstudio.teleports.common.concurrent;

import org.glstudio.nexus.utils.LoggerUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TeleportExecutors {
    private final ExecutorService ioExecutor;
    private final ScheduledExecutorService scheduler;

    public TeleportExecutors(int poolSize) {
        this.ioExecutor = Executors.newFixedThreadPool(Math.max(2, poolSize), namedFactory("GL-Teleports-IO"));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(namedFactory("GL-Teleports-Scheduler"));
    }

    public Executor io() {
        return ioExecutor;
    }

    public Executor delayed(long delay, TimeUnit unit) {
        return CompletableFuture.delayedExecutor(delay, unit, ioExecutor);
    }

    public ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public void shutdown() {
        shutdown(ioExecutor, "GL-Teleports-IO");
        shutdown(scheduler, "GL-Teleports-Scheduler");
    }

    private void shutdown(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                LoggerUtils.logWarn(name + " did not terminate in time; forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
