package com.mlkymc.classes;

import com.mlkymc.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Phase 6: Trophy AoE buff system.
 * Trophies on Trophy Bases grant buffs to all players within 8 blocks.
 * Same trophy type does NOT stack. Different types DO stack.
 */
public class TrophyBuffHandler {

    private static final int BUFF_RADIUS = 30;
    private static final int SCAN_RADIUS = 32; // scan area for trophy bases

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % 60 != 0) return; // Every 3 seconds

        for (ServerPlayer player : level.players()) {
            Set<Block> activeTrophyTypes = new HashSet<>();
            BlockPos playerPos = player.blockPosition();

            // Scan for trophy bases near the player
            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                        BlockPos pos = playerPos.offset(dx, dy, dz);
                        if (!level.getBlockState(pos).is(ModBlocks.TROPHY_BASE.get())) continue;

                        // Check block above for a trophy
                        BlockPos trophyPos = pos.above();
                        Block trophyBlock = level.getBlockState(trophyPos).getBlock();

                        // Only apply if within buff radius and not duplicate type
                        if (playerPos.distSqr(trophyPos) > BUFF_RADIUS * BUFF_RADIUS) continue;
                        if (activeTrophyTypes.contains(trophyBlock)) continue; // No stacking same type

                        // Apply buff based on trophy type
                        if (trophyBlock == ModBlocks.TROPHY_WITHER.get()) {
                            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 80, 0, false, true, true));
                            activeTrophyTypes.add(trophyBlock);
                        } else if (trophyBlock == ModBlocks.TROPHY_DRAGON.get()) {
                            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 1, false, true, true));
                            activeTrophyTypes.add(trophyBlock);
                        } else if (trophyBlock == ModBlocks.TROPHY_ELDER_GUARDIAN.get()) {
                            player.addEffect(new MobEffectInstance(MobEffects.HASTE, 80, 0, false, true, true));
                            activeTrophyTypes.add(trophyBlock);
                        } else if (trophyBlock == ModBlocks.TROPHY_WARDEN.get()) {
                            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 80, 0, false, true, true));
                            activeTrophyTypes.add(trophyBlock);
                        } else if (trophyBlock == ModBlocks.TROPHY_BLAZE.get()) {
                            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 80, 0, false, true, true));
                            activeTrophyTypes.add(trophyBlock);
                        } else if (trophyBlock == ModBlocks.TROPHY_ENDER.get()) {
                            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, true, true));
                            activeTrophyTypes.add(trophyBlock);
                        } else if (trophyBlock == ModBlocks.TROPHY_CREEPER.get()) {
                            // Blast Resistance = custom, use Resistance I as substitute
                            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 0, false, true, true));
                            activeTrophyTypes.add(trophyBlock);
                        } else if (trophyBlock == ModBlocks.TROPHY_SPIDER.get()) {
                            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, true, true));
                            activeTrophyTypes.add(trophyBlock);
                        } else if (trophyBlock == ModBlocks.TROPHY_PHANTOM.get()) {
                            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, true, true));
                            activeTrophyTypes.add(trophyBlock);
                        } else if (trophyBlock == ModBlocks.TROPHY_WITCH.get()) {
                            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 80, 0, false, true, true));
                            activeTrophyTypes.add(trophyBlock);
                        }
                    }
                }
            }
        }
    }
}
