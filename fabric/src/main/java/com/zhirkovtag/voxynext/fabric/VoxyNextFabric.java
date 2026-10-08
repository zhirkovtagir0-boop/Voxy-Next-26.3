package com.zhirkovtag.voxynext.fabric;

import com.zhirkovtag.voxynext.core.VoxyNextEngine;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VoxyNextFabric implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Voxy Next");
    public static final VoxyNextEngine ENGINE = new VoxyNextEngine();

    @Override
    public void onInitialize() {
        VoxyNextConfig config = VoxyNextConfig.load(
                FabricLoader.getInstance().getConfigDir().resolve("voxy_next.json"));
        ENGINE.setBudget(config.budget());
        ENGINE.start();
        LOGGER.info("Voxy Next core started for Minecraft 26.3 (Fabric)");
    }
}
