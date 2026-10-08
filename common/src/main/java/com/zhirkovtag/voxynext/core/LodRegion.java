package com.zhirkovtag.voxynext.core;

import java.util.Arrays;

/** Fixed-size sparse-friendly LOD region. Coordinates are local to the region. */
public final class LodRegion {
    public static final int SIZE = 16;
    private final LodLevel level;
    private final long regionX, regionZ;
    private final VoxelCell[] cells = new VoxelCell[SIZE * SIZE];
    private volatile boolean dirty;

    public LodRegion(LodLevel level, long regionX, long regionZ) {
        this.level = level;
        this.regionX = regionX;
        this.regionZ = regionZ;
    }

    public LodLevel level() { return level; }
    public long regionX() { return regionX; }
    public long regionZ() { return regionZ; }
    public int blockSpan() { return level.blockSpan(); }
    public VoxelCell get(int x, int z) { return cells[(z * SIZE) + x]; }
    public void set(int x, int z, VoxelCell cell) { cells[(z * SIZE) + x] = cell; dirty = true; }
    public boolean dirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public int populatedCells() {
        int count = 0;
        for (VoxelCell cell : cells) if (cell != null) count++;
        return count;
    }
    public void clear() { Arrays.fill(cells, null); dirty = true; }
}
