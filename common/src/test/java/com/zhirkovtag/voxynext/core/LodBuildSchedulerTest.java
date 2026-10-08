package com.zhirkovtag.voxynext.core;

public final class LodBuildSchedulerTest {
    public static void main(String[] args) {
        ChunkSnapshotGrid grid = new ChunkSnapshotGrid();
        ChunkColumnSnapshot snapshot = new ChunkColumnSnapshot(-64, 384);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) snapshot.set(x, 80, z, 9);
        }
        grid.publish(0, 0, snapshot);

        LodRegionCache cache = new LodRegionCache();
        try (LodBuildScheduler scheduler = new LodBuildScheduler(grid, cache, 1)) {
            LodRegion region = scheduler.request(LodLevel.LOD0, 0, 0).join();
            if (region == null) throw new AssertionError("LOD build was discarded");
            if (cache.get(LodLevel.LOD0, 0, 0) == null) throw new AssertionError("LOD was not published");
            if (cache.get(LodLevel.LOD0, 0, 0).populatedCells() != 256) {
                throw new AssertionError("unexpected generated cells");
            }
        }
    }
}
