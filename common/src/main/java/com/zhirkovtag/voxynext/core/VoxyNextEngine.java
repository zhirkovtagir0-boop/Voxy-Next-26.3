package com.zhirkovtag.voxynext.core;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared runtime for both loaders.
 *
 * World ingestion publishes compact chunk snapshots; background workers turn those
 * snapshots into immutable LOD regions, while renderers only consume published data.
 */
public final class VoxyNextEngine implements AutoCloseable {
    private final AtomicBoolean running = new AtomicBoolean();
    private final ChunkSnapshotGrid source = new ChunkSnapshotGrid();
    private final LodRegionCache cache = new LodRegionCache();
    private volatile DistanceBudget budget =
            new DistanceBudget(64, Math.max(1, Runtime.getRuntime().availableProcessors() - 2), 32_768);
    private volatile LodBuildScheduler scheduler;

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        scheduler = new LodBuildScheduler(source, cache, budget.workerCount());
    }

    public boolean isRunning() { return running.get(); }
    public DistanceBudget budget() { return budget; }
    public ChunkSnapshotGrid source() { return source; }
    public LodRegionCache cache() { return cache; }

    public synchronized void setBudget(DistanceBudget budget) {
        if (budget == null) throw new IllegalArgumentException("budget");
        this.budget = budget;
        if (running.get()) {
            LodBuildScheduler old = scheduler;
            scheduler = new LodBuildScheduler(source, cache, budget.workerCount());
            old.close();
        }
    }

    public void requestRegion(LodLevel level, long regionX, long regionZ) {
        LodBuildScheduler current = scheduler;
        if (running.get() && current != null) current.request(level, regionX, regionZ);
    }

    public synchronized void clearWorld() {
        source.clear();
        cache.clear();
    }

    @Override
    public synchronized void close() {
        if (!running.compareAndSet(true, false)) return;
        LodBuildScheduler current = scheduler;
        scheduler = null;
        if (current != null) current.close();
        source.clear();
        cache.clear();
    }
}
