package com.zhirkovtag.voxynext.core;

public final class LodRegionBuilderTest {
    public static void main(String[] args) {
        ChunkSnapshotGrid grid = new ChunkSnapshotGrid();
        ChunkColumnSnapshot snapshot = new ChunkColumnSnapshot(-64, 384);

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                snapshot.set(x, 70, z, 5);
            }
        }

        grid.publish(-1, -1, snapshot);
        if (grid.topY(-16, -16) != 70) throw new AssertionError("negative world coordinate lookup failed");
        if (grid.material(-16, -16) != 5) throw new AssertionError("material lookup failed");

        LodRegion region = LodRegionBuilder.build(grid, LodLevel.LOD0, -1, -1);
        if (region.populatedCells() != 256) throw new AssertionError("unexpected LOD0 population");
        if (region.get(0, 0) == null || region.get(0, 0).maxY() != 70) {
            throw new AssertionError("unexpected generated height");
        }
    }
}
