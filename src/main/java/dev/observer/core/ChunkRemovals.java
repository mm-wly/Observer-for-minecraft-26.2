package dev.observer.core;

import java.util.*;

/** Batched removal mask; small target boxes use direct membership instead of scanning the batch. */
public final class ChunkRemovals {
    private final Set<Long> chunks=new HashSet<>();
    public void add(int x,int z){chunks.add(SectionColumns.key(x,z));}
    public boolean isEmpty(){return chunks.isEmpty();}
    public void clear(){chunks.clear();}
    public boolean intersects(Box box) {
        int x0=box.minX()>>4,x1=(box.maxX()-1)>>4,z0=box.minZ()>>4,z1=(box.maxZ()-1)>>4;
        if((long)(x1-x0+1)*(z1-z0+1)<=chunks.size()) {
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(chunks.contains(SectionColumns.key(x,z)))return true;
        } else for(long chunk:chunks) {int x=(int)(chunk>>32),z=(int)chunk;if(x>=x0&&x<=x1&&z>=z0&&z<=z1)return true;}
        return false;
    }
    public List<Target> filter(List<Target> targets) {
        if(chunks.isEmpty())return targets;
        ArrayList<Target> kept=null;
        for(int i=0;i<targets.size();i++) {
            var t=targets.get(i);
            if(intersects(t.box())){if(kept==null)kept=new ArrayList<>(targets.subList(0,i));}
            else if(kept!=null)kept.add(t);
        }
        return kept==null?targets:List.copyOf(kept);
    }
}
