package dev.observer.client;

import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Opt-in profiling spans. Durations are wall time, not thread CPU time. */
@Name("observer.SearchPhase")
@Label("Observer search phase")
@StackTrace(false)
public final class SearchProfile extends Event implements AutoCloseable {
    private static final boolean ENABLED=Boolean.getBoolean("observer.profile");
    public String phase;
    public static volatile String scenario="validation";
    public String workload;
    private SearchProfile(String phase){this.phase=phase;workload=scenario;begin();}
    static SearchProfile start(String phase){return ENABLED?new SearchProfile(phase):null;}
    @Override public void close(){end();commit();}
}
