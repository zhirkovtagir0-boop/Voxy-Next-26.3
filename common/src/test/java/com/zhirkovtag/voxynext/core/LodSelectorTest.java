package com.zhirkovtag.voxynext.core;

public final class LodSelectorTest {
    public static void main(String[] args) {
        LodSelector selector = new LodSelector();
        assert selector.update(64) == LodLevel.LOD0;
        assert selector.update(300) == LodLevel.LOD2;
        assert selector.update(5000) == LodLevel.LOD5;
    }
}
