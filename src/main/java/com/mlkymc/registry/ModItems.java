package com.mlkymc.registry;

import com.mlkymc.MlkyMC;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All custom items registered by mlkyMC.
 * Uses DeferredRegister.Items which properly handles Item.Properties.setId() in 1.21.11.
 */
public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MlkyMC.MOD_ID);

    // --- Currency & Economy ---
    public static final DeferredItem<Item> MILKY_STAR =
            ITEMS.registerSimpleItem("milky_star", new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> MILKY_STAR_JAR_EMPTY =
            ITEMS.registerSimpleItem("milky_star_jar_empty", new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> MILKY_STAR_JAR_HALF =
            ITEMS.registerSimpleItem("milky_star_jar_half", new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> MILKY_STAR_JAR_FULL =
            ITEMS.registerSimpleItem("milky_star_jar_full", new Item.Properties().stacksTo(1));

    // --- Cross-Class Reagents ---
    public static final DeferredItem<Item> WAYSTONE_SHARD =
            ITEMS.registerSimpleItem("waystone_shard", new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> BLESSED_EMBER =
            ITEMS.registerSimpleItem("blessed_ember", new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> LIVING_ESSENCE =
            ITEMS.registerSimpleItem("living_essence", new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> RESONANT_CORE =
            ITEMS.registerSimpleItem("resonant_core", new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> TEMPERED_PLATE =
            ITEMS.registerSimpleItem("tempered_plate", new Item.Properties().stacksTo(64));

    // --- Adventurer Items ---
    public static final DeferredItem<Item> WAYFINDER_COMPASS =
            ITEMS.registerSimpleItem("wayfinder_compass", new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> WARP_STONE =
            ITEMS.registerSimpleItem("warp_stone", new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> GRAPPLING_HOOK =
            ITEMS.registerSimpleItem("grappling_hook", new Item.Properties().stacksTo(1));

    // --- Cleric Items ---
    public static final DeferredItem<Item> TOTEM_OF_RESURRECTION =
            ITEMS.registerSimpleItem("totem_of_resurrection", new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> HOLY_WATER =
            ITEMS.registerSimpleItem("holy_water", new Item.Properties().stacksTo(16));

    public static final DeferredItem<Item> BLESSING_SCROLL =
            ITEMS.registerSimpleItem("blessing_scroll", new Item.Properties().stacksTo(16));

    // --- Farmhand Items ---
    public static final DeferredItem<Item> GROWTH_FERTILIZER =
            ITEMS.registerSimpleItem("growth_fertilizer", new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> ANIMAL_FEED =
            ITEMS.registerSimpleItem("animal_feed", new Item.Properties().stacksTo(16));

    // --- MineCrafter Items ---
    public static final DeferredItem<Item> REINFORCED_PICKAXE =
            ITEMS.registerSimpleItem("reinforced_pickaxe", new Item.Properties().stacksTo(1).durability(4096));

    public static final DeferredItem<Item> REINFORCED_AXE =
            ITEMS.registerSimpleItem("reinforced_axe", new Item.Properties().stacksTo(1).durability(4096));

    public static final DeferredItem<Item> BUILDERS_WAND =
            ITEMS.registerSimpleItem("builders_wand", new Item.Properties().stacksTo(1).durability(256));

    public static final DeferredItem<Item> ENDER_CHEST_BACKPACK =
            ITEMS.registerSimpleItem("ender_chest_backpack", new Item.Properties().stacksTo(1));

    // --- Smith Items ---
    public static final DeferredItem<Item> WHETSTONE =
            ITEMS.registerSimpleItem("whetstone", new Item.Properties().stacksTo(16));

    public static final DeferredItem<Item> ARMOR_PLATING =
            ITEMS.registerSimpleItem("armor_plating", new Item.Properties().stacksTo(16));

    // --- Economy Items ---
    public static final DeferredItem<Item> STALL_DEED =
            ITEMS.registerSimpleItem("stall_deed", new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> MARKET_CATALOG =
            ITEMS.registerSimpleItem("market_catalog", new Item.Properties().stacksTo(1));
}
