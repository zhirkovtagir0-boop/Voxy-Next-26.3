package com.zhirkovtag.voxynext.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class VoxyNextFabricClient implements ClientModInitializer {
    private static int refreshTicks;
    private static String attachedStoreKey;

    @Override
    public void onInitializeClient() {
        VoxyNextRenderPipelines.VOXEL_SOLID.hashCode();

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
                VoxyNextChunkIngestor.load(chunk, VoxyNextFabric.ENGINE));
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                VoxyNextChunkIngestor.unload(chunk, VoxyNextFabric.ENGINE));

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) {
                attachedStoreKey = null;
                return;
            }

            String serverKey;
            if (client.getSingleplayerServer() != null) {
                // A constant "singleplayer" key mixes terrain snapshots from every local save.
                var saveRoot = client.getSingleplayerServer()
                        .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath().normalize();
                String worldName = saveRoot.getFileName() == null ? "world" : saveRoot.getFileName().toString();
                String worldHash = Integer.toUnsignedString(saveRoot.toString().hashCode(), 16);
                serverKey = "singleplayer_" + worldName + "_" + worldHash;
            } else {
                serverKey = client.getCurrentServer() == null ? "unknown_server" : client.getCurrentServer().ip;
            }
            String dimensionKey = client.level.dimension().identifier().toString();
            String safeKey = (serverKey + "_" + dimensionKey).replaceAll("[^a-zA-Z0-9._-]", "_");

            int cx = client.player.blockPosition().getX() >> 4;
            int cz = client.player.blockPosition().getZ() >> 4;

            if (!safeKey.equals(attachedStoreKey)) {
                VoxyNextFabric.ENGINE.attachStore(
                        client.gameDirectory.toPath().resolve("voxy_next").resolve(safeKey));
                attachedStoreKey = safeKey;

                // attachStore() switches snapshots and clears in-memory state, so re-ingest
                // chunks which were already loaded before this tick (their load events won't repeat).
                int radius = Math.min(32, Math.max(2, client.options.getEffectiveRenderDistance()) + 1);
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        var chunk = client.level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                        if (chunk != null) VoxyNextChunkIngestor.load(chunk, VoxyNextFabric.ENGINE);
                    }
                }
            }

            if (++refreshTicks % 10 != 0) return;
            if (VoxyNextFabric.ENGINE.source().get(cx, cz) == null) {
                var chunk = client.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) VoxyNextChunkIngestor.load(chunk, VoxyNextFabric.ENGINE);
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            VoxyNextWorldRenderer.close();
            VoxyNextFabric.ENGINE.clearWorld();
            VoxyNextFabric.ENGINE.close();
        });
    }
}
