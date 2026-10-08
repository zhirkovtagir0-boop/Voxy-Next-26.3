package com.zhirkovtag.voxynext.core;

import java.nio.file.Path;
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
    private final MaterialPalette palette = new MaterialPalette();
    private volatile DistanceBudget budget =
            new DistanceBudget(64, Math.max(1, Runtime.getRuntime().availableProcessors() - 2), 32_768);
    private volatile LodBuildScheduler scheduler;
    private volatile ChunkSnapshotStore store;
    private volatile Path storeDirectory;

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        scheduler = new LodBuildScheduler(source, cache, budget.workerCount(), budget.maxRegionsInMemory());
    }

    public boolean isRunning() { return running.get(); }
    public DistanceBudget budget() { return budget; }
    public ChunkSnapshotGrid source() { return source; }
    public LodRegionCache cache() { return cache; }
    public MaterialPalette palette() { return palette; }

    public synchronized void attachStore(Path directory) {
        if (directory != null && directory.equals(storeDirectory) && store != null) return;
        ChunkSnapshotStore old = store;
        source.clear();
        cache.clear();
        store = directory == null ? null : new ChunkSnapshotStore(directory);
        storeDirectory = directory;
        if (store != null) store.loadInto(source);
        if (old != null) old.close();
    }

    public void publishChunk(int chunkX, int chunkZ, ChunkColumnSnapshot snapshot) {
        source.publish(chunkX, chunkZ, snapshot);
        ChunkSnapshotStore current = store;
        if (current != null) current.save(chunkX, chunkZ, snapshot);
        cache.invalidateAroundChunk(chunkX, chunkZ);
    }

    public synchronized void setBudget(DistanceBudget budget) {
        if (budget == null) throw new IllegalArgumentException("budget");
        this.budget = budget;
        if (running.get()) {
            LodBuildScheduler old = scheduler;
            scheduler = new LodBuildScheduler(source, cache, budget.workerCount(), budget.maxRegionsInMemory());
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
        ChunkSnapshotStore persistent = store;
        store = null;
        storeDirectory = null;
        if (persistent != null) persistent.close();
        source.clear();
        cache.clear();
    }
}
