package com.zhirkovtag.voxynext.core;

import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;

/**
 * Builds a complete region off-thread from an immutable input snapshot.
 * The returned region is ready to publish atomically into LodRegionCache.
 */
public final class LodRegionBuilder {
    private LodRegionBuilder() {}

    public static CompletableFuture<LodRegion> buildAsync(
            Executor executor, ChunkColumnSnapshot[] chunks, LodLevel level, long regionX, long regionZ) {
        return CompletableFuture.supplyAsync(
                () -> build(chunks, level, regionX, regionZ), executor);
    }

    public static LodRegion build(
            ChunkColumnSnapshot[] chunks, LodLevel level, long regionX, long regionZ) {
        LodRegion region = new LodRegion(level, regionX, regionZ);
        int scale = level.scale();
        int chunkSide = Math.max(1, scale / 16);

        for (int rz = 0; rz < LodRegion.SIZE; rz++) {
            for (int rx = 0; rx < LodRegion.SIZE; rx++) {
                int chunkIndex = Math.min(chunks.length - 1,
                        (rz / chunkSide) * Math.max(1, (int) Math.sqrt(chunks.length))
                                + (rx / chunkSide));
                if (chunkIndex < 0 || chunkIndex >= chunks.length || chunks[chunkIndex] == null) continue;

                ChunkColumnSnapshot source = chunks[chunkIndex];
                int ox = (rx * scale) & 15;
                int oz = (rz * scale) & 15;
                VoxelCell cell = VoxelAggregator.aggregate(source, ox, oz, Math.min(scale, 16));
                if (cell.packedMaterial() != 0) region.set(rx, rz, cell);
            }
        }
        region.clearDirty();
        return region;
    }
}
