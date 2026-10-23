package dev.observer.client;

import dev.observer.core.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** CPU work only. Never calls Minecraft or touches mutable world objects. */
final class SearchComputer {
    record Request(int id,String category,String name) {}
    record Answer(int id,long count,List<Target> targets,long membership) {}
    /** Mutable indexes are owned exclusively by the one search worker, fed by immutable deltas. */
    static final class Cache {
        final Map<SectionKey,LoadedWorldIndex.SectionData> snapshot=new HashMap<>();
        final Map<SectionKey,SectionEvidence> evidence=new HashMap<>(),details=new HashMap<>();
        final Map<SectionKey,BitSet> lava=new HashMap<>();
        final Map<String,SparseBlockIndex> blocks=new HashMap<>();
        final Map<String,BiomeIndex> biomes=new HashMap<>();
        final Map<String,Detector.Incremental> detectors=new HashMap<>();
        final Map<String,List<Target>> landmarks=new HashMap<>(),specialized=new HashMap<>();
        final Map<String,List<Rule>> queryRules=new HashMap<>();
        final Map<String,Set<String>> dependents=new HashMap<>();
        Map<SectionKey,LoadedWorldIndex.SectionData> lastFull;
        String dimension;int detectionRuns;long appliedSections;
        void prepare(Map<SectionKey,LoadedWorldIndex.SectionData> next,String dim) {
            if(lastFull==next&&Objects.equals(dimension,dim))return;
            var delta=new HashMap<SectionKey,LoadedWorldIndex.SectionData>();
            for(var k:snapshot.keySet())if(!next.containsKey(k))delta.put(k,null);
            next.forEach((k,v)->{if(!Objects.equals(dimension,dim)||snapshot.get(k)!=v)delta.put(k,v);});
            apply(delta,dim);lastFull=next;
        }
        void apply(Map<SectionKey,LoadedWorldIndex.SectionData> delta,String dim) {
            if(!Objects.equals(dimension,dim)) {
                snapshot.clear();evidence.clear();details.clear();lava.clear();blocks.clear();biomes.clear();detectors.clear();landmarks.clear();specialized.clear();dimension=dim;
            }
            for(var entry:delta.entrySet()) {
                appliedSections++;
                var key=entry.getKey();var next=entry.getValue();var old=snapshot.get(key);
                var before=old==null?null:old.evidence();var after=next==null?null:next.evidence();
                for(var detector:detectors.entrySet())if(detector.getValue().changed(key,before,after))invalidate(detector.getKey());
                var names=new HashSet<String>();if(old!=null)names.addAll(old.blocks().keySet());if(next!=null)names.addAll(next.blocks().keySet());
                for(String name:names) {
                    var a=old==null?null:old.blocks().get(name);var b=next==null?null:next.blocks().get(name);
                    if(!Objects.equals(a,b))blocks.computeIfAbsent(name,n->new SparseBlockIndex()).put(key,b);
                }
                names.clear();if(old!=null)names.addAll(old.biomes().keySet());if(next!=null)names.addAll(next.biomes().keySet());
                for(String name:names){var a=old==null?null:old.biomes().get(name);var b=next==null?null:next.biomes().get(name);if(!Objects.equals(a,b))biomes.computeIfAbsent(name,n->new BiomeIndex()).put(key,b);}
                boolean detailChange=(before!=null&&before.hasDetails()||after!=null&&after.hasDetails())&&(after==null||!after.sameDetails(before));
                if(detailChange){invalidate("mineshaft");invalidate("mineshaft_mesa");invalidate("swamp_hut");}
                if(after!=null&&after.hasDetails())details.put(key,after);else details.remove(key);
                if(!Objects.equals(old==null?null:old.lavaSources(),next==null?null:next.lavaSources())) {
                    if(next!=null&&!next.lavaSources().isEmpty())lava.put(key,next.lavaSources());else lava.remove(key);
                    // Empty sections must not invalidate connected lava components.
                    if(old!=null&&!old.lavaSources().isEmpty()||next!=null&&!next.lavaSources().isEmpty())invalidate("lava_lake");
                }
                if(!Objects.equals(old==null?List.of():old.treasure(),next==null?List.of():next.treasure()))invalidate("buried_treasure");
                if(!Objects.equals(old==null?List.of():old.dungeons(),next==null?List.of():next.dungeons()))invalidate("dungeon");
                if(next==null){snapshot.remove(key);evidence.remove(key);}else{snapshot.put(key,next);evidence.put(key,after);}
            }
        }
        private void invalidate(String rule) {
            specialized.remove(rule);landmarks.remove("rule:"+rule);
            for(String query:dependents.getOrDefault(rule,Set.of()))landmarks.remove(query);
        }
        List<Rule> rules(String category,String id) {
            String key=category+":"+id;
            return queryRules.computeIfAbsent(key,k->{
                var rules=RuleCatalog.resolve(category,id);
                for(var rule:rules)dependents.computeIfAbsent(rule.id(),r->new HashSet<>()).add(k);
                return rules;
            });
        }
    }
    static List<Answer> computeDelta(List<Request> requests,Map<SectionKey,LoadedWorldIndex.SectionData> delta,
                                    String dimension,double x,double y,double z,int limit,BooleanSupplier cancelled,Cache cache) {
        try(var timing=SearchProfile.start("apply delta")){cache.apply(delta,dimension);}
        return computePrepared(requests,dimension,x,y,z,limit,cancelled,cache);
    }
    static List<Answer> compute(List<Request> requests,Map<SectionKey,LoadedWorldIndex.SectionData> snapshot,
                                String dimension,double x,double y,double z,int limit,BooleanSupplier cancelled) {
        return compute(requests,snapshot,dimension,x,y,z,limit,cancelled,new Cache());
    }
    static List<Answer> compute(List<Request> requests,Map<SectionKey,LoadedWorldIndex.SectionData> snapshot,
                                String dimension,double x,double y,double z,int limit,BooleanSupplier cancelled,Cache cache) {
        cache.prepare(snapshot,dimension);
        return computePrepared(requests,dimension,x,y,z,limit,cancelled,cache);
    }
    private static List<Answer> computePrepared(List<Request> requests,String dimension,double x,double y,double z,int limit,BooleanSupplier cancelled,Cache cache) {
        var snapshot=cache.snapshot;
        var result=new ArrayList<Answer>();
        Comparator<Target> order=Comparator.comparingDouble((Target t)->t.distanceSquared(x,y,z)).thenComparing(Target::position).thenComparing(Target::key);
        for(var q:requests) {
            if(cancelled.getAsBoolean())return List.of();
            try(var timing=SearchProfile.start(q.category+":"+q.name)) {
            if(q.category.equals("block")) {
                var spatial=cache.blocks.get(q.name);
                var targets=spatial==null?List.<Target>of():spatial.nearest(x,y,z,limit,cancelled);
                if(cancelled.getAsBoolean())return List.of();
                result.add(new Answer(q.id,spatial==null?0:spatial.count(),targets,spatial==null?0:spatial.membership()));continue;
            }
            var nearest=new PriorityQueue<Target>(limit,order.reversed());long count=0,membership=0;
            if(q.category.equals("biome")) {
                var spatial=cache.biomes.get(q.name);
                var targets=spatial==null?List.<Target>of():spatial.targets(x,y,z,cancelled);
                membership=TargetMembership.of(targets);count=targets.size();for(var t:targets)retain(nearest,t,limit,order);
            } else {
                String id=q.name.substring("minecraft:".length());List<Target> found=cache.landmarks.get(q.category+":"+id);
                boolean overworld=dimension.equals("overworld")||dimension.equals("minecraft:overworld");
                if(found==null) {
                var rules=cache.rules(q.category,id);
                cache.detectionRuns++;
                if(Set.of("buried_treasure","dungeon","lava_lake").contains(id)) {
                    found=cache.specialized.get(id);
                    if(found==null) {
                        found=!overworld?List.of():switch(id) {
                            case "buried_treasure" -> snapshot.values().stream().flatMap(v->v.treasure().stream()).toList();
                            case "dungeon" -> snapshot.values().stream().flatMap(v->v.dungeons().stream()).toList();
                            default -> ConnectedFeatures.lava(cache.lava,cancelled);
                        };
                        if(cancelled.getAsBoolean())return List.of();cache.specialized.put(id,found);
                    }
                }
                else {
                    var unique=new LinkedHashMap<Box,Target>();
                    for(var rule:rules) {
                        String key="rule:"+rule.id();var detected=cache.landmarks.get(key);
                        if(detected==null) {
                            if(rule.id().startsWith("mineshaft")||rule.id().equals("swamp_hut")) {
                                detected=Detector.detect(rule,dimension,cache.details,cancelled);
                            } else detected=cache.detectors.computeIfAbsent(rule.id(),idKey->new Detector.Incremental(rule,dimension)).detect(cache.evidence,cancelled);
                            if(cancelled.getAsBoolean())return List.of();
                            cache.landmarks.put(key,detected);
                        }
                        for(var target:detected)unique.putIfAbsent(target.box(),target);
                    }
                    found=List.copyOf(unique.values());
                }
                if(cancelled.getAsBoolean())return List.of();
                try(var mergeTiming=SearchProfile.start("merge:"+id)){found=CandidateMerger.merge(id,found,cancelled);}
                cache.landmarks.put(q.category+":"+id,found);
                }
                membership=TargetMembership.of(found);count=found.size();for(var t:found)retain(nearest,t,limit,order);
            }
            var sorted=new ArrayList<>(nearest);sorted.sort(order);result.add(new Answer(q.id,count,List.copyOf(sorted),membership));
            }
        }
        return List.copyOf(result);
    }
    private static void retain(PriorityQueue<Target> heap,Target t,int limit,Comparator<Target> order) {
        if(heap.size()<limit)heap.add(t);
        else if(order.compare(t,heap.peek())<0){heap.poll();heap.add(t);}
    }
}
