package com.zhirkovtag.voxynext.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small persistent surface cache. It lets explored terrain survive client chunk unloads,
 * which is the foundation for genuinely long-distance rendering.
 */
public final class ChunkSnapshotStore implements AutoCloseable {
    private static final int MAGIC = 0x56584E31; // VXN1
    private final Path directory;
    private final ExecutorService writer;

    public ChunkSnapshotStore(Path directory) {
        this.directory = directory;
        this.writer = Executors.newSingleThreadExecutor(task -> {
            Thread t = new Thread(task, "VoxyNext-Storage");
            t.setDaemon(true);
            return t;
        });
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create Voxy Next storage: " + directory, e);
        }
    }

    public void loadInto(ChunkSnapshotGrid grid) {
        try (var stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".vxs"))
                    .forEach(path -> {
                        try {
                            Loaded loaded = read(path);
                            grid.publish(loaded.chunkX, loaded.chunkZ, loaded.snapshot);
                        } catch (Exception ignored) {
                            // A corrupt cache entry is disposable; live chunk data will replace it.
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    public void save(int chunkX, int chunkZ, ChunkColumnSnapshot snapshot) {
        writer.execute(() -> {
            Path target = file(chunkX, chunkZ);
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            try {
                write(temp, chunkX, chunkZ, snapshot);
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {
                try { Files.deleteIfExists(temp); } catch (IOException ignoredAgain) {}
            }
        });
    }

    private Path file(int chunkX, int chunkZ) {
        return directory.resolve(chunkX + "_" + chunkZ + ".vxs");
    }

    private static void write(Path path, int chunkX, int chunkZ, ChunkColumnSnapshot snapshot) throws IOException {
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(MAGIC);
            out.writeInt(chunkX);
            out.writeInt(chunkZ);
            out.writeInt(snapshot.minY());
            out.writeInt(snapshot.height());
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) out.writeInt(snapshot.topY(x, z));
            }
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) out.writeInt(snapshot.topMaterial(x, z));
            }
        }
    }

    private static Loaded read(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readInt() != MAGIC) throw new IOException("bad magic");
            int chunkX = in.readInt();
            int chunkZ = in.readInt();
            int minY = in.readInt();
            int height = in.readInt();
            ChunkColumnSnapshot snapshot = new ChunkColumnSnapshot(minY, height);
            int[] ys = new int[256];
            for (int i = 0; i < 256; i++) ys[i] = in.readInt();
            for (int i = 0; i < 256; i++) {
                int material = in.readInt();
                int x = i & 15;
                int z = i >>> 4;
                int y = ys[i];
                if (material != MaterialPalette.AIR && y >= minY && y <= snapshot.maxY()) {
                    snapshot.set(x, y, z, material);
                }
            }
            return new Loaded(chunkX, chunkZ, snapshot);
        }
    }

    @Override
    public void close() {
        writer.shutdown();
    }

    private record Loaded(int chunkX, int chunkZ, ChunkColumnSnapshot snapshot) {}
}
