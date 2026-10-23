package dev.observer.core;

import java.util.*;

public final class Results {
    public static final int PAGE_SIZE = 8;
    private Results() {}
    public static List<Target> sorted(Collection<Target> values, double x,double y,double z) {
        var list = new ArrayList<>(values);
        list.sort(Comparator.comparingDouble((Target t)->t.distanceSquared(x,y,z))
                .thenComparing(Target::position).thenComparing(Target::key));
        return List.copyOf(list);
    }
    /** Preserve full-sort ordering, but only sort K retained targets when K is small. */
    public static List<Target> nearest(Collection<Target> values,int limit,double x,double y,double z) {
        if(limit<1)throw new IllegalArgumentException("Positive result limit required");
        int keep=Math.min(limit,values.size());
        Comparator<Target> order=Comparator.comparingDouble((Target t)->t.distanceSquared(x,y,z)).thenComparing(Target::position).thenComparing(Target::key);
        // Near the limit, heap maintenance costs more than sorting; reserve it for N > 4K.
        if(keep*4L>=values.size()) {
            var sorted=new ArrayList<>(values);sorted.sort(order);
            return List.copyOf(sorted.subList(0,keep));
        }
        var heap=new PriorityQueue<Target>(keep,order.reversed());
        for(var target:values) {
            if(heap.size()<keep)heap.add(target);
            else if(order.compare(target,heap.peek())<0){heap.poll();heap.add(target);}
        }
        var result=new ArrayList<>(heap);result.sort(order);return List.copyOf(result);
    }
    public static int pages(int count) { return Math.max(1, (count+PAGE_SIZE-1)/PAGE_SIZE); }
    /** Select only the requested prefix instead of sorting thousands of targets to print eight rows. */
    public static List<Target> nearestPage(Collection<Target> values,int page,double x,double y,double z) {
        if(page<1||page>pages(values.size()))throw new IllegalArgumentException("Page out of range");
        int keep=Math.min(values.size(),page*PAGE_SIZE);
        if(keep>=values.size()/2)return page(sorted(values,x,y,z),page);
        Comparator<Target> order=Comparator.comparingDouble((Target t)->t.distanceSquared(x,y,z)).thenComparing(Target::position).thenComparing(Target::key);
        var heap=new PriorityQueue<Target>(Math.max(1,keep),order.reversed());
        for(var t:values)if(heap.size()<keep)heap.add(t);else if(order.compare(t,heap.peek())<0){heap.poll();heap.add(t);}
        var prefix=new ArrayList<>(heap);prefix.sort(order);return List.copyOf(prefix.subList((page-1)*PAGE_SIZE,prefix.size()));
    }
    public static List<Target> page(List<Target> sorted,int page) {
        if (page < 1 || page > pages(sorted.size())) throw new IllegalArgumentException("Page out of range");
        int start=(page-1)*PAGE_SIZE;
        return sorted.subList(start,Math.min(start+PAGE_SIZE,sorted.size()));
    }
    public static int distance(Target t,double x,double y,double z) { return (int)Math.floor(Math.sqrt(t.distanceSquared(x,y,z))); }
}
