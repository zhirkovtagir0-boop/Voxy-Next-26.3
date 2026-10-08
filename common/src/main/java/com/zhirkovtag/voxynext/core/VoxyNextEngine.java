package com.zhirkovtag.voxynext.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Platform-neutral lifecycle for ingestion, LOD generation and render preparation. */
public final class VoxyNextEngine implements AutoCloseable {
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile DistanceBudget budget = new DistanceBudget(64, Math.max(1, Runtime.getRuntime().availableProcessors() - 2), 32_768);
    private ExecutorService workers;

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        workers = Executors.newFixedThreadPool(budget.workerCount(), r -> {
            Thread t = new Thread(r, "VoxyNext-LOD");
            t.setDaemon(true);
            t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
            return t;
        });
    }

    public boolean isRunning() { return running.get(); }
    public DistanceBudget budget() { return budget; }
    public synchronized void setBudget(DistanceBudget budget) {
        if (budget == null) throw new IllegalArgumentException("budget");
        this.budget = budget;
    }

    public synchronized void submit(Runnable task) {
        if (!running.get()) return;
        workers.execute(task);
    }

    @Override public synchronized void close() {
        if (!running.compareAndSet(true, false)) return;
        workers.shutdown();
        try { workers.awaitTermination(3, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        workers.shutdownNow();
        workers = null;
    }
}
