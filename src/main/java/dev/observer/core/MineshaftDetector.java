package dev.observer.core;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Support-frame graph, independent of section boundaries and global bounding-box size. */
public final class MineshaftDetector {
    private MineshaftDetector() {}
    public static final Set<String> DETAILS=Set.of("oak_fence","dark_oak_fence","oak_planks","dark_oak_planks","rail","cobweb");
    private record Frame(Position p,boolean alongX) {}
    public static List<Target> detect(Rule rule,String dimension,Map<SectionKey,SectionEvidence> index,BooleanSupplier cancelled) {
        if(!dimension.equals("overworld")&&!dimension.equals("minecraft:overworld"))return List.of();
        String wood=rule.id().equals("mineshaft_mesa")?"dark_oak":"oak";
        var fences=new HashSet<Position>();var planks=new HashSet<Position>();var clues=new HashSet<Position>();
        for(var s:index.values()) {
            if(cancelled.getAsBoolean())return List.of();
            fences.addAll(s.anchors.getOrDefault(wood+"_fence",List.of()));
            planks.addAll(s.anchors.getOrDefault(wood+"_planks",List.of()));
            clues.addAll(s.anchors.getOrDefault("rail",List.of()));clues.addAll(s.anchors.getOrDefault("cobweb",List.of()));
        }
        var frames=new ArrayList<Frame>();
        for(var p:fences) {
            if(cancelled.getAsBoolean())return List.of();
            if(!fences.contains(p.offset(0,1,0))||!planks.contains(p.offset(0,2,0)))continue;
            for(boolean alongX:new boolean[]{true,false}) {
                int dx=alongX?0:2,dz=alongX?2:0;
                if(fences.contains(p.offset(dx,0,dz))&&fences.contains(p.offset(dx,1,dz))&&planks.contains(p.offset(dx,2,dz))) {
                    var center=p.offset(dx/2,0,dz/2);
                    if(open(index,center))frames.add(new Frame(center,alongX));
                }
            }
        }
        frames.sort(Comparator.comparing(Frame::p).thenComparing(Frame::alongX));
        var buckets=new HashMap<SectionKey,List<Integer>>();
        for(int i=0;i<frames.size();i++)buckets.computeIfAbsent(frames.get(i).p.section(),k->new ArrayList<>()).add(i);
        var seen=new BitSet();var out=new ArrayList<Target>();
        for(int start=0;start<frames.size();start++) {
            if(seen.get(start))continue;
            var queue=new ArrayDeque<Integer>();queue.add(start);seen.set(start);
            var members=new ArrayList<Frame>();Box box=null;boolean evidence=false;
            while(!queue.isEmpty()) {
                if(cancelled.getAsBoolean())return List.of();
                var a=frames.get(queue.removeFirst());members.add(a);
                var p=a.p;Box part=new Box(p.x()-2,p.y()-1,p.z()-2,p.x()+3,p.y()+4,p.z()+3);
                box=box==null?part:box.union(part);
                if(!evidence)for(int dx=-3;dx<=3&&!evidence;dx++)for(int dy=0;dy<=2&&!evidence;dy++)for(int dz=-3;dz<=3;dz++)if(clues.contains(p.offset(dx,dy,dz))){evidence=true;break;}
                var key=p.section();
                for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)
                    for(int j:buckets.getOrDefault(key.offset(dx,dy,dz),List.of()))if(!seen.get(j)&&connected(a,frames.get(j),index)) {seen.set(j);queue.addLast(j);}
            }
            // A single decorative arch or mansion cobweb room is insufficient.
            if(members.size()<2||!evidence)continue;
            var first=members.getFirst().p;
            out.add(new Target(rule.id()+":"+first,first,box,true,"observer.evidence.mineshaft",-1));
        }
        return List.copyOf(out);
    }
    private static boolean open(Map<SectionKey,SectionEvidence> index,Position p) {
        var a=index.get(p.section());var above=p.offset(0,1,0);var b=index.get(above.section());
        return a!=null&&b!=null&&a.passage(p)&&b.passage(above);
    }
    private static boolean connected(Frame a,Frame b,Map<SectionKey,SectionEvidence> index) {
        int dx=Math.abs(a.p.x()-b.p.x()),dy=Math.abs(a.p.y()-b.p.y()),dz=Math.abs(a.p.z()-b.p.z());
        if(dy>5||dx+dz>16||dx+dz==0)return false;
        if(a.alongX==b.alongX) {
            if(a.alongX?dz!=0:dx!=0)return false;
            if(dy==0&&dx+dz>10)return false;
            return line(a.p,b.p,index);
        }
        if(dy!=0)return false;
        Position turn=a.alongX?new Position(b.p.x(),a.p.y(),a.p.z()):new Position(a.p.x(),a.p.y(),b.p.z());
        return line(a.p,turn,index)&&line(turn,b.p,index);
    }
    private static boolean line(Position a,Position b,Map<SectionKey,SectionEvidence> index) {
        int steps=Math.max(Math.abs(a.x()-b.x()),Math.abs(a.z()-b.z()));
        for(int i=0;i<=steps;i++) {
            double f=steps==0?0:(double)i/steps;
            var p=new Position((int)Math.round(a.x()+(b.x()-a.x())*f),(int)Math.round(a.y()+(b.y()-a.y())*f),(int)Math.round(a.z()+(b.z()-a.z())*f));
            if(!open(index,p))return false;
        }
        return true;
    }
}
