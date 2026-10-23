package dev.observer.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;

public final class ObserverConfig {
    public double scanBudgetMillis=2.0;
    public int maxSectionsPerTick=128;
    public int maxResults=4096;
    public int maxRenderBoxes=512;
    public int maxQueries=16;
    public int refreshMillis=500;
    public static ObserverConfig load() {
        return load(FabricLoader.getInstance().getConfigDir().resolve("observer.json"));
    }
    static ObserverConfig load(Path path) {
        var gson=new GsonBuilder().setPrettyPrinting().create();
        ObserverConfig c=new ObserverConfig();
        try {
            if(Files.exists(path)) { var read=gson.fromJson(Files.readString(path),ObserverConfig.class); if(read!=null)c=read; }
        } catch(IOException|JsonParseException e) {
            ObserverClient.LOG.warn("Cannot read observer config; using defaults without overwriting the file",e);
            return c;
        }
        c.scanBudgetMillis=Double.isFinite(c.scanBudgetMillis)?Math.clamp(c.scanBudgetMillis,.25,10):2;
        c.maxSectionsPerTick=Math.clamp(c.maxSectionsPerTick,1,128);
        c.maxResults=Math.clamp(c.maxResults,8,65536); c.maxRenderBoxes=Math.clamp(c.maxRenderBoxes,8,4096);
        c.maxQueries=Math.clamp(c.maxQueries,1,32); c.refreshMillis=Math.clamp(c.refreshMillis,100,10000);
        try {
            Files.createDirectories(path.getParent());Files.writeString(path,gson.toJson(c));
        } catch(IOException e) { ObserverClient.LOG.warn("Cannot save observer config; keeping the validated values in memory",e); }
        return c;
    }
}
