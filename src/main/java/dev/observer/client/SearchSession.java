package dev.observer.client;
import dev.observer.core.Target;
import java.util.List;
/** Persistent query: report membership changes, not motion or distance reordering. */
public final class SearchSession {
    public final int id;
    public final String category,name;
    public long nextReport,membership;
    private long reportedMembership,reportedCount;
    public int page=1;
    public boolean announced,computed,complete;
    public long count;
    public List<Target> targets=List.of();
    public SearchSession(int id,String category,String name) {this.id=id;this.category=category;this.name=name;}
    public String queryKey() {return category+":"+name;}
    public boolean shouldReport(long now) {return computed&&(!announced?(complete||count>0):now>=nextReport&&(count!=reportedCount||membership!=reportedMembership));}
    public void reported(long now) {announced=true;reportedCount=count;reportedMembership=membership;nextReport=now+1_000_000_000L;}
    public void resetResults(){targets=List.of();count=membership=0;computed=complete=false;}
}
