/*
 * This file is part of Velthoric.
 * Licensed under LGPL 3.0.
 */
package net.xmx.velthoric.ship.shape;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A central registry for defining the physical mass of different blocks.
 * This allows for realistic mass and inertia calculations for complex bodies like ships.
 *
 * @author xI-Mx-Ix
 */
public class VxBlockMassRegistry {

    private static final VxBlockMassRegistry INSTANCE = new VxBlockMassRegistry();
    private final Object2FloatMap<Block> massMap = new Object2FloatOpenHashMap<>();
    private static final float DEFAULT_MASS = 100.0f; // Default mass for a generic block

    private VxBlockMassRegistry() {
        registerDefaults();
    }

    public static VxBlockMassRegistry getInstance() {
        return INSTANCE;
    }

    public void register(Block block, float mass) {
        massMap.put(block, mass);
    }

    public float getMass(BlockState state) {
        // Return default mass if a block is not in the map
        return massMap.getOrDefault(state.getBlock(), DEFAULT_MASS);
    }

    private void registerDefaults() {
        // --- MYTHICAL & PRECIOUS MATERIALS ---
        massMap.put(Blocks.NETHERITE_BLOCK, 9500.0f); // The heaviest plausible block
        massMap.put(Blocks.ANCIENT_DEBRIS, 3500.0f);
        massMap.put(Blocks.GOLD_BLOCK, 1932.0f);
        massMap.put(Blocks.DIAMOND_BLOCK, 3510.0f); // Diamonds are dense
        massMap.put(Blocks.EMERALD_BLOCK, 2760.0f);
        massMap.put(Blocks.LAPIS_BLOCK, 2400.0f);
        massMap.put(Blocks.BEACON, 1200.0f);
        massMap.put(Blocks.ENCHANTING_TABLE, 550.0f);
        massMap.put(Blocks.END_PORTAL_FRAME, 8000.0f); // Should be very heavy
        massMap.put(Blocks.DRAGON_EGG, 1500.0f);
        massMap.put(Blocks.DRAGON_HEAD, 150.0f);
        massMap.put(Blocks.CONDUIT, 250.0f);
        massMap.put(Blocks.LODESTONE, 1200.0f);
        massMap.put(Blocks.RESPAWN_ANCHOR, 600.0f);
        massMap.put(Blocks.BEDROCK, 300.0f); // Effectively immovable

        // --- METALS & ORES ---
        massMap.put(Blocks.IRON_BLOCK, 787.0f);
        massMap.put(Blocks.RAW_IRON_BLOCK, 750.0f);
        massMap.put(Blocks.IRON_ORE, 350.0f);
        massMap.put(Blocks.DEEPSLATE_IRON_ORE, 450.0f);
        massMap.put(Blocks.IRON_BARS, 78.7f);
        massMap.put(Blocks.IRON_DOOR, 157.4f);
        massMap.put(Blocks.IRON_TRAPDOOR, 78.7f);
        massMap.put(Blocks.ANVIL, 800.0f);
        massMap.put(Blocks.CHIPPED_ANVIL, 600.0f);
        massMap.put(Blocks.DAMAGED_ANVIL, 400.0f);
        massMap.put(Blocks.CAULDRON, 550.9f); // 7 iron ingots

        massMap.put(Blocks.RAW_GOLD_BLOCK, 1850.0f);
        massMap.put(Blocks.GOLD_ORE, 450.0f);
        massMap.put(Blocks.DEEPSLATE_GOLD_ORE, 550.0f);
        massMap.put(Blocks.NETHER_GOLD_ORE, 350.0f);
        massMap.put(Blocks.BELL, 450.0f);

        // --- COPPER FAMILY (Complete Set) ---
        // Ores and Raw Blocks
        massMap.put(Blocks.COPPER_ORE, 380.0f);
        massMap.put(Blocks.DEEPSLATE_COPPER_ORE, 480.0f);
        massMap.put(Blocks.RAW_COPPER_BLOCK, 860.0f);

        // Stage 1: Normal Copper
        float normalMass = 896.0f;
        massMap.put(Blocks.COPPER_BLOCK, normalMass);
        massMap.put(Blocks.CUT_COPPER, normalMass);
        massMap.put(Blocks.CUT_COPPER_STAIRS, normalMass * 0.75f); // 672.0f
        massMap.put(Blocks.CUT_COPPER_SLAB, normalMass * 0.5f);   // 448.0f

        // Stage 2: Exposed Copper (Slightly lighter due to oxidation)
        float exposedMass = 892.0f;
        massMap.put(Blocks.EXPOSED_COPPER, exposedMass);
        massMap.put(Blocks.EXPOSED_CUT_COPPER, exposedMass);
        massMap.put(Blocks.EXPOSED_CUT_COPPER_STAIRS, exposedMass * 0.75f); // 669.0f
        massMap.put(Blocks.EXPOSED_CUT_COPPER_SLAB, exposedMass * 0.5f);   // 446.0f

        // Stage 3: Weathered Copper
        float weatheredMass = 888.0f;
        massMap.put(Blocks.WEATHERED_COPPER, weatheredMass);
        massMap.put(Blocks.WEATHERED_CUT_COPPER, weatheredMass);
        massMap.put(Blocks.WEATHERED_CUT_COPPER_STAIRS, weatheredMass * 0.75f); // 666.0f
        massMap.put(Blocks.WEATHERED_CUT_COPPER_SLAB, weatheredMass * 0.5f);   // 444.0f

        // Stage 4: Oxidized Copper
        float oxidizedMass = 885.0f;
        massMap.put(Blocks.OXIDIZED_COPPER, oxidizedMass);
        massMap.put(Blocks.OXIDIZED_CUT_COPPER, oxidizedMass);
        massMap.put(Blocks.OXIDIZED_CUT_COPPER_STAIRS, oxidizedMass * 0.75f); // 663.75f
        massMap.put(Blocks.OXIDIZED_CUT_COPPER_SLAB, oxidizedMass * 0.5f);   // 442.5f

        // --- WAXED COPPER FAMILY (Slightly heavier due to wax coating) ---
        float waxAdditive = 1.0f;

        // Stage 1: Waxed Normal Copper
        float waxedNormalMass = normalMass + waxAdditive; // 897.0f
        massMap.put(Blocks.WAXED_COPPER_BLOCK, waxedNormalMass);
        massMap.put(Blocks.WAXED_CUT_COPPER, waxedNormalMass);
        massMap.put(Blocks.WAXED_CUT_COPPER_STAIRS, waxedNormalMass * 0.75f); // 672.75f
        massMap.put(Blocks.WAXED_CUT_COPPER_SLAB, waxedNormalMass * 0.5f);   // 448.5f

        // Stage 2: Waxed Exposed Copper
        float waxedExposedMass = exposedMass + waxAdditive; // 893.0f
        massMap.put(Blocks.WAXED_EXPOSED_COPPER, waxedExposedMass);
        massMap.put(Blocks.WAXED_EXPOSED_CUT_COPPER, waxedExposedMass);
        massMap.put(Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS, waxedExposedMass * 0.75f); // 669.75f
        massMap.put(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB, waxedExposedMass * 0.5f);   // 446.5f

        // Stage 3: Waxed Weathered Copper
        float waxedWeatheredMass = weatheredMass + waxAdditive; // 889.0f
        massMap.put(Blocks.WAXED_WEATHERED_COPPER, waxedWeatheredMass);
        massMap.put(Blocks.WAXED_WEATHERED_CUT_COPPER, waxedWeatheredMass);
        massMap.put(Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, waxedWeatheredMass * 0.75f); // 666.75f
        massMap.put(Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB, waxedWeatheredMass * 0.5f);   // 444.5f

        // Stage 4: Waxed Oxidized Copper
        float waxedOxidizedMass = oxidizedMass + waxAdditive; // 886.0f
        massMap.put(Blocks.WAXED_OXIDIZED_COPPER, waxedOxidizedMass);
        massMap.put(Blocks.WAXED_OXIDIZED_CUT_COPPER, waxedOxidizedMass);
        massMap.put(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, waxedOxidizedMass * 0.75f); // 664.5f
        massMap.put(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, waxedOxidizedMass * 0.5f);   // 443.0f

        // Other Copper Items
        massMap.put(Blocks.LIGHTNING_ROD, 224.0f); // Crafted from 3 Ingots (896 / 9 * 3 = ~298, let's adjust for shape)

        massMap.put(Blocks.DIAMOND_ORE, 250.0f);
        massMap.put(Blocks.DEEPSLATE_DIAMOND_ORE, 350.0f);
        massMap.put(Blocks.EMERALD_ORE, 220.0f);
        massMap.put(Blocks.DEEPSLATE_EMERALD_ORE, 320.0f);
        massMap.put(Blocks.LAPIS_ORE, 200.0f);
        massMap.put(Blocks.DEEPSLATE_LAPIS_ORE, 300.0f);
        massMap.put(Blocks.NETHER_QUARTZ_ORE, 160.0f);
        massMap.put(Blocks.QUARTZ_BLOCK, 265.0f);
        massMap.put(Blocks.QUARTZ_BRICKS, 265.0f);
        massMap.put(Blocks.QUARTZ_PILLAR, 265.0f);
        massMap.put(Blocks.CHISELED_QUARTZ_BLOCK, 265.0f);
        massMap.put(Blocks.SMOOTH_QUARTZ, 265.0f);

        massMap.put(Blocks.COAL_BLOCK, 135.0f);
        massMap.put(Blocks.COAL_ORE, 150.0f);
        massMap.put(Blocks.DEEPSLATE_COAL_ORE, 250.0f);

        massMap.put(Blocks.REDSTONE_BLOCK, 530.0f);
        massMap.put(Blocks.REDSTONE_ORE, 280.0f);
        massMap.put(Blocks.DEEPSLATE_REDSTONE_ORE, 380.0f);

        // --- STONE, MASONRY & EARTH ---
        massMap.put(Blocks.STONE, 150.0f);
        massMap.put(Blocks.COBBLESTONE, 140.0f);
        massMap.put(Blocks.STONE_BRICKS, 150.0f);
        massMap.put(Blocks.MOSSY_COBBLESTONE, 145.0f);
        massMap.put(Blocks.MOSSY_STONE_BRICKS, 155.0f);
        massMap.put(Blocks.CRACKED_STONE_BRICKS, 145.0f);
        massMap.put(Blocks.CHISELED_STONE_BRICKS, 150.0f);
        massMap.put(Blocks.SMOOTH_STONE, 150.0f);
        massMap.put(Blocks.INFESTED_STONE, 160.0f); // Bugs add mass!

        massMap.put(Blocks.GRANITE, 165.0f);
        massMap.put(Blocks.POLISHED_GRANITE, 165.0f);
        massMap.put(Blocks.DIORITE, 160.0f);
        massMap.put(Blocks.POLISHED_DIORITE, 160.0f);
        massMap.put(Blocks.ANDESITE, 155.0f);
        massMap.put(Blocks.POLISHED_ANDESITE, 155.0f);

        massMap.put(Blocks.DEEPSLATE, 200.0f);
        massMap.put(Blocks.COBBLED_DEEPSLATE, 190.0f);
        massMap.put(Blocks.POLISHED_DEEPSLATE, 200.0f);
        massMap.put(Blocks.DEEPSLATE_BRICKS, 200.0f);
        massMap.put(Blocks.DEEPSLATE_TILES, 200.0f);
        massMap.put(Blocks.CRACKED_DEEPSLATE_BRICKS, 195.0f);
        massMap.put(Blocks.CRACKED_DEEPSLATE_TILES, 195.0f);
        massMap.put(Blocks.CHISELED_DEEPSLATE, 200.0f);

        massMap.put(Blocks.BRICKS, 160.0f);
        massMap.put(Blocks.BRICK_SLAB, 80.0f);
        massMap.put(Blocks.BRICK_STAIRS, 120.0f);
        massMap.put(Blocks.BRICK_WALL, 160.0f);

        massMap.put(Blocks.MUD_BRICKS, 145.0f);
        massMap.put(Blocks.MUD_BRICK_SLAB, 72.5f);
        massMap.put(Blocks.MUD_BRICK_STAIRS, 108.75f);
        massMap.put(Blocks.MUD_BRICK_WALL, 145.0f);

        massMap.put(Blocks.OBSIDIAN, 450.0f);
        massMap.put(Blocks.CRYING_OBSIDIAN, 480.0f);

        massMap.put(Blocks.TUFF, 130.0f);
        massMap.put(Blocks.CALCITE, 180.0f);
        massMap.put(Blocks.DRIPSTONE_BLOCK, 170.0f);
        massMap.put(Blocks.POINTED_DRIPSTONE, 40.0f);
        massMap.put(Blocks.AMETHYST_BLOCK, 190.0f);
        massMap.put(Blocks.BUDDING_AMETHYST, 220.0f);
        massMap.put(Blocks.AMETHYST_CLUSTER, 50.0f);

        massMap.put(Blocks.SANDSTONE, 140.0f);
        massMap.put(Blocks.SMOOTH_SANDSTONE, 140.0f);
        massMap.put(Blocks.CUT_SANDSTONE, 140.0f);
        massMap.put(Blocks.CHISELED_SANDSTONE, 140.0f);
        massMap.put(Blocks.RED_SANDSTONE, 140.0f);
        massMap.put(Blocks.SMOOTH_RED_SANDSTONE, 140.0f);
        massMap.put(Blocks.CUT_RED_SANDSTONE, 140.0f);
        massMap.put(Blocks.CHISELED_RED_SANDSTONE, 140.0f);

        massMap.put(Blocks.DIRT, 120.0f);
        massMap.put(Blocks.GRASS_BLOCK, 125.0f);
        massMap.put(Blocks.PODZOL, 122.0f);
        massMap.put(Blocks.MYCELIUM, 128.0f);
        massMap.put(Blocks.COARSE_DIRT, 118.0f);
        massMap.put(Blocks.ROOTED_DIRT, 130.0f);
        massMap.put(Blocks.FARMLAND, 110.0f);
        massMap.put(Blocks.DIRT_PATH, 115.0f);
        massMap.put(Blocks.CLAY, 130.0f);
        massMap.put(Blocks.GRAVEL, 135.0f);
        massMap.put(Blocks.SAND, 130.0f);
        massMap.put(Blocks.RED_SAND, 130.0f);
        massMap.put(Blocks.SUSPICIOUS_SAND, 125.0f);
        massMap.put(Blocks.SUSPICIOUS_GRAVEL, 130.0f);
        massMap.put(Blocks.MUD, 140.0f);
        massMap.put(Blocks.PACKED_MUD, 142.0f);

        // --- NETHER BLOCKS ---
        massMap.put(Blocks.NETHERRACK, 80.0f);
        massMap.put(Blocks.SOUL_SAND, 70.0f);
        massMap.put(Blocks.SOUL_SOIL, 75.0f);
        massMap.put(Blocks.BASALT, 180.0f);
        massMap.put(Blocks.SMOOTH_BASALT, 180.0f);
        massMap.put(Blocks.POLISHED_BASALT, 180.0f);
        massMap.put(Blocks.BLACKSTONE, 170.0f);
        massMap.put(Blocks.POLISHED_BLACKSTONE, 170.0f);
        massMap.put(Blocks.POLISHED_BLACKSTONE_BRICKS, 170.0f);
        massMap.put(Blocks.CHISELED_POLISHED_BLACKSTONE, 170.0f);
        massMap.put(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, 165.0f);
        massMap.put(Blocks.GILDED_BLACKSTONE, 350.0f);
        massMap.put(Blocks.MAGMA_BLOCK, 150.0f);
        massMap.put(Blocks.GLOWSTONE, 60.0f);
        massMap.put(Blocks.SHROOMLIGHT, 70.0f);
        massMap.put(Blocks.WARPED_NYLIUM, 90.0f);
        massMap.put(Blocks.CRIMSON_NYLIUM, 90.0f);
        massMap.put(Blocks.WARPED_WART_BLOCK, 40.0f);
        massMap.put(Blocks.NETHER_WART_BLOCK, 40.0f);
        massMap.put(Blocks.BONE_BLOCK, 120.0f);

        // --- END BLOCKS ---
        massMap.put(Blocks.END_STONE, 220.0f);
        massMap.put(Blocks.END_STONE_BRICKS, 220.0f);
        massMap.put(Blocks.PURPUR_BLOCK, 210.0f);
        massMap.put(Blocks.PURPUR_PILLAR, 210.0f);
        massMap.put(Blocks.PURPUR_STAIRS, 157.5f);
        massMap.put(Blocks.PURPUR_SLAB, 105.0f);
        massMap.put(Blocks.CHORUS_PLANT, 30.0f);
        massMap.put(Blocks.CHORUS_FLOWER, 10.0f);
        massMap.put(Blocks.SHULKER_BOX, 250.0f); // End stone + shulker shells

        // --- WOOD & PLANT-BASED BLOCKS ---
        // Logs and Planks for each wood type
        registerWoodType(Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_OAK_WOOD, 60.0f);
        registerWoodType(Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_WOOD, Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_WOOD, 65.0f);
        registerWoodType(Blocks.BIRCH_LOG, Blocks.BIRCH_PLANKS, Blocks.BIRCH_WOOD, Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_BIRCH_WOOD, 55.0f);
        registerWoodType(Blocks.JUNGLE_LOG, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_WOOD, Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_WOOD, 70.0f);
        registerWoodType(Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_WOOD, Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_ACACIA_WOOD, 75.0f);
        registerWoodType(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_WOOD, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_WOOD, 80.0f);
        registerWoodType(Blocks.MANGROVE_LOG, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_WOOD, Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_WOOD, 68.0f);
        registerWoodType(Blocks.CHERRY_LOG, Blocks.CHERRY_PLANKS, Blocks.CHERRY_WOOD, Blocks.STRIPPED_CHERRY_LOG, Blocks.STRIPPED_CHERRY_WOOD, 58.0f);
        registerWoodType(Blocks.CRIMSON_STEM, Blocks.CRIMSON_PLANKS, Blocks.CRIMSON_HYPHAE, Blocks.STRIPPED_CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_HYPHAE, 85.0f);
        registerWoodType(Blocks.WARPED_STEM, Blocks.WARPED_PLANKS, Blocks.WARPED_HYPHAE, Blocks.STRIPPED_WARPED_STEM, Blocks.STRIPPED_WARPED_HYPHAE, 85.0f);

        massMap.put(Blocks.BAMBOO_BLOCK, 40.0f);
        massMap.put(Blocks.STRIPPED_BAMBOO_BLOCK, 38.0f);
        massMap.put(Blocks.BAMBOO_PLANKS, 35.0f);
        massMap.put(Blocks.BAMBOO_MOSAIC, 35.0f);

        // Functional Wood Blocks
        massMap.put(Blocks.CRAFTING_TABLE, 50.0f);
        massMap.put(Blocks.BOOKSHELF, 70.0f);
        massMap.put(Blocks.CHISELED_BOOKSHELF, 72.0f);
        massMap.put(Blocks.LECTERN, 65.0f);
        massMap.put(Blocks.CARTOGRAPHY_TABLE, 45.0f);
        massMap.put(Blocks.FLETCHING_TABLE, 42.0f);
        massMap.put(Blocks.SMITHING_TABLE, 250.0f);
        massMap.put(Blocks.LOOM, 40.0f);
        massMap.put(Blocks.BARREL, 48.0f);
        massMap.put(Blocks.COMPOSTER, 45.0f);
        massMap.put(Blocks.BEEHIVE, 30.0f);
        massMap.put(Blocks.BEE_NEST, 15.0f);
        massMap.put(Blocks.CHEST, 45.0f);
        massMap.put(Blocks.TRAPPED_CHEST, 46.0f);
        massMap.put(Blocks.JUKEBOX, 100.0f);
        massMap.put(Blocks.NOTE_BLOCK, 50.0f);

        // --- FLORA & LIGHTWEIGHT BLOCKS ---
        massMap.put(Blocks.OAK_LEAVES, 2.0f);
        massMap.put(Blocks.SPRUCE_LEAVES, 2.0f);
        massMap.put(Blocks.BIRCH_LEAVES, 2.0f);
        massMap.put(Blocks.JUNGLE_LEAVES, 2.0f);
        massMap.put(Blocks.ACACIA_LEAVES, 2.0f);
        massMap.put(Blocks.DARK_OAK_LEAVES, 2.0f);
        massMap.put(Blocks.MANGROVE_LEAVES, 2.0f);
        massMap.put(Blocks.CHERRY_LEAVES, 2.0f);
        massMap.put(Blocks.AZALEA_LEAVES, 2.0f);
        massMap.put(Blocks.FLOWERING_AZALEA_LEAVES, 2.5f);
        massMap.put(Blocks.MOSS_BLOCK, 10.0f);
        massMap.put(Blocks.MOSS_CARPET, 1.0f);
        massMap.put(Blocks.VINE, 1.0f);
        massMap.put(Blocks.LILY_PAD, 0.5f);
        massMap.put(Blocks.HAY_BLOCK, 25.0f);
        massMap.put(Blocks.PUMPKIN, 30.0f);
        massMap.put(Blocks.CARVED_PUMPKIN, 28.0f);
        massMap.put(Blocks.JACK_O_LANTERN, 35.0f);
        massMap.put(Blocks.MELON, 32.0f);
        massMap.put(Blocks.COCOA, 5.0f);
        massMap.put(Blocks.CACTUS, 40.0f); // It's mostly water
        massMap.put(Blocks.SUGAR_CANE, 8.0f);
        massMap.put(Blocks.DRIED_KELP_BLOCK, 12.0f);

        // --- WOOL & CARPETS ---
        registerColorSet(15.0f, Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.PINK_WOOL, Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL, Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL, Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL, Blocks.BLACK_WOOL);
        registerColorSet(5.0f, Blocks.WHITE_CARPET, Blocks.ORANGE_CARPET, Blocks.MAGENTA_CARPET, Blocks.LIGHT_BLUE_CARPET, Blocks.YELLOW_CARPET, Blocks.LIME_CARPET, Blocks.PINK_CARPET, Blocks.GRAY_CARPET, Blocks.LIGHT_GRAY_CARPET, Blocks.CYAN_CARPET, Blocks.PURPLE_CARPET, Blocks.BLUE_CARPET, Blocks.BROWN_CARPET, Blocks.GREEN_CARPET, Blocks.RED_CARPET, Blocks.BLACK_CARPET);

        // --- TERRACOTTA & CONCRETE ---
        massMap.put(Blocks.TERRACOTTA, 140.0f);
        registerColorSet(140.0f, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.LIME_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.GRAY_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.PURPLE_TERRACOTTA, Blocks.BLUE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.GREEN_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.BLACK_TERRACOTTA);
        registerColorSet(145.0f, Blocks.WHITE_GLAZED_TERRACOTTA, Blocks.ORANGE_GLAZED_TERRACOTTA, Blocks.MAGENTA_GLAZED_TERRACOTTA, Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, Blocks.YELLOW_GLAZED_TERRACOTTA, Blocks.LIME_GLAZED_TERRACOTTA, Blocks.PINK_GLAZED_TERRACOTTA, Blocks.GRAY_GLAZED_TERRACOTTA, Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA, Blocks.CYAN_GLAZED_TERRACOTTA, Blocks.PURPLE_GLAZED_TERRACOTTA, Blocks.BLUE_GLAZED_TERRACOTTA, Blocks.BROWN_GLAZED_TERRACOTTA, Blocks.GREEN_GLAZED_TERRACOTTA, Blocks.RED_GLAZED_TERRACOTTA, Blocks.BLACK_GLAZED_TERRACOTTA);
        registerColorSet(170.0f, Blocks.WHITE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE, Blocks.PINK_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.BROWN_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.RED_CONCRETE, Blocks.BLACK_CONCRETE);
        registerColorSet(150.0f, Blocks.WHITE_CONCRETE_POWDER, Blocks.ORANGE_CONCRETE_POWDER, Blocks.MAGENTA_CONCRETE_POWDER, Blocks.LIGHT_BLUE_CONCRETE_POWDER, Blocks.YELLOW_CONCRETE_POWDER, Blocks.LIME_CONCRETE_POWDER, Blocks.PINK_CONCRETE_POWDER, Blocks.GRAY_CONCRETE_POWDER, Blocks.LIGHT_GRAY_CONCRETE_POWDER, Blocks.CYAN_CONCRETE_POWDER, Blocks.PURPLE_CONCRETE_POWDER, Blocks.BLUE_CONCRETE_POWDER, Blocks.BROWN_CONCRETE_POWDER, Blocks.GREEN_CONCRETE_POWDER, Blocks.RED_CONCRETE_POWDER, Blocks.BLACK_CONCRETE_POWDER);

        // --- GLASS ---
        massMap.put(Blocks.GLASS, 110.0f);
        massMap.put(Blocks.GLASS_PANE, 20.0f);
        massMap.put(Blocks.TINTED_GLASS, 120.0f);
        registerColorSet(110.0f, Blocks.WHITE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS, Blocks.LIME_STAINED_GLASS, Blocks.PINK_STAINED_GLASS, Blocks.GRAY_STAINED_GLASS, Blocks.LIGHT_GRAY_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS, Blocks.BROWN_STAINED_GLASS, Blocks.GREEN_STAINED_GLASS, Blocks.RED_STAINED_GLASS, Blocks.BLACK_STAINED_GLASS);

        // --- UTILITY & REDSTONE ---
        massMap.put(Blocks.FURNACE, 160.0f);
        massMap.put(Blocks.BLAST_FURNACE, 200.0f);
        massMap.put(Blocks.SMOKER, 100.0f);
        massMap.put(Blocks.BREWING_STAND, 180.0f);
        massMap.put(Blocks.GRINDSTONE, 180.0f);
        massMap.put(Blocks.STONECUTTER, 160.0f);
        massMap.put(Blocks.ENDER_CHEST, 300.0f);
        massMap.put(Blocks.PISTON, 180.0f);
        massMap.put(Blocks.STICKY_PISTON, 185.0f);
        massMap.put(Blocks.DISPENSER, 155.0f);
        massMap.put(Blocks.DROPPER, 150.0f);
        massMap.put(Blocks.HOPPER, 400.0f); // 5 iron ingots + chest
        massMap.put(Blocks.OBSERVER, 160.0f);
        massMap.put(Blocks.DAYLIGHT_DETECTOR, 60.0f);
        massMap.put(Blocks.REDSTONE_LAMP, 120.0f);
        massMap.put(Blocks.TARGET, 40.0f);
        massMap.put(Blocks.TNT, 150.0f);
        massMap.put(Blocks.SLIME_BLOCK, 20.0f);
        massMap.put(Blocks.HONEY_BLOCK, 25.0f);
        massMap.put(Blocks.SCULK_SENSOR, 80.0f);
        massMap.put(Blocks.CALIBRATED_SCULK_SENSOR, 90.0f);

        // --- MISCELLANEOUS ---
        massMap.put(Blocks.AIR, 0.0f);
        massMap.put(Blocks.WATER, 100.0f); // Mass per block of liquid
        massMap.put(Blocks.LAVA, 250.0f); // Lava is denser
        massMap.put(Blocks.TORCH, 5.0f);
        massMap.put(Blocks.SOUL_TORCH, 5.0f);
        massMap.put(Blocks.REDSTONE_TORCH, 6.0f);
        massMap.put(Blocks.SPONGE, 5.0f);
        massMap.put(Blocks.WET_SPONGE, 105.0f);
        massMap.put(Blocks.LADDER, 10.0f);
        massMap.put(Blocks.RAIL, 30.0f);
        massMap.put(Blocks.POWERED_RAIL, 80.0f);
        massMap.put(Blocks.DETECTOR_RAIL, 70.0f);
        massMap.put(Blocks.ACTIVATOR_RAIL, 75.0f);
        massMap.put(Blocks.SCAFFOLDING, 8.0f);
        massMap.put(Blocks.ICE, 91.7f);
        massMap.put(Blocks.PACKED_ICE, 100.0f);
        massMap.put(Blocks.BLUE_ICE, 110.0f);
        massMap.put(Blocks.SNOW_BLOCK, 10.0f);
        massMap.put(Blocks.SNOW, 1.25f);
        massMap.put(Blocks.SEA_LANTERN, 130.0f);
        massMap.put(Blocks.PRISMARINE, 160.0f);
        massMap.put(Blocks.PRISMARINE_BRICKS, 160.0f);
        massMap.put(Blocks.DARK_PRISMARINE, 165.0f);
        massMap.put(Blocks.CHAIN, 50.0f);
        massMap.put(Blocks.LANTERN, 60.0f);
        massMap.put(Blocks.SOUL_LANTERN, 60.0f);
        massMap.put(Blocks.DECORATED_POT, 25.0f);
        massMap.put(Blocks.FLOWER_POT, 12.0f);
        massMap.put(Blocks.CAMPFIRE, 40.0f);
        massMap.put(Blocks.SOUL_CAMPFIRE, 45.0f);
    }

    /**
     * Helper method to register a full set of wood-related blocks.
     * @param log The log block
     * @param planks The plank block
     * @param wood The "wood" (6-sided log) block
     * @param strippedLog The stripped log block
     * @param strippedWood The stripped wood block
     * @param logMass The base mass for the log
     */
    private void registerWoodType(Block log, Block planks, Block wood, Block strippedLog, Block strippedWood, float logMass) {
        float plankMass = logMass * 0.8f; // Planks are less dense than logs
        massMap.put(log, logMass);
        massMap.put(planks, plankMass);
        massMap.put(wood, logMass);
        massMap.put(strippedLog, logMass * 0.95f); // Slightly lighter without bark
        massMap.put(strippedWood, logMass * 0.95f);
    }

    /**
     * Helper method to register an array of blocks with the same mass (e.g., all wool colors).
     * @param mass The mass to apply to all blocks
     * @param blocks The blocks to register
     */
    private void registerColorSet(float mass, Block... blocks) {
        for (Block block : blocks) {
            massMap.put(block, mass);
        }
    }
}