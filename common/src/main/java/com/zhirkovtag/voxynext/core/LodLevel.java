package com.zhirkovtag.voxynext.core;

public enum LodLevel {
    LOD0(1), LOD1(2), LOD2(4), LOD3(8), LOD4(16), LOD5(32), LOD6(64), LOD7(128);

    private final int scale;
    LodLevel(int scale) { this.scale = scale; }
    public int scale() { return scale; }
    public int blockSpan() { return 16 * scale; }

    public static LodLevel forDistance(double blocks) {
        if (blocks < 128) return LOD0;
        if (blocks < 256) return LOD1;
        if (blocks < 512) return LOD2;
        if (blocks < 1024) return LOD3;
        if (blocks < 2048) return LOD4;
        if (blocks < 4096) return LOD5;
        if (blocks < 8192) return LOD6;
        return LOD7;
    }
}
