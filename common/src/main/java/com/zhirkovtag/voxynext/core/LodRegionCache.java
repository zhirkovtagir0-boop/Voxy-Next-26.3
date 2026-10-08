package com.zhirkovtag.voxynext.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe world-space cache for generated LOD regions.
 * Regions are immutable from the renderer's point of view: a replacement is published
 * atomically after generation, avoiding locks on the render path.
 */
public final class LodRegionCache {
    private final ConcurrentHashMap<Long, LodRegion> regions = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    public LodRegion get(LodLevel level, int regionX, int regionZ) {
        return regions.get(key(level, regionX, regionZ));
    }

    public void publish(LodRegion region) {
        regions.put(key(region.level(), region.regionX(), region.regionZ()), region);
        generation.incrementAndGet();
    }

    public void invalidate(LodLevel level, int regionX, int regionZ) {
        regions.remove(key(level, regionX, regionZ));
        generation.incrementAndGet();
    }

    public void invalidateAroundChunk(int chunkX, int chunkZ) {
        int blockX = chunkX << 4;
        int blockZ = chunkZ << 4;
        for (LodLevel level : LodLevel.values()) {
            int span = level.blockSpan();
            int rx = Math.floorDiv(blockX, span);
            int rz = Math.floorDiv(blockZ, span);
            for (int z = rz - 1; z <= rz + 1; z++) {
                for (int x = rx - 1; x <= rx + 1; x++) {
                    regions.remove(key(level, x, z));
                }
            }
        }
        generation.incrementAndGet();
    }

    public void clear() {
        regions.clear();
        generation.incrementAndGet();
    }

    public int size() {
        return regions.size();
    }

    public long generation() {
        return generation.get();
    }

    private static long key(LodLevel level, int x, int z) {
        long levelBits = ((long) level.ordinal()) << 58;
        return levelBits ^ (((long) x & 0x1FFFFFFFL) << 29) ^ ((long) z & 0x1FFFFFFFL);
    }
}
