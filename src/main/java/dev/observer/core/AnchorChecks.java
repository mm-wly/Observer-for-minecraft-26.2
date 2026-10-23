package dev.observer.core;

import java.util.*;
import java.util.function.Function;

/** Geometry checks over an explicit loaded-block view. "unknown" is not air. */
public final class AnchorChecks {
    private AnchorChecks() {}
    private static final Set<String> BASE=Set.of("sandstone","stone","andesite","granite","diorite");
    private static final Set<String> COVER=Set.of("sand","sandstone","gravel","stone","andesite","granite","diorite");
    private static final Set<String> COBBLE=Set.of("cobblestone","mossy_cobblestone");
    public static Optional<Target> treasure(Function<Position,String> blocks,Position p) {
        if(!blocks.apply(p).equals("chest")||Math.floorMod(p.x(),16)!=9||Math.floorMod(p.z(),16)!=9)return Optional.empty();
        if(!BASE.contains(blocks.apply(p.offset(0,-1,0))))return Optional.empty();
        int cover=0;
        for(var d:new int[][]{{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0}})if(COVER.contains(blocks.apply(p.offset(d[0],d[1],d[2]))))cover++;
        if(cover<3)return Optional.empty();
        return Optional.of(new Target("treasure:"+p,p,Box.block(p),true,"observer.evidence.treasure",-1));
    }
    public static Optional<Target> dungeon(Function<Position,String> blocks,Position p) {
        if(!blocks.apply(p).equals("spawner"))return Optional.empty();
        int best=0,bx=0,bz=0;
        for(int rx=2;rx<=3;rx++)for(int rz=2;rz<=3;rz++) {
            int floor=0,moss=0,walls=0;
            for(int x=-rx-1;x<=rx+1;x++)for(int z=-rz-1;z<=rz+1;z++) {
                String id=blocks.apply(p.offset(x,-1,z));if(COBBLE.contains(id))floor++;if(id.equals("mossy_cobblestone"))moss++;
                if(Math.abs(x)==rx+1||Math.abs(z)==rz+1)if(COBBLE.contains(blocks.apply(p.offset(x,1,z))))walls++;
            }
            int area=(2*rx+3)*(2*rz+3);
            if(floor>=area*.6&&moss>=6&&walls>=8&&floor+walls>best){best=floor+walls;bx=rx+1;bz=rz+1;}
        }
        if(best==0)return Optional.empty();
        return Optional.of(new Target("dungeon:"+p,p,new Box(p.x()-bx,p.y()-1,p.z()-bz,p.x()+bx+1,p.y()+4,p.z()+bz+1),true,"observer.evidence.dungeon",-1));
    }
}
