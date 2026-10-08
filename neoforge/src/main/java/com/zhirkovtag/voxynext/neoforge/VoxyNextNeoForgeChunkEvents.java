package com.zhirkovtag.voxynext.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;

/** Client-only chunk lifecycle bridge for the shared voxel store. */
@EventBusSubscriber(modid = "voxy_next", value = Dist.CLIENT)
public final class VoxyNextNeoForgeChunkEvents {
    private static int refreshTicks;
    private VoxyNextNeoForgeChunkEvents() {}

    @SubscribeEvent
    public static void onLoad(ChunkEvent.Load event) {
        VoxyNextChunkIngestor.load(event.getChunk(), VoxyNextNeoForge.ENGINE);
    }

    @SubscribeEvent
    public static void onUnload(ChunkEvent.Unload event) {
        VoxyNextChunkIngestor.unload(event.getChunk(), VoxyNextNeoForge.ENGINE);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || (++refreshTicks % 10) != 0) return;
        String serverKey = client.getCurrentServer() == null ? "singleplayer" : client.getCurrentServer().ip;
        String dimensionKey = client.level.dimension().identifier().toString();
        String safeKey = (serverKey + "_" + dimensionKey).replaceAll("[^a-zA-Z0-9._-]", "_");
        VoxyNextNeoForge.ENGINE.attachStore(client.gameDirectory.toPath().resolve("voxy_next").resolve(safeKey));
        int cx = client.player.blockPosition().getX() >> 4;
        int cz = client.player.blockPosition().getZ() >> 4;
        var chunk = client.level.getChunkSource().getChunkNow(cx, cz);
        if (chunk != null) VoxyNextChunkIngestor.load(chunk, VoxyNextNeoForge.ENGINE);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            VoxyNextNeoForge.ENGINE.clearWorld();
        }
    }
}
