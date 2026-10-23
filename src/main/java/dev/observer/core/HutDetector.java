package dev.observer.core;

import java.util.*;
import java.util.function.BooleanSupplier;

public final class HutDetector {
    private HutDetector() {}
    public static boolean detail(String id) {return id.endsWith("cauldron")||id.equals("flower_pot")||id.equals("potted_red_mushroom");}
    public static List<Target> detect(Rule rule,String dimension,Map<SectionKey,SectionEvidence> index,BooleanSupplier cancelled) {
        var pots=new HashSet<Position>();var cauldrons=new TreeSet<Position>();
        for(var s:index.values()) {
            if(cancelled.getAsBoolean())return List.of();
            if(!rule.environment(dimension,s.biomes))continue;
            s.anchors.forEach((id,points)->{if(id.endsWith("cauldron"))cauldrons.addAll(points);else if(id.equals("potted_red_mushroom")||id.equals("flower_pot"))pots.addAll(points);});
        }
        var out=new ArrayList<Target>();
        for(var c:cauldrons) {
            if(cancelled.getAsBoolean())return List.of();
            boolean found=false;
            for(int dx:new int[]{-3,-1,1,3})for(int dz:new int[]{-3,-1,1,3}) {
                if(found)continue;
                if(Math.abs(dx)+Math.abs(dz)!=4)continue;
                if(!pots.contains(c.offset(dx,1,dz)))continue;
                // Vanilla local offsets: cauldron (4,2,6), mushroom pot (1,3,5).
                // The furniture anchors estimate the shell without depending on its wood.
                var box=Box.block(c).union(Box.block(c.offset(dx,1,dz))).expand(2);
                out.add(new Target("swamp_hut:"+c,c,box,true,"observer.evidence.hut",-1));
                found=true;
                break;
            }
        }
        return List.copyOf(out);
    }
}
