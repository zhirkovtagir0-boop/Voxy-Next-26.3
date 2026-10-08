package com.zhirkovtag.voxynext.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Client-only chunk lifecycle bridge for the shared voxel store. */
@EventBusSubscriber(modid = "voxy_next", value = Dist.CLIENT)
public final class VoxyNextNeoForgeChunkEvents {
    private VoxyNextNeoForgeChunkEvents() {}

    @SubscribeEvent
    public static void onLoad(ChunkEvent.Load event) {
        VoxyNextChunkIngestor.load(event.getChunk(), VoxyNextNeoForge.ENGINE);
    }

    @SubscribeEvent
    public static void onUnload(ChunkEvent.Unload event) {
        VoxyNextChunkIngestor.unload(event.getChunk(), VoxyNextNeoForge.ENGINE);
    }
}
