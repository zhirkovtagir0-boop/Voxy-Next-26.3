package com.zhirkovtag.voxynext.fabric;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
public final class VoxyNextFabricClient implements ClientModInitializer {
 @Override public void onInitializeClient(){ ClientLifecycleEvents.CLIENT_STOPPING.register(client -> VoxyNextFabric.ENGINE.close()); }
}
