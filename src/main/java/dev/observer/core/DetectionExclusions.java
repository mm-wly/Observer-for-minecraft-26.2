package dev.observer.core;

import java.util.Set;

/** Negative context is scanned even when its corresponding structure query is inactive. */
public final class DetectionExclusions {
    private DetectionExclusions() {}
    private static final Set<String> TRIAL=Set.of("trial_spawner","vault","tuff_bricks","chiseled_tuff_bricks","polished_tuff","tuff_brick_stairs","tuff_brick_slab","copper_grate","oxidized_copper_grate","waxed_copper_bulb","waxed_oxidized_copper_bulb");
    public static Set<String> materials(String id) {return id.equals("fossil")?TRIAL:Set.of();}
    public static boolean rejects(String id,int markers,int masonry) {return id.equals("fossil")&&(markers>0||masonry>=16);}
    public static int markers(SectionEvidence e) {return e.count(Set.of("trial_spawner","vault"));}
    public static int masonry(SectionEvidence e) {return e.count(TRIAL);}
}
