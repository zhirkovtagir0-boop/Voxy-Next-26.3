package com.zhirkovtag.voxynext.fabric;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.zhirkovtag.voxynext.core.LodRegion;
import com.zhirkovtag.voxynext.core.LodRegionSelector;
import com.zhirkovtag.voxynext.core.VisibleRegion;
import com.zhirkovtag.voxynext.core.VoxelCell;

/** Fabric 26.3 distant terrain renderer. */
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
    private static final LodRegionSelector SELECTOR = new LodRegionSelector();
    private static final ConcurrentHashMap<Long, RegionCells> REGION_CELL_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_REGION_CELL_CACHE = 8192;

    private static volatile TerrainState state = TerrainState.EMPTY;
    private static volatile StagedVertexBuffer.Draw uploadedDraw;
    private static double lastBuildCameraX = Double.NaN;
    private static double lastBuildCameraZ = Double.NaN;
    private static int lastBuildDistance = -1;
    private static long lastBuildGeneration = Long.MIN_VALUE;

    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.END_EXTRACTION.register(VoxyNextWorldRenderer::extract);
        // GPU uploads must happen before Minecraft opens the terrain render pass.
        LevelRenderEvents.START_MAIN.register(VoxyNextWorldRenderer::upload);
        LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(VoxyNextWorldRenderer::draw);
    }

    public static void close() {
        BUFFER.close();
        state = TerrainState.EMPTY;
    }

    private static void extract(LevelExtractionContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            state = TerrainState.EMPTY;
            lastBuildCameraX = Double.NaN;
            VoxyNextFabric.ENGINE.clearWorld();
            return;
        }

        double camX = context.levelState().cameraRenderState.pos.x;
        double camZ = context.levelState().cameraRenderState.pos.z;
        int renderDistanceChunks = Math.max(
                client.options.getEffectiveRenderDistance(),
                VoxyNextFabric.ENGINE.budget().renderDistanceChunks());

        long cacheGeneration = VoxyNextFabric.ENGINE.cache().generation();
        boolean reuse = !Double.isNaN(lastBuildCameraX)
                && Math.abs(camX - lastBuildCameraX) < 8.0
                && Math.abs(camZ - lastBuildCameraZ) < 8.0
                && renderDistanceChunks == lastBuildDistance
                && cacheGeneration == lastBuildGeneration;
        if (reuse) {
            state = new TerrainState(camX, camZ, state.cameraY, state.cells);
            return;
        }

        List<VisibleRegion> visible = SELECTOR.select(
                camX, camZ, renderDistanceChunks,
                Math.min(VoxyNextFabric.ENGINE.budget().maxRegionsInMemory(), 4096));

        ArrayList<Cell> cells = new ArrayList<>(30000);
        for (VisibleRegion candidate : visible) {
            VoxyNextFabric.ENGINE.requestRegion(candidate.level(), candidate.regionX(), candidate.regionZ());
            LodRegion region = VoxyNextFabric.ENGINE.cache().get(
                    candidate.level(), Math.toIntExact(candidate.regionX()), Math.toIntExact(candidate.regionZ()));
            if (region == null) continue;

            RegionCells cached = cachedCells(region);
            for (Cell cell : cached.cells) {
                if (cells.size() >= 30000) break;
                cells.add(cell);
            }
            if (cells.size() >= 30000) break;
        }

        double camY = context.levelState().cameraRenderState.pos.y;
        state = new TerrainState(camX, camZ, camY, cells.toArray(Cell[]::new));
        lastBuildCameraX = camX;
        lastBuildCameraZ = camZ;
        lastBuildDistance = renderDistanceChunks;
        lastBuildGeneration = cacheGeneration;
    }

    private static void upload(LevelRenderContext context) {
        TerrainState snapshot = state;
        uploadedDraw = null;
        if (snapshot.cells.length == 0) return;

        VertexFormat format = TERRAIN_PIPELINE.getVertexFormatBinding(0);
        if (format == null) return;

        PrimitiveTopology topology = TERRAIN_PIPELINE.getPrimitiveTopology();
        StagedVertexBuffer.Draw draw = BUFFER.appendDraw(format, topology);

        double cameraY = snapshot.cameraY;
        Matrix4f cameraMatrix = new Matrix4f()
                .translation((float) -snapshot.cameraX, (float) -cameraY, (float) -snapshot.cameraZ);

        VertexConsumer out = BUFFER.getVertexBuilder(draw);
        for (Cell cell : snapshot.cells) addCell(out, cameraMatrix, cell);

        // START_MAIN is outside the active terrain render pass, so command-buffer
        // copies performed by StagedVertexBuffer.upload() are legal here.
        BUFFER.upload();
        uploadedDraw = draw;
    }

    private static void draw(LevelTerrainRenderContext context) {
        StagedVertexBuffer.Draw draw = uploadedDraw;
        if (draw == null) return;
        StagedVertexBuffer.ExecuteInfo info = BUFFER.getExecuteInfo(draw);
        if (info == null) {
            BUFFER.endFrame();
            return;
        }

        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var color = target.getColorTextureView();
        if (color == null) {
            BUFFER.endFrame();
            return;
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Voxy Next distant terrain", color, Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(TERRAIN_PIPELINE));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
        BUFFER.endFrame();
        uploadedDraw = null;
    }

    private static RegionCells cachedCells(LodRegion region) {
        long key = regionKey(region);
        RegionCells cached = REGION_CELL_CACHE.get(key);
        if (cached != null && cached.region == region) return cached;

        int scale = region.level().scale();
        long baseX = region.regionX() * (long) region.blockSpan();
        long baseZ = region.regionZ() * (long) region.blockSpan();
        ArrayList<Cell> cells = new ArrayList<>(LodRegion.SIZE * LodRegion.SIZE);
        for (int z = 0; z < LodRegion.SIZE; z++) {
            int x = 0;
            while (x < LodRegion.SIZE) {
                VoxelCell first = region.get(x, z);
                if (first == null || first.packedMaterial() == 0) { x++; continue; }
                int endX = x + 1;
                while (endX < LodRegion.SIZE) {
                    VoxelCell next = region.get(endX, z);
                    if (next == null || next.packedMaterial() != first.packedMaterial() || next.maxY() != first.maxY()) break;
                    endX++;
                }
                int x0 = Math.toIntExact(baseX + (long) x * scale);
                int z0 = Math.toIntExact(baseZ + (long) z * scale);
                int x1 = Math.toIntExact(baseX + (long) endX * scale);
                cells.add(new Cell(x0, z0, x1, z0 + scale, first.maxY(),
                        VoxyNextFabric.ENGINE.palette().color(first.packedMaterial())));
                x = endX;
            }
        }
        RegionCells result = new RegionCells(region, cells.toArray(Cell[]::new));
        REGION_CELL_CACHE.put(key, result);
        if (REGION_CELL_CACHE.size() > MAX_REGION_CELL_CACHE) {
            int target = MAX_REGION_CELL_CACHE * 3 / 4;
            var iterator = REGION_CELL_CACHE.keySet().iterator();
            while (REGION_CELL_CACHE.size() > target && iterator.hasNext()) REGION_CELL_CACHE.remove(iterator.next());
        }
        return result;
    }

    private static long regionKey(LodRegion region) {
        return ((long) region.level().ordinal() << 58)
                ^ ((region.regionX() & 0x1FFFFFFFL) << 29)
                ^ (region.regionZ() & 0x1FFFFFFFL);
    }

    private static void addCell(VertexConsumer out, Matrix4fc matrix, Cell c) {
        float r = ((c.rgb >>> 16) & 255) / 255f;
        float g = ((c.rgb >>> 8) & 255) / 255f;
        float b = (c.rgb & 255) / 255f;
        float y = c.y;
        float skirt = Math.max(2f, Math.min(24f, (c.x1 - c.x0) * .75f));
        float sy = Math.max(y - skirt, y - 24f);

        quad(out,matrix,c.x0,y,c.z1,c.x1,y,c.z1,c.x1,y,c.z0,c.x0,y,c.z0,r,g,b,1);
        quad(out,matrix,c.x0,y,c.z0,c.x0,sy,c.z0,c.x0,sy,c.z1,c.x0,y,c.z1,r*.78f,g*.78f,b*.78f,1);
        quad(out,matrix,c.x1,y,c.z1,c.x1,sy,c.z1,c.x1,sy,c.z0,c.x1,y,c.z0,r*.70f,g*.70f,b*.70f,1);
        quad(out,matrix,c.x0,y,c.z0,c.x0,sy,c.z0,c.x1,sy,c.z0,c.x1,y,c.z0,r*.84f,g*.84f,b*.84f,1);
        quad(out,matrix,c.x1,y,c.z1,c.x1,sy,c.z1,c.x0,sy,c.z1,c.x0,y,c.z1,r*.76f,g*.76f,b*.76f,1);
    }

    private static void quad(VertexConsumer v, Matrix4fc m,
                             float ax,float ay,float az,float bx,float by,float bz,
                             float cx,float cy,float cz,float dx,float dy,float dz,
                             float r,float g,float b,float a) {
        v.addVertex(m,ax,ay,az).setColor(r,g,b,a);
        v.addVertex(m,bx,by,bz).setColor(r,g,b,a);
        v.addVertex(m,cx,cy,cz).setColor(r,g,b,a);
        v.addVertex(m,dx,dy,dz).setColor(r,g,b,a);
    }

    private record Cell(int x0,int z0,int x1,int z1,int y,int rgb) {}
    private record RegionCells(LodRegion region, Cell[] cells) {}
    private record TerrainState(double cameraX,double cameraZ,double cameraY,Cell[] cells) {
        private static final TerrainState EMPTY=new TerrainState(0,0,0,new Cell[0]);
    }
}
