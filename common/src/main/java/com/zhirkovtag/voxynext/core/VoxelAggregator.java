package com.zhirkovtag.voxynext.core;

/** Builds a coarse voxel cell from a square source footprint. */
public final class VoxelAggregator {
    private VoxelAggregator() {}

    public static VoxelCell aggregate(ChunkColumnSnapshot source, int originX, int originZ, int scale) {
        if (scale < 1 || (scale & (scale - 1)) != 0) throw new IllegalArgumentException("scale");
        int maxY = source.minY() - 1;
        int minSolidY = Integer.MAX_VALUE;
        int dominant = 0;
        int dominantCount = 0;
        int[] counts = new int[256];

        for (int z = originZ; z < originZ + scale; z++) {
            for (int x = originX; x < originX + scale; x++) {
                if (x < 0 || z < 0 || x >= 16 || z >= 16) continue;
                int top = source.topY(x, z);
                if (top >= source.minY()) maxY = Math.max(maxY, top);
                for (int y = source.minY(); y <= top; y++) {
                    int id = source.material(x, y, z);
                    if (id == 0) continue;
                    if (minSolidY == Integer.MAX_VALUE) minSolidY = y;
                    int slot = id & 255;
                    int count = ++counts[slot];
                    if (count > dominantCount) {
                        dominantCount = count;
                        dominant = id;
                    }
                }
            }
        }

        if (maxY < source.minY() || dominant == 0) return new VoxelCell(0, 0, 0, 0);
        int flags = VoxelCell.SOLID;
        return new VoxelCell(dominant, minSolidY, maxY, flags);
    }
}
