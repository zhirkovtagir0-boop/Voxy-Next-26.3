package com.zhirkovtag.voxynext.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ChunkSnapshotGrid {
    private final ConcurrentHashMap<Long, ChunkColumnSnapshot> chunks = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();
    public void publish(int chunkX,int chunkZ,ChunkColumnSnapshot snapshot){ if(snapshot==null)throw new NullPointerException("snapshot"); chunks.put(key(chunkX,chunkZ),snapshot); generation.incrementAndGet(); }
    public ChunkColumnSnapshot get(int chunkX,int chunkZ){ return chunks.get(key(chunkX,chunkZ)); }
    public ChunkColumnSnapshot remove(int chunkX,int chunkZ){ ChunkColumnSnapshot r=chunks.remove(key(chunkX,chunkZ)); if(r!=null)generation.incrementAndGet(); return r; }
    public void clear(){ if(!chunks.isEmpty()){chunks.clear();generation.incrementAndGet();} }
    public int size(){return chunks.size();}
    public long generation(){return generation.get();}
    public int topY(int worldX,int worldZ){ChunkColumnSnapshot s=get(Math.floorDiv(worldX,16),Math.floorDiv(worldZ,16)); if(s==null)return Integer.MIN_VALUE; return s.topY(Math.floorMod(worldX,16),Math.floorMod(worldZ,16));}
    public int material(int worldX,int worldZ){ChunkColumnSnapshot s=get(Math.floorDiv(worldX,16),Math.floorDiv(worldZ,16)); if(s==null)return MaterialPalette.AIR; int x=Math.floorMod(worldX,16),z=Math.floorMod(worldZ,16),y=s.topY(x,z); return y<s.minY()?MaterialPalette.AIR:s.material(x,y,z);}
    private static long key(int x,int z){return((long)x<<32)^(z&0xFFFFFFFFL);}
}