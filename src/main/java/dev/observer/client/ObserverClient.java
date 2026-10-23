package dev.observer.client;

import dev.observer.core.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import static dev.observer.client.ObserverText.text;
import java.util.concurrent.*;

public final class ObserverClient implements ClientModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("observer");
    public static ObserverClient INSTANCE;
    public ObserverConfig config;
    public LoadedWorldIndex index;
    public final ClientMetrics metrics=new ClientMetrics();
    public final Map<Integer,SearchSession> sessions=new LinkedHashMap<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"observer-snapshot-search");t.setDaemon(true);return t;});
    private Future<List<SearchComputer.Answer>> pending;
    private volatile long generation;
    private long pendingGeneration,lastCompute,pendingTopology;
    private long lastVisual,lastEntities,lastReport;
    private int lastReportedId;
    private boolean pendingComplete;
    private SearchComputer.Cache searchCache=new SearchComputer.Cache();
    private boolean fullSnapshot=true;
    private long submittedRevision=-1;
    private final ChunkRemovals queuedUnloads=new ChunkRemovals(),removedDuringSearch=new ChunkRemovals();
    public long searchSubmissions,unloadBatches;
    private double submittedX=Double.NaN,submittedY,submittedZ;
    private double visualX=Double.NaN,visualY,visualZ;
    private boolean visualDirty=true;
    private ClientLevel world;
    private final Set<net.minecraft.world.level.ChunkPos> receivedChunks=new HashSet<>();
    private int nextId=1;
    private Set<UUID> glowing=Set.of();
    public record ClickTarget(int sessionId,Target target) {}
    private final LinkedHashMap<String,ClickTarget> clicks=new LinkedHashMap<>();
    public List<Target> renderTargets=List.of();
    public Target navigation;
    private int navigationSession;

    @Override public void onInitializeClient() {
        INSTANCE=this;config=ObserverConfig.load();index=new LoadedWorldIndex(config);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->ObserverCommands.register(dispatcher,this));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientChunkEvents.CHUNK_LOAD.register((level,chunk)->{
            long start=metrics.enabled?System.nanoTime():0;
            syncWorld(level);receivedChunks.add(chunk.getPos());if(!sessions.isEmpty()){index.load(chunk);for(var s:sessions.values())if(!s.category.equals("entity"))s.complete=false;}
            if(start!=0)metrics.event(System.nanoTime()-start);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level,chunk)->{
            long start=metrics.enabled?System.nanoTime():0;
            if(level==world){
                int x=chunk.getPos().x(),z=chunk.getPos().z();receivedChunks.remove(chunk.getPos());index.unload(x,z);
                if(!sessions.isEmpty()){queuedUnloads.add(x,z);if(pending!=null)removedDuringSearch.add(x,z);}
            }
            if(start!=0)metrics.event(System.nanoTime()-start);
        });
        LevelExtractionEvents.END_EXTRACTION.register(context->ObserverRenderer.extract(context,this));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{invalidateWorker();worker.shutdownNow();});
        LOG.info("Observer client initialized (Minecraft 26.2, loaded client data only)");
    }
    public boolean isGlowing(Entity entity) { return entity.level()==world&&entity.isAlive()&&glowing.contains(entity.getUUID()); }
    public void changed(net.minecraft.core.BlockPos p,net.minecraft.world.level.block.state.BlockState oldState,net.minecraft.world.level.block.state.BlockState newState) { if(!sessions.isEmpty())index.changed(new SectionKey(p.getX()>>4,p.getY()>>4,p.getZ()>>4),oldState,newState); }
    public void clear() {
        if(navigation!=null&&Minecraft.getInstance().player!=null)Minecraft.getInstance().player.sendOverlayMessage(Component.empty());
        invalidateWorker();queuedUnloads.clear();sessions.clear();clicks.clear();glowing=Set.of();renderTargets=List.of();ObserverRenderer.clear();navigation=null;index.reset();searchSubmissions=unloadBatches=0;
    }
    private void invalidateWorker() { generation++;if(pending!=null)pending.cancel(true);pending=null;lastCompute=0;submittedRevision=-1;searchCache=new SearchComputer.Cache();fullSnapshot=true;removedDuringSearch.clear();visualDirty=true; }
    private void syncWorld(ClientLevel level) {
        if(world==level)return;
        if(level==null){clear();receivedChunks.clear();world=null;return;}
        invalidateWorker();queuedUnloads.clear();receivedChunks.clear();clicks.clear();glowing=Set.of();renderTargets=List.of();ObserverRenderer.clear();stopNavigation();index.reset();world=level;
        for(var session:sessions.values())session.resetResults();
        index.configure(sessions.values(),level,receivedChunks);
    }
    public int clear(String category,String raw) {
        Identifier id=Identifier.tryParse(raw);
        if(id!=null&&category.equals("structure")&&id.toString().equals("minecraft:shipwreck_beached"))id=Identifier.tryParse("minecraft:shipwreck");
        if(id==null||!names(category).contains(id.toString())){message(text("unknown",text("category."+category),raw));return 0;}
        String name=id.toString();var match=sessions.values().stream().filter(q->q.category.equals(category)&&q.name.equals(name)).findFirst();
        if(match.isEmpty()){message(text("not_tracking",category,name));return 0;}
        var q=match.get();sessions.remove(q.id);clicks.values().removeIf(link->link.sessionId()==q.id);if(navigationSession==q.id)stopNavigation();
        if(sessions.isEmpty())clear();
        else {invalidateWorker();index.configure(sessions.values(),world,receivedChunks);var mc=Minecraft.getInstance();if(mc.player!=null)updateVisuals(mc);}
        message(text("cleared",category,name));return 1;
    }
    public Collection<String> names(String category) {
        if(category.equals("structure")||category.equals("feature"))return RuleCatalog.queryNames(category).stream().map(n->"minecraft:"+n).toList();
        if(category.equals("block"))return BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString).sorted().toList();
        if(category.equals("entity"))return BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString).sorted().toList();
        var level=Minecraft.getInstance().level;
        if(level==null)return List.of();
        return level.registryAccess().lookupOrThrow(Registries.BIOME).keySet().stream().map(Object::toString).sorted().toList();
    }
    public int start(String category,String raw) {
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return 0;
        syncWorld(mc.level);
        Identifier id=Identifier.tryParse(raw);
        if(id!=null&&category.equals("structure")&&id.toString().equals("minecraft:shipwreck_beached"))id=Identifier.tryParse("minecraft:shipwreck");
        if(id==null||!names(category).contains(id.toString())) {message(text("unknown",text("category."+category),raw));return 0;}
        String name=category.equals("structure")?"minecraft:"+RuleCatalog.canonical(id.getPath()):id.toString();
        var existing=sessions.values().stream().filter(s->s.category.equals(category)&&s.name.equals(name)).findFirst();
        if(existing.isEmpty()&&sessions.size()>=config.maxQueries){message(text("query_limit",config.maxQueries));return 0;}
        if(existing.isPresent()){report(existing.get());return 1;}
        var session=new SearchSession(nextId++,category,name);sessions.put(session.id,session);
        invalidateWorker();boolean rescanned=index.configure(sessions.values(),world,receivedChunks);
        if(rescanned)for(var s:sessions.values())s.complete=false;
        message(text("started",session.id,category,name));
        if(category.equals("structure")||category.equals("feature"))message(text("heuristic"));
        return 1;
    }
    private void tick(Minecraft mc) {
        if(!metrics.enabled){tickWork(mc);return;}
        long start=System.nanoTime();try{tickWork(mc);}finally{metrics.tick(System.nanoTime()-start);}
    }
    private void tickWork(Minecraft mc) {
        syncWorld(mc.level);
        if(world==null||mc.player==null||sessions.isEmpty())return;
        if(!queuedUnloads.isEmpty()){purgeUnloaded(queuedUnloads);queuedUnloads.clear();unloadBatches++;}
        long phase;boolean reportedThisTick=false;
        long now=System.nanoTime();
        if(pending!=null&&pending.isDone()) {
            try { if(pendingGeneration==generation)for(var a:pending.get()) {
                var s=sessions.get(a.id());if(s!=null){
                    s.targets=removedDuringSearch.filter(a.targets());s.count=s.targets==a.targets()?a.count():s.targets.size();
                    s.membership=a.membership();s.computed=true;s.complete=pendingComplete&&pendingTopology==index.topologyRevision;visualDirty=true;
                }
            }} catch(CancellationException ignored) {}
            catch(ExecutionException e) {LOG.error("Snapshot search failed",e.getCause());message(text("search_failed"));}
            catch(InterruptedException e) {Thread.currentThread().interrupt();LOG.warn("Interrupted while collecting search results",e);}
            pending=null;removedDuringSearch.clear();
        }
        if(now-lastEntities>=100_000_000L){phase=metrics.enabled?System.nanoTime():0;updateEntities(mc);if(metrics.enabled)metrics.phase(5,System.nanoTime()-phase);lastEntities=now;}
        boolean moved=Double.isNaN(submittedX)||Math.pow(mc.player.getX()-submittedX,2)+Math.pow(mc.player.getY()-submittedY,2)+Math.pow(mc.player.getZ()-submittedZ,2)>=.25;
        if(pending==null&&(index.revision!=submittedRevision||moved)&&now-lastCompute>=config.refreshMillis*1_000_000L) {
            var requests=sessions.values().stream().filter(s->!s.category.equals("entity")).map(s->new SearchComputer.Request(s.id,s.category,s.name)).toList();
            if(!requests.isEmpty()) {
                var delta=index.drainChanges(fullSnapshot);fullSnapshot=false;long gen=generation;pendingGeneration=gen;
                pendingComplete=index.indexed()==index.total()&&index.pending()==0;
                pendingTopology=index.topologyRevision;
                String dimension=world.dimension().identifier().toString();
                double x=mc.player.getX(),y=mc.player.getY(),z=mc.player.getZ();
                submittedX=x;submittedY=y;submittedZ=z;submittedRevision=index.revision;var cache=searchCache;
                searchSubmissions++;
                pending=worker.submit(()->SearchComputer.computeDelta(requests,delta,dimension,x,y,z,config.maxResults,()->gen!=generation||Thread.currentThread().isInterrupted(),cache));
            }
            lastCompute=now;
        }
        // Round-robin notifications keep simultaneous queries from flooding chat or starving each other.
        if(now-lastReport>=250_000_000L) {
            SearchSession first=null,next=null;
            for(var s:sessions.values())if(s.shouldReport(now)){if(first==null)first=s;if(s.id>lastReportedId){next=s;break;}}
            if(next==null)next=first;
            if(next!=null){phase=metrics.enabled?System.nanoTime():0;report(next);if(metrics.enabled)metrics.phase(1,System.nanoTime()-phase);next.reported(now);lastReport=now;lastReportedId=next.id;reportedThisTick=true;}
        }
        // Vanilla chat layout can be expensive. Do not stack the scanner budget onto a report tick.
        if(!reportedThisTick){phase=metrics.enabled?System.nanoTime():0;index.tick(world);if(metrics.enabled)metrics.phase(0,System.nanoTime()-phase);}
        if(now-lastVisual>=100_000_000L&&(visualDirty||navigation!=null||mc.player.getX()!=visualX||mc.player.getY()!=visualY||mc.player.getZ()!=visualZ)) {
            phase=metrics.enabled?System.nanoTime():0;updateVisuals(mc);if(metrics.enabled)metrics.phase(2,System.nanoTime()-phase);lastVisual=now;visualDirty=false;visualX=mc.player.getX();visualY=mc.player.getY();visualZ=mc.player.getZ();
        }
    }
    private void updateEntities(Minecraft mc) {
        var queries=sessions.values().stream().filter(s->s.category.equals("entity")).toList();if(queries.isEmpty())return;
        visualDirty=true;
        Map<String,List<Target>> byType=new HashMap<>();var wanted=new HashSet<String>();for(var q:queries)wanted.add(q.name);
        for(Entity e:world.entitiesForRendering()) {
            if(!e.isAlive())continue;String type=BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
            if(!wanted.contains(type))continue;
            var p=new Position((int)Math.floor(e.getX()),(int)Math.floor(e.getY()),(int)Math.floor(e.getZ()));var b=e.getBoundingBox();
            var box=new Box((int)Math.floor(b.minX),(int)Math.floor(b.minY),(int)Math.floor(b.minZ),Math.max((int)Math.floor(b.minX)+1,(int)Math.ceil(b.maxX)),Math.max((int)Math.floor(b.minY)+1,(int)Math.ceil(b.maxY)),Math.max((int)Math.floor(b.minZ)+1,(int)Math.ceil(b.maxZ)));
            byType.computeIfAbsent(type,k->new ArrayList<>()).add(new Target(e.getUUID().toString(),p,box,false,"observer.evidence.entity",e.getId(),e.getX(),e.getY(),e.getZ()));
        }
        for(var s:queries){var list=byType.getOrDefault(s.name,List.of());s.membership=TargetMembership.of(list);s.count=list.size();s.targets=Results.nearest(list,config.maxResults,mc.player.getX(),mc.player.getY(),mc.player.getZ());s.computed=true;s.complete=true;}
    }
    public boolean loaded(Target t) {
        if(world==null)return false;
        if(t.entityId()>=0){var e=world.getEntity(t.entityId());return e!=null&&e.isAlive()&&e.getUUID().toString().equals(t.key());}
        var p=t.position();return receivedChunks.contains(new net.minecraft.world.level.ChunkPos(p.x()>>4,p.z()>>4))&&world.getChunkSource().getChunk(p.x()>>4,p.z()>>4,ChunkStatus.FULL,false)!=null;
    }
    private void purgeUnloaded(ChunkRemovals removed) {
        // Once per tick, not once per chunk packet. Opaque clicks are checked lazily on use.
        for(var s:sessions.values()){var next=removed.filter(s.targets);if(next!=s.targets){s.targets=next;s.computed=false;}s.complete=false;}
        renderTargets=removed.filter(renderTargets);visualDirty=true;
        if(navigation!=null&&removed.intersects(navigation.box()))stopNavigation();
    }
    private boolean validLink(ClickTarget link) {
        var s=sessions.get(link.sessionId());var t=link.target();
        if(s==null||!loaded(t))return false;
        if(s.category.equals("block"))return AnchorDetector.block(world,t.position()).equals(s.name);
        if(s.name.equals("minecraft:buried_treasure"))return AnchorDetector.treasure(world,t.position()).isPresent();
        if(s.category.equals("entity"))return BuiltInRegistries.ENTITY_TYPE.getKey(world.getEntity(t.entityId()).getType()).toString().equals(s.name);
        if(s.category.equals("biome")) {
            var p=t.position();var chunk=world.getChunkSource().getChunk(p.x()>>4,p.z()>>4,ChunkStatus.FULL,false);
            return chunk!=null&&chunk.getNoiseBiome(p.x()>>2,p.y()>>2,p.z()>>2).getRegisteredName().equals(s.name);
        }
        return s.targets.stream().anyMatch(current->current.key().equals(t.key())&&current.box().equals(t.box()));
    }
    private void stopNavigation() {
        if(navigation!=null&&Minecraft.getInstance().player!=null)Minecraft.getInstance().player.sendOverlayMessage(Component.empty());
        navigation=null;
    }
    private void updateVisuals(Minecraft mc) {
        var entities=new HashSet<UUID>();var unique=new HashMap<Box,Target>();
        if(navigation!=null&&!validLink(new ClickTarget(navigationSession,navigation)))stopNavigation();
        for(var s:sessions.values()) {
            // A biome may span every loaded chunk; drawing every sample grid obscures useful targets.
            if(s.category.equals("biome"))continue;
            int boxes=0;
            for(var t:s.targets) {
                if(!loaded(t))continue;
                if(t.entityId()>=0)entities.add(UUID.fromString(t.key()));
                else {unique.putIfAbsent(t.box(),t);if(++boxes>=config.maxRenderBoxes)break;}
            }
        }
        if(navigation!=null&&sessions.get(navigationSession).category.equals("biome"))unique.put(navigation.box(),navigation);
        glowing=Set.copyOf(entities);
        renderTargets=Results.nearest(unique.values(),config.maxRenderBoxes,mc.player.getX(),mc.player.getY(),mc.player.getZ());
        if(navigation!=null) {
            var t=navigation;
            if(t.entityId()>=0){var e=world.getEntity(t.entityId());var p=new Position((int)Math.floor(e.getX()),(int)Math.floor(e.getY()),(int)Math.floor(e.getZ()));t=new Target(t.key(),p,t.box(),false,t.evidence(),t.entityId(),e.getX(),e.getY(),e.getZ());navigation=t;}
            mc.player.sendOverlayMessage(text("navigation",t.position().x(),t.position().y(),t.position().z(),Results.distance(t,mc.player.getX(),mc.player.getY(),mc.player.getZ())));
        }
    }
    public int page(int id,int page) {
        var s=sessions.get(id);if(s==null){message(text("query_expired"));return 0;}s.page=page;report(s);return 1;
    }
    public int latestPage(int page) { return sessions.isEmpty()?0:page(sessions.keySet().stream().reduce((a,b)->b).orElseThrow(),page); }
    public void report(SearchSession s) {
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        long stage=metrics.enabled?System.nanoTime():0;
        var sorted=s.targets.stream().filter(this::loaded).toList();
        s.page=Math.clamp(s.page,1,Results.pages(sorted.size()));
        Component progress=!s.category.equals("entity")&&!s.complete?(index.indexed()<index.total()?text("scanning",index.indexed(),index.total()):text("refreshing")):Component.empty();
        Component limited=s.count>sorted.size()?text("retained",sorted.size()):Component.empty();
        var output=Component.literal("[Observer] ").append(text("results",s.id,s.count,s.name.replace("minecraft:",""),progress,limited,s.page,Results.pages(sorted.size())));
        int row=(s.page-1)*8;
        for(var t:Results.nearestPage(sorted,s.page,mc.player.getX(),mc.player.getY(),mc.player.getZ())) {
            String token=UUID.randomUUID().toString();clicks.put(token,new ClickTarget(s.id,t));
            while(clicks.size()>8192)clicks.remove(clicks.keySet().iterator().next());
            var p=t.position();var rowText=text("result_row",++row,p.x(),p.y(),p.z(),Results.distance(t,mc.player.getX(),mc.player.getY(),mc.player.getZ()),t.approximate()?text("estimated"):Component.empty());
            output.append("\n").append(rowText.withStyle(style->style.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent.RunCommand("/obs look "+token)).withHoverEvent(new HoverEvent.ShowText(text("target_hover",Component.translatable(t.evidence()))))));
        }
        if(Results.pages(sorted.size())>1) {
            var nav=Component.literal("");
            if(s.page>1)nav.append(pageLink("previous",s.id,s.page-1));
            if(s.page<Results.pages(sorted.size()))nav.append(pageLink("next",s.id,s.page+1));output.append("\n").append(nav);
        }
        if(metrics.enabled){metrics.phase(3,System.nanoTime()-stage);stage=System.nanoTime();}
        ObserverChat.report(output);if(metrics.enabled)metrics.phase(4,System.nanoTime()-stage);
    }
    private Component pageLink(String text,int id,int page) {return text(text).withStyle(s->s.withColor(ChatFormatting.YELLOW).withClickEvent(new ClickEvent.RunCommand("/obs page "+id+" "+page)));}
    public Collection<String> clickTokens() {return Collections.unmodifiableSet(clicks.keySet());}
    public int look(String token) {
        var link=clicks.get(token);var mc=Minecraft.getInstance();
        if(link==null||!validLink(link)||mc.player==null){message(text("target_expired"));return 0;}
        var t=link.target();
        double tx=t.position().x()+.5,ty=t.position().y()+.5,tz=t.position().z()+.5;
        if(t.entityId()>=0){var e=world.getEntity(t.entityId());tx=e.getX();ty=e.getEyeY();tz=e.getZ();}
        var eye=mc.player.getEyePosition();double dx=tx-eye.x,dy=ty-eye.y,dz=tz-eye.z;
        if(dx*dx+dy*dy+dz*dz>1e-12) {
            if(dx*dx+dz*dz>1e-12)mc.player.setYRot((float)(Math.toDegrees(Math.atan2(dz,dx))-90));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
            // Suppress interpolation from the previous rotation: the requested turn is immediate.
            mc.player.yRotO=mc.player.getYRot();mc.player.xRotO=mc.player.getXRot();
        }
        navigation=t;navigationSession=link.sessionId();return 1;
    }
    public void list() { message(queryList()); }
    Component queryList() {
        var output=text("list_summary",sessions.size(),index.indexed(),index.total(),index.pending(),renderTargets.size(),config.maxRenderBoxes);
        for(var session:sessions.values()) {
            output.append("\n").append(Component.literal("#"+session.id+" "+session.category+" "))
                .append(Component.literal(session.name).withStyle(style->style.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent.RunCommand("/obs page "+session.id+" 1"))
                    .withHoverEvent(new HoverEvent.ShowText(text("query_hover")))))
                .append(text("query_count",session.count));
        }
        if(sessions.isEmpty())output.append("\n").append(text("list_empty"));
        return output;
    }
    public static void message(String text) {message(Component.literal(text));}
    public static void message(Component text) {
        var mc=Minecraft.getInstance();
        // This is local UI output, not a server message; do not retain it in the server reporting log.
        if(mc.player!=null)mc.gui.chatListener().handleSystemMessage(Component.literal("[Observer] ").append(text),false);
    }
}
