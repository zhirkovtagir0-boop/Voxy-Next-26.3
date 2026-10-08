package com.zhirkovtag.voxynext.core;

/** Compact CPU-side representation of one aggregated terrain cell. */
public record VoxelCell(int packedMaterial, int minY, int maxY, int flags) {
    public static final int FLAG_SOLID = 1;
    public static final int FLAG_WATER = 1 << 1;
    public static final int FLAG_EMISSIVE = 1 << 2;
    public static final int FLAG_TRANSPARENT = 1 << 3;

    public int height() { return Math.max(0, maxY - minY); }
}
