package dev.observer.core;

import java.util.*;

/** Direct column membership: unload work is proportional to that column's height. */
public final class SectionColumns {
    private final Map<Long,List<SectionKey>> columns=new HashMap<>();
    public static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    public List<SectionKey> put(int x,int z,int minY,int count) {
        var keys=new ArrayList<SectionKey>(count);for(int y=minY;y<minY+count;y++)keys.add(new SectionKey(x,y,z));
        var result=List.copyOf(keys);columns.put(key(x,z),result);return result;
    }
    public List<SectionKey> get(int x,int z){return columns.getOrDefault(key(x,z),List.of());}
    public List<SectionKey> remove(int x,int z){var result=columns.remove(key(x,z));return result==null?List.of():result;}
    public void clear(){columns.clear();}
}
