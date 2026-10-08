package com.zhirkovtag.voxynext.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoForge 26.3 distant terrain renderer using the native SubmitNode geometry path.
 * World data is extracted first; vertex emission happens only during geometry submission.
 */
@EventBusSubscriber(modid = "voxy_next", value = Dist.CLIENT)
public final class VoxyNextNeoForgeRenderer {
    private static final com.zhirkovtag.voxynext.core.LodRegionSelector SELECTOR =
            new com.zhirkovtag.voxynext.core.LodRegionSelector();
    private static final ConcurrentHashMap<Long, RegionCells> REGION_CELL_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_REGION_CELL_CACHE = 8192;
    private static volatile TerrainState state = TerrainState.EMPTY;
    private static double lastBuildCameraX = Double.NaN;
    private static double lastBuildCameraZ = Double.NaN;
    private static int lastBuildDistance = -1;
    private static long lastBuildGeneration = Long.MIN_VALUE;

    private VoxyNextNeoForgeRenderer() {}

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        Level level = event.getLevel();
        Minecraft client = Minecraft.getInstance();
        if (level == null || client.player == null) {
            state = TerrainState.EMPTY;
            lastBuildCameraX = Double.NaN;
            if (level == null) VoxyNextNeoForge.ENGINE.clearWorld();
            return;
        }

        double camX = event.getCamera().getPosition().x;
        double camZ = event.getCamera().getPosition().z;
        int renderDistanceChunks = Math.max(
                client.options.getEffectiveRenderDistance(),
                VoxyNextNeoForge.ENGINE.budget().renderDistanceChunks());

        long cacheGeneration = VoxyNextNeoForge.ENGINE.cache().generation();
        boolean reuse = !Double.isNaN(lastBuildCameraX)
                && Math.abs(camX - lastBuildCameraX) < 8.0
                && Math.abs(camZ - lastBuildCameraZ) < 8.0
                && renderDistanceChunks == lastBuildDistance
                && cacheGeneration == lastBuildGeneration;
        if (reuse) {
            state = new TerrainState(camX, camZ, state.cells);
            return;
        }

        java.util.List<com.zhirkovtag.voxynext.core.VisibleRegion> visible =
                SELECTOR.select(camX, camZ, renderDistanceChunks,
                        Math.min(VoxyNextNeoForge.ENGINE.budget().maxRegionsInMemory(), 4096));

        java.util.ArrayList<Cell> cells = new java.util.ArrayList<>(30000);
        for (com.zhirkovtag.voxynext.core.VisibleRegion candidate : visible) {
            VoxyNextNeoForge.ENGINE.requestRegion(candidate.level(), candidate.regionX(), candidate.regionZ());
            com.zhirkovtag.voxynext.core.LodRegion region =
                    VoxyNextNeoForge.ENGINE.cache().get(candidate.level(),
                            Math.toIntExact(candidate.regionX()), Math.toIntExact(candidate.regionZ()));
            if (region == null) continue;

            int scale = region.level().scale();
            long baseX = region.regionX() * (long) region.blockSpan();
            long baseZ = region.regionZ() * (long) region.blockSpan();

            RegionCells cached = cachedCells(region);
            for (Cell cell : cached.cells) {
                if (cells.size() >= 30000) break;
                cells.add(cell);
            }
            if (cells.size() >= 30000) break;
        }
        Cell[] builtCells = cells.toArray(Cell[]::new);
        state = new TerrainState(camX, camZ, builtCells);
        lastBuildCameraX = camX;
        lastBuildCameraZ = camZ;
        lastBuildDistance = renderDistanceChunks;
        lastBuildGeneration = cacheGeneration;
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

    private static RegionCells cachedCells(com.zhirkovtag.voxynext.core.LodRegion region) {
        long key = regionKey(region);
        RegionCells cached = REGION_CELL_CACHE.get(key);
        if (cached != null && cached.region == region) return cached;

        int scale = region.level().scale();
        long baseX = region.regionX() * (long) region.blockSpan();
        long baseZ = region.regionZ() * (long) region.blockSpan();
        ArrayList<Cell> cells = new ArrayList<>(com.zhirkovtag.voxynext.core.LodRegion.SIZE * com.zhirkovtag.voxynext.core.LodRegion.SIZE);
        for (int z = 0; z < com.zhirkovtag.voxynext.core.LodRegion.SIZE; z++) {
            int x = 0;
            while (x < com.zhirkovtag.voxynext.core.LodRegion.SIZE) {
                com.zhirkovtag.voxynext.core.VoxelCell first = region.get(x, z);
                if (first == null || first.packedMaterial() == 0) { x++; continue; }
                int endX = x + 1;
                while (endX < com.zhirkovtag.voxynext.core.LodRegion.SIZE) {
                    com.zhirkovtag.voxynext.core.VoxelCell next = region.get(endX, z);
                    if (next == null || next.packedMaterial() != first.packedMaterial() || next.maxY() != first.maxY()) break;
                    endX++;
                }
                int x0 = Math.toIntExact(baseX + (long)x * scale);
                int z0 = Math.toIntExact(baseZ + (long)z * scale);
                int x1 = Math.toIntExact(baseX + (long)endX * scale);
                cells.add(new Cell(x0, z0, x1, z0 + scale, first.maxY(),
                        VoxyNextNeoForge.ENGINE.palette().color(first.packedMaterial())));
                x = endX;
            }
        }
        RegionCells result = new RegionCells(region, cells.toArray(Cell[]::new));
        REGION_CELL_CACHE.put(key, result);
        if (REGION_CELL_CACHE.size() > MAX_REGION_CELL_CACHE) REGION_CELL_CACHE.clear();
        return result;
    }

    private static long regionKey(com.zhirkovtag.voxynext.core.LodRegion region) {
        return ((long)region.level().ordinal() << 58)
                ^ ((region.regionX() & 0x1FFFFFFFL) << 29)
                ^ (region.regionZ() & 0x1FFFFFFFL);
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
    private record RegionCells(com.zhirkovtag.voxynext.core.LodRegion region, Cell[] cells) {}
    private record TerrainState(double cameraX,double cameraZ,Cell[] cells) {
        private static final TerrainState EMPTY=new TerrainState(0,0,new Cell[0]);
    }
}
