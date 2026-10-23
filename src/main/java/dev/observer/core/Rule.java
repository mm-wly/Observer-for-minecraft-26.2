package dev.observer.core;

import java.util.*;

/** A rule is deliberately data-like: material groups + environmental constraints. */
public record Rule(String id, String category, String dimension, String biomeFamily,
                   List<Group> groups, Set<String> outlineMaterials, int mergeSpan, int padding,
                   String explanation) {
    public record Group(Set<String> blocks,int minimum) {
        public Group { blocks=Set.copyOf(blocks); if(minimum<1)throw new IllegalArgumentException(); }
    }
    public Rule { groups=List.copyOf(groups); outlineMaterials=Set.copyOf(outlineMaterials); }
    public boolean environment(String dim,Set<String> biomes) {
        if(!dimension.equals(dim)&&!("minecraft:"+dimension).equals(dim))return false;
        if(biomeFamily.isEmpty())return true;
        return biomes.stream().anyMatch(b->switch(biomeFamily) {
            case "mesa" -> b.contains("badlands");
            case "not_mesa" -> !b.contains("badlands");
            case "ocean" -> b.contains("ocean") || b.contains("river");
            case "beach" -> b.contains("beach");
            case "coast" -> b.contains("ocean") || b.contains("river") || b.contains("beach") || b.contains("shore");
            case "desert" -> b.contains("desert");
            case "jungle" -> b.contains("jungle");
            case "swamp" -> b.contains("swamp");
            case "snowy" -> b.contains("snowy") || b.contains("frozen") || b.contains("ice_spikes");
            case "taiga" -> b.contains("taiga") && !b.contains("snowy");
            case "savanna" -> b.contains("savanna");
            case "plains" -> b.contains("plains") && !b.contains("snowy") || b.contains("meadow");
            case "mountain" -> b.contains("peak") || b.contains("mountain") || b.contains("windswept") || b.contains("slope") || b.contains("grove");
            case "standard" -> !(b.contains("ocean") || b.contains("desert") || b.contains("jungle") || b.contains("swamp") || b.contains("peak") || b.contains("mountain") || b.contains("windswept") || b.contains("slope") || b.contains("grove"));
            default -> b.contains(biomeFamily);
        });
    }
    public Set<String> allMaterials() { var s=new HashSet<>(outlineMaterials); groups.forEach(g->s.addAll(g.blocks)); s.addAll(DetectionExclusions.materials(id));return Set.copyOf(s); }
}
