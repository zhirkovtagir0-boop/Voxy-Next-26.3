package com.zhirkovtag.voxynext.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class VoxyNextFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VoxyNextRenderPipelines.VOXEL_SOLID.hashCode();

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) ->
                VoxyNextChunkIngestor.load(chunk, VoxyNextFabric.ENGINE));
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                VoxyNextChunkIngestor.unload(chunk, VoxyNextFabric.ENGINE));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            VoxyNextFabric.ENGINE.clearWorld();
            VoxyNextFabric.ENGINE.close();
        });
    }
}
