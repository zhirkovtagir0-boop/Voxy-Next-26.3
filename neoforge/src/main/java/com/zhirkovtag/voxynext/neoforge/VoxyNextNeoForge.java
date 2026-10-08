package com.zhirkovtag.voxynext.neoforge;
import com.zhirkovtag.voxynext.core.VoxyNextEngine;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Mod("voxy_next") public final class VoxyNextNeoForge {
 private static final Logger LOGGER=LoggerFactory.getLogger("Voxy Next");
 public static final VoxyNextEngine ENGINE=new VoxyNextEngine();
 public VoxyNextNeoForge(){ ENGINE.start(); LOGGER.info("Voxy Next core started for Minecraft 26.3 (NeoForge)"); }
}
