package com.zhirkovtag.voxynext.core;

public final class LodRegionCacheTest {
    public static void main(String[] args) {
        LodRegionCache cache = new LodRegionCache();
        LodRegion region = new LodRegion(LodLevel.LOD2, 4, -2);
        region.set(0, 0, new VoxelCell(7, 64, 80, VoxelCell.FLAG_SOLID));
        region.clearDirty();

        cache.publish(region);
        assert cache.get(LodLevel.LOD2, 4, -2) == region;
        assert cache.size() == 1;

        cache.invalidate(LodLevel.LOD2, 4, -2);
        assert cache.get(LodLevel.LOD2, 4, -2) == null;

        VoxelMesh mesh = new VoxelMesh(1);
        mesh.quad(0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1, 1);
        assert mesh.vertexCount() == 4;
        assert mesh.indexCount() == 6;
    }
}
