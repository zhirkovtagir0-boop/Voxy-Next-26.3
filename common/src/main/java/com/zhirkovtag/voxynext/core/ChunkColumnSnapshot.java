package com.zhirkovtag.voxynext.core;

import java.util.Arrays;

/**
 * Compact loader-neutral surface snapshot of a chunk column.
 *
 * Voxy Next's first terrain representation stores only the highest non-air block
 * and its material for each of the 256 X/Z columns. This is enough to generate
 * a continuous distant terrain surface while using a tiny fixed memory footprint.
 */
public final class ChunkColumnSnapshot {
    public static final int SIZE = 16;
    private final int minY;
    private final int height;
    private final short[] topMaterial = new short[SIZE * SIZE];
    private final int[] topY = new int[SIZE * SIZE];

    public ChunkColumnSnapshot(int minY, int height) {
        if (height <= 0) throw new IllegalArgumentException("height");
        this.minY = minY;
        this.height = height;
        Arrays.fill(topY, minY - 1);
    }

    public int minY() { return minY; }
    public int maxY() { return minY + height - 1; }
    public int height() { return height; }

    /**
     * Records a block only when it is the highest known non-air block in the column.
     */
    public void set(int x, int y, int z, int paletteId) {
        checkXZ(x, z);
        if (y < minY || y > maxY()) throw new IndexOutOfBoundsException("y=" + y);
        int i = x | (z << 4);
        if (paletteId != MaterialPalette.AIR && y >= topY[i]) {
            topY[i] = y;
            topMaterial[i] = (short) paletteId;
        }
    }

    public int material(int x, int y, int z) {
        checkXZ(x, z);
        int i = x | (z << 4);
        return topY[i] == y ? topMaterial[i] & 0xFFFF : MaterialPalette.AIR;
    }

    public int topMaterial(int x, int z) {
        checkXZ(x, z);
        return topMaterial[x | (z << 4)] & 0xFFFF;
    }

    public int topY(int x, int z) {
        checkXZ(x, z);
        return topY[x | (z << 4)];
    }

    private static void checkXZ(int x, int z) {
        if ((x | z) < 0 || x >= SIZE || z >= SIZE) throw new IndexOutOfBoundsException();
    }
}
