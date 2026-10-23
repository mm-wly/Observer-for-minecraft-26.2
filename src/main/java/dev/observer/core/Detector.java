package dev.observer.core;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Runs on immutable client snapshots. Neighbor evidence handles section seams. */
public final class Detector {
    private Detector() {}
    public static List<Target> detect(Rule rule,String dimension,Map<SectionKey,SectionEvidence> index,BooleanSupplier cancelled) {
        if(rule.id().startsWith("mineshaft"))return MineshaftDetector.detect(rule,dimension,index,cancelled);
        if(rule.id().equals("swamp_hut"))return HutDetector.detect(rule,dimension,index,cancelled);
        Map<SectionKey,Box> accepted=new HashMap<>();
        for(var section:index.values()) {
            if(cancelled.getAsBoolean())return List.of();
            Box box=evaluate(rule,dimension,section,index);if(box!=null)accepted.put(section.key,box);
        }
        return merge(rule,index,accepted,cancelled);
    }
    private static Box evaluate(Rule rule,String dimension,SectionEvidence section,Map<SectionKey,SectionEvidence> index) {
        if(section==null||rule.id().equals("buried_treasure")||rule.id().equals("dungeon"))return null;
        Box own=section.bounds(rule.outlineMaterials());if(own==null||!rule.environment(dimension,section.biomes))return null;
        int[] totals=new int[rule.groups().size()];int markers=0,masonry=0;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
            var n=index.get(section.key.offset(dx,dy,dz));if(n==null)continue;
            if(rule.id().equals("fossil")){markers+=DetectionExclusions.markers(n);masonry+=DetectionExclusions.masonry(n);}
            for(int i=0;i<totals.length;i++)totals[i]+=n.count(rule.groups().get(i).blocks());
        }
        for(int i=0;i<totals.length;i++)if(totals[i]<rule.groups().get(i).minimum())return null;
        if(DetectionExclusions.rejects(rule.id(),markers,masonry))return null;
        return own;
    }
    /** Incremental stencil update. Only changed material sections and their 26 neighbors are evaluated. */
    public static final class Incremental {
        private final Rule rule;private final String dimension;private final Set<String> materials;
        private final Map<SectionKey,Box> accepted=new HashMap<>();private final Set<SectionKey> dirty=new HashSet<>();
        private boolean initialized;private List<Target> result=List.of();
        public long evaluatedSections;
        public Incremental(Rule rule,String dimension){this.rule=rule;this.dimension=dimension;materials=rule.allMaterials();}
        public boolean changed(SectionKey key,SectionEvidence old,SectionEvidence next) {
            for(String material:materials)if(!Objects.equals(old==null?null:old.materials.get(material),next==null?null:next.materials.get(material))){dirty.add(key);return true;}
            if(!rule.biomeFamily().isEmpty()&&next!=null&&next.bounds(rule.outlineMaterials())!=null&&(old==null||!old.biomes.equals(next.biomes))){dirty.add(key);return true;}
            // A navigation point may disappear even if the removed section had no material.
            if(next==null&&result.stream().anyMatch(t->t.position().section().equals(key))){dirty.add(key);return true;}
            return false;
        }
        public List<Target> detect(Map<SectionKey,SectionEvidence> index,BooleanSupplier cancelled) {
            if(initialized&&dirty.isEmpty())return result;
            Set<SectionKey> affected=new HashSet<>();
            if(!initialized)affected.addAll(index.keySet());
            else for(var key:dirty)for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
                var n=key.offset(dx,dy,dz);var e=index.get(n);
                if(accepted.containsKey(n)||e!=null&&rule.outlineMaterials().stream().anyMatch(e.materials::containsKey))affected.add(n);
            }
            for(var key:affected) {
                if(cancelled.getAsBoolean())return List.of();evaluatedSections++;
                Box box=evaluate(rule,dimension,index.get(key),index);if(box==null)accepted.remove(key);else accepted.put(key,box);
            }
            var next=merge(rule,index,accepted,cancelled);if(cancelled.getAsBoolean())return List.of();
            result=next;dirty.clear();initialized=true;return result;
        }
    }
    private static List<Target> merge(Rule rule,Map<SectionKey,SectionEvidence> index,Map<SectionKey,Box> accepted,BooleanSupplier cancelled) {
        List<Target> out=new ArrayList<>();
        var ordered=new ArrayList<>(accepted.keySet());
        ordered.sort(Comparator.comparingInt(SectionKey::x).thenComparingInt(SectionKey::y).thenComparingInt(SectionKey::z));
        Set<SectionKey> visited=new HashSet<>();
        for(var start:ordered) {
            if(cancelled.getAsBoolean())return List.of();
            if(!visited.add(start))continue;
            Box box=accepted.get(start); var queue=new ArrayDeque<SectionKey>(); queue.add(start);
            while(!queue.isEmpty()) {
                var p=queue.removeFirst();
                for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
                    var n=p.offset(dx,dy,dz); Box b=accepted.get(n); if(b==null||visited.contains(n))continue;
                    Box combined=box.union(b); if(combined.span()>rule.mergeSpan())continue;
                    visited.add(n);queue.addLast(n);box=combined;
                }
            }
            box=box.expand(rule.padding());
            Position point=box.center();
            // Connected evidence may wrap around an unreceived hole. Never navigate into that hole.
            if(!index.containsKey(point.section()))point=accepted.get(start).center();
            out.add(new Target(rule.id()+":"+start,point,box,true,rule.explanation(),-1));
        }
        return List.copyOf(out);
    }
}
