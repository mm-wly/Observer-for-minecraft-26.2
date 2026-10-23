package dev.observer.client;

import dev.observer.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;

/** Main-thread-only bounded scanner; published SectionData is immutable by ownership. */
public final class LoadedWorldIndex {
    public record SectionData(SectionEvidence evidence,Map<String,BitSet> blocks,Map<String,BitSet> biomes,List<Target> treasure,List<Target> dungeons,BitSet lavaSources) {}
    private final ObserverConfig config;
    private final Map<SectionKey,SectionData> data=new HashMap<>();
    private final Map<SectionKey,SectionData> changes=new HashMap<>(); // null = removal; drained into worker ownership
    private final SectionColumns columns=new SectionColumns();
    private record ColumnRequest(int x,int z,int minY,int count) {}
    private final LinkedHashMap<Long,ColumnRequest> pendingColumns=new LinkedHashMap<>();
    private int pendingSectionCount;
    private final Map<Long,Set<SectionKey>> supportColumns=new HashMap<>();
    private final Set<SectionKey> known=new HashSet<>();
    private final LinkedHashSet<SectionKey> dirty=new LinkedHashSet<>();
    private final Set<String> blockQueries=new HashSet<>(),biomeQueries=new HashSet<>();
    private final Map<net.minecraft.world.level.block.Block,String> ids=new IdentityHashMap<>();
    private final Map<net.minecraft.world.level.block.Block,String> shortIds=new IdentityHashMap<>();
    private final Map<net.minecraft.world.level.block.state.BlockState,Sample> samples=new IdentityHashMap<>();
    private final Set<SectionKey> supports=new HashSet<>(),passageRequested=new HashSet<>(),passageScanned=new HashSet<>();
    private record Sample(String query,String material,boolean passage,boolean support,boolean detail,boolean chest,boolean spawner,boolean lava) {
        boolean wanted(){return query!=null||material!=null;}
    }
    public long scannedVoxels,completedSections,unloadedSections;
    private Set<String> materials=Set.of();
    private boolean landmarks,treasure,dungeons,mines;
    private Job job;
    public long revision;
    public long topologyRevision;
    public LoadedWorldIndex(ObserverConfig config) { this.config=config; }
    public void reset() { data.clear();changes.clear();columns.clear();pendingColumns.clear();pendingSectionCount=0;supportColumns.clear();known.clear();dirty.clear();supports.clear();passageRequested.clear();passageScanned.clear();samples.clear();job=null;blockQueries.clear();biomeQueries.clear();materials=Set.of();landmarks=false;treasure=false;dungeons=false;mines=false;scannedVoxels=completedSections=unloadedSections=0;revision++;topologyRevision++; }
    public boolean configure(Collection<SearchSession> sessions,ClientLevel level,Collection<net.minecraft.world.level.ChunkPos> received) {
        var nextBlocks=new HashSet<String>();var nextBiomes=new HashSet<String>();var m=new HashSet<String>();
        boolean nextLandmarks=false,nextTreasure=false,nextDungeons=false;
        for(var s:sessions) {
            if(s.category.equals("block"))nextBlocks.add(s.name);
            if(s.category.equals("biome"))nextBiomes.add(s.name);
            RuleCatalog.resolve(s.category,s.name.replace("minecraft:","")).forEach(r->m.addAll(r.allMaterials()));
            if(s.category.equals("structure")||s.category.equals("feature"))nextLandmarks=true;
            if(s.category.equals("structure")&&s.name.equals("minecraft:buried_treasure"))nextTreasure=true;
            if(s.category.equals("feature")&&s.name.equals("minecraft:dungeon"))nextDungeons=true;
        }
        if(nextBlocks.isEmpty()&&nextBiomes.isEmpty()&&!nextLandmarks){if(requiresScan()){reset();return true;}return false;}
        // A reduced schema reuses received evidence but stops collecting removed fields immediately.
        if(blockQueries.containsAll(nextBlocks)&&biomeQueries.containsAll(nextBiomes)&&materials.containsAll(m)&&(!nextLandmarks||landmarks)&&(!nextTreasure||treasure)&&(!nextDungeons||dungeons)) {
            blockQueries.retainAll(nextBlocks);biomeQueries.retainAll(nextBiomes);materials=Set.copyOf(m);samples.clear();
            landmarks=nextLandmarks;treasure=nextTreasure;dungeons=nextDungeons;
            mines=sessions.stream().anyMatch(s->s.category.equals("structure")&&s.name.startsWith("minecraft:mineshaft"));
            return false;
        }
        // Expand the schema in place: retain results while changed sections are rebuilt under the tick budget.
        samples.clear();job=null;dirty.addAll(known);blockQueries.clear();biomeQueries.clear();
        blockQueries.addAll(nextBlocks);biomeQueries.addAll(nextBiomes);landmarks=nextLandmarks;treasure=nextTreasure;dungeons=nextDungeons;
        materials=Set.copyOf(m);
        mines=sessions.stream().anyMatch(s->s.category.equals("structure")&&s.name.startsWith("minecraft:mineshaft"));
        // Includes already-loaded chunks when the first command is issued after login.
        var mc=Minecraft.getInstance();if(mc.player==null)return true;
        for(var p:received) {
            if(!columns.get(p.x(),p.z()).isEmpty()||pendingColumns.containsKey(SectionColumns.key(p.x(),p.z())))continue;
            var chunk=level.getChunkSource().getChunk(p.x(),p.z(),ChunkStatus.FULL,false);if(chunk!=null)load(chunk);
        }
        var ordered=new ArrayList<>(pendingColumns.values());var p=mc.player.blockPosition();
        ordered.sort(Comparator.comparingDouble(c->Math.pow(c.x()*16+8-p.getX(),2)+Math.pow(c.z()*16+8-p.getZ(),2)));
        pendingColumns.clear();for(var c:ordered)pendingColumns.put(SectionColumns.key(c.x,c.z),c);
        return true;
    }
    public void load(LevelChunk chunk) {
        if(!requiresScan())return;
        topologyRevision++;
        int x=chunk.getPos().x(),z=chunk.getPos().z();removeColumn(x,z,false);
        var request=new ColumnRequest(x,z,chunk.getSectionYFromSectionIndex(0),chunk.getSectionsCount());
        var old=pendingColumns.put(SectionColumns.key(x,z),request);pendingSectionCount+=request.count-(old==null?0:old.count);
        revision++;
    }
    private void register(ColumnRequest c) {
        for(var key:columns.put(c.x,c.z,c.minY,c.count)){known.add(key);dirty.add(key);}
        if(mines&&!supports.isEmpty())for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
            for(var support:supportColumns.getOrDefault(SectionColumns.key(c.x+dx,c.z+dz),Set.of()))for(int dy=-1;dy<=1;dy++) {
                var key=new SectionKey(c.x,support.y()+dy,c.z);if(known.contains(key))passageRequested.add(key);
            }
    }
    public void unload(int x,int z) {
        topologyRevision++;
        var request=pendingColumns.remove(SectionColumns.key(x,z));if(request!=null)pendingSectionCount-=request.count;
        removeColumn(x,z,true);revision++;
    }
    private void removeColumn(int x,int z,boolean unloading) {
        for(var key:columns.remove(x,z)) {
            if(unloading)unloadedSections++;known.remove(key);dirty.remove(key);supports.remove(key);passageRequested.remove(key);passageScanned.remove(key);
            if(data.remove(key)!=null)changes.put(key,null);
        }
        supportColumns.remove(SectionColumns.key(x,z));
        if(job!=null&&job.key.x()==x&&job.key.z()==z)job=null;
    }
    public void changed(SectionKey key) {
        // Adjacent sections may contain a treasure/spawner whose context changed.
        int radius=treasure||dungeons?1:0;
        for(int dx=-radius;dx<=radius;dx++)for(int dy=-radius;dy<=radius;dy++)for(int dz=-radius;dz<=radius;dz++) {
            var k=key.offset(dx,dy,dz);if(known.contains(k))dirty.add(k);
        }
        if(job!=null&&Math.abs(job.key.x()-key.x())<=radius&&Math.abs(job.key.y()-key.y())<=radius&&Math.abs(job.key.z()-key.z())<=radius)job=null;
    }
    public void biomesChanged(int x,int z) {
        dirty.addAll(columns.get(x,z));
        if(job!=null&&job.key.x()==x&&job.key.z()==z)job=null;
    }
    public void changed(SectionKey key,net.minecraft.world.level.block.state.BlockState oldState,net.minecraft.world.level.block.state.BlockState newState) {
        if(oldState==newState)return;
        if(mines&&passageRequested.contains(key)||treasure||dungeons||relevant(oldState)||relevant(newState))changed(key);
    }
    private boolean relevant(net.minecraft.world.level.block.state.BlockState state) {
        return sample(state).wanted();
    }
    public Map<SectionKey,SectionData> snapshot() { return Map.copyOf(data); }
    public Map<SectionKey,SectionData> drainChanges(boolean full) {
        Map<SectionKey,SectionData> result=Collections.unmodifiableMap(new HashMap<>(full?data:changes));changes.clear();return result;
    }
    public int indexed() { return data.size(); }
    public int total() { return known.size()+pendingSectionCount; }
    public int pending() { return dirty.size()+pendingSectionCount+(job==null?0:1); }
    private boolean requiresScan() {return !blockQueries.isEmpty()||!biomeQueries.isEmpty()||landmarks;}
    public void tick(ClientLevel level) {
        if(!requiresScan()||known.isEmpty()&&pendingColumns.isEmpty())return;
        long deadline=System.nanoTime()+(long)(config.scanBudgetMillis*1_000_000);
        int completed=0,registered=0;
        while(completed<config.maxSectionsPerTick&&System.nanoTime()<deadline) {
            if(!pendingColumns.isEmpty()&&registered<8) {
                var it=pendingColumns.entrySet().iterator();var c=it.next().getValue();it.remove();pendingSectionCount-=c.count;
                if(level.getChunkSource().getChunk(c.x,c.z,ChunkStatus.FULL,false)!=null)register(c);else revision++;
                registered++;continue;
            }
            if(job==null) {
                SectionKey key;
                if(!dirty.isEmpty()) {var it=dirty.iterator();key=it.next();it.remove();}
                else break;
                var chunk=level.getChunkSource().getChunk(key.x(),key.z(),ChunkStatus.FULL,false);
                if(chunk==null){unload(key.x(),key.z());continue;}
                job=new Job(key,chunk.getSection(chunk.getSectionIndexFromSectionY(key.y())));
            }
            if(job.step(level,deadline)) {
                var value=job.finish();data.put(job.key,value);changes.put(job.key,value);
                if(job.detailPassage)passageScanned.add(job.key);
                job=null;revision++;completed++;completedSections++;
            } else break;
        }
    }
    private String id(net.minecraft.world.level.block.Block b) { return ids.computeIfAbsent(b,k->BuiltInRegistries.BLOCK.getKey(k).toString()); }
    private String shortId(net.minecraft.world.level.block.Block b) {return shortIds.computeIfAbsent(b,k->{String full=id(k);return full.startsWith("minecraft:")?full.substring(10):full;});}
    private Sample sample(net.minecraft.world.level.block.state.BlockState state) {
        var found=samples.get(state);if(found!=null)return found;
        String full=id(state.getBlock()),shortName=shortId(state.getBlock());
        boolean material=materials.contains(shortName);
        found=new Sample(blockQueries.contains(full)?full:null,material?shortName:null,
                state.isAir()||switch(shortName){case "rail","cobweb","torch","wall_torch","chain"->true;default->false;},
                material&&(shortName.equals("oak_fence")||shortName.equals("dark_oak_fence")),
                mines&&MineshaftDetector.DETAILS.contains(shortName)||HutDetector.detail(shortName),
                treasure&&shortName.equals("chest"),dungeons&&shortName.equals("spawner"),shortName.equals("lava")&&state.getFluidState().isSource());
        samples.put(state,found);return found;
    }
    private void requestPassages(SectionKey support) {
        supports.add(support);
        supportColumns.computeIfAbsent(SectionColumns.key(support.x(),support.z()),k->new HashSet<>()).add(support);
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
            var k=support.offset(dx,dy,dz);
            if(known.contains(k)&&passageRequested.add(k)&&!k.equals(support)&&!passageScanned.contains(k))dirty.add(k);
        }
    }
    private final class Job {
        final SectionKey key;final LevelChunkSection section;final SectionEvidence.Builder evidence;
        final Map<String,BitSet> blocks=new HashMap<>(),biomes=new HashMap<>();
        final List<Position> chests=new ArrayList<>(),spawners=new ArrayList<>();
        final List<Target> treasures=new ArrayList<>(),rooms=new ArrayList<>();
        final BitSet lavaSources=new BitSet();
        int cursor=0,anchor=0;final boolean scan,detailPassage;
        Job(SectionKey key,LevelChunkSection section) {
            this.key=key;this.section=section;this.evidence=new SectionEvidence.Builder(key);
            if(mines&&section.maybeHas(s->sample(s).support))requestPassages(key);
            else if(supports.remove(key)){var col=supportColumns.get(SectionColumns.key(key.x(),key.z()));if(col!=null)col.remove(key);}
            detailPassage=mines&&passageRequested.contains(key);
            boolean wanted=section.maybeHas(LoadedWorldIndex.this::relevant);
            // A sky section is not a reason to examine 4096 voxels. Passages are only needed near supports.
            if(detailPassage&&section.hasOnlyAir())evidence.passage.set(0,4096);
            scan=wanted||detailPassage&&!section.hasOnlyAir();
            if(!scan)cursor=4096;
            if(scan) {
                var first=section.getBlockState(0,0,0);scannedVoxels++;var info=sample(first);
                if(!info.detail&&!info.chest&&!info.spawner&&!section.maybeHas(s->s!=first)) {
                    if(info.query!=null){var full=new BitSet(4096);full.set(0,4096);blocks.put(info.query,full);}
                    if(info.material!=null)evidence.uniform(info.material);
                    if(detailPassage&&info.passage)evidence.passage.set(0,4096);
                    if(info.material!=null&&info.lava)lavaSources.set(0,4096);
                    cursor=4096;
                }
            }
            if(!biomeQueries.isEmpty()||landmarks&&scan)for(int y=0;y<4;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++) {
                String id=section.getNoiseBiome(x,y,z).getRegisteredName();evidence.biomes.add(id);
                if(biomeQueries.contains(id))biomes.computeIfAbsent(id,k->new BitSet(64)).set((y<<4)|(z<<2)|x);
            }
        }
        boolean step(ClientLevel level,long deadline) {
            while(cursor<4096) {
                if((cursor&127)==0&&System.nanoTime()>=deadline)return false;
                int from=cursor,end=Math.min(4096,(cursor|127)+1);
                var state=section.getBlockState(cursor&15,cursor>>8,(cursor>>4)&15);
                cursor++;
                while(cursor<end&&section.getBlockState(cursor&15,cursor>>8,(cursor>>4)&15)==state)cursor++;
                var info=sample(state);scannedVoxels+=cursor-from;
                if(info.query!=null)blocks.computeIfAbsent(info.query,k->new BitSet(4096)).set(from,cursor);
                if(detailPassage&&info.passage)evidence.passage.set(from,cursor);
                if(info.material!=null) {
                    evidence.addRun(info.material,from,cursor,info.detail);
                    if(info.chest||info.spawner)for(int i=from;i<cursor;i++) {
                        var p=new Position(key.x()*16+(i&15),key.y()*16+(i>>8),key.z()*16+((i>>4)&15));
                        if(info.chest)chests.add(p);if(info.spawner)spawners.add(p);
                    }
                    if(info.lava)lavaSources.set(from,cursor);
                }
            }
            while(anchor<chests.size()+spawners.size()) {
                if(System.nanoTime()>=deadline)return false;
                if(anchor<chests.size())AnchorDetector.treasure(level,chests.get(anchor)).ifPresent(treasures::add);
                else AnchorDetector.dungeon(level,spawners.get(anchor-chests.size())).ifPresent(rooms::add);
                anchor++;
            }
            return true;
        }
        SectionData finish() {return new SectionData(evidence.build(),Map.copyOf(blocks),Map.copyOf(biomes),List.copyOf(treasures),List.copyOf(rooms),lavaSources);}
    }
}
