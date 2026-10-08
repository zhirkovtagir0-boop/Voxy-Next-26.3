package com.zhirkovtag.voxynext.core;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Background LOD builder with de-duplication.
 *
 * Only one task for a given level/region can be in flight at once. Completed
 * regions are atomically published to the shared cache, so render extraction
 * never waits on generation.
 */
public final class LodBuildScheduler implements AutoCloseable {
    private final ChunkSnapshotGrid source;
    private final LodRegionCache cache;
    private final ExecutorService executor;
    private final Set<Key> pending = ConcurrentHashMap.newKeySet();

    public LodBuildScheduler(ChunkSnapshotGrid source, LodRegionCache cache, int workers) {
        this.source = source;
        this.cache = cache;
        int count = Math.max(1, Math.min(16, workers));
        this.executor = Executors.newFixedThreadPool(count, task -> {
            Thread thread = new Thread(task, "VoxyNext-LOD");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<LodRegion> request(LodLevel level, long regionX, long regionZ) {
        LodRegion existing = cache.get(level, regionX, regionZ);
        if (existing != null) return CompletableFuture.completedFuture(existing);

        Key key = new Key(level, regionX, regionZ);
        if (!pending.add(key)) {
            return CompletableFuture.completedFuture(cache.get(level, regionX, regionZ));
        }

        long sourceGeneration = source.generation();
        return CompletableFuture.supplyAsync(() -> {
                    LodRegion region = LodRegionBuilder.build(source, level, regionX, regionZ);
                    return source.generation() == sourceGeneration ? region : null;
                }, executor)
                .whenComplete((region, error) -> {
                    pending.remove(key);
                    if (error == null && region != null) {
                        cache.publish(region);
                    }
                });
    }

    public int pendingCount() {
        return pending.size();
    }

    @Override
    public void close() {
        executor.shutdownNow();
        pending.clear();
    }

    private record Key(LodLevel level, long x, long z) {}
}
