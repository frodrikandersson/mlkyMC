package com.mlkymc.transmutation;

import com.mlkymc.classes.ClassData;
import com.mlkymc.classes.ClassManager;
import com.mlkymc.classes.ProfessionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for the Cauldron Transmutation system.
 *
 * <p>Polls each online player once per second. If a player whose Cleric profession
 * level is at least 30 is standing inside a water cauldron block, scans the cauldron
 * AABB for dropped ItemEntities, tries to match the pile against the
 * {@link TransmutationRegistry} (in priority order), and if a recipe fires:
 * consumes the inputs, spawns any output item, runs the side effect, and plays
 * sound + particle feedback.
 */
public class TransmutationHandler {

    private final ClassManager classManager;

    public TransmutationHandler(ClassManager classManager) {
        this.classManager = classManager;
    }

    /**
     * Block item pickup while the player is standing inside a water cauldron so
     * dropped ingredients stay in the ritual until the recipe fires. Sneaking
     * bypasses the lock — crouch in the cauldron to retrieve any accidentally-
     * dropped items.
     *
     * <p>Only fires when the player's block position is a water cauldron AND the
     * item entity is inside (or adjacent to) the cauldron block, so unrelated
     * pickups outside the cauldron are unaffected.
     */
    @SubscribeEvent
    public void onItemPickupInCauldron(ItemEntityPickupEvent.Pre event) {
        var player = event.getPlayer();
        // Sneak bypass — crouch to retrieve mis-dropped items from the cauldron
        if (player.isShiftKeyDown()) return;

        BlockPos playerPos = player.blockPosition();
        if (!player.level().getBlockState(playerPos).is(Blocks.WATER_CAULDRON)) return;

        var ie = event.getItemEntity();
        BlockPos iePos = ie.blockPosition();
        // Same block or within a 1-block radius (items can bob slightly out of bounds)
        if (iePos.equals(playerPos) || iePos.distSqr(playerPos) <= 2) {
            event.setCanPickup(net.minecraft.util.TriState.FALSE);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Once per second per player
        if (player.tickCount % 20 != 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Player must be standing inside a water cauldron block
        BlockPos pos = player.blockPosition();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.WATER_CAULDRON)) return;

        // Gate: the player in the cauldron OR any player within 4 blocks must have
        // Cleric profession Lv30+. This allows a high-level Cleric to "officiate"
        // the ritual for a low-level player who is standing in the cauldron. The
        // stricken player receives the effect; the Cleric just needs to be nearby.
        int clericLevel = classManager.getOrCreate(player).getLevel(ProfessionType.CLERIC);
        if (clericLevel < 30) {
            // Check nearby players within 4 blocks for a Cleric Lv30+
            boolean hasOfficiant = false;
            for (var nearby : level.getEntitiesOfClass(ServerPlayer.class,
                    player.getBoundingBox().inflate(4.0), p -> p != player)) {
                if (classManager.getOrCreate(nearby).getLevel(ProfessionType.CLERIC) >= 30) {
                    hasOfficiant = true;
                    clericLevel = classManager.getOrCreate(nearby).getLevel(ProfessionType.CLERIC);
                    break;
                }
            }
            if (!hasOfficiant) return;
        }

        // Scan the cauldron's AABB for dropped items
        AABB aabb = new AABB(pos).inflate(0.2);
        List<ItemEntity> itemEntities = level.getEntitiesOfClass(
                ItemEntity.class, aabb, ItemEntity::isAlive);
        if (itemEntities.isEmpty()) return;

        // Aggregate item counts
        Map<Item, Integer> itemCounts = new HashMap<>();
        for (ItemEntity ie : itemEntities) {
            ItemStack stack = ie.getItem();
            if (stack.isEmpty()) continue;
            itemCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        if (itemCounts.isEmpty()) return;

        // Match recipes in priority order. clericLevel is already set above to the
        // highest available level (the patient's own, or an officiant's if they're nearby).
        for (TransmutationRecipe recipe : TransmutationRegistry.getRecipes()) {
            if (clericLevel < recipe.clericLevelRequired()) continue;
            if (!matches(recipe, itemCounts)) continue;
            // Optional precondition — e.g. Soul Fracture cleanse skips if the player
            // has no stacks to remove, so their Milky Stars stay in the cauldron
            // instead of being consumed for nothing.
            if (recipe.canApply() != null && !recipe.canApply().test(player)) continue;

            // Fire the recipe: consume inputs, spawn output, run side effect, play FX.
            fireRecipe(level, pos, player, recipe, itemEntities);
            return; // only one recipe per tick per cauldron
        }
    }

    // =========================================================================
    // Recipe matching
    // =========================================================================

    private boolean matches(TransmutationRecipe recipe, Map<Item, Integer> itemCounts) {
        for (var entry : recipe.inputs().entrySet()) {
            Integer have = itemCounts.get(entry.getKey());
            if (have == null || have < entry.getValue()) return false;
        }
        return true;
    }

    // =========================================================================
    // Recipe execution
    // =========================================================================

    private void fireRecipe(ServerLevel level, BlockPos pos, ServerPlayer player,
                             TransmutationRecipe recipe, List<ItemEntity> itemEntities) {
        // Consume required inputs from the item entities
        Map<Item, Integer> remainingToConsume = new HashMap<>(recipe.inputs());
        for (ItemEntity ie : itemEntities) {
            if (remainingToConsume.isEmpty()) break;
            ItemStack stack = ie.getItem();
            if (stack.isEmpty()) continue;
            Integer needed = remainingToConsume.get(stack.getItem());
            if (needed == null || needed <= 0) continue;

            int take = Math.min(needed, stack.getCount());
            stack.shrink(take);
            needed -= take;
            if (needed <= 0) {
                remainingToConsume.remove(stack.getItem());
            } else {
                remainingToConsume.put(stack.getItem(), needed);
            }
            if (stack.isEmpty()) {
                ie.discard();
            } else {
                ie.setItem(stack);
            }
        }

        // Spawn output item (if non-empty). Auto-deposit into the casting player's
        // inventory so they don't have to step out of the cauldron to pick it up
        // (the pickup block would prevent that anyway). If their inventory is full,
        // fall back to dropping at their feet where it's reachable after stepping out.
        ItemStack output = recipe.output();
        if (output != null && !output.isEmpty()) {
            ItemStack outputCopy = output.copy();
            if (!player.getInventory().add(outputCopy)) {
                ItemEntity dropped = new ItemEntity(level,
                        player.getX(), player.getY() + 0.5, player.getZ(), outputCopy);
                dropped.setDeltaMovement(0, 0.1, 0);
                level.addFreshEntity(dropped);
            }
        }

        // Run the side effect, if any (e.g. Soul Fracture cleanse)
        if (recipe.sideEffect() != null) {
            recipe.sideEffect().accept(player);
        }

        // Audio + visual feedback
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
        level.sendParticles(ParticleTypes.SOUL,
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                24, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.ENCHANT,
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                16, 0.3, 0.3, 0.3, 0.5);

        // Drain one water level from the cauldron for flavor (makes the ritual a
        // consumable action — refill required before the next cast).
        try {
            BlockState cauldron = level.getBlockState(pos);
            if (cauldron.is(Blocks.WATER_CAULDRON)) {
                int waterLevel = cauldron.getValue(LayeredCauldronBlock.LEVEL);
                if (waterLevel > 1) {
                    level.setBlock(pos, cauldron.setValue(LayeredCauldronBlock.LEVEL, waterLevel - 1), 3);
                } else {
                    level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                }
            }
        } catch (Exception ignored) {}
    }
}
