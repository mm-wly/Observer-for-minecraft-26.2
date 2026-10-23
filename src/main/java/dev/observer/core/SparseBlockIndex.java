package dev.observer.core;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Two-level spatial index: 128-block regions -> occupied 16-block sections -> matching bits. */
public final class SparseBlockIndex {
    private final Map<SectionKey,Map<SectionKey,BitSet>> regions=new HashMap<>();
    private long count,membership;
    public long membership(){return membership;}
    private static long digest(SectionKey key,BitSet bits){
        if(bits==null||bits.isEmpty())return 0;
        long h=TargetMembership.mix(key.x())^Long.rotateLeft(TargetMembership.mix(key.y()),21)^Long.rotateLeft(TargetMembership.mix(key.z()),42);
        for(long word:bits.toLongArray())h=TargetMembership.mix(h^word^0x9e3779b97f4a7c15L);
        return h;
    }
    public long visitedSections,visitedVoxels;
    public long count(){return count;}
    public void put(SectionKey section,BitSet bits) {
        var key=new SectionKey(section.x()>>3,section.y()>>3,section.z()>>3);
        var region=regions.get(key);
        if(region==null){if(bits==null||bits.isEmpty())return;region=new HashMap<>();regions.put(key,region);}
        var old=region.remove(section);membership-=digest(section,old);membership+=digest(section,bits);if(old!=null)count-=old.cardinality();
        if(bits!=null&&!bits.isEmpty()){region.put(section,bits);count+=bits.cardinality();}
        if(region.isEmpty())regions.remove(key);
    }
    private record Node(SectionKey key,Map<SectionKey,BitSet> region,BitSet bits,double distance) implements Comparable<Node> {
        public int compareTo(Node b){return Double.compare(distance,b.distance);}
    }
    public List<Target> nearest(double x,double y,double z,int limit,BooleanSupplier cancelled) {
        visitedSections=0;visitedVoxels=0;
        var roots=new ArrayList<Node>(regions.size());
        for(var e:regions.entrySet())roots.add(new Node(e.getKey(),e.getValue(),null,regionDistance(e.getKey(),x,y,z)));
        var frontier=new PriorityQueue<>(roots);
        var best=new NearestBlocks(limit);
        while(!frontier.isEmpty()) {
            if(cancelled.getAsBoolean())return List.of();
            var node=frontier.remove();if(best.full()&&node.distance>best.distance[0])break;
            if(node.region!=null) {
                for(var e:node.region.entrySet()) {
                    double distance=e.getKey().minBlockDistanceSquared(x,y,z);
                    if(!best.full()||distance<=best.distance[0])frontier.add(new Node(e.getKey(),null,e.getValue(),distance));
                }
                continue;
            }
            visitedSections++;var k=node.key;
            for(int bit=node.bits.nextSetBit(0);bit>=0;bit=node.bits.nextSetBit(bit+1)) {
                if((++visitedVoxels&1023)==0&&cancelled.getAsBoolean())return List.of();
                int px=k.x()*16+(bit&15),py=k.y()*16+(bit>>8),pz=k.z()*16+((bit>>4)&15);
                double dx=px+.5-x,dy=py+.5-y,dz=pz+.5-z,distance=dx*dx+dy*dy+dz*dz;
                best.offer(px,py,pz,distance);
            }
        }
        return best.sorted();
    }
    /** Primitive max heap: allocate render/navigation objects only for the final retained points. */
    private static final class NearestBlocks {
        final int[] xs,ys,zs;final double[] distance;int size;
        NearestBlocks(int limit){xs=new int[limit];ys=new int[limit];zs=new int[limit];distance=new double[limit];}
        boolean full(){return size==xs.length;}
        int compare(int x,int y,int z,double d,int i) {
            int c=Double.compare(d,distance[i]);if(c==0)c=Integer.compare(x,xs[i]);if(c==0)c=Integer.compare(y,ys[i]);if(c==0)c=Integer.compare(z,zs[i]);return c;
        }
        void set(int i,int x,int y,int z,double d){xs[i]=x;ys[i]=y;zs[i]=z;distance[i]=d;}
        void copy(int from,int to){set(to,xs[from],ys[from],zs[from],distance[from]);}
        void offer(int x,int y,int z,double d) {
            if(full()){if(compare(x,y,z,d,0)>=0)return;down(x,y,z,d);return;}
            int i=size++;while(i>0){int parent=(i-1)/2;if(compare(x,y,z,d,parent)<=0)break;copy(parent,i);i=parent;}set(i,x,y,z,d);
        }
        void down(int x,int y,int z,double d) {
            int i=0;while(i*2+1<size) {
                int child=i*2+1;if(child+1<size&&compare(xs[child+1],ys[child+1],zs[child+1],distance[child+1],child)>0)child++;
                if(compare(x,y,z,d,child)>=0)break;copy(child,i);i=child;
            }
            set(i,x,y,z,d);
        }
        List<Target> sorted() {
            var result=new Target[size];while(size>0) {
                var target=Target.block(new Position(xs[0],ys[0],zs[0]));size--;result[size]=target;
                if(size>0)down(xs[size],ys[size],zs[size],distance[size]);
            }
            return List.of(result);
        }
    }
    private static double regionDistance(SectionKey k,double x,double y,double z) {
        double dx=gap(x,k.x()*128+.5,k.x()*128+127.5),dy=gap(y,k.y()*128+.5,k.y()*128+127.5),dz=gap(z,k.z()*128+.5,k.z()*128+127.5);
        return dx*dx+dy*dy+dz*dz;
    }
    private static double gap(double v,double lo,double hi){return v<lo?lo-v:v>hi?v-hi:0;}
}
