package dev.observer.client;

import dev.observer.core.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;

/** Reads only loaded ClientChunkCache entries. Unknown neighbors fail conservatively. */
final class AnchorDetector {
    static String block(ClientLevel level,Position p) {
        var c=level.getChunkSource().getChunk(p.x()>>4,p.z()>>4,ChunkStatus.FULL,false);
        if(c==null||p.y()<level.getMinY()||p.y()>level.getMaxY())return "unknown";
        return BuiltInRegistries.BLOCK.getKey(c.getBlockState(new BlockPos(p.x(),p.y(),p.z())) .getBlock()).toString();
    }
    private static String vanilla(ClientLevel l,Position p) {
        String id=block(l,p);return id.startsWith("minecraft:")?id.substring(10):id;
    }
    static Optional<Target> treasure(ClientLevel l,Position p) {
        return AnchorChecks.treasure(pos->vanilla(l,pos),p);
    }
    static Optional<Target> dungeon(ClientLevel l,Position p) {
        return AnchorChecks.dungeon(pos->vanilla(l,pos),p);
    }
}
