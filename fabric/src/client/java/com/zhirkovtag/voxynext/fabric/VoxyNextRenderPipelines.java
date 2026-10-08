package com.zhirkovtag.voxynext.fabric;

import java.util.Optional;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class VoxyNextRenderPipelines {
    public static final RenderPipeline VOXEL_SOLID = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("voxy_next", "pipeline/voxel_solid"))
                    .withDepthStencilState(Optional.empty())
                    .build()
    );

    private VoxyNextRenderPipelines() {}
}
