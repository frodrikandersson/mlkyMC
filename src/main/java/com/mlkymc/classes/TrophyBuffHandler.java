package com.mlkymc.classes;

import com.mlkymc.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Trophy AoE buff system.
 * Trophies on Trophy Bases grant buffs to all players within range.
 * Same trophy type does NOT stack. Different types DO stack.
 *
 * Uses a cached set of trophy base positions instead of scanning 139k blocks.
 */
public class TrophyBuffHandler {

    private static final int BUFF_RADIUS = 30;

    // Cached trophy base positions — updated on place/break
    private static final CopyOnWriteArraySet<BlockPos> trophyBases = new CopyOnWriteArraySet<>();
    private static boolean cacheBuilt = false;

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % 60 != 0) return; // Every 3 seconds

        // One-time cache build on first tick
        if (!cacheBuilt) {
            rebuildCache(level);
            cacheBuilt = true;
        }

        if (trophyBases.isEmpty()) return;

        for (ServerPlayer player : level.players()) {
            Set<Block> activeTrophyTypes = new HashSet<>();
            BlockPos playerPos = player.blockPosition();

            for (BlockPos basePos : trophyBases) {
                if (playerPos.distSqr(basePos) > BUFF_RADIUS * BUFF_RADIUS) continue;

                // Verify base still exists (lazy cleanup)
                if (!level.isLoaded(basePos)) continue;
                if (!level.getBlockState(basePos).is(ModBlocks.TROPHY_BASE.get())) {
                    trophyBases.remove(basePos);
                    continue;
                }

                BlockPos trophyPos = basePos.above();
                Block trophyBlock = level.getBlockState(trophyPos).getBlock();
                if (activeTrophyTypes.contains(trophyBlock)) continue;

                applyTrophyBuff(player, trophyBlock, activeTrophyTypes);
            }
        }
    }

    @SubscribeEvent
    public void onBlockPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().is(ModBlocks.TROPHY_BASE.get())) {
            trophyBases.add(event.getPos().immutable());
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().getBlockState(event.getPos()).is(ModBlocks.TROPHY_BASE.get())) {
            trophyBases.remove(event.getPos());
        }
    }

    /**
     * One-time scan on server start to find existing trophy bases.
     * Only scans chunks near online players — trophies in unloaded chunks
     * are picked up lazily when players move nearby.
     */
    private void rebuildCache(ServerLevel level) {
        var scannedChunks = new java.util.HashSet<Long>();
        for (ServerPlayer player : level.players()) {
            BlockPos center = player.blockPosition();
            int chunkMinX = (center.getX() - 48) >> 4;
            int chunkMaxX = (center.getX() + 48) >> 4;
            int chunkMinZ = (center.getZ() - 48) >> 4;
            int chunkMaxZ = (center.getZ() + 48) >> 4;

            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                    long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                    if (!scannedChunks.add(key)) continue; // skip already scanned
                    if (!level.hasChunk(cx, cz)) continue;
                    var chunk = level.getChunk(cx, cz);
                    for (var be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getBlockPos();
                        if (level.getBlockState(pos).is(ModBlocks.TROPHY_BASE.get())) {
                            trophyBases.add(pos.immutable());
                        }
                    }
                }
            }
        }
    }

    private void applyTrophyBuff(ServerPlayer player, Block trophyBlock, Set<Block> activeTrophyTypes) {
        if (trophyBlock == ModBlocks.TROPHY_WITHER.get()) {
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 80, 1, false, true, true));
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
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 0, false, true, true));
            activeTrophyTypes.add(trophyBlock);
        } else if (trophyBlock == ModBlocks.TROPHY_SPIDER.get()) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false, true));
            activeTrophyTypes.add(trophyBlock);
        } else if (trophyBlock == ModBlocks.TROPHY_PHANTOM.get()) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, true, true));
            player.resetStat(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_REST));
            activeTrophyTypes.add(trophyBlock);
        } else if (trophyBlock == ModBlocks.TROPHY_WITCH.get()) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 80, 0, false, true, true));
            activeTrophyTypes.add(trophyBlock);
        }
    }
}
