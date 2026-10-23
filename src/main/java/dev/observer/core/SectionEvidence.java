package dev.observer.core;

import java.util.*;

/** Compact material histogram; never contains server generation metadata. */
public final class SectionEvidence {
    public record Material(int count, Box bounds) {}
    public final SectionKey key;
    public final Map<String,Material> materials;
    public final Set<String> biomes;
    public final Map<String,List<Position>> anchors;
    private final BitSet passage;
    public SectionEvidence(SectionKey key,Map<String,Material> materials,Set<String> biomes,Map<String,List<Position>> anchors) {
        this(key,materials,biomes,anchors,new BitSet());
    }
    public SectionEvidence(SectionKey key,Map<String,Material> materials,Set<String> biomes,Map<String,List<Position>> anchors,BitSet passage) {
        this.key=key; this.materials=Map.copyOf(materials); this.biomes=Set.copyOf(biomes);
        this.passage=(BitSet)passage.clone();
        var a=new HashMap<String,List<Position>>(); anchors.forEach((k,v)->a.put(k,List.copyOf(v))); this.anchors=Map.copyOf(a);
    }
    public boolean passage(Position p) { return passage.get(((p.y()&15)<<8)|((p.z()&15)<<4)|(p.x()&15)); }
    public boolean hasDetails(){return !anchors.isEmpty()||!passage.isEmpty();}
    public boolean sameDetails(SectionEvidence other){return other!=null&&anchors.equals(other.anchors)&&passage.equals(other.passage)&&biomes.equals(other.biomes);}
    public int count(Set<String> blocks) { int n=0; for(String b:blocks) { var m=materials.get(b); if(m!=null)n+=m.count; } return n; }
    public Box bounds(Set<String> blocks) {
        Box result=null; for(String b:blocks) { var m=materials.get(b); if(m!=null)result=result==null?m.bounds:result.union(m.bounds); } return result;
    }
    public static final class Builder {
        private final SectionKey key;
        private final Map<String,int[]> materials=new HashMap<>();
        private final Map<String,List<Position>> anchors=new HashMap<>();
        public final BitSet passage=new BitSet();
        public final Set<String> biomes=new HashSet<>();
        public Builder(SectionKey key) { this.key=key; }
        public void uniform(String id) {
            int x=key.x()*16,y=key.y()*16,z=key.z()*16;materials.put(id,new int[]{4096,x,y,z,x+16,y+16,z+16});
        }
        public void add(String id,Position p,boolean anchor) {
            add(id,p.x(),p.y(),p.z(),anchor);
        }
        public void add(String id,int x,int y,int z,boolean anchor) {
            var a=materials.get(id);if(a==null){a=new int[]{0,x,y,z,x+1,y+1,z+1};materials.put(id,a);}
            a[0]++;a[1]=Math.min(a[1],x);a[2]=Math.min(a[2],y);a[3]=Math.min(a[3],z);
            a[4]=Math.max(a[4],x+1);a[5]=Math.max(a[5],y+1);a[6]=Math.max(a[6],z+1);
            if(anchor)anchors.computeIfAbsent(id,k->new ArrayList<>()).add(new Position(x,y,z));
        }
        /** Aggregate a contiguous range in section storage order (X, then Z, then Y). */
        public void addRun(String id,int from,int to,boolean anchor) {
            int last=to-1,ox=key.x()*16,oy=key.y()*16,oz=key.z()*16;
            boolean rows=(from>>4)!=(last>>4),layers=(from>>8)!=(last>>8);
            int minX=ox+(rows?0:from&15),maxX=ox+(rows?16:(last&15)+1);
            int minY=oy+(from>>8),maxY=oy+(last>>8)+1;
            int minZ=oz+(layers?0:(from>>4)&15),maxZ=oz+(layers?16:((last>>4)&15)+1);
            var a=materials.get(id);
            if(a==null)materials.put(id,new int[]{to-from,minX,minY,minZ,maxX,maxY,maxZ});
            else {a[0]+=to-from;a[1]=Math.min(a[1],minX);a[2]=Math.min(a[2],minY);a[3]=Math.min(a[3],minZ);a[4]=Math.max(a[4],maxX);a[5]=Math.max(a[5],maxY);a[6]=Math.max(a[6],maxZ);}
            if(anchor){var points=anchors.computeIfAbsent(id,k->new ArrayList<>());for(int i=from;i<to;i++)points.add(new Position(ox+(i&15),oy+(i>>8),oz+((i>>4)&15)));}
        }
        public SectionEvidence build() {
            var result=new HashMap<String,Material>();
            materials.forEach((id,a)->result.put(id,new Material(a[0],new Box(a[1],a[2],a[3],a[4],a[5],a[6]))));
            return new SectionEvidence(key,result,biomes,anchors,passage);
        }
    }
}
