package com.zhirkovtag.voxynext.core;

/** Builds a coarse voxel cell from a square source footprint. */
public final class VoxelAggregator {
    private VoxelAggregator() {}

    public static VoxelCell aggregate(ChunkColumnSnapshot source, int originX, int originZ, int scale) {
        if (scale < 1 || (scale & (scale - 1)) != 0) throw new IllegalArgumentException("scale");

        int maxY = source.minY() - 1;
        int minY = Integer.MAX_VALUE;
        int dominant = MaterialPalette.AIR;
        int dominantCount = 0;
        int[] ids = new int[256];
        int[] counts = new int[256];
        int distinct = 0;

        for (int z = originZ; z < originZ + scale; z++) {
            for (int x = originX; x < originX + scale; x++) {
                if (x < 0 || z < 0 || x >= 16 || z >= 16) continue;
                int y = source.topY(x, z);
                if (y < source.minY()) continue;

                maxY = Math.max(maxY, y);
                minY = Math.min(minY, y);
                int id = source.topMaterial(x, z);
                if (id == MaterialPalette.AIR) continue;

                int slot = -1;
                for (int i = 0; i < distinct; i++) {
                    if (ids[i] == id) { slot = i; break; }
                }
                if (slot < 0 && distinct < ids.length) {
                    slot = distinct++;
                    ids[slot] = id;
                }
                if (slot >= 0) {
                    int count = ++counts[slot];
                    if (count > dominantCount) {
                        dominantCount = count;
                        dominant = id;
                    }
                }
            }
        }

        if (maxY < source.minY() || dominant == MaterialPalette.AIR) return new VoxelCell(0, 0, 0, 0);
        return new VoxelCell(dominant, minY, maxY, VoxelCell.SOLID);
    }
}
