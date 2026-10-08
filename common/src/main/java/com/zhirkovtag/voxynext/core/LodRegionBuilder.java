package com.zhirkovtag.voxynext.core;

/**
 * Builds one fixed 32x32 LOD region from world-space chunk snapshots.
 * A region at LOD scale S covers 32*S blocks on each axis.
 */
public final class LodRegionBuilder {
    private LodRegionBuilder() {}

    public static LodRegion build(ChunkSnapshotGrid grid, LodLevel level, long regionX, long regionZ) {
        LodRegion region = new LodRegion(level, regionX, regionZ);
        int scale = level.scale();
        long originX = regionX * (long) region.blockSpan();
        long originZ = regionZ * (long) region.blockSpan();

        for (int z = 0; z < LodRegion.SIZE; z++) {
            for (int x = 0; x < LodRegion.SIZE; x++) {
                VoxelCell cell = aggregate(grid, originX + (long) x * scale, originZ + (long) z * scale, scale);
                if (cell != null) region.set(x, z, cell);
            }
        }
        region.clearDirty();
        return region;
    }

    private static VoxelCell aggregate(ChunkSnapshotGrid grid, long originX, long originZ, int scale) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int dominant = MaterialPalette.AIR;
        int dominantCount = 0;
        int[] counts = new int[256];

        for (int z = 0; z < scale; z++) {
            for (int x = 0; x < scale; x++) {
                int worldX = Math.toIntExact(originX + x);
                int worldZ = Math.toIntExact(originZ + z);
                int y = grid.topY(worldX, worldZ);
                if (y == Integer.MIN_VALUE) continue;
                maxY = Math.max(maxY, y);
                minY = Math.min(minY, y);

                int material = grid.material(worldX, worldZ);
                if (material == MaterialPalette.AIR) continue;
                int slot = material & 255;
                int count = ++counts[slot];
                if (count > dominantCount) {
                    dominantCount = count;
                    dominant = material;
                }
            }
        }

        if (maxY == Integer.MIN_VALUE || dominant == MaterialPalette.AIR) return null;
        return new VoxelCell(dominant, minY, maxY, VoxelCell.SOLID);
    }
}
