package dev.observer.core;

import java.util.*;

/** Vanilla 26.2 registry identifiers; no world seed or structure-start access. */
public final class RuleCatalog {
    private RuleCatalog() {}
    public static Set<String> blocks(String csv) { return Set.of(csv.split(",")); }
    private static Rule.Group g(int n,String csv) { return new Rule.Group(blocks(csv),n); }
    private static Rule r(String id,String dim,String biome,int span,int pad,String outline,String why,Rule.Group...groups) {
        return new Rule(id,"structure",dim,biome,List.of(groups),blocks(outline),span,pad,why);
    }
    private static Rule f(String id,int span,int pad,String outline,String why,Rule.Group...groups) {
        return new Rule(id,"feature","overworld","",List.of(groups),blocks(outline),span,pad,why);
    }
    private static final String WOOD="oak_planks,spruce_planks,birch_planks,jungle_planks,acacia_planks,dark_oak_planks,mangrove_planks";
    private static final String STONEBRICK="stone_bricks,mossy_stone_bricks,cracked_stone_bricks,chiseled_stone_bricks";
    private static final String PORTAL="obsidian,crying_obsidian";
    private static final String TUFF="tuff_bricks,chiseled_tuff_bricks,polished_tuff,tuff_brick_stairs,tuff_brick_slab";
    private static final String SHIP_PARTS="oak_log,spruce_log,stripped_oak_log,stripped_spruce_log,oak_slab,spruce_slab,birch_slab,jungle_slab,acacia_slab,dark_oak_slab,oak_stairs,spruce_stairs,birch_stairs,jungle_stairs,acacia_stairs,dark_oak_stairs,oak_trapdoor,spruce_trapdoor,jungle_trapdoor,dark_oak_trapdoor,oak_fence,spruce_fence";
    public static final List<Rule> RULES=create();
    private static List<Rule> create() {
        var a=new ArrayList<Rule>();
        a.add(r("buried_treasure","overworld","",1,0,"chest","observer.evidence.treasure_rule",g(1,"chest")));
        a.add(r("mineshaft","overworld","not_mesa",112,1,"oak_planks,oak_fence,rail,cobweb","observer.evidence.mineshaft_rule",g(5,"oak_planks,oak_fence"),g(2,"rail,cobweb")));
        a.add(r("mineshaft_mesa","overworld","mesa",112,1,"dark_oak_planks,dark_oak_fence,rail,cobweb","observer.evidence.mineshaft_mesa",g(5,"dark_oak_planks,dark_oak_fence"),g(2,"rail,cobweb")));
        a.add(r("mansion","overworld","",112,1,"dark_oak_planks,dark_oak_log,birch_planks,cobblestone","observer.evidence.mansion",g(24,"dark_oak_planks,dark_oak_log"),g(12,"birch_planks"),g(4,"glass_pane")));
        a.add(r("pillager_outpost","overworld","",64,1,"dark_oak_log,dark_oak_planks,birch_planks,cobblestone","observer.evidence.pillager_outpost",g(16,"dark_oak_log,dark_oak_planks"),g(8,"birch_planks"),g(4,"dark_oak_fence")));
        a.add(r("desert_pyramid","overworld","",48,1,"sandstone,cut_sandstone,chiseled_sandstone,orange_terracotta,blue_terracotta","observer.evidence.desert_pyramid",g(16,"sandstone,cut_sandstone,chiseled_sandstone"),g(1,"blue_terracotta,tnt"),g(4,"orange_terracotta")));
        a.add(r("jungle_pyramid","overworld","",40,1,"mossy_cobblestone,cobblestone,tripwire_hook,dispenser","observer.evidence.jungle_pyramid",g(16,"mossy_cobblestone"),g(1,"tripwire_hook"),g(1,"dispenser")));
        a.add(r("igloo","overworld","snowy",64,1,"snow_block,packed_ice,white_carpet,stone_bricks","observer.evidence.igloo",g(12,"snow_block"),g(2,"white_carpet"),g(1,"furnace"),g(1,"ice,packed_ice")));
        a.add(r("swamp_hut","overworld","swamp",32,1,"cauldron,water_cauldron,lava_cauldron,powder_snow_cauldron,potted_red_mushroom,flower_pot","observer.evidence.hut_rule",g(1,"cauldron,water_cauldron,lava_cauldron,powder_snow_cauldron"),g(1,"potted_red_mushroom,flower_pot")));
        a.add(r("stronghold","overworld","",160,1,STONEBRICK+",end_portal_frame,iron_bars","observer.evidence.stronghold",g(16,STONEBRICK),g(1,"iron_door,end_portal_frame"),g(3,"iron_bars,mossy_stone_bricks,cracked_stone_bricks")));
        a.add(r("monument","overworld","",80,1,"prismarine,prismarine_bricks,dark_prismarine,sea_lantern","observer.evidence.monument",g(24,"prismarine,prismarine_bricks"),g(4,"dark_prismarine"),g(1,"sea_lantern")));
        a.add(r("ocean_ruin_cold","overworld","ocean",48,1,STONEBRICK+",cobblestone,mossy_cobblestone,suspicious_gravel","observer.evidence.ocean_ruin_cold",g(12,STONEBRICK+",cobblestone,mossy_cobblestone"),g(1,"chest,suspicious_gravel")));
        a.add(r("ocean_ruin_warm","overworld","ocean",48,1,"sandstone,cut_sandstone,chiseled_sandstone,suspicious_sand","observer.evidence.ocean_ruin_warm",g(12,"sandstone,cut_sandstone,chiseled_sandstone"),g(1,"suspicious_sand,chest")));
        for(String id:List.of("shipwreck","shipwreck_beached"))
            a.add(r(id,"overworld","coast",64,1,WOOD+","+SHIP_PARTS+",chest","observer.evidence.shipwreck",g(12,WOOD+","+SHIP_PARTS),g(1,"chest"),g(3,SHIP_PARTS),g(1,"oak_trapdoor,spruce_trapdoor,birch_trapdoor,jungle_trapdoor,acacia_trapdoor,dark_oak_trapdoor,mangrove_trapdoor,cherry_trapdoor,pale_oak_trapdoor,bamboo_trapdoor,crimson_trapdoor,warped_trapdoor")));
        a.add(r("fortress","the_nether","",160,1,"nether_bricks,nether_brick_fence,nether_brick_stairs","observer.evidence.fortress",g(24,"nether_bricks"),g(4,"nether_brick_fence,nether_brick_stairs")));
        a.add(r("bastion_remnant","the_nether","",112,1,"polished_blackstone_bricks,cracked_polished_blackstone_bricks,gilded_blackstone","observer.evidence.bastion_remnant",g(24,"polished_blackstone_bricks"),g(4,"gilded_blackstone,cracked_polished_blackstone_bricks")));
        a.add(r("nether_fossil","the_nether","",32,0,"bone_block","observer.evidence.nether_fossil",g(5,"bone_block")));
        a.add(r("end_city","the_end","",144,1,"purpur_block,purpur_pillar,end_stone_bricks","observer.evidence.end_city",g(16,"purpur_block,purpur_pillar"),g(4,"end_stone_bricks,end_rod")));
        a.add(r("ancient_city","overworld","",208,1,"reinforced_deepslate,polished_deepslate,deepslate_bricks,deepslate_tiles","observer.evidence.ancient_city",g(32,"polished_deepslate,deepslate_bricks,deepslate_tiles"),g(2,"reinforced_deepslate,soul_lantern"),g(4,"reinforced_deepslate,gray_wool,cyan_wool,light_blue_wool")));
        a.add(r("trail_ruins","overworld","",80,1,"suspicious_gravel,mud_bricks,terracotta,bricks","observer.evidence.trail_ruins",g(2,"suspicious_gravel"),g(8,"mud_bricks,bricks,terracotta")));
        a.add(r("trial_chambers","overworld","",144,1,TUFF+",trial_spawner,vault","observer.evidence.trial_chambers",g(16,TUFF),g(1,"trial_spawner,vault")));
        for(String family:List.of("plains","desert","savanna","snowy","taiga")) {
            String material=switch(family) { case "desert"->"sandstone,cut_sandstone,smooth_sandstone"; case "savanna"->"acacia_planks,acacia_log,terracotta"; case "snowy","taiga"->"spruce_planks,spruce_log,stripped_spruce_log,cobblestone"; default->"oak_planks,oak_log,cobblestone"; };
            a.add(r("village_"+family,"overworld",family,192,1,material+",bell","observer.evidence.village",g(16,material),g(1,"bell,white_bed,yellow_bed,red_bed,orange_bed,cyan_bed,blue_bed,brown_bed,green_bed,black_bed,purple_bed,lime_bed,light_blue_bed"),g(1,"dirt_path,composter,hay_block,blast_furnace,grindstone,smithing_table,cartography_table,stonecutter")));
        }
        for(String suffix:List.of("","_desert","_jungle","_swamp","_mountain","_ocean","_nether")) {
            String family=suffix.isEmpty()?"standard":suffix.substring(1);
            a.add(r("ruined_portal"+suffix,suffix.equals("_nether")?"the_nether":"overworld",suffix.equals("_nether")?"":family,64,1,PORTAL+",stone_bricks,mossy_stone_bricks","observer.evidence.ruined_portal",g(4,PORTAL),g(4,"netherrack,magma_block"),g(1,"crying_obsidian,stone_bricks,mossy_stone_bricks,gold_block")));
        }
        a.add(f("amethyst_geode",48,1,"amethyst_block,budding_amethyst,calcite,smooth_basalt","observer.evidence.amethyst_geode",g(8,"amethyst_block,budding_amethyst"),g(8,"calcite"),g(8,"smooth_basalt")));
        a.add(f("dungeon",12,0,"spawner,cobblestone,mossy_cobblestone","observer.evidence.dungeon_rule",g(1,"spawner")));
        a.add(f("lava_lake",32,0,"lava","observer.evidence.lava_rule",g(12,"lava")));
        a.add(f("fossil",32,0,"bone_block","observer.evidence.fossil",g(5,"bone_block")));
        return List.copyOf(a);
    }
    public static List<String> names(String category) { return RULES.stream().filter(r->r.category().equals(category)).map(Rule::id).sorted().toList(); }
    public static Optional<Rule> find(String category,String id) { return RULES.stream().filter(r->r.category().equals(category)&&r.id().equals(id)).findFirst(); }
    /** Query families are separate from the exact vanilla registry contract. */
    public static String canonical(String id) { return id.equals("shipwreck_beached")?"shipwreck":id; }
    public static List<String> queryNames(String category) {
        var result=new TreeSet<>(names(category));
        if(category.equals("structure")){result.addAll(List.of("village","ocean_ruin"));result.remove("shipwreck_beached");}
        return List.copyOf(result);
    }
    public static List<Rule> resolve(String category,String id) {
        if(!category.equals("structure"))return find(category,id).stream().toList();
        final String family=canonical(id);
        return RULES.stream().filter(r->r.category().equals(category)).filter(r->switch(family) {
            case "village","ocean_ruin" -> r.id().startsWith(family+"_");
            case "mineshaft","ruined_portal" -> r.id().equals(family)||r.id().startsWith(family+"_");
            case "shipwreck" -> r.id().equals("shipwreck");
            default -> r.id().equals(family);
        }).toList();
    }
    public static Set<String> materials() { var s=new HashSet<String>(); RULES.forEach(r->s.addAll(r.allMaterials())); return Set.copyOf(s); }
}
