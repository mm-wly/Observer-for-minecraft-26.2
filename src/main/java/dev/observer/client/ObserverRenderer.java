package dev.observer.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.minecraft.gizmos.*;
import net.minecraft.world.phys.AABB;
import dev.observer.core.Target;
import dev.observer.core.OutlineAppearance;
import net.minecraft.client.Minecraft;
import java.util.List;

final class ObserverRenderer {
    private static List<Target> source=List.of();
    private static List<SimpleGizmoCollector.GizmoInstance> prepared=List.of();
    private static long lastPrepare;
    private static double px=Double.NaN,py,pz;
    static void clear() {source=List.of();prepared=List.of();}
    static void extract(LevelExtractionContext context,ObserverClient app) {
        if(app.renderTargets.isEmpty()){clear();return;}
        var camera=Minecraft.getInstance().player.getEyePosition();long now=System.nanoTime();
        boolean moved=Double.isNaN(px)||Math.abs(camera.x-px)+Math.abs(camera.y-py)+Math.abs(camera.z-pz)>=2;
        if(!source.equals(app.renderTargets)||moved&&now-lastPrepare>=100_000_000L) {
            px=camera.x;py=camera.y;pz=camera.z;prepare(app.renderTargets);lastPrepare=now;
        }
        source=app.renderTargets;
        // Temporary submission must happen each frame; geometry construction need not.
        context.levelRenderer().addMainThreadGizmos(prepared);
    }
    private static void prepare(List<Target> targets) {
        var collector=new SimpleGizmoCollector();
        try(var ignored=Gizmos.withCollector(collector)) {
            for(var t:targets) {
                var b=t.box();var style=OutlineAppearance.at(b,t.approximate(),px,py,pz);
                Gizmos.cuboid(new AABB(b.minX()-.003,b.minY()-.003,b.minZ()-.003,b.maxX()+.003,b.maxY()+.003,b.maxZ()+.003),GizmoStyle.stroke(t.key().startsWith("biome:")?(style.color()&0xff000000)|0xffd783:style.color(),style.width())).setAlwaysOnTop();
            }
        }
        prepared=List.copyOf(collector.drainGizmos());
    }
}
