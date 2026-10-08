package com.zhirkovtag.voxynext.core;

import java.util.Arrays;

/**
 * Immutable, loader-neutral snapshot of a chunk column.
 * Heights are inclusive block Y coordinates; material is a compact palette id.
 */
public final class ChunkColumnSnapshot {
    public static final int SIZE = 16;
    private final int minY;
    private final int height;
    private final short[] material;
    private final int[] topY;

    public ChunkColumnSnapshot(int minY, int height) {
        if (height <= 0) throw new IllegalArgumentException("height");
        this.minY = minY;
        this.height = height;
        this.material = new short[SIZE * SIZE * height];
        this.topY = new int[SIZE * SIZE];
        Arrays.fill(topY, minY - 1);
    }

    public int minY() { return minY; }
    public int maxY() { return minY + height - 1; }
    public int height() { return height; }

    public void set(int x, int y, int z, int paletteId) {
        checkXZ(x, z);
        int i = index(x, y, z);
        material[i] = (short) paletteId;
        if (paletteId != 0) topY[x | (z << 4)] = Math.max(topY[x | (z << 4)], y);
    }

    public int material(int x, int y, int z) {
        checkXZ(x, z);
        return material[index(x, y, z)] & 0xFFFF;
    }

    public int topY(int x, int z) {
        checkXZ(x, z);
        return topY[x | (z << 4)];
    }

    private int index(int x, int y, int z) {
        if (y < minY || y > maxY) throw new IndexOutOfBoundsException("y=" + y);
        return ((y - minY) << 8) | (z << 4) | x;
    }

    private static void checkXZ(int x, int z) {
        if ((x | z) < 0 || x >= SIZE || z >= SIZE) throw new IndexOutOfBoundsException();
    }
}
