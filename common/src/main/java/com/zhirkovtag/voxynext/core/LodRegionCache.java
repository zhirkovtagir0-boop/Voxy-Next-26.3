package com.zhirkovtag.voxynext.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe world-space LOD cache. Published regions are immutable from the
 * renderer's perspective and replaced atomically after background generation.
 */
public final class LodRegionCache {
    private final ConcurrentHashMap<Long, LodRegion> regions = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    public LodRegion get(LodLevel level, long regionX, long regionZ) {
        if (regionX < Integer.MIN_VALUE || regionX > Integer.MAX_VALUE ||
                regionZ < Integer.MIN_VALUE || regionZ > Integer.MAX_VALUE) return null;
        return regions.get(key(level, (int)regionX, (int)regionZ));
    }

    public void publish(LodRegion region) {
        regions.put(key(region.level(), region.regionX(), region.regionZ()), region);
        generation.incrementAndGet();
    }

    public void invalidate(LodLevel level, long regionX, long regionZ) {
        if (regionX < Integer.MIN_VALUE || regionX > Integer.MAX_VALUE ||
                regionZ < Integer.MIN_VALUE || regionZ > Integer.MAX_VALUE) return;
        regions.remove(key(level, (int)regionX, (int)regionZ));
        generation.incrementAndGet();
    }

    public void invalidateAroundChunk(int chunkX, int chunkZ) {
        int blockX = chunkX << 4;
        int blockZ = chunkZ << 4;
        boolean changed = false;
        for (LodLevel level : LodLevel.values()) {
            int span = level.blockSpan();
            int rx = Math.floorDiv(blockX, span);
            int rz = Math.floorDiv(blockZ, span);
            // Invalidate the containing region plus a one-cell border. The
            // border is required because neighboring coarse cells sample this
            // chunk at their footprint edge.
            for (int z = rz - 1; z <= rz + 1; z++) {
                for (int x = rx - 1; x <= rx + 1; x++) {
                    changed |= regions.remove(key(level, x, z)) != null;
                }
            }
        }
        if (changed) generation.incrementAndGet();
    }

    public void clear() {
        if (!regions.isEmpty()) {
            regions.clear();
            generation.incrementAndGet();
        }
    }

    public int size() { return regions.size(); }
    public long generation() { return generation.get(); }

    private static long key(LodLevel level, int x, int z) {
        long levelBits = ((long)level.ordinal()) << 58;
        return levelBits ^ (((long)x & 0x1FFFFFFFL) << 29) ^ ((long)z & 0x1FFFFFFFL);
    }
}
