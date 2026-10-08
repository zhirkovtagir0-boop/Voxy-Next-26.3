package com.zhirkovtag.voxynext.fabric;

import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * First visible Voxy-Next terrain pass.
 *
 * It deliberately uses the modern extraction/drawing split: world sampling happens
 * during extraction, while GPU submission happens during drawing. The mesh is a
 * coarse height-field now; the storage/voxel pipeline will replace this sampler
 * without changing the render pass.
 */
public final class VoxyNextWorldRenderer implements ClientModInitializer {
    private static final RenderPipeline TERRAIN_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("voxy_next", "pipeline/distant_terrain"))
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final StagedVertexBuffer BUFFER =
            new StagedVertexBuffer(() -> "Voxy Next Distant Terrain", RenderType.SMALL_BUFFER_SIZE);

    private static volatile TerrainState state = TerrainState.EMPTY;

    @Override
    public void onInitializeClient() {
        LevelExtractionEvents.END_EXTRACTION.register(VoxyNextWorldRenderer::extract);
        LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(VoxyNextWorldRenderer::draw);
    }

    private static void extract(LevelExtractionContext context) {
        Minecraft client = Minecraft.getInstance();
        Level level = client.level;
        if (level == null || client.player == null) {
            state = TerrainState.EMPTY;
            return;
        }

        double camX = context.levelState().cameraRenderState.pos.x;
        double camZ = context.levelState().cameraRenderState.pos.z;

        // The first pass intentionally starts beyond vanilla's close terrain.
        final int nearChunks = Math.max(12, client.options.getEffectiveRenderDistance());
        final int maxChunks = Math.min(256, Math.max(64, nearChunks * 4));
        final int nearBlocks = nearChunks * 16;
        final int maxBlocks = maxChunks * 16;

        int minX = floorTo(camX - maxBlocks);
        int maxX = floorTo(camX + maxBlocks);
        int minZ = floorTo(camZ - maxBlocks);
        int maxZ = floorTo(camZ + maxBlocks);

        // Keep extraction bounded. Cell size grows with distance.
        java.util.ArrayList<Cell> cells = new java.util.ArrayList<>(8192);
        for (int z = minZ; z < maxZ; ) {
            int scaleZ = lodScale(z + 1, camZ, nearBlocks);
            int nextZ = Math.min(maxZ, z + scaleZ);
            for (int x = minX; x < maxX; ) {
                int scale = Math.max(scaleZ, lodScale(x + 1, camX, nearBlocks));
                scale = Math.min(scale, 32);
                int nextX = Math.min(maxX, x + scale);

                double cx = x + (nextX - x) * 0.5;
                double cz = z + (nextZ - z) * 0.5;
                double d2 = distanceSquared(cx, cz, camX, camZ);
                if (d2 >= (nearBlocks * 0.9) * (nearBlocks * 0.9)
                        && d2 <= (maxBlocks + 32.0) * (maxBlocks + 32.0)
                        && cells.size() < 12000) {
                    int sx = (x + nextX) >> 1;
                    int sz = (z + nextZ) >> 1;
                    int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, sx, sz);
                    if (y > level.getMinY()) {
                        BlockPos pos = new BlockPos(sx, Math.max(level.getMinY(), y - 1), sz);
                        BlockState block = level.getBlockState(pos);
                        int rgb = terrainColor(block, level, pos, y);
                        cells.add(new Cell(x, z, nextX, nextZ, y, rgb));
                    }
                }
                x = nextX;
            }
            z = nextZ;
        }

        state = new TerrainState(camX, camZ, cells.toArray(Cell[]::new));
    }

    private static void draw(LevelRenderContext context) {
        TerrainState snapshot = state;
        if (snapshot.cells.length == 0) {
            return;
        }

        RenderPipeline pipeline = TERRAIN_PIPELINE;
        VertexFormat format = pipeline.getVertexFormatBinding(0);
        if (format == null) {
            return;
        }

        PrimitiveTopology topology = pipeline.getPrimitiveTopology();
        StagedVertexBuffer.Draw draw =
                BUFFER.appendDraw(format, topology, topology == PrimitiveTopology.QUADS
                        ? RenderSystem.getProjectionType().vertexSorting() : null);

        PoseStack pose = context.poseStack();
        pose.pushPose();
        pose.translate(-snapshot.cameraX, -context.levelState().cameraRenderState.pos.y, -snapshot.cameraZ);

        VertexConsumer out = BUFFER.getVertexBuilder(draw);
        for (Cell cell : snapshot.cells) {
            addCell(out, pose.last().pose(), cell);
        }

        pose.popPose();

        BUFFER.upload();
        StagedVertexBuffer.ExecuteInfo info = BUFFER.getExecuteInfo(draw);
        if (info != null) {
            submit(Minecraft.getInstance(), info, pipeline);
        }
        BUFFER.endFrame();
    }

    private static void addCell(VertexConsumer out, Matrix4fc matrix, Cell c) {
        float r = ((c.rgb >>> 16) & 255) / 255f;
        float g = ((c.rgb >>> 8) & 255) / 255f;
        float b = (c.rgb & 255) / 255f;

        float x0 = c.x0, x1 = c.x1, z0 = c.z0, z1 = c.z1, y = c.y;

        // Top.
        quad(out, matrix, x0, y, z1, x1, y, z1, x1, y, z0, x0, y, z0, r, g, b, 1f);

        // A shallow skirt hides cracks between different LOD rings and gives cliffs volume.
        float skirt = Math.max(2f, Math.min(24f, (x1 - x0) * 0.75f));
        quad(out, matrix, x0, y, z0, x0, Math.max(y - skirt, y - 24f), z0,
                x0, Math.max(y - skirt, y - 24f), z1, x0, y, z1, r * .78f, g * .78f, b * .78f, 1f);
        quad(out, matrix, x1, y, z1, x1, Math.max(y - skirt, y - 24f), z1,
                x1, Math.max(y - skirt, y - 24f), z0, x1, y, z0, r * .70f, g * .70f, b * .70f, 1f);
        quad(out, matrix, x0, y, z0, x0, Math.max(y - skirt, y - 24f), z0,
                x1, Math.max(y - skirt, y - 24f), z0, x1, y, z0, r * .84f, g * .84f, b * .84f, 1f);
        quad(out, matrix, x1, y, z1, x1, Math.max(y - skirt, y - 24f), z1,
                x0, Math.max(y - skirt, y - 24f), z1, x0, y, z1, r * .76f, g * .76f, b * .76f, 1f);
    }

    private static void quad(VertexConsumer v, Matrix4fc m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float a) {
        v.addVertex(m, ax, ay, az).setColor(r, g, b, a);
        v.addVertex(m, bx, by, bz).setColor(r, g, b, a);
        v.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
        v.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
    }

    private static void submit(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        RenderTarget target = client.gameRenderer.mainRenderTarget();
        GpuTextureView color = target.getColorTextureView();
        if (color == null) {
            return;
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Voxy Next distant terrain", color, Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    private static int lodScale(double coordinate, double camera, int nearBlocks) {
        double d = Math.abs(coordinate - camera);
        if (d < nearBlocks) return 8;
        if (d < nearBlocks * 2.0) return 16;
        return 32;
    }

    private static int floorTo(double value) {
        return (int) Math.floor(value / 8.0) * 8;
    }

    private static double distanceSquared(double x, double z, double cx, double cz) {
        double dx = x - cx;
        double dz = z - cz;
        return dx * dx + dz * dz;
    }

    private static int terrainColor(BlockState state, Level level, BlockPos pos, int y) {
        String name = state.getBlock().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("water")) return 0x3F78A8;
        if (name.contains("sand")) return 0xC9B56A;
        if (name.contains("snow") || name.contains("ice")) return 0xDDE8EA;
        if (name.contains("grass") || name.contains("leaves")) return 0x5F8F45;
        if (name.contains("stone") || name.contains("deepslate")) return y < 50 ? 0x666A6B : 0x777B7B;
        if (name.contains("dirt") || name.contains("mud")) return 0x806044;
        return y < 64 ? 0x77705D : 0x748A55;
    }

    private record Cell(int x0, int z0, int x1, int z1, int y, int rgb) {
    }

    private record TerrainState(double cameraX, double cameraZ, Cell[] cells) {
        private static final TerrainState EMPTY = new TerrainState(0, 0, new Cell[0]);
    }
}
