package com.mlkymc.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Grappling Hook item.
 * Requires grappling_hook_ammo to fire (like arrows for a bow).
 * Infinity enchant skips ammo. Fire Aspect sets hooked entities on fire.
 */
public class GrapplingHookItem extends Item {

    // Custom enchantment key
    public static final ResourceKey<Enchantment> WIND_BURST =
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("mlkymc", "wind_burst"));

    // Wind Burst tracking
    private static final Map<UUID, double[]> windBurstTrack = new HashMap<>(); // [startY, maxY, enchantLevel]
    private static final Map<UUID, Integer> windBurstArmed = new HashMap<>(); // UUID -> enchant level
    private static final Map<UUID, Long> windBurstArmedTime = new HashMap<>();
    private static final Map<UUID, Long> windBurstCooldown = new HashMap<>();

    public GrapplingHookItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, net.neoforged.neoforge.common.ItemAbility action) {
        return action == net.neoforged.neoforge.common.ItemAbilities.FISHING_ROD_CAST;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        var key = enchantment.getKey();
        if (key == null) return false;
        return key.equals(Enchantments.UNBREAKING)
                || key.equals(Enchantments.MENDING)
                || key.equals(Enchantments.INFINITY)
                || key.equals(Enchantments.FIRE_ASPECT)
                || key.equals(Enchantments.VANISHING_CURSE)
                || key.equals(WIND_BURST);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        // Same set — all appear on enchanting table
        return supportsEnchantment(stack, enchantment);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.fishing != null) {
            // === REEL IN ===
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                FishingHook hook = player.fishing;

                if (hook instanceof GrapplingHookEntity ghe) {
                    if (ghe.isAttachedToEntity()) {
                        // === ENTITY PULL: both meet in the middle ===
                        net.minecraft.world.entity.Entity target = ghe.getAttachedEntity();
                        Vec3 midpoint = player.position().add(target.position()).scale(0.5);

                        Vec3 playerPull = midpoint.subtract(player.position()).normalize().scale(2.0);
                        player.setDeltaMovement(playerPull.x, Math.max(playerPull.y, 0.3), playerPull.z);
                        player.fallDistance = 0;
                        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));

                        Vec3 entityPull = midpoint.subtract(target.position()).normalize().scale(2.0);
                        target.setDeltaMovement(entityPull.x, Math.max(entityPull.y, 0.3), entityPull.z);
                        target.hurtMarked = true;

                        // Fire Aspect: set entity on fire
                        int fireLevel = getEnchantLevel(stack, level, Enchantments.FIRE_ASPECT);
                        if (fireLevel > 0) {
                            target.igniteForSeconds(4 * fireLevel);
                        }

                        stack.hurtAndBreak(1, sp, hand.asEquipmentSlot());

                    } else if (ghe.isFrozen()) {
                        // === BLOCK PULL: pull player to hook ===
                        Vec3 hookPos = hook.position();
                        double dist = player.position().distanceTo(hookPos);

                        if (dist > 2.0) {
                            double speed = Math.min(dist * 0.12 + 1.0, 3.0);
                            Vec3 pull = hookPos.subtract(player.position()).normalize().scale(speed);
                            player.setDeltaMovement(pull.x, Math.max(pull.y, 0.35), pull.z);
                            player.fallDistance = 0;
                            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
                        }

                        stack.hurtAndBreak(1, sp, hand.asEquipmentSlot());
                    }
                    // Rebound: start tracking height gain
                    int windLevel = getEnchantLevel(stack, level, WIND_BURST);
                    if (windLevel > 0 && (ghe.isFrozen() || ghe.isAttachedToEntity())) {
                        UUID pid = sp.getUUID();
                        Long lastUse = windBurstCooldown.get(pid);
                        boolean onCooldown = lastUse != null && (level.getGameTime() - lastUse) < 50; // 2.5 seconds
                        if (!onCooldown) {
                            windBurstTrack.put(pid, new double[]{sp.getY(), sp.getY(), windLevel, level.getGameTime()});
                        }
                    }
                }

                hook.discard();
            }

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL,
                    1.0f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
            player.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ITEM_INTERACT_FINISH);

        } else {
            // === THROW ===
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                // Check for ammo (unless Infinity or Creative)
                boolean hasInfinity = getEnchantLevel(stack, level, Enchantments.INFINITY) > 0;
                boolean isCreative = sp.getAbilities().instabuild;

                if (!hasInfinity && !isCreative) {
                    int ammoSlot = findAmmoSlot(sp);
                    if (ammoSlot == -1) {
                        sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "No grappling hooks! Craft some with 2 Iron Nuggets + String.").withColor(0xFF5555));
                        return InteractionResult.FAIL;
                    }
                    // Consume 1 ammo
                    sp.getInventory().getItem(ammoSlot).shrink(1);
                }

                GrapplingHookEntity hook = new GrapplingHookEntity(sp, level);

                // Fire Aspect: hook entity catches fire visually
                int fireLevel = getEnchantLevel(stack, level, Enchantments.FIRE_ASPECT);
                if (fireLevel > 0) {
                    hook.igniteForSeconds(10);
                }

                level.addFreshEntity(hook);
            }

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL,
                    0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
            player.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ITEM_INTERACT_START);
        }

        return InteractionResult.SUCCESS_SERVER;
    }

    private static int findAmmoSlot(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.GRAPPLING_HOOK_AMMO.get())) return i;
        }
        return -1;
    }

    private static int getEnchantLevel(ItemStack stack, Level level, net.minecraft.resources.ResourceKey<Enchantment> enchantKey) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var holder = registry.get(enchantKey);
        if (holder.isEmpty()) return 0;
        return stack.getEnchantmentLevel(holder.get());
    }

    public static void tickHooks(Level level) {
        if (level.isClientSide()) return;

        // === Freeze detection for active hooks ===
        for (var player : level.players()) {
            if (player.fishing == null) continue;
            if (!(player.fishing instanceof GrapplingHookEntity ghe)) continue;
            if (ghe.isFrozen() || ghe.isAttachedToEntity()) continue;

            if (ghe.tickCount < 4) continue;

            Vec3 vel = ghe.getDeltaMovement();
            boolean slowing = vel.lengthSqr() < 0.5;

            if (slowing && isAdjacentToSolid(level, ghe)) {
                ghe.freeze();
            }
        }

        // === Wind Burst: track height gain + trigger on landing ===
        for (var player : level.players()) {
            if (!(player instanceof ServerPlayer sp)) continue;
            UUID pid = sp.getUUID();

            // Phase 1: Track max height after grapple pull
            double[] track = windBurstTrack.get(pid);
            if (track != null) {
                double startY = track[0];
                int windLevel = (int) track[2];
                long startTick = (long) track[3];
                long ticksElapsed = level.getGameTime() - startTick;

                // Update max Y reached
                if (sp.getY() > track[1]) {
                    track[1] = sp.getY();
                }

                double maxHeightGain = track[1] - startY;

                // Grace period: ignore onGround for first 10 ticks (player needs time to leave ground)
                boolean pastGrace = ticksElapsed > 10;

                // After grace, check if player has landed or is falling
                if (pastGrace && (sp.onGround() || sp.getY() < track[1] - 0.5)) {
                    if (maxHeightGain >= 3.0 && windLevel > 0) {
                        windBurstArmed.put(pid, windLevel);
                        windBurstArmedTime.put(pid, level.getGameTime());
                    }
                    windBurstTrack.remove(pid);
                }

                // Safety timeout — 5 seconds max tracking
                if (ticksElapsed > 100) {
                    if (maxHeightGain >= 3.0 && windLevel > 0) {
                        windBurstArmed.put(pid, windLevel);
                        windBurstArmedTime.put(pid, level.getGameTime());
                    }
                    windBurstTrack.remove(pid);
                }
            }

            // Phase 2: Wind Burst armed — waiting to land
            Integer armedLevel = windBurstArmed.get(pid);
            if (armedLevel != null) {
                Long armedTime = windBurstArmedTime.get(pid);
                // Timeout after 10 seconds
                if (armedTime != null && level.getGameTime() - armedTime > 200) {
                    windBurstArmed.remove(pid);
                    windBurstArmedTime.remove(pid);
                    continue;
                }

                // Keep resetting fall distance while armed — prevents fall damage on the rebound landing
                sp.fallDistance = 0;

                // Trigger on landing
                if (sp.onGround()) {
                    // Launch player upward — stronger per level
                    double launchPower = 0.8 + (armedLevel * 0.4); // Lv1=1.2, Lv2=1.6, Lv3=2.0
                    sp.setDeltaMovement(sp.getDeltaMovement().x, launchPower, sp.getDeltaMovement().z);
                    sp.fallDistance = 0;
                    sp.hurtMarked = true;
                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));

                    // AoE knockback on nearby mobs — radius and strength scale with level
                    double knockRadius = 3.0 + armedLevel; // Lv1=4, Lv2=5, Lv3=6
                    double knockStrength = 0.6 + (armedLevel * 0.3); // Lv1=0.9, Lv2=1.2, Lv3=1.5
                    if (level instanceof ServerLevel sl) {
                        for (var entity : sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                                sp.getBoundingBox().inflate(knockRadius), e -> e != sp)) {
                            Vec3 knockDir = entity.position().subtract(sp.position()).normalize();
                            entity.setDeltaMovement(
                                    knockDir.x * knockStrength,
                                    0.3 + (armedLevel * 0.1),
                                    knockDir.z * knockStrength);
                            entity.hurtMarked = true;
                        }
                    }

                    // Wind burst particles + sound
                    if (level instanceof ServerLevel sl) {
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.GUST_EMITTER_LARGE,
                                sp.getX(), sp.getY(), sp.getZ(),
                                1, 0, 0, 0, 0);
                    }
                    level.playSound(null, sp.blockPosition(),
                            SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.5f, 1.0f);

                    // Set cooldown (2.5 seconds)
                    windBurstCooldown.put(pid, level.getGameTime());
                    windBurstArmed.remove(pid);
                    windBurstArmedTime.remove(pid);
                }
            }
        }
    }

    private static boolean isAdjacentToSolid(Level level, FishingHook hook) {
        net.minecraft.core.BlockPos pos = hook.blockPosition();
        return isSolidBlock(level, pos)
                || isSolidBlock(level, pos.below())
                || isSolidBlock(level, pos.above())
                || isSolidBlock(level, pos.north())
                || isSolidBlock(level, pos.south())
                || isSolidBlock(level, pos.east())
                || isSolidBlock(level, pos.west());
    }

    private static boolean isSolidBlock(Level level, net.minecraft.core.BlockPos pos) {
        var state = level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty();
    }
}
