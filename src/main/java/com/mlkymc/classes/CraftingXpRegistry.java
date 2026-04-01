package com.mlkymc.classes;

import com.mlkymc.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of crafting XP values for all items.
 * When a player crafts an item, they receive XP for the matching profession.
 * XP values reflect the difficulty/rarity of ingredients used.
 *
 * Lookup order:
 * 1. Explicit override map (highest priority)
 * 2. Food component check (-> FARMHAND)
 * 3. Pattern rules based on item registry name
 * 4. Default fallback (1 XP, MINECRAFTER)
 */
public class CraftingXpRegistry {

    public record CraftXpEntry(int xp, ProfessionType profession) {}

    private static final Map<Item, CraftXpEntry> OVERRIDES = new HashMap<>();
    private static boolean initialized = false;

    public static CraftXpEntry lookup(ItemStack stack) {
        ensureInitialized();

        Item item = stack.getItem();

        // 1. Explicit overrides
        CraftXpEntry override = OVERRIDES.get(item);
        if (override != null) return override;

        // 2. Food items -> FARMHAND
        if (stack.has(DataComponents.FOOD)) {
            return foodXp(item);
        }

        // 3. Pattern rules based on registry name
        String id = item.toString();
        CraftXpEntry pattern = matchPattern(id);
        if (pattern != null) return pattern;

        // 4. Default
        return new CraftXpEntry(1, ProfessionType.MINECRAFTER);
    }

    private static CraftXpEntry foodXp(Item item) {
        String id = item.toString();
        // Complex foods
        if (id.contains("cake") || id.contains("pumpkin_pie") || id.contains("rabbit_stew")
                || id.contains("mushroom_stew") || id.contains("beetroot_soup")
                || id.contains("suspicious_stew")) {
            return new CraftXpEntry(5, ProfessionType.FARMHAND);
        }
        // Golden foods
        if (id.contains("golden_apple") || id.contains("golden_carrot")) {
            return new CraftXpEntry(8, ProfessionType.FARMHAND);
        }
        // Basic cooked/crafted food
        return new CraftXpEntry(2, ProfessionType.FARMHAND);
    }

    private static CraftXpEntry matchPattern(String id) {
        // === SMITH: Armor, weapons, shields, anvils ===
        if (id.contains("netherite") && (isArmor(id) || isWeapon(id))) return new CraftXpEntry(20, ProfessionType.SMITH);
        if (id.contains("diamond") && (isArmor(id) || isWeapon(id))) return new CraftXpEntry(12, ProfessionType.SMITH);
        if (id.contains("iron") && (isArmor(id) || isWeapon(id))) return new CraftXpEntry(6, ProfessionType.SMITH);
        if (id.contains("gold") && (isArmor(id) || isWeapon(id))) return new CraftXpEntry(6, ProfessionType.SMITH);
        if (id.contains("chainmail") && isArmor(id)) return new CraftXpEntry(5, ProfessionType.SMITH);
        if (id.contains("leather") && isArmor(id)) return new CraftXpEntry(3, ProfessionType.SMITH);
        if (id.contains("stone") && isWeapon(id)) return new CraftXpEntry(3, ProfessionType.SMITH);
        if (id.contains("wooden") && isWeapon(id)) return new CraftXpEntry(1, ProfessionType.SMITH);
        if (id.equals("shield")) return new CraftXpEntry(5, ProfessionType.SMITH);
        if (id.equals("bow")) return new CraftXpEntry(3, ProfessionType.SMITH);
        if (id.equals("crossbow")) return new CraftXpEntry(6, ProfessionType.SMITH);
        if (id.equals("arrow")) return new CraftXpEntry(1, ProfessionType.SMITH);
        if (id.contains("tipped_arrow")) return new CraftXpEntry(3, ProfessionType.SMITH);
        if (id.contains("spectral_arrow")) return new CraftXpEntry(3, ProfessionType.SMITH);

        // === MINECRAFTER: Tools, building blocks, redstone ===
        if (id.contains("netherite") && isTool(id)) return new CraftXpEntry(20, ProfessionType.MINECRAFTER);
        if (id.contains("diamond") && isTool(id)) return new CraftXpEntry(12, ProfessionType.MINECRAFTER);
        if (id.contains("iron") && isTool(id)) return new CraftXpEntry(6, ProfessionType.MINECRAFTER);
        if (id.contains("gold") && isTool(id)) return new CraftXpEntry(5, ProfessionType.MINECRAFTER);
        if (id.contains("stone") && isTool(id)) return new CraftXpEntry(3, ProfessionType.MINECRAFTER);
        if (id.contains("wooden") && isTool(id)) return new CraftXpEntry(1, ProfessionType.MINECRAFTER);

        // Redstone
        if (id.equals("piston") || id.equals("sticky_piston")) return new CraftXpEntry(5, ProfessionType.MINECRAFTER);
        if (id.equals("hopper")) return new CraftXpEntry(8, ProfessionType.MINECRAFTER);
        if (id.equals("dropper") || id.equals("dispenser")) return new CraftXpEntry(5, ProfessionType.MINECRAFTER);
        if (id.equals("observer")) return new CraftXpEntry(5, ProfessionType.MINECRAFTER);
        if (id.equals("comparator") || id.equals("repeater")) return new CraftXpEntry(4, ProfessionType.MINECRAFTER);
        if (id.equals("redstone_torch") || id.equals("redstone_lamp")) return new CraftXpEntry(2, ProfessionType.MINECRAFTER);
        if (id.equals("daylight_detector")) return new CraftXpEntry(5, ProfessionType.MINECRAFTER);
        if (id.equals("target")) return new CraftXpEntry(4, ProfessionType.MINECRAFTER);
        if (id.equals("tnt")) return new CraftXpEntry(6, ProfessionType.MINECRAFTER);
        if (id.equals("tripwire_hook")) return new CraftXpEntry(2, ProfessionType.MINECRAFTER);

        // Rails
        if (id.equals("rail")) return new CraftXpEntry(3, ProfessionType.MINECRAFTER);
        if (id.equals("powered_rail") || id.equals("detector_rail") || id.equals("activator_rail"))
            return new CraftXpEntry(5, ProfessionType.MINECRAFTER);
        if (id.equals("minecart")) return new CraftXpEntry(5, ProfessionType.MINECRAFTER);
        if (id.contains("minecart")) return new CraftXpEntry(8, ProfessionType.MINECRAFTER);

        // Stairs, slabs, walls — low XP bulk blocks
        if (id.contains("_stairs") || id.contains("_slab") || id.contains("_wall"))
            return new CraftXpEntry(1, ProfessionType.MINECRAFTER);

        // Fences, gates, doors, trapdoors
        if (id.contains("_fence") || id.contains("fence_gate"))
            return new CraftXpEntry(1, ProfessionType.MINECRAFTER);
        if (id.contains("_door") || id.contains("_trapdoor"))
            return new CraftXpEntry(2, ProfessionType.MINECRAFTER);
        if (id.contains("iron_door") || id.contains("iron_trapdoor"))
            return new CraftXpEntry(4, ProfessionType.MINECRAFTER);

        // Glass panes, bars
        if (id.contains("glass_pane") || id.equals("iron_bars"))
            return new CraftXpEntry(2, ProfessionType.MINECRAFTER);

        // Buttons, pressure plates, levers
        if (id.contains("_button") || id.contains("_pressure_plate") || id.equals("lever"))
            return new CraftXpEntry(1, ProfessionType.MINECRAFTER);

        // Signs, banners
        if (id.contains("_sign")) return new CraftXpEntry(1, ProfessionType.MINECRAFTER);
        if (id.contains("_banner")) return new CraftXpEntry(3, ProfessionType.MINECRAFTER);

        // Beds, carpets, wool
        if (id.contains("_bed")) return new CraftXpEntry(3, ProfessionType.MINECRAFTER);
        if (id.contains("_carpet")) return new CraftXpEntry(1, ProfessionType.MINECRAFTER);
        if (id.contains("_wool")) return new CraftXpEntry(1, ProfessionType.MINECRAFTER);

        // Concrete powder, terracotta, stained glass
        if (id.contains("_concrete_powder")) return new CraftXpEntry(1, ProfessionType.MINECRAFTER);
        if (id.contains("_stained_glass") && !id.contains("pane"))
            return new CraftXpEntry(2, ProfessionType.MINECRAFTER);
        if (id.contains("_terracotta")) return new CraftXpEntry(1, ProfessionType.MINECRAFTER);

        // Candles, lanterns, torches
        if (id.contains("candle")) return new CraftXpEntry(1, ProfessionType.MINECRAFTER);
        if (id.contains("lantern")) return new CraftXpEntry(3, ProfessionType.MINECRAFTER);
        if (id.equals("torch") || id.equals("soul_torch")) return new CraftXpEntry(1, ProfessionType.MINECRAFTER);

        // Chests, barrels, shulker boxes
        if (id.equals("chest") || id.equals("barrel")) return new CraftXpEntry(2, ProfessionType.MINECRAFTER);
        if (id.contains("shulker_box")) return new CraftXpEntry(10, ProfessionType.MINECRAFTER);

        // Storage blocks (iron block, gold block, etc.)
        if (id.equals("iron_block") || id.equals("gold_block") || id.equals("copper_block"))
            return new CraftXpEntry(3, ProfessionType.MINECRAFTER);
        if (id.equals("diamond_block") || id.equals("emerald_block") || id.equals("lapis_block"))
            return new CraftXpEntry(8, ProfessionType.MINECRAFTER);
        if (id.equals("netherite_block")) return new CraftXpEntry(15, ProfessionType.MINECRAFTER);
        if (id.equals("redstone_block")) return new CraftXpEntry(3, ProfessionType.MINECRAFTER);

        // Brick/stone blocks
        if (id.contains("brick") && !id.contains("nether")) return new CraftXpEntry(2, ProfessionType.MINECRAFTER);
        if (id.contains("nether_brick")) return new CraftXpEntry(2, ProfessionType.MINECRAFTER);
        if (id.contains("polished") || id.contains("smooth") || id.contains("chiseled")
                || id.contains("cut_") || id.contains("mossy"))
            return new CraftXpEntry(1, ProfessionType.MINECRAFTER);

        // Planks, sticks
        if (id.contains("_planks") || id.equals("stick")) return new CraftXpEntry(1, ProfessionType.MINECRAFTER);

        // Boats
        if (id.contains("_boat")) return new CraftXpEntry(2, ProfessionType.MINECRAFTER);

        // Ladders, scaffolding
        if (id.equals("ladder") || id.equals("scaffolding")) return new CraftXpEntry(2, ProfessionType.MINECRAFTER);

        // === FARMHAND: Farming, animal, nature items ===
        if (id.equals("hay_block")) return new CraftXpEntry(3, ProfessionType.FARMHAND);
        if (id.equals("bone_meal")) return new CraftXpEntry(1, ProfessionType.FARMHAND);
        if (id.equals("sugar")) return new CraftXpEntry(1, ProfessionType.FARMHAND);
        if (id.equals("lead")) return new CraftXpEntry(2, ProfessionType.FARMHAND);
        if (id.equals("composter")) return new CraftXpEntry(2, ProfessionType.FARMHAND);
        if (id.equals("beehive")) return new CraftXpEntry(4, ProfessionType.FARMHAND);
        if (id.equals("fishing_rod")) return new CraftXpEntry(3, ProfessionType.FARMHAND);
        if (id.equals("flower_pot")) return new CraftXpEntry(1, ProfessionType.FARMHAND);
        if (id.equals("painting")) return new CraftXpEntry(2, ProfessionType.FARMHAND);
        if (id.equals("item_frame") || id.equals("glow_item_frame")) return new CraftXpEntry(2, ProfessionType.FARMHAND);

        // Dyes
        if (id.contains("_dye")) return new CraftXpEntry(1, ProfessionType.FARMHAND);

        // === CLERIC: Brewing, enchanting, potions ===
        if (id.equals("brewing_stand")) return new CraftXpEntry(8, ProfessionType.CLERIC);
        if (id.equals("enchanting_table")) return new CraftXpEntry(12, ProfessionType.CLERIC);
        if (id.equals("bookshelf") || id.equals("chiseled_bookshelf")) return new CraftXpEntry(4, ProfessionType.CLERIC);
        if (id.equals("ender_eye")) return new CraftXpEntry(8, ProfessionType.CLERIC);
        if (id.equals("ender_chest")) return new CraftXpEntry(10, ProfessionType.CLERIC);
        if (id.equals("end_crystal")) return new CraftXpEntry(15, ProfessionType.CLERIC);
        if (id.equals("blaze_powder")) return new CraftXpEntry(2, ProfessionType.CLERIC);
        if (id.equals("magma_cream")) return new CraftXpEntry(2, ProfessionType.CLERIC);
        if (id.equals("fire_charge")) return new CraftXpEntry(2, ProfessionType.CLERIC);
        if (id.equals("fermented_spider_eye")) return new CraftXpEntry(3, ProfessionType.CLERIC);
        if (id.equals("glistering_melon_slice")) return new CraftXpEntry(4, ProfessionType.CLERIC);
        if (id.equals("book")) return new CraftXpEntry(2, ProfessionType.CLERIC);
        if (id.equals("writable_book")) return new CraftXpEntry(3, ProfessionType.CLERIC);
        if (id.equals("paper")) return new CraftXpEntry(1, ProfessionType.CLERIC);
        if (id.equals("glass_bottle")) return new CraftXpEntry(1, ProfessionType.CLERIC);
        if (id.equals("cauldron")) return new CraftXpEntry(5, ProfessionType.CLERIC);

        // === ADVENTURER: Exploration, maps, transport ===
        if (id.equals("compass") || id.equals("recovery_compass")) return new CraftXpEntry(5, ProfessionType.ADVENTURER);
        if (id.equals("clock")) return new CraftXpEntry(5, ProfessionType.ADVENTURER);
        if (id.equals("map") || id.equals("filled_map")) return new CraftXpEntry(4, ProfessionType.ADVENTURER);
        if (id.equals("spyglass")) return new CraftXpEntry(6, ProfessionType.ADVENTURER);
        if (id.equals("campfire") || id.equals("soul_campfire")) return new CraftXpEntry(3, ProfessionType.ADVENTURER);
        if (id.equals("respawn_anchor")) return new CraftXpEntry(12, ProfessionType.ADVENTURER);
        if (id.equals("lodestone")) return new CraftXpEntry(15, ProfessionType.ADVENTURER);
        if (id.equals("conduit")) return new CraftXpEntry(20, ProfessionType.ADVENTURER);
        if (id.equals("beacon")) return new CraftXpEntry(25, ProfessionType.ADVENTURER);

        return null;
    }

    // === EXPLICIT OVERRIDES ===

    private static void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        registerOverrides();
    }

    private static void registerOverrides() {
        // --- Smith: Anvil & Smithing ---
        put(Items.ANVIL, 10, ProfessionType.SMITH);
        put(Items.SMITHING_TABLE, 5, ProfessionType.SMITH);
        put(Items.GRINDSTONE, 4, ProfessionType.SMITH);
        put(Items.STONECUTTER, 4, ProfessionType.SMITH);
        // Chain is not craftable in vanilla, skip
        put(Items.LIGHTNING_ROD, 5, ProfessionType.SMITH);
        put(Items.HEAVY_CORE, 15, ProfessionType.SMITH);
        put(Items.MACE, 20, ProfessionType.SMITH);

        // --- MineCrafter: Crafting stations & high-value ---
        put(Items.CRAFTING_TABLE, 2, ProfessionType.MINECRAFTER);
        put(Items.FURNACE, 3, ProfessionType.MINECRAFTER);
        put(Items.BLAST_FURNACE, 6, ProfessionType.MINECRAFTER);
        put(Items.SMOKER, 4, ProfessionType.MINECRAFTER);
        put(Items.CARTOGRAPHY_TABLE, 3, ProfessionType.MINECRAFTER);
        put(Items.LOOM, 3, ProfessionType.MINECRAFTER);
        put(Items.FLETCHING_TABLE, 3, ProfessionType.MINECRAFTER);
        put(Items.BUCKET, 4, ProfessionType.MINECRAFTER);
        put(Items.FLINT_AND_STEEL, 3, ProfessionType.MINECRAFTER);
        put(Items.SHEARS, 3, ProfessionType.MINECRAFTER);
        put(Items.JUKEBOX, 10, ProfessionType.MINECRAFTER);
        put(Items.NOTE_BLOCK, 2, ProfessionType.MINECRAFTER);
        put(Items.LECTERN, 4, ProfessionType.MINECRAFTER);
        put(Items.TRAPPED_CHEST, 3, ProfessionType.MINECRAFTER);

        // --- Farmhand: Nature & farming overrides ---
        put(Items.BREAD, 3, ProfessionType.FARMHAND);
        put(Items.COOKIE, 2, ProfessionType.FARMHAND);
        put(Items.MELON_SEEDS, 1, ProfessionType.FARMHAND);
        put(Items.PUMPKIN_SEEDS, 1, ProfessionType.FARMHAND);
        put(Items.WHEAT, 1, ProfessionType.FARMHAND);

        // --- Cleric: Overrides for special items ---
        put(Items.SOUL_LANTERN, 4, ProfessionType.CLERIC);

        // --- Adventurer: Overrides ---
        put(Items.ENDER_PEARL, 5, ProfessionType.ADVENTURER);

        // --- mlkyMC mod items ---
        // Adventurer
        put(ModItems.WAYSTONE_SHARD.get(), 5, ProfessionType.ADVENTURER);
        put(ModItems.WAYFINDER_COMPASS.get(), 10, ProfessionType.ADVENTURER);
        put(ModItems.WARP_STONE.get(), 8, ProfessionType.ADVENTURER);
        put(ModItems.GRAPPLING_HOOK.get(), 12, ProfessionType.ADVENTURER);
        put(ModItems.GRAPPLING_HOOK_AMMO.get(), 2, ProfessionType.ADVENTURER);
        put(ModItems.DIMENSION_COMPASS.get(), 15, ProfessionType.ADVENTURER);

        // Cleric
        put(ModItems.BLESSED_EMBER.get(), 5, ProfessionType.CLERIC);
        put(ModItems.TOTEM_OF_RESURRECTION.get(), 20, ProfessionType.CLERIC);
        put(ModItems.HOLY_WATER.get(), 8, ProfessionType.CLERIC);
        put(ModItems.BLESSING_SCROLL.get(), 6, ProfessionType.CLERIC);

        // Farmhand
        put(ModItems.LIVING_ESSENCE.get(), 5, ProfessionType.FARMHAND);
        put(ModItems.GROWTH_FERTILIZER.get(), 3, ProfessionType.FARMHAND);
        put(ModItems.ANIMAL_FEED.get(), 4, ProfessionType.FARMHAND);

        // MineCrafter
        put(ModItems.RESONANT_CORE.get(), 5, ProfessionType.MINECRAFTER);
        put(ModItems.REINFORCED_PICKAXE.get(), 25, ProfessionType.MINECRAFTER);
        put(ModItems.REINFORCED_AXE.get(), 25, ProfessionType.MINECRAFTER);
        put(ModItems.BUILDERS_WAND.get(), 15, ProfessionType.MINECRAFTER);
        put(ModItems.ENDER_POUCH.get(), 12, ProfessionType.MINECRAFTER);

        // Smith
        put(ModItems.TEMPERED_PLATE.get(), 5, ProfessionType.SMITH);
        put(ModItems.WHETSTONE.get(), 6, ProfessionType.SMITH);
        put(ModItems.ARMOR_PLATING.get(), 8, ProfessionType.SMITH);

        // Economy (no class XP for currency items)
        put(ModItems.MILKY_STAR.get(), 0, ProfessionType.MINECRAFTER);
        put(ModItems.STALL_DEED.get(), 0, ProfessionType.MINECRAFTER);
        put(ModItems.MARKET_CATALOG.get(), 0, ProfessionType.MINECRAFTER);

        // Ingot/nugget conversions (low XP to prevent exploits)
        put(Items.IRON_NUGGET, 0, ProfessionType.MINECRAFTER);
        put(Items.GOLD_NUGGET, 0, ProfessionType.MINECRAFTER);
        put(Items.IRON_INGOT, 0, ProfessionType.MINECRAFTER);
        put(Items.GOLD_INGOT, 0, ProfessionType.MINECRAFTER);
        put(Items.COPPER_INGOT, 0, ProfessionType.MINECRAFTER);

        // Block <-> ingot conversions (0 XP to prevent exploits)
        put(Items.QUARTZ_BLOCK, 0, ProfessionType.MINECRAFTER);
        put(Items.SLIME_BLOCK, 0, ProfessionType.MINECRAFTER);
        put(Items.HONEY_BLOCK, 0, ProfessionType.FARMHAND);
        put(Items.HONEYCOMB_BLOCK, 0, ProfessionType.FARMHAND);
        put(Items.DRIED_KELP_BLOCK, 0, ProfessionType.FARMHAND);
        put(Items.MELON, 0, ProfessionType.FARMHAND);
        put(Items.SNOW_BLOCK, 0, ProfessionType.MINECRAFTER);
        put(Items.CLAY, 0, ProfessionType.MINECRAFTER);
        put(Items.GLOWSTONE, 0, ProfessionType.CLERIC);
    }

    private static void put(Item item, int xp, ProfessionType prof) {
        OVERRIDES.put(item, new CraftXpEntry(xp, prof));
    }

    // === Helper predicates ===

    private static boolean isArmor(String id) {
        return id.contains("helmet") || id.contains("chestplate")
                || id.contains("leggings") || id.contains("boots");
    }

    private static boolean isWeapon(String id) {
        return id.contains("sword") || id.contains("axe")
                || id.contains("trident") || id.contains("mace");
    }

    private static boolean isTool(String id) {
        return id.contains("pickaxe") || id.contains("shovel")
                || id.contains("hoe") || (id.contains("axe") && !id.contains("pickaxe"));
    }
}
