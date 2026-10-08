package com.zhirkovtag.voxynext.fabric;

import java.util.Optional;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Render-pipeline definitions for the distant voxel pass.
 *
 * The actual geometry submission is intentionally kept separate so extraction can
 * be moved off the render thread without coupling the storage core to Blaze3D.
 */
public final class VoxyNextRenderPipelines {
    public static final RenderPipeline VOXEL_SOLID = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("voxy_next", "pipeline/voxel_solid"))
                    .withDepthStencilState(Optional.empty())
                    .build()
    );

    private VoxyNextRenderPipelines() {}
}
