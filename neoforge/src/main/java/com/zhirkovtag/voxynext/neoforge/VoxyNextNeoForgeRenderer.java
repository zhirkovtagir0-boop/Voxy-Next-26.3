package com.zhirkovtag.voxynext.neoforge;

import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Optional;

/**
 * NeoForge 26.3 implementation of the first distant-terrain pass.
 * Extraction samples the world; the stage reuses NeoForge's active RenderPass.
 */
@EventBusSubscriber(modid = "voxy_next", value = Dist.CLIENT)
public final class VoxyNextNeoForgeRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("voxy_next", "pipeline/distant_terrain"))
                    .build()
    );

    private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f TEX = new Matrix4f();
    private static volatile TerrainState state = TerrainState.EMPTY;

    private VoxyNextNeoForgeRenderer() {}

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        Level level = event.getLevel();
        Minecraft client = Minecraft.getInstance();
        if (level == null || client.player == null) {
            state = TerrainState.EMPTY;
            return;
        }

        double camX = event.getCamera().getPosition().x;
        double camZ = event.getCamera().getPosition().z;
        int nearChunks = Math.max(12, client.options.getEffectiveRenderDistance());
        int near = nearChunks * 16;
        int far = Math.min(4096, Math.max(1024, near * 4));
        int minX = floorTo(camX - far);
        int maxX = floorTo(camX + far);
        int minZ = floorTo(camZ - far);
        int maxZ = floorTo(camZ + far);

        ArrayList<Cell> cells = new ArrayList<>(8192);
        for (int z = minZ; z < maxZ && cells.size() < 12000; ) {
            int sz = lodScale(z + 1, camZ, near);
            int nz = Math.min(maxZ, z + sz);
            for (int x = minX; x < maxX && cells.size() < 12000; ) {
                int scale = Math.min(32, Math.max(sz, lodScale(x + 1, camX, near)));
                int nx = Math.min(maxX, x + scale);
                double cx = (x + nx) * 0.5;
                double cz = (z + nz) * 0.5;
                double d2 = dist2(cx, cz, camX, camZ);
                if (d2 > (near * .9) * (near * .9) && d2 < (far + 32.0) * (far + 32.0)) {
                    int sx = (x + nx) >> 1;
                    int sz2 = (z + nz) >> 1;
                    int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, sx, sz2);
                    if (y > level.getMinY()) {
                        BlockPos pos = new BlockPos(sx, Math.max(level.getMinY(), y - 1), sz2);
                        cells.add(new Cell(x, z, nx, nz, y, color(level.getBlockState(pos), y)));
                    }
                }
                x = nx;
            }
            z = nz;
        }
        state = new TerrainState(camX, camZ, cells.toArray(Cell[]::new));
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        TerrainState snapshot = state;
        if (snapshot.cells.length == 0 || event.getRenderPass() == null) return;

        StagedVertexBuffer buffer = Minecraft.getInstance().renderBuffers().stagedVertexBuffer();
        VertexFormat format = PIPELINE.getVertexFormatBinding(0);
        if (format == null) return;

        StagedVertexBuffer.Draw draw = buffer.appendDraw(format, PIPELINE.getPrimitiveTopology());
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-snapshot.cameraX, -event.getLevelRenderState().cameraRenderState.pos.y, -snapshot.cameraZ);

        VertexConsumer out = buffer.getVertexBuilder(draw);
        for (Cell c : snapshot.cells) addCell(out, pose.last().pose(), c);
        pose.popPose();

        buffer.upload();
        StagedVertexBuffer.ExecuteInfo info = buffer.getExecuteInfo(draw);
        if (info == null || info.customIndexBuffer() == null) {
            buffer.endFrame();
            return;
        }

        RenderPass pass = event.getRenderPass();
        pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), WHITE, ZERO, TEX);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setVertexBuffer(0, info.vertexBuffer().slice());
        pass.setIndexBuffer(info.customIndexBuffer(), info.indexType());
        pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        buffer.endFrame();
    }

    private static void addCell(VertexConsumer out, Matrix4fc m, Cell c) {
        float r = ((c.rgb >>> 16) & 255) / 255f, g = ((c.rgb >>> 8) & 255) / 255f, b = (c.rgb & 255) / 255f;
        float y = c.y, skirt = Math.max(2, Math.min(24, (c.x1 - c.x0) * .75f));
        quad(out, m, c.x0,y,c.z1,c.x1,y,c.z1,c.x1,y,c.z0,c.x0,y,c.z0,r,g,b,1);
        float sy = Math.max(y - skirt, y - 24);
        quad(out,m,c.x0,y,c.z0,c.x0,sy,c.z0,c.x0,sy,c.z1,c.x0,y,c.z1,r*.78f,g*.78f,b*.78f,1);
        quad(out,m,c.x1,y,c.z1,c.x1,sy,c.z1,c.x1,sy,c.z0,c.x1,y,c.z0,r*.70f,g*.70f,b*.70f,1);
        quad(out,m,c.x0,y,c.z0,c.x0,sy,c.z0,c.x1,sy,c.z0,c.x1,y,c.z0,r*.84f,g*.84f,b*.84f,1);
        quad(out,m,c.x1,y,c.z1,c.x1,sy,c.z1,c.x0,sy,c.z1,c.x0,y,c.z1,r*.76f,g*.76f,b*.76f,1);
    }

    private static void quad(VertexConsumer v, Matrix4fc m,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,float r,float g,float b,float a){
        v.addVertex(m,ax,ay,az).setColor(r,g,b,a);
        v.addVertex(m,bx,by,bz).setColor(r,g,b,a);
        v.addVertex(m,cx,cy,cz).setColor(r,g,b,a);
        v.addVertex(m,dx,dy,dz).setColor(r,g,b,a);
    }

    private static int lodScale(double p,double c,int near){double d=Math.abs(p-c);return d<near?8:d<near*2?16:32;}
    private static int floorTo(double v){return (int)Math.floor(v/8.0)*8;}
    private static double dist2(double x,double z,double cx,double cz){double dx=x-cx,dz=z-cz;return dx*dx+dz*dz;}
    private static int color(BlockState s,int y){
        String n=s.getBlock().toString().toLowerCase(java.util.Locale.ROOT);
        if(n.contains("water"))return 0x3F78A8;if(n.contains("sand"))return 0xC9B56A;
        if(n.contains("snow")||n.contains("ice"))return 0xDDE8EA;if(n.contains("grass")||n.contains("leaves"))return 0x5F8F45;
        if(n.contains("stone")||n.contains("deepslate"))return y<50?0x666A6B:0x777B7B;
        if(n.contains("dirt")||n.contains("mud"))return 0x806044;return y<64?0x77705D:0x748A55;
    }
    private record Cell(int x0,int z0,int x1,int z1,int y,int rgb){}
    private record TerrainState(double cameraX,double cameraZ,Cell[] cells){private static final TerrainState EMPTY=new TerrainState(0,0,new Cell[0]);}
}
