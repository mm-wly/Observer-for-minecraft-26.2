package dev.observer.core;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Sparse quart-cell index, grouped by chunk. Allocate one result per chunk, not per sample. */
public final class BiomeIndex {
    private final Map<Long,Map<SectionKey,BitSet>> chunks=new HashMap<>();
    public void put(SectionKey key,BitSet bits) {
        long column=SectionColumns.key(key.x(),key.z());var sections=chunks.get(column);
        if(sections==null){if(bits==null||bits.isEmpty())return;sections=new HashMap<>();chunks.put(column,sections);}
        if(bits==null||bits.isEmpty())sections.remove(key);else sections.put(key,bits);
        if(sections.isEmpty())chunks.remove(column);
    }
    public List<Target> targets(double x,double y,double z,BooleanSupplier cancelled) {
        var result=new ArrayList<Target>();int fx=(int)Math.floor(x),fy=(int)Math.floor(y),fz=(int)Math.floor(z);
        for(var column:chunks.entrySet()) {
            if(cancelled.getAsBoolean())return List.of();
            double best=Double.POSITIVE_INFINITY;int px=0,py=0,pz=0,bx=0,by=0,bz=0;
            for(var entry:column.getValue().entrySet()) {
                var key=entry.getKey();if(key.minBlockDistanceSquared(x,y,z)>best)continue;
                for(int bit=entry.getValue().nextSetBit(0);bit>=0;bit=entry.getValue().nextSetBit(bit+1)) {
                    int cx=key.x()*16+(bit&3)*4,cy=key.y()*16+(bit>>4)*4,cz=key.z()*16+((bit>>2)&3)*4;
                    int nx=Math.clamp(fx,cx,cx+3),ny=Math.clamp(fy,cy,cy+3),nz=Math.clamp(fz,cz,cz+3);
                    double dx=nx+.5-x,dy=ny+.5-y,dz=nz+.5-z,d=dx*dx+dy*dy+dz*dz;
                    if(d<best||d==best&&(nx<px||nx==px&&(ny<py||ny==py&&nz<pz))){best=d;px=nx;py=ny;pz=nz;bx=cx;by=cy;bz=cz;}
                }
            }
            result.add(new Target("biome:"+column.getKey(),new Position(px,py,pz),new Box(bx,by,bz,bx+4,by+4,bz+4),true,"observer.evidence.biome",-1));
        }
        return List.copyOf(result);
    }
}
