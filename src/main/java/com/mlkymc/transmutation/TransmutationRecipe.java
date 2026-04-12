package com.mlkymc.transmutation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A single cauldron transmutation recipe.
 *
 * <p>When a player with sufficient Cleric profession level stands inside a water
 * cauldron and enough ItemEntities matching the {@link #inputs} map are sitting
 * inside the cauldron block, the recipe fires: the inputs are consumed, the
 * {@link #output} is spawned (if non-null), and the {@link #sideEffect} runs
 * (if non-null).
 *
 * <p>Priority controls check ordering when multiple recipes could match the same
 * pile — higher values are checked first. Recipes with specific-subset matches
 * (e.g. Soul Fracture cleanse needs ONLY Milky Stars) should have higher priority
 * than more generic recipes so they aren't shadowed.
 *
 * @param id                  unique recipe identifier (for logging / debugging)
 * @param priority            higher = checked first
 * @param clericLevelRequired minimum Cleric profession level to cast
 * @param inputs              required items and their minimum counts (exact match on type, ≥ on count)
 * @param output              item produced when recipe fires (nullable for effect-only recipes)
 * @param sideEffect          callback that runs when the recipe fires, with the casting player (nullable)
 * @param canApply            optional precondition checked before the recipe is considered a match;
 *                            nullable (null = always castable). Used to prevent wasteful casts like
 *                            the Soul Fracture cleanse firing on a player who has no stacks to remove.
 */
public record TransmutationRecipe(
        String id,
        int priority,
        int clericLevelRequired,
        Map<Item, Integer> inputs,
        ItemStack output,
        Consumer<ServerPlayer> sideEffect,
        Predicate<ServerPlayer> canApply
) {
    /** Convenience constructor for recipes with no precondition. */
    public TransmutationRecipe(
            String id,
            int priority,
            int clericLevelRequired,
            Map<Item, Integer> inputs,
            ItemStack output,
            Consumer<ServerPlayer> sideEffect) {
        this(id, priority, clericLevelRequired, inputs, output, sideEffect, null);
    }
}
