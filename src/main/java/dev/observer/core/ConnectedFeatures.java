package dev.observer.core;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Six-connected source bodies; primitive voxel queue avoids per-edge allocation in large seas. */
public final class ConnectedFeatures {
    private ConnectedFeatures() {}
    public static List<Target> lava(Map<SectionKey,BitSet> sections,BooleanSupplier cancelled) {
        var keys=new ArrayList<>(sections.keySet());keys.sort(Comparator.comparingInt(SectionKey::x).thenComparingInt(SectionKey::y).thenComparingInt(SectionKey::z));
        var ids=new HashMap<SectionKey,Integer>();var bits=new BitSet[keys.size()];var neighbors=new int[keys.size()][];
        for(int i=0;i<keys.size();i++){if(cancelled.getAsBoolean())return List.of();ids.put(keys.get(i),i);bits[i]=(BitSet)sections.get(keys.get(i)).clone();}
        for(int i=0;i<keys.size();i++) {
            if(cancelled.getAsBoolean())return List.of();
            var k=keys.get(i);neighbors[i]=new int[]{ids.getOrDefault(k.offset(-1,0,0),-1),ids.getOrDefault(k.offset(1,0,0),-1),ids.getOrDefault(k.offset(0,-1,0),-1),ids.getOrDefault(k.offset(0,1,0),-1),ids.getOrDefault(k.offset(0,0,-1),-1),ids.getOrDefault(k.offset(0,0,1),-1)};
        }
        var out=new ArrayList<Target>();var queue=new VoxelQueue();
        for(int section=0;section<bits.length;section++)for(int first=bits[section].nextSetBit(0);first>=0;first=bits[section].nextSetBit(0)) {
            if(cancelled.getAsBoolean())return List.of();
            var origin=position(keys.get(section),first);int minX=origin.x(),minY=origin.y(),minZ=origin.z(),maxX=minX,maxY=minY,maxZ=minZ;long count=0;
            bits[section].clear(first);queue.add(((long)section<<12)|first);
            while(!queue.empty()) {
                if((count&1023)==0&&cancelled.getAsBoolean())return List.of();
                long voxel=queue.remove();int s=(int)(voxel>>>12),i=(int)(voxel&4095);count++;
                var k=keys.get(s);int x=i&15,y=i>>8,z=(i>>4)&15;
                int wx=k.x()*16+x,wy=k.y()*16+y,wz=k.z()*16+z;
                minX=Math.min(minX,wx);minY=Math.min(minY,wy);minZ=Math.min(minZ,wz);maxX=Math.max(maxX,wx);maxY=Math.max(maxY,wy);maxZ=Math.max(maxZ,wz);
                visit(x==0?neighbors[s][0]:s,x==0?i+15:i-1,bits,queue);
                visit(x==15?neighbors[s][1]:s,x==15?i-15:i+1,bits,queue);
                visit(y==0?neighbors[s][2]:s,y==0?i+3840:i-256,bits,queue);
                visit(y==15?neighbors[s][3]:s,y==15?i-3840:i+256,bits,queue);
                visit(z==0?neighbors[s][4]:s,z==0?i+240:i-16,bits,queue);
                visit(z==15?neighbors[s][5]:s,z==15?i-240:i+16,bits,queue);
            }
            if(count>=12&&maxX-minX<16&&maxZ-minZ<16&&maxY-minY<4) {
                var box=new Box(minX,minY,minZ,maxX+1,maxY+1,maxZ+1);
                out.add(new Target("lava:"+origin,origin,box,true,"observer.evidence.lava",-1));
            }
        }
        return List.copyOf(out);
    }
    private static void visit(int section,int bit,BitSet[] remaining,VoxelQueue queue) {
        if(section>=0&&remaining[section].get(bit)){remaining[section].clear(bit);queue.add(((long)section<<12)|bit);}
    }
    private static final class VoxelQueue {
        long[] items=new long[1024];int head,size;
        boolean empty(){return size==0;}
        void add(long v) {
            if(size==items.length){long[] next=new long[items.length*2];for(int i=0;i<size;i++)next[i]=items[(head+i)%items.length];items=next;head=0;}
            items[(head+size)%items.length]=v;size++;
        }
        long remove(){long v=items[head];head=(head+1)%items.length;size--;return v;}
    }
    public static Position position(SectionKey k,int bit) {return new Position(k.x()*16+(bit&15),k.y()*16+(bit>>8),k.z()*16+((bit>>4)&15));}
}
