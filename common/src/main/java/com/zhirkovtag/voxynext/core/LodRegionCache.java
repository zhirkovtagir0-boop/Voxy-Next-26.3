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
    private final ConcurrentHashMap<Long, Long> lastAccess = new ConcurrentHashMap<>();
    private final AtomicLong accessClock = new AtomicLong();

    public LodRegion get(LodLevel level, long regionX, long regionZ) {
        long key = key(level, regionX, regionZ);
        LodRegion region = regions.get(key);
        if (region != null) lastAccess.put(key, accessClock.incrementAndGet());
        return region;
    }

    public void publish(LodRegion region) {
        long key = key(region.level(), region.regionX(), region.regionZ());
        regions.put(key, region);
        lastAccess.put(key, accessClock.incrementAndGet());
        generation.incrementAndGet();
    }

    public void invalidate(LodLevel level, long regionX, long regionZ) {
        long key = key(level, regionX, regionZ);
        if (regions.remove(key) != null) {
            lastAccess.remove(key);
            generation.incrementAndGet();
        }
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
                    long key = key(level, x, z);
                    if (regions.remove(key) != null) {
                        lastAccess.remove(key);
                        changed = true;
                    }
                }
            }
        }
        if (changed) generation.incrementAndGet();
    }

    public void clear() {
        if (!regions.isEmpty() || !lastAccess.isEmpty()) {
            regions.clear();
            lastAccess.clear();
            generation.incrementAndGet();
        }
    }

    public int size() { return regions.size(); }
    public long generation() { return generation.get(); }

    /** Keeps the hottest regions and evicts the coldest entries without blocking readers. */
    public void trimTo(int maximum) {
        int limit = Math.max(128, maximum);
        int size = regions.size();
        if (size <= limit + Math.max(64, limit / 20)) return;

        int target = limit;
        while (regions.size() > target) {
            long coldKey = 0L;
            long coldStamp = Long.MAX_VALUE;
            for (var entry : lastAccess.entrySet()) {
                if (entry.getValue() < coldStamp && regions.containsKey(entry.getKey())) {
                    coldStamp = entry.getValue();
                    coldKey = entry.getKey();
                }
            }
            if (coldStamp == Long.MAX_VALUE) return;
            lastAccess.remove(coldKey);
            regions.remove(coldKey);
        }
    }

    private static long key(LodLevel level, long x, long z) {
        long levelBits = ((long)level.ordinal()) << 58;
        return levelBits ^ (((long)x & 0x1FFFFFFFL) << 29) ^ ((long)z & 0x1FFFFFFFL);
    }
}
