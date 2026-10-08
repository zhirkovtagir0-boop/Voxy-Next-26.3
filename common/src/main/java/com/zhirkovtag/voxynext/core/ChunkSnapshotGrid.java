package com.zhirkovtag.voxynext.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * World-space index of chunk-column snapshots.
 * Chunk coordinates are stored explicitly so LOD generation never assumes that a
 * region starts at world origin.
 */
public final class ChunkSnapshotGrid {
    private final ConcurrentHashMap<Long, ChunkColumnSnapshot> chunks = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    public void publish(int chunkX, int chunkZ, ChunkColumnSnapshot snapshot) {
        if (snapshot == null) throw new NullPointerException("snapshot");
        chunks.put(key(chunkX, chunkZ), snapshot);
        generation.incrementAndGet();
    }

    public ChunkColumnSnapshot get(int chunkX, int chunkZ) {
        return chunks.get(key(chunkX, chunkZ));
    }

    public ChunkColumnSnapshot remove(int chunkX, int chunkZ) {
        ChunkColumnSnapshot removed = chunks.remove(key(chunkX, chunkZ));
        if (removed != null) generation.incrementAndGet();
        return removed;
    }

    public void clear() { chunks.clear(); }
    public int size() { return chunks.size(); }

    public int topY(int worldX, int worldZ) {
        ChunkColumnSnapshot snapshot = get(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16));
        if (snapshot == null) return Integer.MIN_VALUE;
        return snapshot.topY(Math.floorMod(worldX, 16), Math.floorMod(worldZ, 16));
    }

    public int material(int worldX, int worldZ) {
        ChunkColumnSnapshot snapshot = get(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16));
        if (snapshot == null) return MaterialPalette.AIR;
        int x = Math.floorMod(worldX, 16);
        int z = Math.floorMod(worldZ, 16);
        int y = snapshot.topY(x, z);
        return y < snapshot.minY() ? MaterialPalette.AIR : snapshot.material(x, y, z);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
