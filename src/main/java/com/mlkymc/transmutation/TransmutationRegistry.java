package com.mlkymc.transmutation;

import com.mlkymc.MlkyMC;
import com.mlkymc.classes.ClassData;
import com.mlkymc.classes.ClassManager;
import com.mlkymc.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardcoded recipe list for the Cauldron Transmutation system. Recipes are
 * iterated in priority order (highest first) when matching a pile of items in
 * a water cauldron. See {@link TransmutationRecipe} for field semantics.
 */
public final class TransmutationRegistry {

    private static final List<TransmutationRecipe> RECIPES = new ArrayList<>();

    private TransmutationRegistry() {}

    /** Returns the full recipe list, sorted by priority descending. */
    public static List<TransmutationRecipe> getRecipes() {
        return RECIPES;
    }

    // Eager init via static block — ensures recipes exist before JEI's registration
    // flow or any other consumer queries getRecipes().
    static {
        populate();
    }

    private static void populate() {

        // ---- R1: Soul Fracture Cleanse ----
        // 64 Milky Stars → remove 1 Soul Fracture stack. Highest priority so that the
        // same 64 stars can't accidentally match a different recipe. Guarded by a
        // canApply predicate so the recipe is only considered when the player actually
        // has stacks to remove — prevents wasteful casts on a fresh player.
        RECIPES.add(new TransmutationRecipe(
                "soul_fracture_cleanse",
                /* priority */ 100,
                /* clericLevelRequired */ 30,
                inputs(ModItems.MILKY_STAR.get(), 64),
                /* output */ ItemStack.EMPTY,
                /* sideEffect */ player -> {
                    ClassManager mgr = MlkyMC.getClassManager();
                    if (mgr == null) return;
                    ClassData data = mgr.getOrCreate(player);
                    int remaining = data.removeSoulFractureStack();
                    mgr.save();
                    player.displayClientMessage(Component.literal(
                            "Soul Fracture cleansed! " + remaining + "/5 remaining")
                            .withColor(0x55FFFF), true);
                },
                /* canApply */ player -> {
                    ClassManager mgr = MlkyMC.getClassManager();
                    if (mgr == null) return false;
                    return mgr.getOrCreate(player).getSoulFractureStacks() > 0;
                }
        ));

        // ---- R2: Flint → Tempered Plate ----
        // 16 Flint + 16 Milky Stars → 1 Tempered Plate. Smith reagent base recipe.
        RECIPES.add(new TransmutationRecipe(
                "flint_to_tempered_plate",
                /* priority */ 50,
                /* clericLevelRequired */ 30,
                inputs(
                        Items.FLINT, 16,
                        ModItems.MILKY_STAR.get(), 16
                ),
                new ItemStack(ModItems.TEMPERED_PLATE.get()),
                null
        ));

        // ---- R3: Diamond → Blessed Ember ----
        // 4 Diamonds + 32 Milky Stars → 1 Blessed Ember. High-tier Cleric reagent.
        RECIPES.add(new TransmutationRecipe(
                "diamond_to_blessed_ember",
                /* priority */ 50,
                /* clericLevelRequired */ 45,
                inputs(
                        Items.DIAMOND, 4,
                        ModItems.MILKY_STAR.get(), 32
                ),
                new ItemStack(ModItems.BLESSED_EMBER.get()),
                null
        ));

        // ---- R4: Redstone + Ender Pearl → Resonant Core ----
        // 32 Redstone + 1 Ender Pearl + 8 Milky Stars → 1 Resonant Core.
        RECIPES.add(new TransmutationRecipe(
                "redstone_to_resonant_core",
                /* priority */ 50,
                /* clericLevelRequired */ 35,
                inputs(
                        Items.REDSTONE, 32,
                        Items.ENDER_PEARL, 1,
                        ModItems.MILKY_STAR.get(), 8
                ),
                new ItemStack(ModItems.RESONANT_CORE.get()),
                null
        ));

        // ---- R5: Wheat → Living Essence ----
        // 16 Wheat + 16 Milky Stars → 1 Living Essence.
        RECIPES.add(new TransmutationRecipe(
                "wheat_to_living_essence",
                /* priority */ 50,
                /* clericLevelRequired */ 30,
                inputs(
                        Items.WHEAT, 16,
                        ModItems.MILKY_STAR.get(), 16
                ),
                new ItemStack(ModItems.LIVING_ESSENCE.get()),
                null
        ));

        // ---- R6: Bone + Rotten Flesh → Waystone Shard ----
        // 8 Bone + 8 Rotten Flesh + 8 Milky Stars → 1 Waystone Shard.
        // Vanilla crafting recipe is Adventurer-restricted (1 bone + 1 rotten flesh +
        // 1 milky star); the cauldron path charges 8× the inputs as the cross-class tax.
        RECIPES.add(new TransmutationRecipe(
                "bones_to_waystone_shard",
                /* priority */ 50,
                /* clericLevelRequired */ 30,
                inputs(
                        Items.BONE, 8,
                        Items.ROTTEN_FLESH, 8,
                        ModItems.MILKY_STAR.get(), 8
                ),
                new ItemStack(ModItems.WAYSTONE_SHARD.get()),
                null
        ));

        // ---- R7: Gravel → Flint ----
        // 16 Gravel + 16 Milky Stars → 8 Flint. Consistent flint supply without RNG.
        RECIPES.add(new TransmutationRecipe(
                "gravel_to_flint",
                /* priority */ 50,
                /* clericLevelRequired */ 30,
                inputs(
                        Items.GRAVEL, 16,
                        ModItems.MILKY_STAR.get(), 16
                ),
                new ItemStack(Items.FLINT, 8),
                null
        ));

        // ---- R8: Raw Copper → Raw Iron ----
        // 16 Raw Copper + 16 Milky Stars → 8 Raw Iron.
        RECIPES.add(new TransmutationRecipe(
                "raw_copper_to_raw_iron",
                /* priority */ 50,
                /* clericLevelRequired */ 30,
                inputs(
                        Items.RAW_COPPER, 16,
                        ModItems.MILKY_STAR.get(), 16
                ),
                new ItemStack(Items.RAW_IRON, 8),
                null
        ));

        // ---- R9: Raw Iron → Raw Gold ----
        // 16 Raw Iron + 16 Milky Stars → 8 Raw Gold.
        RECIPES.add(new TransmutationRecipe(
                "raw_iron_to_raw_gold",
                /* priority */ 50,
                /* clericLevelRequired */ 30,
                inputs(
                        Items.RAW_IRON, 16,
                        ModItems.MILKY_STAR.get(), 16
                ),
                new ItemStack(Items.RAW_GOLD, 8),
                null
        ));

        // ---- R10: Raw Gold → Diamonds ----
        // 16 Raw Gold + 16 Milky Stars → 2 Diamonds.
        RECIPES.add(new TransmutationRecipe(
                "raw_gold_to_diamond",
                /* priority */ 50,
                /* clericLevelRequired */ 30,
                inputs(
                        Items.RAW_GOLD, 16,
                        ModItems.MILKY_STAR.get(), 16
                ),
                new ItemStack(Items.DIAMOND, 2),
                null
        ));

        // Sort by priority descending so highest-priority recipes are checked first.
        RECIPES.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
    }

    // --- Small helpers to keep the recipe definitions readable ---

    private static Map<Item, Integer> inputs(Item item, int count) {
        Map<Item, Integer> m = new LinkedHashMap<>();
        m.put(item, count);
        return m;
    }

    private static Map<Item, Integer> inputs(Item a, int aCount, Item b, int bCount) {
        Map<Item, Integer> m = new LinkedHashMap<>();
        m.put(a, aCount);
        m.put(b, bCount);
        return m;
    }

    private static Map<Item, Integer> inputs(Item a, int aCount, Item b, int bCount, Item c, int cCount) {
        Map<Item, Integer> m = new LinkedHashMap<>();
        m.put(a, aCount);
        m.put(b, bCount);
        m.put(c, cCount);
        return m;
    }
}
