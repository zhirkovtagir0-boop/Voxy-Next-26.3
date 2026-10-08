package com.zhirkovtag.voxynext.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;

/**
 * NeoForge 26.3 distant terrain renderer using the native SubmitNode geometry path.
 * World data is extracted first; vertex emission happens only during geometry submission.
 */
@EventBusSubscriber(modid = "voxy_next", value = Dist.CLIENT)
public final class VoxyNextNeoForgeRenderer {
    private static volatile TerrainState state = TerrainState.EMPTY;

    private VoxyNextNeoForgeRenderer() {}

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        Level level = event.getLevel();
        Minecraft client = Minecraft.getInstance();
        if (level == null || client.player == null) {
            state = TerrainState.EMPTY;
            if (level == null) VoxyNextNeoForge.ENGINE.clearWorld();
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
                    int sz = (z + nz) >> 1;
                    int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, sx, sz);
                    if (y > level.getMinY()) {
                        BlockPos pos = new BlockPos(sx, y, sz);
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
    public static void submit(SubmitCustomGeometryEvent event) {
        TerrainState snapshot = state;
        if (snapshot.cells.length == 0) return;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-snapshot.cameraX, -event.getLevelRenderState().cameraRenderState.pos.y, -snapshot.cameraZ);

        event.getSubmitNodeCollector().submitCustomGeometry(
                pose,
                RenderTypes.solid(),
                (entry, buffer) -> {
                    Matrix4f matrix = entry.pose();
                    for (Cell cell : snapshot.cells) addCell(buffer, matrix, cell);
                }
        );

        pose.popPose();
    }

    private static void addCell(VertexConsumer out, Matrix4fc m, Cell c) {
        float r = ((c.rgb >>> 16) & 255) / 255f;
        float g = ((c.rgb >>> 8) & 255) / 255f;
        float b = (c.rgb & 255) / 255f;
        float y = c.y;
        float skirt = Math.max(2f, Math.min(24f, (c.x1 - c.x0) * .75f));
        float sy = Math.max(y - skirt, y - 24f);

        quad(out,m,c.x0,y,c.z1,c.x1,y,c.z1,c.x1,y,c.z0,c.x0,y,c.z0,r,g,b,1);
        quad(out,m,c.x0,y,c.z0,c.x0,sy,c.z0,c.x0,sy,c.z1,c.x0,y,c.z1,r*.78f,g*.78f,b*.78f,1);
        quad(out,m,c.x1,y,c.z1,c.x1,sy,c.z1,c.x1,sy,c.z0,c.x1,y,c.z0,r*.70f,g*.70f,b*.70f,1);
        quad(out,m,c.x0,y,c.z0,c.x0,sy,c.z0,c.x1,sy,c.z0,c.x1,y,c.z0,r*.84f,g*.84f,b*.84f,1);
        quad(out,m,c.x1,y,c.z1,c.x1,sy,c.z1,c.x0,sy,c.z1,c.x0,y,c.z1,r*.76f,g*.76f,b*.76f,1);
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

    private static int lodScale(double p,double c,int near) {
        double d=Math.abs(p-c);
        return d<near?8:d<near*2?16:32;
    }

    private static int floorTo(double v) {
        return (int)Math.floor(v / 8.0) * 8;
    }

    private static double dist2(double x,double z,double cx,double cz) {
        double dx=x-cx,dz=z-cz;
        return dx*dx+dz*dz;
    }

    private static int color(BlockState s,int y) {
        String n=s.getBlock().toString().toLowerCase(java.util.Locale.ROOT);
        if(n.contains("water"))return 0x3F78A8;
        if(n.contains("sand"))return 0xC9B56A;
        if(n.contains("snow")||n.contains("ice"))return 0xDDE8EA;
        if(n.contains("grass")||n.contains("leaves"))return 0x5F8F45;
        if(n.contains("stone")||n.contains("deepslate"))return y<50?0x666A6B:0x777B7B;
        if(n.contains("dirt")||n.contains("mud"))return 0x806044;
        return y<64?0x77705D:0x748A55;
    }

    private record Cell(int x0,int z0,int x1,int z1,int y,int rgb) {}
    private record TerrainState(double cameraX,double cameraZ,Cell[] cells) {
        private static final TerrainState EMPTY=new TerrainState(0,0,new Cell[0]);
    }
}
