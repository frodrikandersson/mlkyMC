package com.mlkymc.classes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Milky Curse — any player wearing gear with conflicting enchants accumulates
 * "Milky Curse" stacks. Periodic random procs apply escalating magical misfires
 * ranging from harmless speed bursts (low stacks) to blindness, self-damage,
 * and inventory drops (high stacks).
 *
 * <p>Detection scans all 6 equipment slots (armor + main/off hand). For each
 * item, count the number of conflicting enchantment pairs (via
 * {@link Enchantment#areCompatible}). A piece with N mutually-exclusive
 * enchantments contributes (N - 1) conflicts.
 *
 * <p>Stack thresholds:
 * <pre>
 * conflicts  stacks
 * 0          0
 * 1          1
 * 2          2
 * 3-4        3
 * 5-6        4
 * 7          5
 * 8          6
 * 9+         7
 * </pre>
 */
public class MilkyCurseHandler {

    private static MilkyCurseHandler INSTANCE;

    public static MilkyCurseHandler getInstance() {
        return INSTANCE;
    }

    /** Convenience accessor for PowerHandler.syncSkillStatuses. */
    public static int getStacks(ServerPlayer player) {
        return INSTANCE != null ? INSTANCE.getOrComputeStacks(player) : 0;
    }

    private final Map<UUID, Integer> curseStackCache = new ConcurrentHashMap<>();

    public MilkyCurseHandler() {
        INSTANCE = this;
    }

    // =========================================================================
    // DETECTION: count conflicting enchants across all 6 equipment slots
    // =========================================================================

    private int getOrComputeStacks(ServerPlayer player) {
        return curseStackCache.computeIfAbsent(player.getUUID(), u -> computeStacksFor(player));
    }

    private int computeStacksFor(ServerPlayer player) {
        int totalConflicts = 0;
        // Armor slots (always counted)
        totalConflicts += computePieceConflicts(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
        totalConflicts += computePieceConflicts(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
        totalConflicts += computePieceConflicts(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
        totalConflicts += computePieceConflicts(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
        // Main/off hand — only if the item is actual equipment (tool/weapon/shield), not a book
        totalConflicts += computePieceConflicts(filterHeldItem(player.getMainHandItem()));
        totalConflicts += computePieceConflicts(filterHeldItem(player.getOffhandItem()));
        return conflictsToStacks(totalConflicts);
    }

    /**
     * Return the stack if it's a real equipment item (damageable weapon/tool/shield/armor).
     * Return empty for enchanted books and other non-equipment held items.
     */
    private static ItemStack filterHeldItem(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (stack.is(Items.ENCHANTED_BOOK)) return ItemStack.EMPTY;
        // Damageable items are weapons/tools/armor/shields. Food, blocks, and misc items aren't.
        if (!stack.isDamageableItem()) return ItemStack.EMPTY;
        return stack;
    }

    /**
     * Count conflict pairs on a single item. Builds a connected-components graph:
     * each enchant is a node, each incompatibility is an edge. For every component
     * of size N ≥ 2, adds (N - 1) to the count.
     */
    private static int computePieceConflicts(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchants.isEmpty()) return 0;

        List<net.minecraft.core.Holder<Enchantment>> list = new ArrayList<>();
        for (var entry : enchants.entrySet()) list.add(entry.getKey());
        if (list.size() < 2) return 0;

        int n = list.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        // Union any two enchants that are incompatible with each other
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!Enchantment.areCompatible(list.get(i), list.get(j))) {
                    union(parent, i, j);
                }
            }
        }

        // Count component sizes. For each component of size >= 2, contribute (size - 1).
        int[] sizes = new int[n];
        for (int i = 0; i < n; i++) sizes[find(parent, i)]++;
        int conflicts = 0;
        for (int s : sizes) {
            if (s >= 2) conflicts += (s - 1);
        }
        return conflicts;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }

    private static int conflictsToStacks(int conflicts) {
        if (conflicts <= 0) return 0;
        if (conflicts == 1) return 1;
        if (conflicts == 2) return 2;
        if (conflicts <= 4) return 3;
        if (conflicts <= 6) return 4;
        if (conflicts == 7) return 5;
        if (conflicts == 8) return 6;
        return 7;
    }

    // =========================================================================
    // CACHE INVALIDATION
    // =========================================================================

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            curseStackCache.remove(sp.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            curseStackCache.remove(sp.getUUID());
        }
    }

    // =========================================================================
    // TICK: per-second random proc roll
    // =========================================================================

    // Per-proc message buffer — allows multiple effects in the same proc to combine
    // their messages into a single action bar line so neither gets overwritten.
    private final java.util.List<String> pendingMessages = new java.util.ArrayList<>();

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (sl.getGameTime() % 20 != 0) return; // once per second

        for (ServerPlayer player : sl.players()) {
            if (player.isCreative() || player.isSpectator()) continue;

            int stacks = getOrComputeStacks(player);
            if (stacks <= 0) continue;

            double chance = procChanceForStacks(stacks);
            if (player.getRandom().nextDouble() < chance) {
                pendingMessages.clear();
                fireRandomEffect(player, sl, stacks);

                int firstMessages = pendingMessages.size();

                // Tier 7+: 60% chance to cascade a second effect from the same pool
                boolean cascadeRolled = false;
                boolean cascadeFired = false;
                if (stacks >= 7) {
                    cascadeRolled = true;
                    if (player.getRandom().nextDouble() < 0.60) {
                        cascadeFired = true;
                        fireRandomEffect(player, sl, stacks);
                    }
                }

                int totalMessages = pendingMessages.size();
                com.mlkymc.MlkyMC.LOGGER.info(
                        "[MilkyCurseDebug] {} stacks={} firstMsgs={} cascadeRolled={} cascadeFired={} totalMsgs={}",
                        player.getName().getString(), stacks, firstMessages, cascadeRolled, cascadeFired, totalMessages);

                // Send the combined message to the player
                if (!pendingMessages.isEmpty()) {
                    String combined = String.join("  +  ", pendingMessages);
                    player.displayClientMessage(
                            Component.literal(combined).withColor(0xFFAA88FF), true);
                    pendingMessages.clear();
                }
            }
        }
    }

    private static double procChanceForStacks(int stacks) {
        if (stacks <= 2) return 1.0 / 60.0;  // avg 1min
        if (stacks <= 4) return 1.0 / 45.0;  // avg 45s
        if (stacks <= 6) return 1.0 / 30.0;  // avg 30s
        return 1.0 / 15.0;                    // avg 15s
    }

    // =========================================================================
    // EFFECT POOL — picks uniformly from the pool unlocked at the player's tier
    // =========================================================================

    private enum Effect {
        SPEED_BURST, JUMP_BURST,        // tier 1
        HUNGER,                          // tier 2
        LEVITATION_SHORT,                // tier 3
        LEVITATION_LONG, TELEPORT,       // tier 4
        WEAKNESS_FATIGUE, SELF_DAMAGE,   // tier 5
        HOTBAR_SHUFFLE, DROP_ITEM,       // tier 6
        BLINDNESS                        // tier 7
    }

    private void fireRandomEffect(ServerPlayer player, ServerLevel sl, int stacks) {
        Effect chosen = pickWeightedEffect(stacks);
        applyEffect(player, sl, chosen);
        // Visual + subtle note block sound (pitch varies per proc for variety)
        sl.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(),
                20, 0.5, 1.0, 0.5, 0.05);
        float pitch = 0.5f + ThreadLocalRandom.current().nextFloat() * 0.5f; // 0.5 - 1.0
        sl.playSound(null, player.blockPosition(),
                SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.4f, pitch);
    }

    /**
     * Picks an effect from the unlocked pool using stack-scaled weights.
     * At low stacks, positives (speed/jump bursts) are common. At high stacks,
     * positives become rare and negatives dominate the roll.
     */
    private static Effect pickWeightedEffect(int stacks) {
        List<Effect> pool = poolForStacks(stacks);
        // Positives weight: starts at 5 (low stacks favor positives), drops to 1 at 7+ stacks.
        // Negatives weight: always 5.
        int positiveWeight;
        if (stacks <= 2) positiveWeight = 5;
        else if (stacks <= 4) positiveWeight = 3;
        else if (stacks <= 6) positiveWeight = 2;
        else positiveWeight = 1;
        final int posW = positiveWeight;

        int totalWeight = 0;
        for (Effect e : pool) totalWeight += weightFor(e, posW);

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int acc = 0;
        for (Effect e : pool) {
            acc += weightFor(e, posW);
            if (roll < acc) return e;
        }
        return pool.get(pool.size() - 1); // fallback (shouldn't reach)
    }

    private static int weightFor(Effect e, int positiveWeight) {
        return switch (e) {
            case SPEED_BURST, JUMP_BURST -> positiveWeight;
            default -> 5;
        };
    }

    private static List<Effect> poolForStacks(int stacks) {
        List<Effect> pool = new ArrayList<>();
        // Tier 1 — always unlocked
        pool.add(Effect.SPEED_BURST);
        pool.add(Effect.JUMP_BURST);
        if (stacks >= 2) pool.add(Effect.HUNGER);
        if (stacks >= 3) pool.add(Effect.LEVITATION_SHORT);
        if (stacks >= 4) {
            pool.add(Effect.LEVITATION_LONG);
            pool.add(Effect.TELEPORT);
        }
        if (stacks >= 5) {
            pool.add(Effect.WEAKNESS_FATIGUE);
            pool.add(Effect.SELF_DAMAGE);
        }
        if (stacks >= 6) {
            pool.add(Effect.HOTBAR_SHUFFLE);
            pool.add(Effect.DROP_ITEM);
        }
        if (stacks >= 7) pool.add(Effect.BLINDNESS);
        return pool;
    }

    private void applyEffect(ServerPlayer player, ServerLevel sl, Effect effect) {
        switch (effect) {
            case SPEED_BURST -> {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 1, false, true, true));
                surge(player, "Milky Curse: Speed surge!");
            }
            case JUMP_BURST -> {
                player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 100, 1, false, true, true));
                surge(player, "Milky Curse: Jump surge!");
            }
            case HUNGER -> {
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 80, 1, false, true, true));
                surge(player, "Milky Curse: Hunger surge!");
            }
            case LEVITATION_SHORT -> {
                applyLevitationToSelfAndNearby(player, sl, 80);
                surge(player, "Milky Curse: Levitation surge!");
            }
            case LEVITATION_LONG -> {
                applyLevitationToSelfAndNearby(player, sl, 140);
                surge(player, "Milky Curse: Prolonged levitation!");
            }
            case TELEPORT -> {
                if (tryRandomSafeTeleport(player, sl)) {
                    surge(player, "Milky Curse: Reality slip!");
                } else {
                    // Couldn't find a safe spot in 8 attempts — still queue a message
                    // so cascading procs aren't silently swallowed
                    surge(player, "Milky Curse: Reality slip fizzles!");
                }
            }
            case WEAKNESS_FATIGUE -> {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 1, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 140, 1, false, true, true));
                surge(player, "Milky Curse: Weakening aura!");
            }
            case SELF_DAMAGE -> {
                player.hurtServer(sl, sl.damageSources().magic(), 4.0f);
                surge(player, "Milky brainpower!");
            }
            case HOTBAR_SHUFFLE -> {
                // Always swap the currently-selected main-hand slot with another random hotbar slot
                int held = player.getInventory().getSelectedSlot();
                int other;
                do { other = ThreadLocalRandom.current().nextInt(9); } while (other == held);
                ItemStack tmp = player.getInventory().getItem(held).copy();
                player.getInventory().setItem(held, player.getInventory().getItem(other).copy());
                player.getInventory().setItem(other, tmp);
                surge(player, "Milky Curse: Hand slipped!");
            }
            case DROP_ITEM -> {
                dropRandomInventoryItem(player, sl);
            }
            case BLINDNESS -> {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0, false, true, true));
                surge(player, "Milky Curse: Darkness!");
            }
        }
    }

    /** Queue a message into the pending buffer for the current proc tick. */
    private void surge(ServerPlayer player, String msg) {
        pendingMessages.add(msg);
    }

    private static void applyLevitationToSelfAndNearby(ServerPlayer player, ServerLevel sl, int durationTicks) {
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, durationTicks, 0, false, true, true));
        var nearby = sl.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(1.0),
                e -> e != player && e.isAlive());
        for (var entity : nearby) {
            entity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, durationTicks, 0, false, true, true));
        }
    }

    /**
     * Pick a random angle + distance 3-5 from the player, validate the landing spot
     * (full solid block below, air at destination + above, no lava/water within 2 blocks),
     * and teleport if successful. Retries up to 8 times before giving up.
     */
    private static boolean tryRandomSafeTeleport(ServerPlayer player, ServerLevel sl) {
        var rng = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            int dist = 3 + rng.nextInt(3); // 3, 4, or 5
            int dx = (int) Math.round(Math.cos(angle) * dist);
            int dz = (int) Math.round(Math.sin(angle) * dist);
            BlockPos target = player.blockPosition().offset(dx, 0, dz);
            if (isSafeTeleportSpot(sl, target)) {
                player.teleportTo(sl, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                        java.util.Set.of(), player.getYRot(), player.getXRot(), false);
                return true;
            }
        }
        return false;
    }

    private static boolean isSafeTeleportSpot(ServerLevel sl, BlockPos target) {
        // Need a solid full block under the target
        var below = sl.getBlockState(target.below());
        if (!below.isFaceSturdy(sl, target.below(), Direction.UP)) return false;
        // Target and block above must be passable
        if (!sl.getBlockState(target).isAir() && !sl.getBlockState(target).canBeReplaced()) return false;
        if (!sl.getBlockState(target.above()).isAir() && !sl.getBlockState(target.above()).canBeReplaced()) return false;
        // No lava or water within 2 blocks
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    var fluid = sl.getFluidState(target.offset(dx, dy, dz));
                    if (!fluid.isEmpty()) return false;
                }
            }
        }
        return true;
    }

    private void dropRandomInventoryItem(ServerPlayer player, ServerLevel sl) {
        var inv = player.getInventory();
        List<Integer> candidates = new ArrayList<>();
        // Hotbar is slots 0-8, main inventory 9-35
        for (int i = 0; i < 36; i++) {
            if (!inv.getItem(i).isEmpty()) candidates.add(i);
        }
        if (candidates.isEmpty()) {
            surge(player, "Milky Curse: Empty pockets!");
            return;
        }
        int slot = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        ItemStack stack = inv.getItem(slot).copy();
        inv.setItem(slot, ItemStack.EMPTY);
        ItemEntity drop = new ItemEntity(sl, player.getX(), player.getY(), player.getZ(), stack);
        drop.setDefaultPickUpDelay();
        sl.addFreshEntity(drop);
        surge(player, "Milky Curse: Butterfingers!");
    }
}
