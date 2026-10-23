package dev.observer.core;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Sweep-line candidate graph. Only original evidence boxes form edges, never empty union space. */
public final class CandidateMerger {
    private CandidateMerger() {}
    public record Policy(int gap,int vertical,int span) {}
    public static Policy policy(String name) {
        String id=name.replace("minecraft:","");
        if(id.startsWith("mineshaft"))return new Policy(12,8,512);
        if(id.startsWith("village"))return new Policy(24,12,384);
        if(id.startsWith("ocean_ruin"))return new Policy(8,6,96);
        return switch(id) {
            case "mansion","monument" -> new Policy(8,12,160);
            case "stronghold","fortress","end_city","ancient_city","trial_chambers","bastion_remnant" -> new Policy(12,16,384);
            case "shipwreck","shipwreck_beached","trail_ruins" -> new Policy(4,6,96);
            case "amethyst_geode","fossil","nether_fossil" -> new Policy(1,1,48);
            case "buried_treasure","dungeon","lava_lake","swamp_hut" -> new Policy(-1,0,0);
            default -> new Policy(2,4,96);
        };
    }
    private static int gap(int a,int b,int c,int d){return Math.max(0,Math.max(a-d,c-b));}
    static boolean connects(Box a,Box b,Policy p) {
        int x=gap(a.minX(),a.maxX(),b.minX(),b.maxX()),y=gap(a.minY(),a.maxY(),b.minY(),b.maxY()),z=gap(a.minZ(),a.maxZ(),b.minZ(),b.maxZ());
        if(x>p.gap||z>p.gap||y>p.vertical)return false;
        return (long)x*x+(long)z*z<=(long)p.gap*p.gap;
    }
    public static List<Target> merge(String name,List<Target> source,BooleanSupplier cancelled) {
        var p=policy(name);if(source.size()<2||p.gap<0)return source;
        var list=new ArrayList<>(source);list.sort(Comparator.comparingInt((Target t)->t.box().minX()).thenComparing(Target::key));
        int n=list.size();int[] parent=new int[n];Box[] bounds=new Box[n];
        for(int i=0;i<n;i++){parent[i]=i;bounds[i]=list.get(i).box();}
        var active=new ArrayList<Integer>();
        for(int i=0;i<n;i++) {
            if(cancelled.getAsBoolean())return List.of();var b=list.get(i).box();
            active.removeIf(j->(long)list.get(j).box().maxX()+p.gap<b.minX());
            for(int j:active) {
                if(cancelled.getAsBoolean())return List.of();
                int a=root(parent,i),c=root(parent,j);if(a==c||!connects(b,list.get(j).box(),p))continue;
                var union=bounds[a].union(bounds[c]);if(union.span()>p.span)continue;
                parent[c]=a;bounds[a]=union;
            }
            active.add(i);
        }
        var representatives=new TreeMap<Integer,Target>();
        for(int i=0;i<n;i++){int r=root(parent,i);var t=list.get(i);var old=representatives.get(r);if(old==null||t.key().compareTo(old.key())<0)representatives.put(r,t);}
        var result=new ArrayList<Target>();
        representatives.forEach((r,t)->result.add(new Target(t.key(),t.position(),bounds[r],t.approximate(),t.evidence(),t.entityId())));
        return List.copyOf(result);
    }
    private static int root(int[] parent,int i){while(parent[i]!=i){parent[i]=parent[parent[i]];i=parent[i];}return i;}
}
