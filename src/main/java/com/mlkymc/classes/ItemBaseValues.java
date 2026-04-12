package com.mlkymc.classes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * Central registry for every item's base XP value, profession mapping,
 * world-generation status, and optional attribute buff.
 *
 * Used by:
 * - Crafting XP: sum of ingredient base values
 * - Smelting XP: input item's base value
 * - Mining XP: naturallyGenerated check
 * - Ingredient buffs: AttributeBuff on food/potions
 * - Conversion detection: prevent XP exploits
 */
public final class ItemBaseValues {

    // =========================================================================
    // Data structures
    // =========================================================================

    public enum AttributeBuff {
        NONE,
        HEALTH,              // +% max health
        DAMAGE,              // +% attack damage
        ATTACK_SPEED,        // +% attack speed
        ARMOR,               // +% armor
        ARMOR_TOUGHNESS,     // +% armor toughness
        SPEED,               // +% movement speed
        JUMP,                // +% jump strength
        MINING_SPEED,        // +% block break speed
        REACH,               // +% block interaction range
        ENTITY_REACH,        // +% entity interaction range
        LUCK,                // +% luck (fishing loot)
        KNOCKBACK_RESISTANCE,// +% knockback resistance
        OXYGEN,              // +% underwater breath
        SAFE_FALL,           // +% safe fall distance
        FALL_DAMAGE,         // -% fall damage (inverted: positive = less damage)
        STEP_HEIGHT,         // +% step height
        BURNING_TIME,        // -% burning duration (inverted: positive = burn less)
        UNDERWATER_MINING,   // +% submerged mining speed
        SNEAKING_SPEED       // +% sneaking speed
    }

    public record ItemValue(int baseXp, ProfessionType profession, boolean naturallyGenerated, AttributeBuff buff) {
        public ItemValue(int baseXp, ProfessionType profession, boolean naturallyGenerated) {
            this(baseXp, profession, naturallyGenerated, AttributeBuff.NONE);
        }
    }

    private static final Map<Item, ItemValue> ITEM_VALUES = new HashMap<>(3000);
    private static final Set<Item> CONVERSION_OUTPUTS = new HashSet<>();
    private static final Map<Item, Item> SMELTING_INPUT_MAP = new HashMap<>();
    private static final Set<Block> COOKING_BLOCKS = new HashSet<>();

    private static final ItemValue DEFAULT = new ItemValue(1, ProfessionType.MINECRAFTER, false);

    // =========================================================================
    // Public API
    // =========================================================================

    public static int getBaseXp(Item item) {
        return ITEM_VALUES.getOrDefault(item, DEFAULT).baseXp();
    }

    public static ProfessionType getProfession(Item item) {
        return ITEM_VALUES.getOrDefault(item, DEFAULT).profession();
    }

    public static boolean isNaturallyGenerated(Item item) {
        return ITEM_VALUES.getOrDefault(item, DEFAULT).naturallyGenerated();
    }

    public static AttributeBuff getAttributeBuff(Item item) {
        return ITEM_VALUES.getOrDefault(item, DEFAULT).buff();
    }

    public static boolean isConversionOutput(Item item) {
        return CONVERSION_OUTPUTS.contains(item);
    }

    public static Item getSmeltingInput(Item output) {
        return SMELTING_INPUT_MAP.get(output);
    }

    public static boolean isCookingBlock(Block block) {
        return COOKING_BLOCKS.contains(block);
    }

    public static ItemValue get(Item item) {
        return ITEM_VALUES.getOrDefault(item, DEFAULT);
    }

    // =========================================================================
    // Registration helpers
    // =========================================================================

    private static void r(Item item, int xp, ProfessionType prof, boolean natural) {
        ITEM_VALUES.put(item, new ItemValue(xp, prof, natural));
    }

    private static void r(Item item, int xp, ProfessionType prof, boolean natural, AttributeBuff buff) {
        ITEM_VALUES.put(item, new ItemValue(xp, prof, natural, buff));
    }

    /** Register a modded item by string ID (safe if mod not loaded) */
    private static void rMod(String modId, String itemName, int xp, ProfessionType prof, boolean natural) {
        var id = Identifier.tryParse(modId + ":" + itemName);
        if (id == null) return;
        BuiltInRegistries.ITEM.get(id).ifPresent(holder ->
                ITEM_VALUES.put(holder.value(), new ItemValue(xp, prof, natural)));
    }

    private static void rMod(String modId, String itemName, int xp, ProfessionType prof, boolean natural, AttributeBuff buff) {
        var id = Identifier.tryParse(modId + ":" + itemName);
        if (id == null) return;
        BuiltInRegistries.ITEM.get(id).ifPresent(holder ->
                ITEM_VALUES.put(holder.value(), new ItemValue(xp, prof, natural, buff)));
    }

    private static void conversion(Item item) {
        CONVERSION_OUTPUTS.add(item);
    }

    /** Mark a modded item as a conversion output (safe if the mod isn't loaded). */
    private static void conversionMod(String modId, String itemName) {
        var id = Identifier.tryParse(modId + ":" + itemName);
        if (id == null) return;
        BuiltInRegistries.ITEM.get(id).ifPresent(holder -> CONVERSION_OUTPUTS.add(holder.value()));
    }

    private static void smelt(Item output, Item input) {
        SMELTING_INPUT_MAP.put(output, input);
    }

    // =========================================================================
    // Pattern-based bulk registration
    // =========================================================================

    /**
     * Called once at mod init to register ALL items.
     * Must be called AFTER registries are frozen (FMLCommonSetupEvent or later).
     */
    public static void init() {
        registerVanillaItems();
        registerConversions();
        registerSmeltingMap();
        registerCookingBlocks();
        registerMlkymcItems();
        registerModdedItems();
        registerPatternFallbacks();

        com.mlkymc.MlkyMC.LOGGER.info("[ItemBaseValues] Registered {} items, {} conversions, {} smelting maps",
                ITEM_VALUES.size(), CONVERSION_OUTPUTS.size(), SMELTING_INPUT_MAP.size());
    }

    // =========================================================================
    // VANILLA ITEMS (~1481)
    // =========================================================================

    private static void registerVanillaItems() {
        // P = ProfessionType shorthand
        var M = ProfessionType.MINECRAFTER;
        var S = ProfessionType.SMITH;
        var F = ProfessionType.FARMHAND;
        var C = ProfessionType.CLERIC;
        var A = ProfessionType.ADVENTURER;

        // --- ZERO VALUE (technical, unobtainable, creative-only) ---
        r(Items.AIR, 0, M, false);
        r(Items.BARRIER, 0, M, false);
        r(Items.BEDROCK, 0, M, false);
        r(Items.COMMAND_BLOCK, 0, M, false);
        r(Items.CHAIN_COMMAND_BLOCK, 0, M, false);
        r(Items.REPEATING_COMMAND_BLOCK, 0, M, false);
        r(Items.COMMAND_BLOCK_MINECART, 0, M, false);
        r(Items.STRUCTURE_BLOCK, 0, M, false);
        r(Items.STRUCTURE_VOID, 0, M, false);
        r(Items.JIGSAW, 0, M, false);
        r(Items.DEBUG_STICK, 0, M, false);
        r(Items.LIGHT, 0, M, false);
        r(Items.BUNDLE, 0, M, false);
        r(Items.KNOWLEDGE_BOOK, 0, C, false);
        r(Items.PETRIFIED_OAK_SLAB, 0, M, false);
        r(Items.PLAYER_HEAD, 0, M, false);
        r(Items.SPAWNER, 0, M, true);
        r(Items.TRIAL_SPAWNER, 0, M, true);
        r(Items.VAULT, 0, M, true);

        // --- TERRAIN & NATURAL BLOCKS (naturally generated) ---
        r(Items.STONE, 1, M, true);
        r(Items.GRANITE, 1, M, true);
        r(Items.POLISHED_GRANITE, 1, M, false);
        r(Items.DIORITE, 1, M, true);
        r(Items.POLISHED_DIORITE, 1, M, false);
        r(Items.ANDESITE, 1, M, true);
        r(Items.POLISHED_ANDESITE, 1, M, false);
        r(Items.DEEPSLATE, 1, M, true);
        r(Items.COBBLED_DEEPSLATE, 1, M, true);
        r(Items.POLISHED_DEEPSLATE, 1, M, false);
        r(Items.CALCITE, 1, M, true);
        r(Items.TUFF, 1, M, true);
        r(Items.POLISHED_TUFF, 1, M, false);
        r(Items.DRIPSTONE_BLOCK, 1, M, true);
        r(Items.POINTED_DRIPSTONE, 1, M, true);
        r(Items.GRASS_BLOCK, 1, F, true);
        r(Items.DIRT, 1, F, true);
        r(Items.COARSE_DIRT, 1, F, true);
        r(Items.PODZOL, 1, F, true);
        r(Items.ROOTED_DIRT, 1, F, true);
        r(Items.MUD, 1, F, true);
        r(Items.CRIMSON_NYLIUM, 1, F, true);
        r(Items.WARPED_NYLIUM, 1, F, true);
        r(Items.COBBLESTONE, 1, M, true);
        r(Items.SAND, 1, M, true);
        r(Items.RED_SAND, 1, M, true);
        r(Items.GRAVEL, 1, M, true);
        r(Items.CLAY, 2, M, true);
        r(Items.MYCELIUM, 1, F, true);
        r(Items.SOUL_SAND, 1, M, true);
        r(Items.SOUL_SOIL, 1, M, true);
        r(Items.NETHERRACK, 1, M, true);
        r(Items.BASALT, 1, M, true);
        r(Items.SMOOTH_BASALT, 1, M, true);
        r(Items.BLACKSTONE, 1, M, true);
        r(Items.POLISHED_BLACKSTONE, 1, M, false);
        r(Items.END_STONE, 2, M, true);
        r(Items.OBSIDIAN, 5, M, true);
        r(Items.CRYING_OBSIDIAN, 8, M, true);
        r(Items.SANDSTONE, 1, M, true);
        r(Items.RED_SANDSTONE, 1, M, true);
        r(Items.PRISMARINE, 3, M, true);
        r(Items.DARK_PRISMARINE, 3, M, true);
        r(Items.SEA_LANTERN, 3, M, true);
        r(Items.ICE, 1, M, true);
        r(Items.PACKED_ICE, 1, M, true);
        r(Items.BLUE_ICE, 2, M, true);
        r(Items.SNOW_BLOCK, 1, M, true);
        r(Items.SNOW, 1, M, true);
        r(Items.MOSS_BLOCK, 1, F, true);
        r(Items.SCULK, 2, M, true);
        r(Items.SCULK_VEIN, 1, M, true);
        r(Items.SCULK_CATALYST, 5, M, true);
        r(Items.SCULK_SHRIEKER, 5, M, true);
        r(Items.SCULK_SENSOR, 5, M, true);
        r(Items.CALIBRATED_SCULK_SENSOR, 5, M, false);
        r(Items.AMETHYST_BLOCK, 3, M, true);
        r(Items.BUDDING_AMETHYST, 5, M, true);
        r(Items.AMETHYST_CLUSTER, 3, M, true);
        r(Items.LARGE_AMETHYST_BUD, 2, M, true);
        r(Items.MEDIUM_AMETHYST_BUD, 2, M, true);
        r(Items.SMALL_AMETHYST_BUD, 1, M, true);
        r(Items.AMETHYST_SHARD, 3, M, true);
        r(Items.POWDER_SNOW_BUCKET, 2, M, false);
        r(Items.MAGMA_BLOCK, 2, M, true);
        r(Items.GLOWSTONE, 3, C, true);

        // --- ORES (naturally generated, high value) ---
        r(Items.COAL_ORE, 2, M, true);
        r(Items.DEEPSLATE_COAL_ORE, 2, M, true);
        r(Items.IRON_ORE, 3, M, true);
        r(Items.DEEPSLATE_IRON_ORE, 3, M, true);
        r(Items.COPPER_ORE, 2, M, true);
        r(Items.DEEPSLATE_COPPER_ORE, 2, M, true);
        r(Items.GOLD_ORE, 5, M, true);
        r(Items.DEEPSLATE_GOLD_ORE, 5, M, true);
        r(Items.NETHER_GOLD_ORE, 3, M, true);
        r(Items.REDSTONE_ORE, 3, M, true);
        r(Items.DEEPSLATE_REDSTONE_ORE, 3, M, true);
        r(Items.LAPIS_ORE, 5, M, true);
        r(Items.DEEPSLATE_LAPIS_ORE, 5, M, true);
        r(Items.DIAMOND_ORE, 8, M, true);
        r(Items.DEEPSLATE_DIAMOND_ORE, 8, M, true);
        r(Items.EMERALD_ORE, 8, M, true);
        r(Items.DEEPSLATE_EMERALD_ORE, 8, M, true);
        r(Items.NETHER_QUARTZ_ORE, 3, M, true);
        r(Items.ANCIENT_DEBRIS, 12, M, true);

        // --- RAW MATERIALS ---
        r(Items.RAW_IRON, 3, M, true);
        r(Items.RAW_COPPER, 2, M, true);
        r(Items.RAW_GOLD, 5, M, true);
        r(Items.RAW_IRON_BLOCK, 0, M, false); // conversion
        r(Items.RAW_COPPER_BLOCK, 0, M, false);
        r(Items.RAW_GOLD_BLOCK, 0, M, false);

        // --- INGOTS, NUGGETS, GEMS ---
        r(Items.COAL, 2, M, true);
        r(Items.CHARCOAL, 2, M, false);
        r(Items.IRON_INGOT, 3, S, false);
        r(Items.IRON_NUGGET, 0, S, false); // conversion
        r(Items.COPPER_INGOT, 2, S, false);
        r(Items.GOLD_INGOT, 5, S, false);
        r(Items.GOLD_NUGGET, 0, S, false, AttributeBuff.LUCK); // conversion
        r(Items.NETHERITE_SCRAP, 12, S, false);
        r(Items.NETHERITE_INGOT, 20, S, false);
        r(Items.DIAMOND, 8, M, true);
        r(Items.EMERALD, 8, M, true);
        r(Items.LAPIS_LAZULI, 5, C, true);
        r(Items.REDSTONE, 3, M, false);
        r(Items.QUARTZ, 3, M, true);
        r(Items.FLINT, 2, M, true);
        r(Items.PRISMARINE_SHARD, 3, M, true);
        r(Items.PRISMARINE_CRYSTALS, 3, M, true);
        r(Items.ECHO_SHARD, 12, A, true);
        r(Items.NETHER_STAR, 20, C, false);
        r(Items.HEART_OF_THE_SEA, 20, A, true);
        r(Items.DRAGON_BREATH, 20, C, false);
        r(Items.DRAGON_EGG, 20, A, false);

        // --- WOOD (naturally generated logs, crafted planks/items) ---
        // Logs are natural, planks/crafted wood items are not
        String[] woodTypes = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK",
                "MANGROVE", "CHERRY", "PALE_OAK", "CRIMSON", "WARPED", "BAMBOO"};
        for (String w : woodTypes) {
            registerWoodItem(w + "_LOG", 1, true);
            registerWoodItem(w + "_WOOD", 1, true);
            registerWoodItem("STRIPPED_" + w + "_LOG", 1, false);
            registerWoodItem("STRIPPED_" + w + "_WOOD", 1, false);
            registerWoodItem(w + "_PLANKS", 1, false);
            registerWoodItem(w + "_SLAB", 1, false);
            registerWoodItem(w + "_STAIRS", 1, false);
            registerWoodItem(w + "_FENCE", 1, false);
            registerWoodItem(w + "_FENCE_GATE", 1, false);
            registerWoodItem(w + "_DOOR", 1, false);
            registerWoodItem(w + "_TRAPDOOR", 1, false);
            registerWoodItem(w + "_BUTTON", 1, false);
            registerWoodItem(w + "_PRESSURE_PLATE", 1, false);
            registerWoodItem(w + "_SIGN", 1, false);
            registerWoodItem(w + "_HANGING_SIGN", 1, false);
            registerWoodItem(w + "_BOAT", 2, false);
            registerWoodItem(w + "_CHEST_BOAT", 3, false);
            registerWoodItem(w + "_SHELF", 1, false);
        }
        // Stems (crimson/warped already handled as LOG/WOOD above)
        registerWoodItem("CRIMSON_STEM", 1, true);
        registerWoodItem("WARPED_STEM", 1, true);
        registerWoodItem("STRIPPED_CRIMSON_STEM", 1, false);
        registerWoodItem("STRIPPED_WARPED_STEM", 1, false);
        registerWoodItem("CRIMSON_HYPHAE", 1, true);
        registerWoodItem("WARPED_HYPHAE", 1, true);
        registerWoodItem("STRIPPED_CRIMSON_HYPHAE", 1, false);
        registerWoodItem("STRIPPED_WARPED_HYPHAE", 1, false);
        registerWoodItem("BAMBOO_BLOCK", 1, true);
        registerWoodItem("STRIPPED_BAMBOO_BLOCK", 1, false);
        registerWoodItem("BAMBOO_MOSAIC", 1, false);
        registerWoodItem("BAMBOO_MOSAIC_SLAB", 1, false);
        registerWoodItem("BAMBOO_MOSAIC_STAIRS", 1, false);
        registerWoodItem("BAMBOO_RAFT", 2, false);
        registerWoodItem("BAMBOO_CHEST_RAFT", 3, false);

        // Leaves & saplings
        String[] treeTypes = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK",
                "MANGROVE", "CHERRY", "PALE_OAK", "AZALEA"};
        for (String t : treeTypes) {
            registerWoodItem(t + "_LEAVES", 1, true);
            registerWoodItem(t + "_SAPLING", 1, true);
        }
        r(Items.FLOWERING_AZALEA_LEAVES, 1, F, true);
        r(Items.AZALEA, 1, F, true);
        r(Items.FLOWERING_AZALEA, 1, F, true);
        r(Items.MANGROVE_PROPAGULE, 1, F, true);
        r(Items.STICK, 1, M, false);
        r(Items.BAMBOO, 1, F, true);

        // --- STONE VARIANTS (crafted, not natural) ---
        r(Items.STONE_SLAB, 1, M, false);
        r(Items.STONE_STAIRS, 1, M, false);
        r(Items.STONE_BUTTON, 1, M, false);
        r(Items.STONE_PRESSURE_PLATE, 1, M, false);
        r(Items.COBBLESTONE_SLAB, 1, M, false);
        r(Items.COBBLESTONE_STAIRS, 1, M, false);
        r(Items.COBBLESTONE_WALL, 1, M, false);
        r(Items.MOSSY_COBBLESTONE, 1, M, true);
        r(Items.MOSSY_COBBLESTONE_SLAB, 1, M, false);
        r(Items.MOSSY_COBBLESTONE_STAIRS, 1, M, false);
        r(Items.MOSSY_COBBLESTONE_WALL, 1, M, false);
        r(Items.SMOOTH_STONE, 1, M, false);
        r(Items.SMOOTH_STONE_SLAB, 1, M, false);
        r(Items.STONE_BRICKS, 1, M, true);
        r(Items.STONE_BRICK_SLAB, 1, M, false);
        r(Items.STONE_BRICK_STAIRS, 1, M, false);
        r(Items.STONE_BRICK_WALL, 1, M, false);
        r(Items.MOSSY_STONE_BRICKS, 1, M, true);
        r(Items.MOSSY_STONE_BRICK_SLAB, 1, M, false);
        r(Items.MOSSY_STONE_BRICK_STAIRS, 1, M, false);
        r(Items.MOSSY_STONE_BRICK_WALL, 1, M, false);
        r(Items.CRACKED_STONE_BRICKS, 1, M, true);
        r(Items.CHISELED_STONE_BRICKS, 2, M, false);
        r(Items.BRICKS, 2, M, false);
        r(Items.BRICK_SLAB, 1, M, false);
        r(Items.BRICK_STAIRS, 1, M, false);
        r(Items.BRICK_WALL, 1, M, false);
        r(Items.BRICK, 2, M, false);
        r(Items.NETHER_BRICKS, 2, M, true);
        r(Items.NETHER_BRICK, 2, M, false);
        r(Items.NETHER_BRICK_SLAB, 1, M, false);
        r(Items.NETHER_BRICK_STAIRS, 1, M, false);
        r(Items.NETHER_BRICK_WALL, 1, M, false);
        r(Items.NETHER_BRICK_FENCE, 1, M, false);
        r(Items.RED_NETHER_BRICKS, 2, M, false);
        r(Items.RED_NETHER_BRICK_SLAB, 1, M, false);
        r(Items.RED_NETHER_BRICK_STAIRS, 1, M, false);
        r(Items.RED_NETHER_BRICK_WALL, 1, M, false);
        r(Items.SANDSTONE_SLAB, 1, M, false);
        r(Items.SANDSTONE_STAIRS, 1, M, false);
        r(Items.SANDSTONE_WALL, 1, M, false);
        r(Items.CUT_SANDSTONE, 1, M, false);
        // CUT_SANDSTONE_SLAB removed in 1.21.11
        r(Items.CHISELED_SANDSTONE, 1, M, false);
        r(Items.SMOOTH_SANDSTONE, 1, M, false);
        r(Items.SMOOTH_SANDSTONE_SLAB, 1, M, false);
        r(Items.SMOOTH_SANDSTONE_STAIRS, 1, M, false);
        r(Items.RED_SANDSTONE_SLAB, 1, M, false);
        r(Items.RED_SANDSTONE_STAIRS, 1, M, false);
        r(Items.RED_SANDSTONE_WALL, 1, M, false);
        r(Items.CUT_RED_SANDSTONE, 1, M, false);
        r(Items.CUT_RED_SANDSTONE_SLAB, 1, M, false);
        r(Items.CHISELED_RED_SANDSTONE, 1, M, false);
        r(Items.SMOOTH_RED_SANDSTONE, 1, M, false);
        r(Items.SMOOTH_RED_SANDSTONE_SLAB, 1, M, false);
        r(Items.SMOOTH_RED_SANDSTONE_STAIRS, 1, M, false);

        // Deepslate variants
        r(Items.DEEPSLATE_BRICKS, 2, M, false);
        r(Items.DEEPSLATE_BRICK_SLAB, 1, M, false);
        r(Items.DEEPSLATE_BRICK_STAIRS, 1, M, false);
        r(Items.DEEPSLATE_BRICK_WALL, 1, M, false);
        r(Items.CRACKED_DEEPSLATE_BRICKS, 2, M, false);
        r(Items.DEEPSLATE_TILES, 2, M, false);
        r(Items.DEEPSLATE_TILE_SLAB, 1, M, false);
        r(Items.DEEPSLATE_TILE_STAIRS, 1, M, false);
        r(Items.DEEPSLATE_TILE_WALL, 1, M, false);
        r(Items.CRACKED_DEEPSLATE_TILES, 2, M, false);
        r(Items.CHISELED_DEEPSLATE, 2, M, false);
        r(Items.COBBLED_DEEPSLATE_SLAB, 1, M, false);
        r(Items.COBBLED_DEEPSLATE_STAIRS, 1, M, false);
        r(Items.COBBLED_DEEPSLATE_WALL, 1, M, false);

        // Tuff variants
        r(Items.TUFF_SLAB, 1, M, false);
        r(Items.TUFF_STAIRS, 1, M, false);
        r(Items.TUFF_WALL, 1, M, false);
        r(Items.CHISELED_TUFF, 1, M, false);
        r(Items.TUFF_BRICKS, 1, M, false);
        r(Items.TUFF_BRICK_SLAB, 1, M, false);
        r(Items.TUFF_BRICK_STAIRS, 1, M, false);
        r(Items.TUFF_BRICK_WALL, 1, M, false);
        r(Items.CHISELED_TUFF_BRICKS, 1, M, false);
        r(Items.POLISHED_TUFF_SLAB, 1, M, false);
        r(Items.POLISHED_TUFF_STAIRS, 1, M, false);
        r(Items.POLISHED_TUFF_WALL, 1, M, false);

        // Blackstone variants
        r(Items.POLISHED_BLACKSTONE_SLAB, 1, M, false);
        r(Items.POLISHED_BLACKSTONE_STAIRS, 1, M, false);
        r(Items.POLISHED_BLACKSTONE_WALL, 1, M, false);
        r(Items.POLISHED_BLACKSTONE_BUTTON, 1, M, false);
        r(Items.POLISHED_BLACKSTONE_PRESSURE_PLATE, 1, M, false);
        r(Items.POLISHED_BLACKSTONE_BRICKS, 1, M, true);
        r(Items.POLISHED_BLACKSTONE_BRICK_SLAB, 1, M, false);
        r(Items.POLISHED_BLACKSTONE_BRICK_STAIRS, 1, M, false);
        r(Items.POLISHED_BLACKSTONE_BRICK_WALL, 1, M, false);
        r(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS, 1, M, false);
        r(Items.CHISELED_POLISHED_BLACKSTONE, 1, M, false);
        r(Items.GILDED_BLACKSTONE, 3, M, true);
        r(Items.BLACKSTONE_SLAB, 1, M, false);
        r(Items.BLACKSTONE_STAIRS, 1, M, false);
        r(Items.BLACKSTONE_WALL, 1, M, false);

        // Granite/Diorite/Andesite variants
        r(Items.GRANITE_SLAB, 1, M, false);
        r(Items.GRANITE_STAIRS, 1, M, false);
        r(Items.GRANITE_WALL, 1, M, false);
        r(Items.POLISHED_GRANITE_SLAB, 1, M, false);
        r(Items.POLISHED_GRANITE_STAIRS, 1, M, false);
        r(Items.DIORITE_SLAB, 1, M, false);
        r(Items.DIORITE_STAIRS, 1, M, false);
        r(Items.DIORITE_WALL, 1, M, false);
        r(Items.POLISHED_DIORITE_SLAB, 1, M, false);
        r(Items.POLISHED_DIORITE_STAIRS, 1, M, false);
        r(Items.ANDESITE_SLAB, 1, M, false);
        r(Items.ANDESITE_STAIRS, 1, M, false);
        r(Items.ANDESITE_WALL, 1, M, false);
        r(Items.POLISHED_ANDESITE_SLAB, 1, M, false);
        r(Items.POLISHED_ANDESITE_STAIRS, 1, M, false);

        // Prismarine variants
        r(Items.PRISMARINE_SLAB, 1, M, false);
        r(Items.PRISMARINE_STAIRS, 1, M, false);
        r(Items.PRISMARINE_WALL, 1, M, false);
        r(Items.PRISMARINE_BRICK_SLAB, 1, M, false);
        r(Items.PRISMARINE_BRICK_STAIRS, 1, M, false);
        r(Items.DARK_PRISMARINE_SLAB, 1, M, false);
        r(Items.DARK_PRISMARINE_STAIRS, 1, M, false);

        // End stone variants
        r(Items.END_STONE_BRICKS, 2, M, false);
        r(Items.END_STONE_BRICK_SLAB, 1, M, false);
        r(Items.END_STONE_BRICK_STAIRS, 1, M, false);
        r(Items.END_STONE_BRICK_WALL, 1, M, false);
        r(Items.PURPUR_BLOCK, 3, M, false);
        r(Items.PURPUR_PILLAR, 3, M, false);
        r(Items.PURPUR_SLAB, 2, M, false);
        r(Items.PURPUR_STAIRS, 2, M, false);
        r(Items.END_ROD, 3, A, false);

        // --- STORAGE BLOCKS (crafted, NOT natural) ---
        r(Items.IRON_BLOCK, 0, S, false);
        r(Items.GOLD_BLOCK, 0, S, false);
        r(Items.DIAMOND_BLOCK, 0, M, false);
        r(Items.EMERALD_BLOCK, 0, M, false);
        r(Items.LAPIS_BLOCK, 0, C, false);
        r(Items.REDSTONE_BLOCK, 0, M, false);
        r(Items.NETHERITE_BLOCK, 0, S, false);
        r(Items.COPPER_BLOCK, 0, S, false);
        r(Items.CUT_COPPER, 2, S, false);
        r(Items.CUT_COPPER_SLAB, 1, S, false);
        r(Items.CUT_COPPER_STAIRS, 1, S, false);
        r(Items.EXPOSED_COPPER, 2, S, true);
        r(Items.WEATHERED_COPPER, 2, S, true);
        r(Items.OXIDIZED_COPPER, 2, S, true);
        r(Items.WAXED_COPPER_BLOCK, 2, S, false);
        r(Items.COAL_BLOCK, 0, M, false);
        r(Items.QUARTZ_BLOCK, 0, M, false);
        r(Items.QUARTZ_SLAB, 1, M, false);
        r(Items.QUARTZ_STAIRS, 1, M, false);
        r(Items.QUARTZ_PILLAR, 1, M, false);
        r(Items.CHISELED_QUARTZ_BLOCK, 1, M, false);
        r(Items.SMOOTH_QUARTZ, 1, M, false);
        r(Items.SMOOTH_QUARTZ_SLAB, 1, M, false);
        r(Items.SMOOTH_QUARTZ_STAIRS, 1, M, false);

        // --- COLORED BLOCKS ---
        String[] colors = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME",
                "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"};
        for (String c2 : colors) {
            registerColorItem(c2 + "_WOOL", 1, F, false);
            registerColorItem(c2 + "_CARPET", 1, F, false);
            registerColorItem(c2 + "_BED", 2, F, false);
            registerColorItem(c2 + "_TERRACOTTA", 1, M, false);
            registerColorItem(c2 + "_GLAZED_TERRACOTTA", 2, M, false);
            registerColorItem(c2 + "_CONCRETE", 1, M, false);
            registerColorItem(c2 + "_CONCRETE_POWDER", 1, M, false);
            registerColorItem(c2 + "_STAINED_GLASS", 1, M, false);
            registerColorItem(c2 + "_STAINED_GLASS_PANE", 1, M, false);
            registerColorItem(c2 + "_BANNER", 2, F, false);
            registerColorItem(c2 + "_CANDLE", 1, F, false);
            registerColorItem(c2 + "_SHULKER_BOX", 10, M, false);
            registerColorItem(c2 + "_DYE", 1, F, false);
        }
        r(Items.TERRACOTTA, 1, M, true);
        r(Items.GLASS, 1, M, false);
        r(Items.GLASS_PANE, 1, M, false);
        r(Items.TINTED_GLASS, 3, M, false);
        r(Items.CANDLE, 1, F, false);
        r(Items.SHULKER_BOX, 10, M, false);

        // --- TOOLS ---
        r(Items.WOODEN_SWORD, 1, S, false);
        r(Items.WOODEN_PICKAXE, 1, M, false);
        r(Items.WOODEN_AXE, 1, M, false);
        r(Items.WOODEN_SHOVEL, 1, M, false);
        r(Items.WOODEN_HOE, 1, F, false);
        r(Items.STONE_SWORD, 2, S, false);
        r(Items.STONE_PICKAXE, 2, M, false);
        r(Items.STONE_AXE, 2, M, false);
        r(Items.STONE_SHOVEL, 2, M, false);
        r(Items.STONE_HOE, 2, F, false);
        r(Items.IRON_SWORD, 5, S, false);
        r(Items.IRON_PICKAXE, 5, M, false);
        r(Items.IRON_AXE, 5, M, false);
        r(Items.IRON_SHOVEL, 5, M, false);
        r(Items.IRON_HOE, 5, F, false);
        r(Items.GOLDEN_SWORD, 5, S, false);
        r(Items.GOLDEN_PICKAXE, 5, M, false);
        r(Items.GOLDEN_AXE, 5, M, false);
        r(Items.GOLDEN_SHOVEL, 5, M, false);
        r(Items.GOLDEN_HOE, 5, F, false);
        r(Items.DIAMOND_SWORD, 10, S, false);
        r(Items.DIAMOND_PICKAXE, 10, M, false);
        r(Items.DIAMOND_AXE, 10, M, false);
        r(Items.DIAMOND_SHOVEL, 10, M, false);
        r(Items.DIAMOND_HOE, 10, F, false);
        r(Items.NETHERITE_SWORD, 20, S, false);
        r(Items.NETHERITE_PICKAXE, 20, M, false);
        r(Items.NETHERITE_AXE, 20, M, false);
        r(Items.NETHERITE_SHOVEL, 20, M, false);
        r(Items.NETHERITE_HOE, 20, F, false);
        r(Items.BOW, 3, S, false);
        r(Items.CROSSBOW, 5, S, false);
        r(Items.SHIELD, 5, S, false);
        r(Items.TRIDENT, 12, S, true);
        r(Items.MACE, 20, S, false);
        r(Items.FISHING_ROD, 3, F, false);
        r(Items.SHEARS, 3, M, false);
        r(Items.FLINT_AND_STEEL, 3, M, false);
        r(Items.LEAD, 2, F, false);
        r(Items.NAME_TAG, 5, F, true);
        r(Items.SPYGLASS, 5, A, false);
        r(Items.BRUSH, 3, A, false);

        // --- ARMOR ---
        r(Items.LEATHER_HELMET, 3, S, false);
        r(Items.LEATHER_CHESTPLATE, 3, S, false);
        r(Items.LEATHER_LEGGINGS, 3, S, false);
        r(Items.LEATHER_BOOTS, 3, S, false);
        r(Items.CHAINMAIL_HELMET, 5, S, false);
        r(Items.CHAINMAIL_CHESTPLATE, 5, S, false);
        r(Items.CHAINMAIL_LEGGINGS, 5, S, false);
        r(Items.CHAINMAIL_BOOTS, 5, S, false);
        r(Items.IRON_HELMET, 5, S, false);
        r(Items.IRON_CHESTPLATE, 5, S, false);
        r(Items.IRON_LEGGINGS, 5, S, false);
        r(Items.IRON_BOOTS, 5, S, false);
        r(Items.GOLDEN_HELMET, 5, S, false);
        r(Items.GOLDEN_CHESTPLATE, 5, S, false);
        r(Items.GOLDEN_LEGGINGS, 5, S, false);
        r(Items.GOLDEN_BOOTS, 5, S, false);
        r(Items.DIAMOND_HELMET, 10, S, false);
        r(Items.DIAMOND_CHESTPLATE, 10, S, false);
        r(Items.DIAMOND_LEGGINGS, 10, S, false);
        r(Items.DIAMOND_BOOTS, 10, S, false);
        r(Items.NETHERITE_HELMET, 20, S, false);
        r(Items.NETHERITE_CHESTPLATE, 20, S, false);
        r(Items.NETHERITE_LEGGINGS, 20, S, false);
        r(Items.NETHERITE_BOOTS, 20, S, false);
        r(Items.TURTLE_HELMET, 8, S, false);
        r(Items.LEATHER_HORSE_ARMOR, 3, S, false);
        r(Items.IRON_HORSE_ARMOR, 5, S, true);
        r(Items.GOLDEN_HORSE_ARMOR, 8, S, true);
        r(Items.DIAMOND_HORSE_ARMOR, 12, S, true);
        r(Items.WOLF_ARMOR, 5, S, false);
        r(Items.ELYTRA, 20, A, false);
        r(Items.SADDLE, 5, A, true);
        r(Items.HEAVY_CORE, 15, S, true);

        // --- FOOD & CROPS ---
        r(Items.WHEAT_SEEDS, 1, F, true);
        r(Items.WHEAT, 2, F, false);
        r(Items.BREAD, 3, F, false);
        r(Items.APPLE, 2, F, true, AttributeBuff.HEALTH);
        r(Items.GOLDEN_APPLE, 8, F, false, AttributeBuff.ARMOR_TOUGHNESS);
        r(Items.ENCHANTED_GOLDEN_APPLE, 20, C, true, AttributeBuff.KNOCKBACK_RESISTANCE);
        r(Items.GOLDEN_CARROT, 5, F, false, AttributeBuff.LUCK);
        r(Items.CARROT, 2, F, true, AttributeBuff.HEALTH);
        r(Items.POTATO, 2, F, true, AttributeBuff.ARMOR);
        r(Items.BAKED_POTATO, 3, F, false, AttributeBuff.ARMOR);
        r(Items.POISONOUS_POTATO, 1, F, true);
        r(Items.MELON_SLICE, 1, F, true, AttributeBuff.SPEED);
        r(Items.MELON, 0, F, true, AttributeBuff.SPEED); // conversion
        r(Items.PUMPKIN, 2, F, true, AttributeBuff.STEP_HEIGHT);
        r(Items.CARVED_PUMPKIN, 2, F, false);
        r(Items.JACK_O_LANTERN, 2, F, false);
        r(Items.SUGAR_CANE, 1, F, true);
        r(Items.SUGAR, 1, F, false, AttributeBuff.SPEED);
        r(Items.BEETROOT, 2, F, true, AttributeBuff.BURNING_TIME);
        r(Items.BEETROOT_SEEDS, 1, F, true);
        r(Items.BEETROOT_SOUP, 3, F, false, AttributeBuff.ARMOR);
        r(Items.MUSHROOM_STEW, 3, F, false, AttributeBuff.ARMOR_TOUGHNESS);
        r(Items.RABBIT_STEW, 5, F, false, AttributeBuff.JUMP);
        r(Items.SUSPICIOUS_STEW, 3, F, false, AttributeBuff.LUCK);
        r(Items.CAKE, 5, F, false, AttributeBuff.HEALTH);
        r(Items.PUMPKIN_PIE, 3, F, false, AttributeBuff.STEP_HEIGHT);
        r(Items.COOKIE, 2, F, false, AttributeBuff.SNEAKING_SPEED);
        r(Items.COCOA_BEANS, 2, F, true, AttributeBuff.MINING_SPEED);
        r(Items.SWEET_BERRIES, 1, F, true, AttributeBuff.ATTACK_SPEED);
        r(Items.GLOW_BERRIES, 2, F, true, AttributeBuff.SAFE_FALL);
        r(Items.TORCHFLOWER_SEEDS, 3, F, false);
        r(Items.PITCHER_POD, 3, F, false);
        r(Items.TORCHFLOWER, 3, F, false);
        r(Items.PITCHER_PLANT, 3, F, false);
        r(Items.DRIED_KELP, 1, F, false, AttributeBuff.UNDERWATER_MINING);
        r(Items.KELP, 1, F, true, AttributeBuff.OXYGEN);
        r(Items.CACTUS, 1, F, true);
        r(Items.CHORUS_FRUIT, 3, F, true, AttributeBuff.REACH);
        r(Items.CHORUS_FLOWER, 3, F, true);
        r(Items.POPPED_CHORUS_FRUIT, 3, F, false, AttributeBuff.ENTITY_REACH);
        r(Items.NETHER_WART, 3, C, true);
        r(Items.GLISTERING_MELON_SLICE, 5, C, false, AttributeBuff.ENTITY_REACH);
        r(Items.HONEY_BOTTLE, 3, F, false, AttributeBuff.FALL_DAMAGE);
        r(Items.HONEYCOMB, 2, F, false, AttributeBuff.ARMOR_TOUGHNESS);
        r(Items.DRIED_KELP_BLOCK, 0, F, false); // conversion

        // Raw/cooked meats — raw gives buff, cooked is the output
        r(Items.BEEF, 2, F, false, AttributeBuff.DAMAGE);
        r(Items.COOKED_BEEF, 3, F, false, AttributeBuff.DAMAGE);
        r(Items.PORKCHOP, 2, F, false, AttributeBuff.KNOCKBACK_RESISTANCE);
        r(Items.COOKED_PORKCHOP, 3, F, false, AttributeBuff.KNOCKBACK_RESISTANCE);
        r(Items.MUTTON, 2, F, false, AttributeBuff.ARMOR);
        r(Items.COOKED_MUTTON, 3, F, false, AttributeBuff.ARMOR);
        r(Items.CHICKEN, 2, F, false, AttributeBuff.SPEED);
        r(Items.COOKED_CHICKEN, 3, F, false, AttributeBuff.SPEED);
        r(Items.RABBIT, 2, F, false, AttributeBuff.JUMP);
        r(Items.COOKED_RABBIT, 3, F, false, AttributeBuff.JUMP);
        r(Items.COD, 2, F, true, AttributeBuff.LUCK);
        r(Items.COOKED_COD, 3, F, false, AttributeBuff.OXYGEN);
        r(Items.SALMON, 2, F, true, AttributeBuff.UNDERWATER_MINING);
        r(Items.COOKED_SALMON, 3, F, false, AttributeBuff.REACH);
        r(Items.TROPICAL_FISH, 2, F, true, AttributeBuff.ENTITY_REACH);
        r(Items.PUFFERFISH, 2, F, true, AttributeBuff.OXYGEN);
        r(Items.ROTTEN_FLESH, 1, F, false);
        r(Items.SPIDER_EYE, 2, C, false, AttributeBuff.SNEAKING_SPEED);
        r(Items.FERMENTED_SPIDER_EYE, 3, C, false, AttributeBuff.MINING_SPEED);

        // Eggs/breeding
        r(Items.EGG, 1, F, false, AttributeBuff.JUMP);
        r(Items.BROWN_EGG, 1, F, false, AttributeBuff.JUMP);
        r(Items.BLUE_EGG, 1, F, false, AttributeBuff.JUMP);
        r(Items.TURTLE_EGG, 3, F, false);
        r(Items.SNIFFER_EGG, 5, F, true);
        r(Items.FROGSPAWN, 2, F, false);
        r(Items.ARMADILLO_SCUTE, 3, S, false, AttributeBuff.ARMOR_TOUGHNESS);
        r(Items.TURTLE_SCUTE, 3, S, false, AttributeBuff.OXYGEN);

        // --- MOB DROPS ---
        r(Items.LEATHER, 2, F, false, AttributeBuff.BURNING_TIME);
        r(Items.RABBIT_HIDE, 1, F, false);
        r(Items.RABBIT_FOOT, 3, F, false, AttributeBuff.LUCK);
        r(Items.FEATHER, 1, F, false, AttributeBuff.SAFE_FALL);
        r(Items.STRING, 1, F, false);
        r(Items.BONE, 2, F, false);
        r(Items.BONE_MEAL, 1, F, false);
        r(Items.BONE_BLOCK, 0, F, false); // conversion
        r(Items.SLIME_BALL, 2, M, false);
        r(Items.SLIME_BLOCK, 0, M, false); // conversion
        r(Items.HONEY_BLOCK, 0, F, false, AttributeBuff.FALL_DAMAGE); // conversion
        r(Items.HONEYCOMB_BLOCK, 0, F, false, AttributeBuff.ARMOR_TOUGHNESS); // conversion
        r(Items.INK_SAC, 1, F, false);
        r(Items.GLOW_INK_SAC, 2, F, false);
        r(Items.GUNPOWDER, 3, M, false);
        r(Items.PHANTOM_MEMBRANE, 5, A, false);
        r(Items.GHAST_TEAR, 5, C, false, AttributeBuff.HEALTH);
        r(Items.BLAZE_ROD, 5, C, false);
        r(Items.BLAZE_POWDER, 3, C, false, AttributeBuff.DAMAGE);
        r(Items.MAGMA_CREAM, 3, C, false);
        r(Items.ENDER_PEARL, 5, A, false);
        r(Items.SHULKER_SHELL, 12, A, false);
        r(Items.NETHER_WART_BLOCK, 0, C, false); // conversion
        r(Items.WARPED_WART_BLOCK, 1, C, true);
        r(Items.WITHER_SKELETON_SKULL, 8, A, false);
        r(Items.SKELETON_SKULL, 3, A, false);
        r(Items.ZOMBIE_HEAD, 3, A, false);
        r(Items.CREEPER_HEAD, 3, A, false);
        r(Items.PIGLIN_HEAD, 3, A, false);
        r(Items.GOAT_HORN, 5, A, false);
        r(Items.BREEZE_ROD, 5, A, true);
        r(Items.WIND_CHARGE, 3, A, false);
        r(Items.TRIAL_KEY, 5, A, true);
        r(Items.OMINOUS_TRIAL_KEY, 8, A, true);
        r(Items.OMINOUS_BOTTLE, 5, A, true);

        // --- REDSTONE ---
        r(Items.REDSTONE_TORCH, 2, M, false);
        r(Items.REDSTONE_LAMP, 3, M, false);
        r(Items.REPEATER, 3, M, false);
        r(Items.COMPARATOR, 3, M, false);
        r(Items.PISTON, 4, M, false);
        r(Items.STICKY_PISTON, 5, M, false);
        r(Items.OBSERVER, 4, M, false);
        r(Items.HOPPER, 5, M, false);
        r(Items.DROPPER, 3, M, false);
        r(Items.DISPENSER, 4, M, false);
        r(Items.LEVER, 1, M, false);
        r(Items.TRIPWIRE_HOOK, 2, M, false);
        r(Items.DAYLIGHT_DETECTOR, 3, M, false);
        r(Items.TARGET, 3, M, false);
        r(Items.LIGHTNING_ROD, 3, S, false);
        r(Items.TNT, 5, M, false);
        r(Items.NOTE_BLOCK, 2, M, false);
        r(Items.JUKEBOX, 8, M, false);
        r(Items.CRAFTER, 5, M, false);

        // --- RAILS & TRANSPORT ---
        r(Items.RAIL, 3, M, false);
        r(Items.POWERED_RAIL, 5, M, false);
        r(Items.DETECTOR_RAIL, 5, M, false);
        r(Items.ACTIVATOR_RAIL, 5, M, false);
        r(Items.MINECART, 5, M, false);
        r(Items.CHEST_MINECART, 6, M, false);
        r(Items.HOPPER_MINECART, 8, M, false);
        r(Items.TNT_MINECART, 8, M, false);
        r(Items.FURNACE_MINECART, 6, M, false);

        // --- FUNCTIONAL BLOCKS ---
        r(Items.CRAFTING_TABLE, 2, M, false);
        r(Items.FURNACE, 3, S, false);
        r(Items.BLAST_FURNACE, 5, S, false);
        r(Items.SMOKER, 4, S, false);
        r(Items.ANVIL, 8, S, false);
        r(Items.CHIPPED_ANVIL, 6, S, false);
        r(Items.DAMAGED_ANVIL, 4, S, false);
        r(Items.GRINDSTONE, 4, S, false);
        r(Items.SMITHING_TABLE, 5, S, false);
        r(Items.STONECUTTER, 3, M, false);
        r(Items.LOOM, 3, F, false);
        r(Items.CARTOGRAPHY_TABLE, 3, A, false);
        r(Items.FLETCHING_TABLE, 3, A, false);
        r(Items.COMPOSTER, 2, F, false);
        r(Items.BARREL, 3, M, false);
        r(Items.CHEST, 2, M, false);
        r(Items.TRAPPED_CHEST, 3, M, false);
        r(Items.ENDER_CHEST, 8, A, false);
        r(Items.LECTERN, 4, C, false);
        r(Items.BOOKSHELF, 4, C, false);
        r(Items.CHISELED_BOOKSHELF, 4, C, false);
        r(Items.ENCHANTING_TABLE, 12, C, false);
        r(Items.BREWING_STAND, 8, C, false);
        r(Items.CAULDRON, 5, S, false);
        r(Items.BELL, 8, A, true);
        r(Items.BEACON, 20, C, false);
        r(Items.CONDUIT, 20, A, false);
        r(Items.LODESTONE, 12, A, false);
        r(Items.RESPAWN_ANCHOR, 10, A, false);
        r(Items.END_CRYSTAL, 12, C, false);
        r(Items.END_PORTAL_FRAME, 0, A, true);
        r(Items.FLOWER_POT, 1, F, false);
        r(Items.ARMOR_STAND, 3, S, false);
        r(Items.ITEM_FRAME, 2, M, false);
        r(Items.GLOW_ITEM_FRAME, 3, M, false);
        r(Items.PAINTING, 2, F, false);
        r(Items.DECORATED_POT, 2, F, false);

        // --- LIGHT SOURCES ---
        r(Items.TORCH, 1, M, false);
        r(Items.SOUL_TORCH, 1, M, false);
        r(Items.LANTERN, 2, M, false);
        r(Items.SOUL_LANTERN, 2, M, false);
        r(Items.CAMPFIRE, 3, A, false);
        r(Items.SOUL_CAMPFIRE, 3, A, false);
        r(Items.IRON_CHAIN, 2, S, false);
        r(Items.SHROOMLIGHT, 2, F, true);
        r(Items.GLOWSTONE_DUST, 2, C, false);
        r(Items.SEA_PICKLE, 1, F, true);
        r(Items.JACK_O_LANTERN, 2, F, false);
        // FROGLIGHT base removed — individual variants below
        r(Items.OCHRE_FROGLIGHT, 3, F, false);
        r(Items.VERDANT_FROGLIGHT, 3, F, false);
        r(Items.PEARLESCENT_FROGLIGHT, 3, F, false);

        // --- BOOKS & CLERIC ITEMS ---
        r(Items.BOOK, 2, C, false);
        r(Items.WRITABLE_BOOK, 3, C, false);
        r(Items.WRITTEN_BOOK, 3, C, false);
        r(Items.ENCHANTED_BOOK, 8, C, false);
        r(Items.PAPER, 1, C, false);
        r(Items.MAP, 3, A, false);
        r(Items.FILLED_MAP, 3, A, false);
        r(Items.COMPASS, 5, A, false);
        r(Items.RECOVERY_COMPASS, 8, A, false);
        r(Items.CLOCK, 5, A, false);
        r(Items.ENDER_EYE, 8, C, false);
        r(Items.FIRE_CHARGE, 2, C, false);
        r(Items.FIREWORK_ROCKET, 2, M, false);
        r(Items.FIREWORK_STAR, 2, M, false);
        r(Items.EXPERIENCE_BOTTLE, 5, C, false);
        r(Items.GLASS_BOTTLE, 1, C, false);
        r(Items.TOTEM_OF_UNDYING, 20, C, false);

        // --- POTIONS & ARROWS ---
        r(Items.POTION, 3, C, false);
        r(Items.SPLASH_POTION, 4, C, false);
        r(Items.LINGERING_POTION, 5, C, false);
        r(Items.TIPPED_ARROW, 3, C, false);
        r(Items.ARROW, 2, A, false);
        r(Items.SPECTRAL_ARROW, 3, A, false);

        // --- BUCKETS ---
        r(Items.BUCKET, 3, M, false);
        r(Items.WATER_BUCKET, 3, M, false);
        r(Items.LAVA_BUCKET, 5, M, false);
        r(Items.MILK_BUCKET, 3, F, false);
        r(Items.AXOLOTL_BUCKET, 5, F, false);
        r(Items.COD_BUCKET, 3, F, false);
        r(Items.SALMON_BUCKET, 3, F, false);
        r(Items.TROPICAL_FISH_BUCKET, 3, F, false);
        r(Items.PUFFERFISH_BUCKET, 3, F, false);
        r(Items.TADPOLE_BUCKET, 3, F, false);

        // --- FLOWERS & PLANTS ---
        r(Items.DANDELION, 1, F, true, AttributeBuff.HEALTH);           // vanilla stew: saturation
        r(Items.POPPY, 1, F, true, AttributeBuff.SPEED);               // vanilla stew: night vision
        r(Items.BLUE_ORCHID, 1, F, true, AttributeBuff.OXYGEN);        // vanilla stew: saturation
        r(Items.ALLIUM, 1, F, true, AttributeBuff.BURNING_TIME);       // vanilla stew: fire resistance
        r(Items.AZURE_BLUET, 1, F, true, AttributeBuff.SNEAKING_SPEED);// vanilla stew: blindness
        r(Items.RED_TULIP, 1, F, true, AttributeBuff.ARMOR);           // vanilla stew: weakness
        r(Items.ORANGE_TULIP, 1, F, true, AttributeBuff.ARMOR);        // vanilla stew: weakness
        r(Items.WHITE_TULIP, 1, F, true, AttributeBuff.ARMOR);         // vanilla stew: weakness
        r(Items.PINK_TULIP, 1, F, true, AttributeBuff.ARMOR);          // vanilla stew: weakness
        r(Items.OXEYE_DAISY, 1, F, true, AttributeBuff.HEALTH);        // vanilla stew: regeneration
        r(Items.CORNFLOWER, 1, F, true, AttributeBuff.JUMP);           // vanilla stew: jump boost
        r(Items.LILY_OF_THE_VALLEY, 1, F, true, AttributeBuff.LUCK);   // vanilla stew: poison
        r(Items.WITHER_ROSE, 3, C, false, AttributeBuff.DAMAGE);       // vanilla stew: wither
        r(Items.SUNFLOWER, 1, F, true);
        r(Items.LILAC, 1, F, true);
        r(Items.ROSE_BUSH, 1, F, true);
        r(Items.PEONY, 1, F, true);
        r(Items.PINK_PETALS, 1, F, true);
        r(Items.OPEN_EYEBLOSSOM, 1, F, true);
        r(Items.CLOSED_EYEBLOSSOM, 1, F, true);
        r(Items.SHORT_GRASS, 1, F, true);
        r(Items.TALL_GRASS, 1, F, true);
        r(Items.FERN, 1, F, true);
        r(Items.LARGE_FERN, 1, F, true);
        r(Items.DEAD_BUSH, 1, F, true);
        r(Items.VINE, 1, F, true);
        r(Items.LILY_PAD, 1, F, true);
        r(Items.SPORE_BLOSSOM, 2, F, true);
        r(Items.HANGING_ROOTS, 1, F, true);
        r(Items.BIG_DRIPLEAF, 1, F, true);
        r(Items.SMALL_DRIPLEAF, 1, F, true);
        r(Items.MOSS_CARPET, 1, F, true);
        r(Items.PALE_MOSS_BLOCK, 1, F, true);
        r(Items.PALE_MOSS_CARPET, 1, F, true);
        r(Items.PALE_HANGING_MOSS, 1, F, true);
        r(Items.BROWN_MUSHROOM, 1, F, true, AttributeBuff.SAFE_FALL);
        r(Items.RED_MUSHROOM, 1, F, true, AttributeBuff.MINING_SPEED);
        r(Items.BROWN_MUSHROOM_BLOCK, 1, F, true);
        r(Items.RED_MUSHROOM_BLOCK, 1, F, true);
        r(Items.MUSHROOM_STEM, 1, F, true);
        r(Items.CRIMSON_FUNGUS, 1, F, true);
        r(Items.WARPED_FUNGUS, 1, F, true);
        r(Items.CRIMSON_ROOTS, 1, F, true);
        r(Items.WARPED_ROOTS, 1, F, true);
        r(Items.NETHER_SPROUTS, 1, F, true);
        r(Items.WEEPING_VINES, 1, F, true);
        r(Items.TWISTING_VINES, 1, F, true);
        r(Items.GLOW_LICHEN, 1, F, true);
        r(Items.SCULK_VEIN, 1, M, true);

        // Coral
        r(Items.TUBE_CORAL_BLOCK, 1, F, true);
        r(Items.BRAIN_CORAL_BLOCK, 1, F, true);
        r(Items.BUBBLE_CORAL_BLOCK, 1, F, true);
        r(Items.FIRE_CORAL_BLOCK, 1, F, true);
        r(Items.HORN_CORAL_BLOCK, 1, F, true);
        r(Items.DEAD_TUBE_CORAL_BLOCK, 1, F, true);
        r(Items.DEAD_BRAIN_CORAL_BLOCK, 1, F, true);
        r(Items.DEAD_BUBBLE_CORAL_BLOCK, 1, F, true);
        r(Items.DEAD_FIRE_CORAL_BLOCK, 1, F, true);
        r(Items.DEAD_HORN_CORAL_BLOCK, 1, F, true);
        r(Items.TUBE_CORAL, 1, F, true);
        r(Items.BRAIN_CORAL, 1, F, true);
        r(Items.BUBBLE_CORAL, 1, F, true);
        r(Items.FIRE_CORAL, 1, F, true);
        r(Items.HORN_CORAL, 1, F, true);
        r(Items.DEAD_TUBE_CORAL, 1, F, true);
        r(Items.DEAD_BRAIN_CORAL, 1, F, true);
        r(Items.DEAD_BUBBLE_CORAL, 1, F, true);
        r(Items.DEAD_FIRE_CORAL, 1, F, true);
        r(Items.DEAD_HORN_CORAL, 1, F, true);
        r(Items.TUBE_CORAL_FAN, 1, F, true);
        r(Items.BRAIN_CORAL_FAN, 1, F, true);
        r(Items.BUBBLE_CORAL_FAN, 1, F, true);
        r(Items.FIRE_CORAL_FAN, 1, F, true);
        r(Items.HORN_CORAL_FAN, 1, F, true);
        r(Items.DEAD_TUBE_CORAL_FAN, 1, F, true);
        r(Items.DEAD_BRAIN_CORAL_FAN, 1, F, true);
        r(Items.DEAD_BUBBLE_CORAL_FAN, 1, F, true);
        r(Items.DEAD_FIRE_CORAL_FAN, 1, F, true);
        r(Items.DEAD_HORN_CORAL_FAN, 1, F, true);

        // --- MISC ITEMS ---
        r(Items.CLAY_BALL, 1, M, true);
        r(Items.SNOWBALL, 1, M, true);
        r(Items.HAY_BLOCK, 0, F, false); // conversion
        r(Items.FARMLAND, 1, F, false);
        r(Items.DIRT_PATH, 1, F, false);
        r(Items.SPONGE, 5, M, true);
        r(Items.WET_SPONGE, 5, M, false);
        r(Items.COBWEB, 2, M, true);
        r(Items.BEEHIVE, 3, F, false);
        r(Items.BEE_NEST, 3, F, true);
        r(Items.IRON_BARS, 2, M, false);
        r(Items.LADDER, 1, M, false);
        r(Items.SCAFFOLDING, 1, M, false);
        r(Items.CREEPER_BANNER_PATTERN, 5, A, false);
        r(Items.SKULL_BANNER_PATTERN, 5, A, false);
        r(Items.FLOWER_BANNER_PATTERN, 3, F, false);
        r(Items.MOJANG_BANNER_PATTERN, 5, A, false);
        r(Items.GLOBE_BANNER_PATTERN, 5, A, false);
        r(Items.PIGLIN_BANNER_PATTERN, 5, A, false);
        r(Items.FLOW_BANNER_PATTERN, 5, A, false);
        r(Items.GUSTER_BANNER_PATTERN, 5, A, false);
        r(Items.FIELD_MASONED_BANNER_PATTERN, 3, M, false);
        r(Items.BORDURE_INDENTED_BANNER_PATTERN, 3, M, false);
        r(Items.COPPER_GRATE, 2, S, false);
        r(Items.COPPER_BULB, 2, S, false);
        r(Items.COPPER_DOOR, 3, S, false);
        r(Items.COPPER_TRAPDOOR, 2, S, false);
        r(Items.CHISELED_COPPER, 2, S, false);

        // Music discs
        r(Items.MUSIC_DISC_13, 8, A, true);
        r(Items.MUSIC_DISC_CAT, 8, A, true);
        r(Items.MUSIC_DISC_BLOCKS, 8, A, true);
        r(Items.MUSIC_DISC_CHIRP, 8, A, true);
        r(Items.MUSIC_DISC_CREATOR, 8, A, true);
        r(Items.MUSIC_DISC_CREATOR_MUSIC_BOX, 8, A, true);
        r(Items.MUSIC_DISC_FAR, 8, A, true);
        r(Items.MUSIC_DISC_MALL, 8, A, true);
        r(Items.MUSIC_DISC_MELLOHI, 8, A, true);
        r(Items.MUSIC_DISC_STAL, 8, A, true);
        r(Items.MUSIC_DISC_STRAD, 8, A, true);
        r(Items.MUSIC_DISC_WARD, 8, A, true);
        r(Items.MUSIC_DISC_11, 8, A, true);
        r(Items.MUSIC_DISC_WAIT, 8, A, true);
        r(Items.MUSIC_DISC_OTHERSIDE, 12, A, true);
        r(Items.MUSIC_DISC_PIGSTEP, 12, A, true);
        r(Items.MUSIC_DISC_5, 8, A, true);
        r(Items.MUSIC_DISC_RELIC, 12, A, true);
        r(Items.MUSIC_DISC_PRECIPICE, 8, A, true);

        // Pottery sherds (all found in archaeology)
        r(Items.ANGLER_POTTERY_SHERD, 5, A, true);
        r(Items.ARCHER_POTTERY_SHERD, 5, A, true);
        r(Items.ARMS_UP_POTTERY_SHERD, 5, A, true);
        r(Items.BLADE_POTTERY_SHERD, 5, A, true);
        r(Items.BREWER_POTTERY_SHERD, 5, A, true);
        r(Items.BURN_POTTERY_SHERD, 5, A, true);
        r(Items.DANGER_POTTERY_SHERD, 5, A, true);
        r(Items.EXPLORER_POTTERY_SHERD, 5, A, true);
        r(Items.FLOW_POTTERY_SHERD, 5, A, true);
        r(Items.FRIEND_POTTERY_SHERD, 5, A, true);
        r(Items.GUSTER_POTTERY_SHERD, 5, A, true);
        r(Items.HEART_POTTERY_SHERD, 5, A, true);
        r(Items.HEARTBREAK_POTTERY_SHERD, 5, A, true);
        r(Items.HOWL_POTTERY_SHERD, 5, A, true);
        r(Items.MINER_POTTERY_SHERD, 5, A, true);
        r(Items.MOURNER_POTTERY_SHERD, 5, A, true);
        r(Items.PLENTY_POTTERY_SHERD, 5, A, true);
        r(Items.PRIZE_POTTERY_SHERD, 5, A, true);
        r(Items.SCRAPE_POTTERY_SHERD, 5, A, true);
        r(Items.SHEAF_POTTERY_SHERD, 5, A, true);
        r(Items.SHELTER_POTTERY_SHERD, 5, A, true);
        r(Items.SKULL_POTTERY_SHERD, 5, A, true);
        r(Items.SNORT_POTTERY_SHERD, 5, A, true);

        // Armor trims (all found in structures)
        r(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 12, S, true);
        r(Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, 8, S, true);
        r(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 12, S, true);

        // Spawn eggs — all 0 XP, not natural
        // (handled in pattern fallback)
    }

    /** Helper: register a wood-type item by field name */
    private static void registerWoodItem(String fieldName, int xp, boolean natural) {
        try {
            var field = Items.class.getField(fieldName);
            var item = (Item) field.get(null);
            if (item != null) {
                ITEM_VALUES.put(item, new ItemValue(xp, ProfessionType.MINECRAFTER, natural));
            }
        } catch (Exception ignored) {
            // Some wood types may not exist in this MC version
        }
    }

    /** Helper: register a color-variant item by field name */
    private static void registerColorItem(String fieldName, int xp, ProfessionType prof, boolean natural) {
        try {
            var field = Items.class.getField(fieldName);
            var item = (Item) field.get(null);
            if (item != null) {
                ITEM_VALUES.put(item, new ItemValue(xp, prof, natural));
            }
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // CONVERSIONS
    // =========================================================================

    private static void registerConversions() {
        // Block ↔ item conversions (both directions give 0 XP)
        conversion(Items.IRON_BLOCK);
        conversion(Items.IRON_INGOT);
        conversion(Items.IRON_NUGGET);
        conversion(Items.GOLD_BLOCK);
        conversion(Items.GOLD_INGOT);
        conversion(Items.GOLD_NUGGET);
        conversion(Items.COPPER_BLOCK);
        conversion(Items.COPPER_INGOT);
        conversion(Items.DIAMOND_BLOCK);
        conversion(Items.DIAMOND);
        conversion(Items.EMERALD_BLOCK);
        conversion(Items.EMERALD);
        conversion(Items.LAPIS_BLOCK);
        conversion(Items.LAPIS_LAZULI);
        conversion(Items.REDSTONE_BLOCK);
        conversion(Items.REDSTONE);
        conversion(Items.NETHERITE_BLOCK);
        conversion(Items.NETHERITE_INGOT);
        conversion(Items.COAL_BLOCK);
        conversion(Items.COAL);
        conversion(Items.QUARTZ_BLOCK);
        conversion(Items.QUARTZ);
        conversion(Items.SLIME_BLOCK);
        conversion(Items.SLIME_BALL);
        conversion(Items.HONEY_BLOCK);
        conversion(Items.HONEYCOMB_BLOCK);
        conversion(Items.HONEYCOMB);
        conversion(Items.DRIED_KELP_BLOCK);
        conversion(Items.DRIED_KELP);
        conversion(Items.MELON);
        conversion(Items.MELON_SLICE);
        conversion(Items.HAY_BLOCK);
        conversion(Items.WHEAT);
        conversion(Items.SNOW_BLOCK);
        conversion(Items.SNOWBALL);
        conversion(Items.CLAY);
        conversion(Items.CLAY_BALL);
        conversion(Items.GLOWSTONE);
        conversion(Items.GLOWSTONE_DUST);
        conversion(Items.NETHER_WART_BLOCK);
        conversion(Items.NETHER_WART);
        conversion(Items.BONE_BLOCK);
        conversion(Items.BONE_MEAL);
        conversion(Items.RAW_IRON_BLOCK);
        conversion(Items.RAW_IRON);
        conversion(Items.RAW_COPPER_BLOCK);
        conversion(Items.RAW_COPPER);
        conversion(Items.RAW_GOLD_BLOCK);
        conversion(Items.RAW_GOLD);

        // Glass cycle: vanilla glass <-> Connected Glass mod variants (clear/borderless/scratched).
        // Players can loop through the cycle in either direction, so every node is a conversion
        // and must give 0 XP. Covers uncolored + all 16 dye variants, blocks + panes.
        conversion(Items.GLASS);
        conversion(Items.GLASS_PANE);
        conversion(Items.WHITE_STAINED_GLASS);
        conversion(Items.ORANGE_STAINED_GLASS);
        conversion(Items.MAGENTA_STAINED_GLASS);
        conversion(Items.LIGHT_BLUE_STAINED_GLASS);
        conversion(Items.YELLOW_STAINED_GLASS);
        conversion(Items.LIME_STAINED_GLASS);
        conversion(Items.PINK_STAINED_GLASS);
        conversion(Items.GRAY_STAINED_GLASS);
        conversion(Items.LIGHT_GRAY_STAINED_GLASS);
        conversion(Items.CYAN_STAINED_GLASS);
        conversion(Items.PURPLE_STAINED_GLASS);
        conversion(Items.BLUE_STAINED_GLASS);
        conversion(Items.BROWN_STAINED_GLASS);
        conversion(Items.GREEN_STAINED_GLASS);
        conversion(Items.RED_STAINED_GLASS);
        conversion(Items.BLACK_STAINED_GLASS);
        conversion(Items.WHITE_STAINED_GLASS_PANE);
        conversion(Items.ORANGE_STAINED_GLASS_PANE);
        conversion(Items.MAGENTA_STAINED_GLASS_PANE);
        conversion(Items.LIGHT_BLUE_STAINED_GLASS_PANE);
        conversion(Items.YELLOW_STAINED_GLASS_PANE);
        conversion(Items.LIME_STAINED_GLASS_PANE);
        conversion(Items.PINK_STAINED_GLASS_PANE);
        conversion(Items.GRAY_STAINED_GLASS_PANE);
        conversion(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
        conversion(Items.CYAN_STAINED_GLASS_PANE);
        conversion(Items.PURPLE_STAINED_GLASS_PANE);
        conversion(Items.BLUE_STAINED_GLASS_PANE);
        conversion(Items.BROWN_STAINED_GLASS_PANE);
        conversion(Items.GREEN_STAINED_GLASS_PANE);
        conversion(Items.RED_STAINED_GLASS_PANE);
        conversion(Items.BLACK_STAINED_GLASS_PANE);

        // Connected Glass mod variants (no-op if mod isn't loaded)
        String[] cgVariants = {"clear", "borderless", "scratched"};
        String[] cgColors = {"white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (String v : cgVariants) {
            conversionMod("connectedglass", v + "_glass");
            conversionMod("connectedglass", v + "_glass_pane");
            for (String c : cgColors) {
                conversionMod("connectedglass", v + "_" + c + "_stained_glass");
                conversionMod("connectedglass", v + "_" + c + "_stained_glass_pane");
            }
        }
    }

    // =========================================================================
    // SMELTING MAP (output → input)
    // =========================================================================

    private static void registerSmeltingMap() {
        // Ores → ingots
        smelt(Items.IRON_INGOT, Items.RAW_IRON);
        smelt(Items.GOLD_INGOT, Items.RAW_GOLD);
        smelt(Items.COPPER_INGOT, Items.RAW_COPPER);
        smelt(Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS);

        // Stone processing
        smelt(Items.STONE, Items.COBBLESTONE);
        smelt(Items.SMOOTH_STONE, Items.STONE);
        smelt(Items.GLASS, Items.SAND);
        smelt(Items.SMOOTH_SANDSTONE, Items.SANDSTONE);
        smelt(Items.SMOOTH_RED_SANDSTONE, Items.RED_SANDSTONE);
        smelt(Items.SMOOTH_QUARTZ, Items.QUARTZ_BLOCK);
        smelt(Items.SMOOTH_BASALT, Items.BASALT);
        smelt(Items.CRACKED_STONE_BRICKS, Items.STONE_BRICKS);
        smelt(Items.CRACKED_DEEPSLATE_BRICKS, Items.DEEPSLATE_BRICKS);
        smelt(Items.CRACKED_DEEPSLATE_TILES, Items.DEEPSLATE_TILES);
        smelt(Items.CRACKED_NETHER_BRICKS, Items.NETHER_BRICKS);
        smelt(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS, Items.POLISHED_BLACKSTONE_BRICKS);
        smelt(Items.BRICK, Items.CLAY_BALL);
        smelt(Items.NETHER_BRICK, Items.NETHERRACK);
        smelt(Items.TERRACOTTA, Items.CLAY);
        smelt(Items.CHARCOAL, Items.OAK_LOG);
        smelt(Items.POPPED_CHORUS_FRUIT, Items.CHORUS_FRUIT);
        smelt(Items.SPONGE, Items.WET_SPONGE);

        // Cooked foods
        smelt(Items.COOKED_BEEF, Items.BEEF);
        smelt(Items.COOKED_PORKCHOP, Items.PORKCHOP);
        smelt(Items.COOKED_MUTTON, Items.MUTTON);
        smelt(Items.COOKED_CHICKEN, Items.CHICKEN);
        smelt(Items.COOKED_RABBIT, Items.RABBIT);
        smelt(Items.COOKED_COD, Items.COD);
        smelt(Items.COOKED_SALMON, Items.SALMON);
        smelt(Items.BAKED_POTATO, Items.POTATO);
        smelt(Items.DRIED_KELP, Items.KELP);
    }

    // =========================================================================
    // COOKING BLOCKS (furnace-like blocks for Smith compatibility)
    // =========================================================================

    private static void registerCookingBlocks() {
        COOKING_BLOCKS.add(Blocks.FURNACE);
        COOKING_BLOCKS.add(Blocks.BLAST_FURNACE);
        COOKING_BLOCKS.add(Blocks.SMOKER);
        COOKING_BLOCKS.add(Blocks.CAMPFIRE);
        COOKING_BLOCKS.add(Blocks.SOUL_CAMPFIRE);

        // Refurbished Furniture mod cooking blocks (registered by string if mod is loaded)
        registerModCookingBlock("refurbished_furniture", "light_stove");
        registerModCookingBlock("refurbished_furniture", "dark_stove");
        registerModCookingBlock("refurbished_furniture", "light_microwave");
        registerModCookingBlock("refurbished_furniture", "dark_microwave");
        registerModCookingBlock("refurbished_furniture", "light_toaster");
        registerModCookingBlock("refurbished_furniture", "dark_toaster");
        registerModCookingBlock("refurbished_furniture", "frying_pan");
        // Grills (16 colors)
        String[] colors = {"black", "blue", "brown", "cyan", "gray", "green", "light_gray",
                "light_blue", "lime", "magenta", "orange", "pink", "purple", "red", "white", "yellow"};
        for (String c : colors) {
            registerModCookingBlock("refurbished_furniture", c + "_grill");
        }
        // Cutting boards (all wood types)
        String[] woods = {"acacia", "birch", "cherry", "crimson", "dark_oak", "jungle",
                "mangrove", "oak", "pale_oak", "spruce", "warped"};
        for (String w : woods) {
            registerModCookingBlock("refurbished_furniture", w + "_cutting_board");
        }
    }

    private static void registerModCookingBlock(String modId, String blockName) {
        var id = Identifier.tryParse(modId + ":" + blockName);
        if (id == null) return;
        BuiltInRegistries.BLOCK.get(id).ifPresent(holder ->
                COOKING_BLOCKS.add(holder.value()));
    }

    // =========================================================================
    // MLKYMC MOD ITEMS
    // =========================================================================

    private static void registerMlkymcItems() {
        try {
            rMod("mlkymc", "milky_star", 2, ProfessionType.MINECRAFTER, false);
            rMod("mlkymc", "milky_star_jar_empty", 0, ProfessionType.MINECRAFTER, false);
            rMod("mlkymc", "milky_star_jar_half", 0, ProfessionType.MINECRAFTER, false);
            rMod("mlkymc", "milky_star_jar_full", 0, ProfessionType.MINECRAFTER, false);
            rMod("mlkymc", "stall_deed", 0, ProfessionType.MINECRAFTER, false);
            rMod("mlkymc", "market_catalog", 0, ProfessionType.MINECRAFTER, false);
            rMod("mlkymc", "waystone_shard", 5, ProfessionType.ADVENTURER, false);
            rMod("mlkymc", "wayfinder_compass", 10, ProfessionType.ADVENTURER, false);
            rMod("mlkymc", "warp_stone", 8, ProfessionType.ADVENTURER, false);
            rMod("mlkymc", "warp_anchor", 12, ProfessionType.ADVENTURER, false);
            rMod("mlkymc", "grappling_hook", 12, ProfessionType.ADVENTURER, false);
            rMod("mlkymc", "grappling_hook_ammo", 2, ProfessionType.ADVENTURER, false);
            rMod("mlkymc", "dimension_compass", 15, ProfessionType.ADVENTURER, false);
            rMod("mlkymc", "blessed_ember", 5, ProfessionType.CLERIC, false);
            rMod("mlkymc", "totem_of_resurrection", 20, ProfessionType.CLERIC, false);
            rMod("mlkymc", "blessing_scroll", 6, ProfessionType.CLERIC, false);
            rMod("mlkymc", "living_essence", 5, ProfessionType.FARMHAND, false);
            rMod("mlkymc", "growth_fertilizer", 3, ProfessionType.FARMHAND, false);
            rMod("mlkymc", "animal_feed", 4, ProfessionType.FARMHAND, false);
            rMod("mlkymc", "resonant_core", 5, ProfessionType.MINECRAFTER, false);
            rMod("mlkymc", "reinforced_pickaxe", 25, ProfessionType.MINECRAFTER, false);
            rMod("mlkymc", "reinforced_axe", 25, ProfessionType.MINECRAFTER, false);
            rMod("mlkymc", "builders_wand", 15, ProfessionType.MINECRAFTER, false);
            rMod("mlkymc", "ender_pouch", 12, ProfessionType.MINECRAFTER, false);
            rMod("mlkymc", "tempered_plate", 5, ProfessionType.SMITH, false);
            rMod("mlkymc", "whetstone", 6, ProfessionType.SMITH, false);
            rMod("mlkymc", "armor_plating", 8, ProfessionType.SMITH, false);
            rMod("mlkymc", "soulstone_brick", 3, ProfessionType.CLERIC, false);
            rMod("mlkymc", "soul_pillar", 3, ProfessionType.CLERIC, false);
            rMod("mlkymc", "conduit_core", 5, ProfessionType.CLERIC, false);
            rMod("mlkymc", "soul_altar_capstone", 8, ProfessionType.CLERIC, false);
            rMod("mlkymc", "tome_of_the_soul_warden", 5, ProfessionType.CLERIC, false);
            rMod("mlkymc", "scarecrow", 4, ProfessionType.FARMHAND, false);
            rMod("mlkymc", "sandwich", 5, ProfessionType.FARMHAND, false);
            rMod("mlkymc", "soul_forge", 10, ProfessionType.SMITH, false);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // MODDED ITEMS (Refurbished Furniture, Animal Garden, etc.)
    // =========================================================================

    private static void registerModdedItems() {
        var F = ProfessionType.FARMHAND;
        var S = ProfessionType.SMITH;
        var M = ProfessionType.MINECRAFTER;
        var A = ProfessionType.ADVENTURER;

        // Refurbished Furniture — food items (with buffs where applicable)
        rMod("refurbished_furniture", "dough", 2, F, false, AttributeBuff.STEP_HEIGHT);
        rMod("refurbished_furniture", "bread_slice", 1, F, false, AttributeBuff.KNOCKBACK_RESISTANCE);
        rMod("refurbished_furniture", "toast", 2, F, false, AttributeBuff.MINING_SPEED);
        rMod("refurbished_furniture", "cheese", 2, F, false, AttributeBuff.ARMOR);
        rMod("refurbished_furniture", "cheese_sandwich", 3, F, false, AttributeBuff.ARMOR_TOUGHNESS);
        rMod("refurbished_furniture", "cheese_toastie", 3, F, false, AttributeBuff.BURNING_TIME);
        rMod("refurbished_furniture", "raw_meatlovers_pizza", 3, F, false, AttributeBuff.DAMAGE);
        rMod("refurbished_furniture", "raw_vegetable_pizza", 3, F, false, AttributeBuff.REACH);
        rMod("refurbished_furniture", "cooked_meatlovers_pizza", 5, F, false, AttributeBuff.ATTACK_SPEED);
        rMod("refurbished_furniture", "cooked_vegetable_pizza", 5, F, false, AttributeBuff.ENTITY_REACH);
        rMod("refurbished_furniture", "meatlovers_pizza_slice", 2, F, false, AttributeBuff.SNEAKING_SPEED);
        rMod("refurbished_furniture", "vegetable_pizza_slice", 2, F, false, AttributeBuff.UNDERWATER_MINING);
        rMod("refurbished_furniture", "glow_berry_jam", 2, F, false, AttributeBuff.SAFE_FALL);
        rMod("refurbished_furniture", "sweet_berry_jam", 2, F, false, AttributeBuff.SPEED);
        rMod("refurbished_furniture", "glow_berry_jam_toast", 3, F, false, AttributeBuff.JUMP);
        rMod("refurbished_furniture", "sweet_berry_jam_toast", 3, F, false, AttributeBuff.LUCK);
        rMod("refurbished_furniture", "sea_salt", 1, F, false, AttributeBuff.OXYGEN);
        rMod("refurbished_furniture", "wheat_flour", 1, F, false);

        // Refurbished Furniture — tools
        rMod("refurbished_furniture", "knife", 3, S, false);
        rMod("refurbished_furniture", "spatula", 2, S, false);
        rMod("refurbished_furniture", "wrench", 3, M, false);

        // Refurbished Furniture — key functional blocks
        rMod("refurbished_furniture", "light_stove", 5, S, false);
        rMod("refurbished_furniture", "dark_stove", 5, S, false);
        rMod("refurbished_furniture", "light_microwave", 5, S, false);
        rMod("refurbished_furniture", "dark_microwave", 5, S, false);
        rMod("refurbished_furniture", "light_toaster", 3, S, false);
        rMod("refurbished_furniture", "dark_toaster", 3, S, false);
        rMod("refurbished_furniture", "frying_pan", 3, S, false);
        rMod("refurbished_furniture", "light_electricity_generator", 8, M, false);
        rMod("refurbished_furniture", "dark_electricity_generator", 8, M, false);
        rMod("refurbished_furniture", "light_fridge", 5, M, false);
        rMod("refurbished_furniture", "dark_fridge", 5, M, false);
        rMod("refurbished_furniture", "light_freezer", 5, M, false);
        rMod("refurbished_furniture", "dark_freezer", 5, M, false);

        // Camerapture
        rMod("camerapture", "camera", 5, ProfessionType.ADVENTURER, false);

        // Tom's Storage — functional blocks
        rMod("toms_storage", "ts.storage_terminal", 5, M, false);
        rMod("toms_storage", "ts.crafting_terminal", 8, M, false);
        rMod("toms_storage", "ts.inventory_connector", 3, M, false);
        rMod("toms_storage", "ts.trim", 1, M, false);
        rMod("toms_storage", "ts.painted_trim", 1, M, false);
        rMod("toms_storage", "ts.inventory_cable", 2, M, false);
        rMod("toms_storage", "ts.inventory_cable_connector", 3, M, false);
        rMod("toms_storage", "ts.inventory_proxy", 3, M, false);
        rMod("toms_storage", "ts.level_emitter", 3, M, false);
        rMod("toms_storage", "ts.open_crate", 2, M, false);
        rMod("toms_storage", "ts.inventory_hopper_basic", 3, M, false);

        // Tom's Storage — remaining items
        rMod("toms_storage", "ts.adv_wireless_terminal", 8, M, false);
        rMod("toms_storage", "ts.wireless_terminal", 5, M, false);
        rMod("toms_storage", "ts.filing_cabinet", 3, M, false);
        rMod("toms_storage", "ts.inventory_configurator", 3, M, false);
        rMod("toms_storage", "ts.inventory_interface", 3, M, false);
        rMod("toms_storage", "ts.item_filter", 2, M, false);
        rMod("toms_storage", "ts.polymorphic_item_filter", 3, M, false);
        rMod("toms_storage", "ts.tag_item_filter", 3, M, false);
        rMod("toms_storage", "ts.paint_kit", 2, M, false);

        // Backpacked
        rMod("backpacked", "backpack", 5, F, false);
        rMod("backpacked", "backpack_dock", 3, M, false);
        rMod("backpacked", "unlock_token", 3, F, false);
        String[] bpWoods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "cherry", "crimson", "warped"};
        for (String w : bpWoods) {
            rMod("backpacked", w + "_backpack_shelf", 2, M, false);
        }

        // Camerapture — remaining
        rMod("camerapture", "album", 3, A, false);
        rMod("camerapture", "picture", 1, A, false);

        // MC Paint
        rMod("mcpaint", "brush", 2, F, false);
        rMod("mcpaint", "custom_painting", 2, F, false);
        rMod("mcpaint", "easel", 3, M, false);
        rMod("mcpaint", "studio", 5, M, false);

        // Connected Glass — all glass variants (1 XP each, crafted)
        String[] cgTypes = {"borderless_glass", "clear_glass", "scratched_glass"};
        String[] cgColors = {"", "_black", "_blue", "_brown", "_cyan", "_gray", "_green",
                "_light_blue", "_light_gray", "_lime", "_magenta", "_orange", "_pink",
                "_purple", "_red", "_white", "_yellow"};
        for (String type : cgTypes) {
            for (String color : cgColors) {
                rMod("connectedglass", type + color, 1, M, false);
                rMod("connectedglass", type + color + "_pane", 1, M, false);
            }
        }
        // Tinted borderless glass
        for (String color : cgColors) {
            rMod("connectedglass", "tinted_borderless_glass" + color, 2, M, false);
        }

        // Nice Mobs — food and items (with buffs)
        rMod("mr_nice_mobs", "cheese", 2, F, false, AttributeBuff.ARMOR);
        rMod("mr_nice_mobs", "chorus_juice", 3, F, false, AttributeBuff.REACH);
        rMod("mr_nice_mobs", "chorus_kebab", 3, F, false, AttributeBuff.ENTITY_REACH);
        rMod("mr_nice_mobs", "chorus_pie", 5, F, false, AttributeBuff.STEP_HEIGHT);
        rMod("mr_nice_mobs", "chorus_salad", 3, F, false, AttributeBuff.SPEED);
        rMod("mr_nice_mobs", "cluckshroom_egg", 2, F, false, AttributeBuff.JUMP);
        rMod("mr_nice_mobs", "creamy_egg", 2, F, false, AttributeBuff.HEALTH);
        rMod("mr_nice_mobs", "dark_brown_egg", 1, F, false);
        rMod("mr_nice_mobs", "dried_jellyfish", 2, F, false, AttributeBuff.UNDERWATER_MINING);
        rMod("mr_nice_mobs", "duck_egg", 1, F, false, AttributeBuff.JUMP);
        rMod("mr_nice_mobs", "ender_egg", 3, F, false, AttributeBuff.REACH);
        rMod("mr_nice_mobs", "ender_horn", 5, A, false);
        rMod("mr_nice_mobs", "golden_chorus_fruit", 8, F, false, AttributeBuff.KNOCKBACK_RESISTANCE);
        rMod("mr_nice_mobs", "golden_egg", 5, F, false, AttributeBuff.HEALTH);
        rMod("mr_nice_mobs", "nautilus_helmet", 8, S, false);
        rMod("mr_nice_mobs", "nautilus_horn", 5, A, false);
        rMod("mr_nice_mobs", "used_nautilus_horn", 2, A, false);
        rMod("mr_nice_mobs", "zombie_egg", 1, F, false);

        // Refurbished Furniture — ALL remaining blocks (bulk by pattern)
        // Wood furniture variants: 11 wood types × 20+ furniture types
        String[] rfWoods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                "mangrove", "cherry", "pale_oak", "crimson", "warped"};
        String[] rfWoodFurniture = {"basin", "bath", "chair", "crate", "cutting_board",
                "dark_ceiling_fan", "desk", "drawer", "hedge", "kitchen_cabinetry",
                "kitchen_drawer", "kitchen_sink", "kitchen_storage_cabinet",
                "lattice_fence", "lattice_fence_gate", "light_ceiling_fan",
                "mail_box", "storage_cabinet", "storage_jar", "table", "toilet"};
        for (String w : rfWoods) {
            for (String f2 : rfWoodFurniture) {
                rMod("refurbished_furniture", w + "_" + f2, 2, M, false);
            }
        }

        // Color furniture variants: 16 colors × multiple types
        String[] rfColors = {"white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        String[] rfColorFurniture = {"basin", "bath", "cooler", "grill", "kitchen_cabinetry",
                "kitchen_drawer", "kitchen_sink", "kitchen_storage_cabinet",
                "lamp", "sofa", "stool", "toilet", "trampoline"};
        for (String c2 : rfColors) {
            for (String f2 : rfColorFurniture) {
                rMod("refurbished_furniture", c2 + "_" + f2, 2, M, false);
            }
        }

        // Refurbished Furniture — misc blocks
        rMod("refurbished_furniture", "computer", 5, M, false);
        rMod("refurbished_furniture", "television", 5, M, false);
        rMod("refurbished_furniture", "television_remote", 2, M, false);
        rMod("refurbished_furniture", "light_ceiling_light", 2, M, false);
        rMod("refurbished_furniture", "dark_ceiling_light", 2, M, false);
        rMod("refurbished_furniture", "light_lightswitch", 1, M, false);
        rMod("refurbished_furniture", "dark_lightswitch", 1, M, false);
        rMod("refurbished_furniture", "light_range_hood", 3, M, false);
        rMod("refurbished_furniture", "dark_range_hood", 3, M, false);
        rMod("refurbished_furniture", "plate", 1, F, false);
        rMod("refurbished_furniture", "milk", 2, F, false);
        rMod("refurbished_furniture", "door_mat", 1, M, false);
        rMod("refurbished_furniture", "doorbell", 2, M, false);
        rMod("refurbished_furniture", "post_box", 2, M, false);
        rMod("refurbished_furniture", "recycle_bin", 2, M, false);
        rMod("refurbished_furniture", "package", 2, M, false);
        rMod("refurbished_furniture", "workbench", 3, M, false);
        rMod("refurbished_furniture", "azalea_hedge", 1, F, false);
        rMod("refurbished_furniture", "stone_stepping_stones", 1, M, false);
        rMod("refurbished_furniture", "andesite_stepping_stones", 1, M, false);
        rMod("refurbished_furniture", "diorite_stepping_stones", 1, M, false);
        rMod("refurbished_furniture", "granite_stepping_stones", 1, M, false);
        rMod("refurbished_furniture", "deepslate_stepping_stones", 1, M, false);
        rMod("refurbished_furniture", "light_freezer", 5, M, false);
        rMod("refurbished_furniture", "dark_freezer", 5, M, false);
    }

    // =========================================================================
    // PATTERN FALLBACKS (catch items not explicitly registered)
    // =========================================================================

    private static void registerPatternFallbacks() {
        // Register all spawn eggs as 0 XP
        for (var holder : BuiltInRegistries.ITEM) {
            Item item = holder;
            if (ITEM_VALUES.containsKey(item)) continue; // already registered

            var key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null) continue;
            String id = key.toString();
            String path = key.getPath();

            // Spawn eggs
            if (path.endsWith("_spawn_egg")) {
                ITEM_VALUES.put(item, new ItemValue(0, ProfessionType.FARMHAND, false));
                continue;
            }

            // Modded furniture blocks (refurbished_furniture bulk — tables, chairs, etc.)
            if (id.startsWith("refurbished_furniture:")) {
                ITEM_VALUES.put(item, new ItemValue(2, ProfessionType.MINECRAFTER, false));
                continue;
            }

            // Animal Garden mobs/items
            if (id.startsWith("animalgarden_")) {
                ITEM_VALUES.put(item, new ItemValue(1, ProfessionType.FARMHAND, false));
                continue;
            }

            // Any remaining unregistered items get the default
            // (handled by getOrDefault in the API methods)
        }
    }

    // =========================================================================
    // Cracked/special items that weren't covered above
    // =========================================================================

    // Note: Items not explicitly registered here fall through to DEFAULT
    // (1 XP, MINECRAFTER, not naturally generated, no buff)
    // This is safe — unregistered items give minimal XP and no mining bonuses.
}
