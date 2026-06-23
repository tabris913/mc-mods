package com.tosshie.rbd.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;

public class RBDConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue WORLD_SIZE;
    public static final ForgeConfigSpec.IntValue DIFFICULTY_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue HEALTH_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue ATTACK_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue DEFENSE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PORTAL_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BIOME_POOL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BIOME_EXCLUDE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ADVANCEMENT_MODS;


    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("dimensions");
        WORLD_SIZE = builder
                .comment("World border size (one side length in blocks)")
                .defineInRange("worldSize", 1000, 100, 100000);
        PORTAL_BLOCKS = builder
                .comment("Portal frame blocks for each dimension. List length = number of dimensions.")
                .defineList("portalBlocks", Arrays.asList(
                        "minecraft:crying_obsidian",
                        "minecraft:prismarine",
                        "minecraft:purpur_block",
                        "minecraft:end_stone_bricks"
                ), o -> o instanceof String);
        builder.pop();

        builder.push("difficulty");
        DIFFICULTY_INTERVAL = builder
                .comment("Distance in blocks between difficulty increases")
                .defineInRange("difficultyInterval", 100, 10, 10000);
        HEALTH_MULTIPLIER = builder
                .comment("Health multiplier per difficulty step")
                .defineInRange("healthMultiplier", 1.5, 1.0, 10.0);
        ATTACK_MULTIPLIER = builder
                .comment("Attack multiplier per difficulty step")
                .defineInRange("attackMultiplier", 1.2, 1.0, 10.0);
        DEFENSE_MULTIPLIER = builder
                .comment("Defense multiplier per difficulty step")
                .defineInRange("defenseMultiplier", 1.1, 1.0, 10.0);
        XP_MULTIPLIER = builder
                .comment("XP multiplier per difficulty step")
                .defineInRange("xpMultiplier", 1.2, 1.0, 10.0);
        builder.pop();



        builder.push("biomes");
        BIOME_POOL = builder
                .comment("List of biome resource locations available for dimension generation.",
                        "Use 'namespace:*' wildcard to include all biomes from a mod.")
                .defineList("biomePool", Arrays.asList(
                        // Vanilla (63 biomes in 1.20.1)
                        "minecraft:*",
                        // Biomes O' Plenty
                        "biomesoplenty:bayou",
                        "biomesoplenty:cherry_blossom_grove",
                        "biomesoplenty:coniferous_forest",
                        "biomesoplenty:dead_forest",
                        "biomesoplenty:fir_clearing",
                        "biomesoplenty:floodplain",
                        "biomesoplenty:fungal_jungle",
                        "biomesoplenty:highland",
                        "biomesoplenty:jade_cliffs",
                        "biomesoplenty:lavender_field",
                        "biomesoplenty:lush_desert",
                        "biomesoplenty:maple_woods",
                        "biomesoplenty:marsh",
                        "biomesoplenty:mediterranean_forest",
                        "biomesoplenty:muskeg",
                        "biomesoplenty:mystic_grove",
                        "biomesoplenty:old_growth_dead_forest",
                        "biomesoplenty:old_growth_woodland",
                        "biomesoplenty:ominous_woods",
                        "biomesoplenty:orchard",
                        "biomesoplenty:origin_valley",
                        "biomesoplenty:prairie",
                        "biomesoplenty:pumpkin_patch",
                        "biomesoplenty:rainbow_hills",
                        "biomesoplenty:redwood_forest",
                        "biomesoplenty:rocky_shrubland",
                        "biomesoplenty:scrubland",
                        "biomesoplenty:seasonal_forest",
                        "biomesoplenty:snowy_coniferous_forest",
                        "biomesoplenty:snowy_fir_clearing",
                        "biomesoplenty:snowy_maple_woods",
                        "biomesoplenty:tropics",
                        "biomesoplenty:tundra",
                        "biomesoplenty:volcanic_plains",
                        "biomesoplenty:wasteland",
                        "biomesoplenty:wetland",
                        "biomesoplenty:woodland",
                        // Oh The Biomes We've Gone
                        "biomeswevegone:allium_fields",
                        "biomeswevegone:amaranth_grasslands",
                        "biomeswevegone:araucaria_savanna",
                        "biomeswevegone:aspen_boreal",
                        "biomeswevegone:atacama_desert",
                        "biomeswevegone:autumnal_valley",
                        "biomeswevegone:baobab_savanna",
                        "biomeswevegone:bayou",
                        "biomeswevegone:black_forest",
                        "biomeswevegone:canadian_shield",
                        "biomeswevegone:cika_woods",
                        "biomeswevegone:coniferous_forest",
                        "biomeswevegone:coconino_meadow",
                        "biomeswevegone:crag_gardens",
                        "biomeswevegone:crimson_gardens",
                        "biomeswevegone:cypress_swamplands",
                        "biomeswevegone:dacite_ridges",
                        "biomeswevegone:dead_sea",
                        "biomeswevegone:ebony_woods",
                        "biomeswevegone:enchanted_tangle",
                        "biomeswevegone:eroded_borealis",
                        "biomeswevegone:forgotten_forest",
                        "biomeswevegone:frosted_taiga",
                        "biomeswevegone:howling_peaks",
                        "biomeswevegone:jacaranda_jungle",
                        "biomeswevegone:lush_stacks",
                        "biomeswevegone:maple_taiga",
                        "biomeswevegone:mojave_desert",
                        "biomeswevegone:orchard",
                        "biomeswevegone:overgrowth_woodlands",
                        "biomeswevegone:prairie",
                        "biomeswevegone:pumpkin_valley",
                        "biomeswevegone:rainbow_beach",
                        "biomeswevegone:redwood_thicket",
                        "biomeswevegone:rose_fields",
                        "biomeswevegone:sakura_grove",
                        "biomeswevegone:shattered_glacier",
                        "biomeswevegone:skyris_vale",
                        "biomeswevegone:tropical_rainforest",
                        "biomeswevegone:twilight_meadow",
                        "biomeswevegone:weeping_witch_forest",
                        "biomeswevegone:white_mangrove_wetlands",
                        "biomeswevegone:zelkova_forest",
                        // Regions Unexplored
                        "regions_unexplored:autumnal_maple_forest",
                        "regions_unexplored:bamboo_forest",
                        "regions_unexplored:baobab_savanna",
                        "regions_unexplored:blackwood_forest",
                        "regions_unexplored:chalk_cliffs",
                        "regions_unexplored:cold_deciduous_forest",
                        "regions_unexplored:colorful_beach",
                        "regions_unexplored:deciduous_forest",
                        "regions_unexplored:eucalyptus_forest",
                        "regions_unexplored:flower_fields",
                        "regions_unexplored:frozen_tundra",
                        "regions_unexplored:grassland",
                        "regions_unexplored:highland_fields",
                        "regions_unexplored:hyacinth_delight",
                        "regions_unexplored:japanese_maple_forest",
                        "regions_unexplored:lavender_fields",
                        "regions_unexplored:lush_hills",
                        "regions_unexplored:magrove_delta",
                        "regions_unexplored:maple_forest",
                        "regions_unexplored:meadow",
                        "regions_unexplored:mushroom_forest",
                        "regions_unexplored:old_growth_rainforest",
                        "regions_unexplored:orchard",
                        "regions_unexplored:palm_forest",
                        "regions_unexplored:pine_forest",
                        "regions_unexplored:poplar_forest",
                        "regions_unexplored:prairie",
                        "regions_unexplored:pumpkin_fields",
                        "regions_unexplored:rainforest",
                        "regions_unexplored:redwood_forest",
                        "regions_unexplored:scorched_plains",
                        "regions_unexplored:silver_birch_forest",
                        "regions_unexplored:sparse_redwoods",
                        "regions_unexplored:spires",
                        "regions_unexplored:temperate_grove",
                        "regions_unexplored:tropical_forest",
                        "regions_unexplored:tropics",
                        "regions_unexplored:willow_forest",
                        // Terralith
                        "terralith:amethyst_canyon",
                        "terralith:amethyst_rainforest",
                        "terralith:arid_highlands",
                        "terralith:birch_taiga",
                        "terralith:blooming_plateau",
                        "terralith:blooming_valley",
                        "terralith:brushland",
                        "terralith:caldera",
                        "terralith:cloud_forest",
                        "terralith:desert_canyon",
                        "terralith:desert_oasis",
                        "terralith:forested_highlands",
                        "terralith:fractured_savanna",
                        "terralith:granite_cliffs",
                        "terralith:gravel_desert",
                        "terralith:haze_mountain",
                        "terralith:highland_clearing",
                        "terralith:lavender_forest",
                        "terralith:lavender_valley",
                        "terralith:lush_valley",
                        "terralith:mirage_isles",
                        "terralith:moonlight_grove",
                        "terralith:moonlight_valley",
                        "terralith:orchid_swamp",
                        "terralith:red_oasis",
                        "terralith:rocky_mountains",
                        "terralith:sakura_grove",
                        "terralith:sakura_valley",
                        "terralith:sandstone_valley",
                        "terralith:savanna_badlands",
                        "terralith:shield",
                        "terralith:shield_clearing",
                        "terralith:skylands_autumn",
                        "terralith:skylands_spring",
                        "terralith:skylands_summer",
                        "terralith:skylands_winter",
                        "terralith:snowy_shield",
                        "terralith:steppe",
                        "terralith:temperate_highlands",
                        "terralith:valley_clearing",
                        "terralith:volcanic_crater",
                        "terralith:volcanic_peaks",
                        "terralith:warm_river",
                        "terralith:wisteria_forest",
                        "terralith:wisteria_valley",
                        "terralith:yellowstone",
                        "terralith:yosemite_cliffs",
                        "terralith:yosemite_lowlands"
                ), o -> o instanceof String);
        BIOME_EXCLUDE = builder
                .comment("Biomes to exclude from dimension generation and advancement tracking")
                .defineList("biomeExclude", Arrays.asList(
                        "minecraft:the_void",
                        "minecraft:the_end",
                        "minecraft:end_highlands",
                        "minecraft:end_midlands",
                        "minecraft:small_end_islands",
                        "minecraft:end_barrens"
                ), o -> o instanceof String);
        builder.pop();

        builder.push("advancements");
        ADVANCEMENT_MODS = builder
                .comment("MOD advancement settings. Format: 'modNamespace:advancementTitle'.",
                        "When a player discovers all biomes from a mod namespace, the advancement is granted.",
                        "The advancement title is displayed in chat.")
                .defineList("advancementMods", Arrays.asList(
                        "minecraft:真・冒険の時間",
                        "biomesoplenty:ホワット・ア・ワンダフル・バイオーム",
                        "biomeswevegone:ウィー・アー・ザ・バイオーマーズ",
                        "regions_unexplored:バイオーミアン・ラプソディ",
                        "terralith:ルール・ザ・テラ"
                ), o -> o instanceof String);
        builder.pop();

        SPEC = builder.build();
    }
}
