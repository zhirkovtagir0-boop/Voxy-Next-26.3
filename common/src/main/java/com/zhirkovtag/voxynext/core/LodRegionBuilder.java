package com.zhirkovtag.voxynext.core;

/**
 * Builds one fixed 16x16 LOD region from world-space chunk snapshots.
 *
 * For close LODs every source column is sampled. Farther LODs use a bounded
 * sample grid per cell, keeping generation cost effectively constant as the
 * LOD scale grows.
 */
public final class LodRegionBuilder {
    private LodRegionBuilder() {}

    public static LodRegion build(ChunkSnapshotGrid grid, LodLevel level, long regionX, long regionZ) {
        LodRegion region = new LodRegion(level, regionX, regionZ);
        int scale = level.scale();
        long originX = regionX * (long) region.blockSpan();
        long originZ = regionZ * (long) region.blockSpan();

        int[] ids = new int[16];
        int[] counts = new int[16];
        for (int z = 0; z < LodRegion.SIZE; z++) {
            for (int x = 0; x < LodRegion.SIZE; x++) {
                VoxelCell cell = aggregate(grid, originX + (long) x * scale, originZ + (long) z * scale, scale, ids, counts);
                if (cell != null) region.set(x, z, cell);
            }
        }
        region.clearDirty();
        return region;
    }

    private static VoxelCell aggregate(ChunkSnapshotGrid grid, long originX, long originZ, int scale, int[] ids, int[] counts) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int dominant = MaterialPalette.AIR;
        int dominantCount = 0;

        // Never perform more than 4x4 samples per output cell.
        int step = Math.max(1, (scale + 3) / 4);
        int sampleCount = 0;
        for (int i = 0; i < ids.length; i++) {
            ids[i] = MaterialPalette.AIR;
            counts[i] = 0;
        }

        for (int z = 0; z < scale; z += step) {
            for (int x = 0; x < scale; x += step) {
                int worldX = Math.toIntExact(originX + x);
                int worldZ = Math.toIntExact(originZ + z);
                int y = grid.topY(worldX, worldZ);
                if (y == Integer.MIN_VALUE) continue;

                maxY = Math.max(maxY, y);
                minY = Math.min(minY, y);

                int material = grid.material(worldX, worldZ);
                if (material == MaterialPalette.AIR) continue;

                int slot = -1;
                for (int i = 0; i < sampleCount; i++) {
                    if (ids[i] == material) {
                        slot = i;
                        break;
                    }
                }
                if (slot < 0 && sampleCount < ids.length) {
                    slot = sampleCount++;
                    ids[slot] = material;
                }
                if (slot >= 0) {
                    int count = ++counts[slot];
                    if (count > dominantCount) {
                        dominantCount = count;
                        dominant = material;
                    }
                }
            }
        }

        if (maxY == Integer.MIN_VALUE || dominant == MaterialPalette.AIR) return null;
        return new VoxelCell(dominant, minY, maxY, VoxelCell.FLAG_SOLID);
    }
}
