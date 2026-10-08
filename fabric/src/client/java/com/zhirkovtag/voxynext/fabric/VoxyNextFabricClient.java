package com.zhirkovtag.voxynext.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class VoxyNextFabricClient implements ClientModInitializer {
    private static int refreshTicks;

    @Override
    public void onInitializeClient() {
        VoxyNextRenderPipelines.VOXEL_SOLID.hashCode();

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
                VoxyNextChunkIngestor.load(chunk, VoxyNextFabric.ENGINE));
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                VoxyNextChunkIngestor.unload(chunk, VoxyNextFabric.ENGINE));

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null || (++refreshTicks % 10) != 0) return;
            String serverKey = client.getCurrentServer() == null ? "singleplayer" : client.getCurrentServer().ip;
            String dimensionKey = client.level.dimension().identifier().toString();
            String safeKey = (serverKey + "_" + dimensionKey).replaceAll("[^a-zA-Z0-9._-]", "_");
            VoxyNextFabric.ENGINE.attachStore(client.gameDirectory.toPath().resolve("voxy_next").resolve(safeKey));
            int cx = client.player.blockPosition().getX() >> 4;
            int cz = client.player.blockPosition().getZ() >> 4;
            var chunk = client.level.getChunkSource().getChunkNow(cx, cz);
            if (chunk != null) VoxyNextChunkIngestor.load(chunk, VoxyNextFabric.ENGINE);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            VoxyNextWorldRenderer.close();
            VoxyNextFabric.ENGINE.clearWorld();
            VoxyNextFabric.ENGINE.close();
        });
    }
}
