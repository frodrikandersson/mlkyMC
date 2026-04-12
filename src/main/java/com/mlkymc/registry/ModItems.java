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
            ITEMS.registerSimpleItem("milky_star", () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> MILKY_STAR_JAR_EMPTY =
            ITEMS.registerSimpleItem("milky_star_jar_empty", () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> MILKY_STAR_JAR_HALF =
            ITEMS.registerSimpleItem("milky_star_jar_half", () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> MILKY_STAR_JAR_FULL =
            ITEMS.registerSimpleItem("milky_star_jar_full", () -> new Item.Properties().stacksTo(1));

    // --- Cross-Class Reagents ---
    public static final DeferredItem<Item> WAYSTONE_SHARD =
            ITEMS.registerSimpleItem("waystone_shard", () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> BLESSED_EMBER =
            ITEMS.registerSimpleItem("blessed_ember", () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> LIVING_ESSENCE =
            ITEMS.registerSimpleItem("living_essence", () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> RESONANT_CORE =
            ITEMS.registerSimpleItem("resonant_core", () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> TEMPERED_PLATE =
            ITEMS.registerSimpleItem("tempered_plate", () -> new Item.Properties().stacksTo(64));

    // --- Cleric Soul Altar Items ---
    public static final DeferredItem<Item> TOME_OF_THE_SOUL_WARDEN =
            ITEMS.registerSimpleItem("tome_of_the_soul_warden", () -> new Item.Properties().stacksTo(1));

    // --- Adventurer Items ---
    public static final DeferredItem<Item> WAYFINDER_COMPASS =
            ITEMS.registerSimpleItem("wayfinder_compass", () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> WARP_STONE =
            ITEMS.registerSimpleItem("warp_stone", () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> GRAPPLING_HOOK =
            ITEMS.registerItem("grappling_hook", GrapplingHookItem::new,
                    () -> new Item.Properties().stacksTo(1).durability(384).enchantable(14));

    // Grappling Hook ammo — consumed on use (like arrows for a bow)
    public static final DeferredItem<Item> GRAPPLING_HOOK_AMMO =
            ITEMS.registerSimpleItem("grappling_hook_ammo", () -> new Item.Properties().stacksTo(64));

    // --- Cleric Items ---
    public static final DeferredItem<Item> TOTEM_OF_RESURRECTION =
            ITEMS.registerSimpleItem("totem_of_resurrection", () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> HOLY_WATER =
            ITEMS.registerSimpleItem("holy_water", () -> new Item.Properties().stacksTo(16));

    public static final DeferredItem<Item> BLESSING_SCROLL =
            ITEMS.registerSimpleItem("blessing_scroll", () -> new Item.Properties().stacksTo(16));

    // --- Farmhand Items ---
    public static final DeferredItem<Item> GROWTH_FERTILIZER =
            ITEMS.registerSimpleItem("growth_fertilizer", () -> new Item.Properties().stacksTo(64));

    public static final DeferredItem<Item> ANIMAL_FEED =
            ITEMS.registerSimpleItem("animal_feed", () -> new Item.Properties().stacksTo(16));

    // --- MineCrafter Items ---
    public static final DeferredItem<Item> REINFORCED_PICKAXE =
            ITEMS.registerItem("reinforced_pickaxe", Item::new,
                    () -> net.minecraft.world.item.ToolMaterial.NETHERITE.applyToolProperties(
                            new Item.Properties(),
                            net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE,
                            1.0f, -2.8f, 0.0f));

    public static final DeferredItem<Item> REINFORCED_AXE =
            ITEMS.registerItem("reinforced_axe",
                    props -> new net.minecraft.world.item.AxeItem(
                            net.minecraft.world.item.ToolMaterial.NETHERITE, 5.0f, -3.0f, props),
                    new Item.Properties());

    public static final DeferredItem<Item> BUILDERS_WAND =
            ITEMS.registerSimpleItem("builders_wand", () -> new Item.Properties().stacksTo(1).durability(256));

    public static final DeferredItem<Item> ENDER_POUCH =
            ITEMS.registerSimpleItem("ender_pouch", () -> new Item.Properties().stacksTo(1));

    // --- Smith Items ---
    public static final DeferredItem<Item> WHETSTONE =
            ITEMS.registerSimpleItem("whetstone", () -> new Item.Properties().stacksTo(16));

    public static final DeferredItem<Item> ARMOR_PLATING =
            ITEMS.registerSimpleItem("armor_plating", () -> new Item.Properties().stacksTo(16));

    // --- Economy Items ---
    // STALL_DEED is now a block item — see ModBlocks.STALL_DEED_ITEM

    public static final DeferredItem<Item> MARKET_CATALOG =
            ITEMS.registerSimpleItem("market_catalog", () -> new Item.Properties().stacksTo(1));

    // --- Utility Items ---
    public static final DeferredItem<Item> DIMENSION_COMPASS =
            ITEMS.registerSimpleItem("dimension_compass", () -> new Item.Properties().stacksTo(1));

    // --- Farmhand Exclusive ---
    public static final DeferredItem<Item> SANDWICH =
            ITEMS.register("sandwich", id -> new SandwichItem(new Item.Properties()
                    .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                    .stacksTo(64)
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(8).saturationModifier(0.8f).build())));

    // --- Cleric Exclusive ---
    public static final DeferredItem<Item> CONCOCTION =
            ITEMS.register("concoction", id -> new com.mlkymc.registry.ConcoctionItem(
                    new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(1)
                            .component(net.minecraft.core.component.DataComponents.CONSUMABLE,
                                    net.minecraft.world.item.component.Consumables.defaultDrink().build()),
                    ConcoctionItem.ConcoctionType.DRINKABLE));

    public static final DeferredItem<Item> CONCOCTION_SPLASH =
            ITEMS.register("concoction_splash", id -> new com.mlkymc.registry.ConcoctionItem(
                    new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(1),
                    ConcoctionItem.ConcoctionType.SPLASH));

    public static final DeferredItem<Item> CONCOCTION_LINGERING =
            ITEMS.register("concoction_lingering", id -> new com.mlkymc.registry.ConcoctionItem(
                    new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(1),
                    ConcoctionItem.ConcoctionType.LINGERING));
}
