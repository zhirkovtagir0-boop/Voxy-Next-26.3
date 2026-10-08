package com.zhirkovtag.voxynext.fabric;

import com.zhirkovtag.voxynext.core.ChunkColumnSnapshot;
import com.zhirkovtag.voxynext.core.VoxyNextEngine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Converts loaded Fabric client chunks into compact surface snapshots. */
final class VoxyNextChunkIngestor {
    private VoxyNextChunkIngestor() {}

    static void load(LevelChunk chunk, VoxyNextEngine engine) {
        int minY = chunk.getMinY();
        int height = chunk.getHeight();
        ChunkColumnSnapshot snapshot = new ChunkColumnSnapshot(minY, height);

        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (y < minY || y > minY + height - 1) continue;
                var state = chunk.getBlockState(new net.minecraft.core.BlockPos(baseX + x, y, baseZ + z));
                if (state.isAir()) continue;
                int material = BuiltInRegistries.BLOCK.getId(state.getBlock()) + 1;
                snapshot.set(x, y, z, material);
            }
        }
        engine.source().publish(chunkX, chunkZ, snapshot);
        engine.cache().invalidateAroundChunk(chunkX, chunkZ);
    }

    static void unload(LevelChunk chunk, VoxyNextEngine engine) {
        int x = chunk.getPos().x;
        int z = chunk.getPos().z;
        engine.source().remove(x, z);
        engine.cache().invalidateAroundChunk(x, z);
    }
}
