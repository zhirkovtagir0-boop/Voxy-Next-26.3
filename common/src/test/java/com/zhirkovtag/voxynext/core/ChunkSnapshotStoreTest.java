package com.zhirkovtag.voxynext.core;

import java.nio.file.Files;

public final class ChunkSnapshotStoreTest {
    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("voxy-next-store");
        ChunkColumnSnapshot snapshot = new ChunkColumnSnapshot(-64, 384);
        snapshot.set(3, 90, 7, 42);

        try (ChunkSnapshotStore store = new ChunkSnapshotStore(dir)) {
            store.save(-12, 34, snapshot);
            Thread.sleep(100);
        }

        ChunkSnapshotGrid grid = new ChunkSnapshotGrid();
        try (ChunkSnapshotStore store = new ChunkSnapshotStore(dir)) {
            store.loadInto(grid);
        }

        if (grid.topY(-12 * 16 + 3, 34 * 16 + 7) != 90) {
            throw new AssertionError("persisted height was not restored");
        }
        if (grid.material(-12 * 16 + 3, 34 * 16 + 7) != 42) {
            throw new AssertionError("persisted material was not restored");
        }
        System.out.println("ChunkSnapshotStoreTest OK");
    }
}
