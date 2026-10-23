package dev.observer.core;

import java.util.Collection;

/** Order-independent membership digest; motion and pagination do not change identity. */
public final class TargetMembership {
    private TargetMembership() {}
    public static long hash(String key) {
        long h=0xcbf29ce484222325L;
        for(int i=0;i<key.length();i++)h=(h^key.charAt(i))*0x100000001b3L;
        return mix(h);
    }
    public static long mix(long v) {v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return v^(v>>>31);}
    public static long of(Collection<Target> targets) {long h=0;for(var t:targets)h+=hash(t.key());return h;}
}
