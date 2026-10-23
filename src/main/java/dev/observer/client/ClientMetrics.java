package dev.observer.client;

import java.util.Arrays;
import java.util.Locale;

/** Opt-in test/developer measurements; no collection in normal play. Measures Observer work, not FPS. */
public final class ClientMetrics {
    public boolean enabled;
    private final long[] ticks=new long[4096],events=new long[16384];
    private int tickCount,eventCount;
    private final long[][] phases=new long[6][4096];private final int[] phaseCounts=new int[6];
    public void reset(){tickCount=eventCount=0;Arrays.fill(phaseCounts,0);}
    void tick(long nanos){ticks[tickCount++%ticks.length]=nanos;}
    void event(long nanos){events[eventCount++%events.length]=nanos;}
    void phase(int id,long nanos){phases[id][phaseCounts[id]++%4096]=nanos;}
    public String summary(){return stats("Observer tick",ticks,tickCount)+"\n"+stats("Observer chunk callback",events,eventCount)+"\n"+stats("Index scan",phases[0],phaseCounts[0])+"\n"+stats("Chat report",phases[1],phaseCounts[1])+"\n"+stats("Visual selection",phases[2],phaseCounts[2])+"\n"+stats("Chat preparation",phases[3],phaseCounts[3])+"\n"+stats("Chat insertion",phases[4],phaseCounts[4])+"\n"+stats("Entity update",phases[5],phaseCounts[5]);}
    private static String stats(String label,long[] source,int count) {
        var data=Arrays.copyOf(source,Math.min(count,source.length));Arrays.sort(data);
        if(data.length==0)return label+": no samples";
        return String.format(Locale.ROOT,"%s: n=%d, p50=%.3f ms, p95=%.3f ms, p99=%.3f ms, max=%.3f ms, retained=%d, total=%.3f ms",label,count,data[data.length/2]/1e6,data[(int)((data.length-1)*.95)]/1e6,data[(int)((data.length-1)*.99)]/1e6,data[data.length-1]/1e6,data.length,Arrays.stream(data).sum()/1e6);
    }
}
