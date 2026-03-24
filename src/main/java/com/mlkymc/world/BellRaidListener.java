package com.mlkymc.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom raid system triggered by ringing a bell with Bad Omen.
 * Spawns waves of hostile mobs that path toward the bell.
 * Lasts 30 minutes, no village required.
 */
public class BellRaidListener {

    // Active raids: bell position key -> RaidData
    private static final Map<String, RaidData> activeRaids = new ConcurrentHashMap<>();

    private static class RaidData {
        final BlockPos bellPos;
        final String dimension;
        final int omenLevel; // 1-5
        final long startTick;
        int currentWave = 0;
        long lastWaveTick;
        final List<Mob> spawnedMobs = new ArrayList<>();

        RaidData(BlockPos bellPos, String dimension, int omenLevel, long startTick) {
            this.bellPos = bellPos;
            this.dimension = dimension;
            this.omenLevel = omenLevel;
            this.startTick = startTick;
            this.lastWaveTick = startTick;
        }
    }

    // Wave definitions matching vanilla raid composition
    // Each wave entry: {EntityType, count} — scaled by omen level
    // Omen 1: waves 1-3, Omen 2: waves 1-4, Omen 3-5: waves 1-5 with more mobs
    private static final long WAVE_TIMEOUT = 2400; // 2 minutes per wave timeout
    private static final long RAID_DURATION = 24000; // 20 minutes

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        var state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof net.minecraft.world.level.block.BellBlock)) return;

        boolean hasOmen = player.hasEffect(MobEffects.BAD_OMEN)
                || player.hasEffect(MobEffects.RAID_OMEN);
        if (!hasOmen) return;

        // Get omen level
        int omenLevel = 0;
        var badOmen = player.getEffect(MobEffects.BAD_OMEN);
        var raidOmen = player.getEffect(MobEffects.RAID_OMEN);
        if (badOmen != null) omenLevel = Math.max(omenLevel, badOmen.getAmplifier() + 1);
        if (raidOmen != null) omenLevel = Math.max(omenLevel, raidOmen.getAmplifier() + 1);
        omenLevel = Math.max(1, Math.min(5, omenLevel));

        // Check if raid already active at this bell
        String key = makeKey(level, pos);
        if (activeRaids.containsKey(key)) {
            player.sendSystemMessage(Component.literal("A raid is already active here!").withColor(0xFF5555));
            return;
        }

        // Remove omen
        player.removeEffect(MobEffects.BAD_OMEN);
        player.removeEffect(MobEffects.RAID_OMEN);

        // Start custom raid
        RaidData raid = new RaidData(pos, level.dimension().identifier().toString(), omenLevel, level.getGameTime());
        activeRaids.put(key, raid);

        // Announce
        for (var p : level.players()) {
            p.sendSystemMessage(Component.literal("A raid has begun! (Level " + omenLevel + ")")
                    .withColor(0xFF0000));
            p.sendSystemMessage(Component.literal("Defend the bell at " +
                    pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "!")
                    .withColor(0xFFAA00));
        }

        // Sound
        level.playSound(null, pos, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0f, 1.0f);

        // Spawn first wave immediately
        spawnWave(level, raid);

        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % 20 != 0) return; // Check every second

        String dim = level.dimension().identifier().toString();
        var iter = activeRaids.entrySet().iterator();

        while (iter.hasNext()) {
            var entry = iter.next();
            RaidData raid = entry.getValue();
            if (!raid.dimension.equals(dim)) continue;

            long elapsed = level.getGameTime() - raid.startTick;

            // Raid expired (20 minutes) — despawn all remaining enemies
            if (elapsed > RAID_DURATION) {
                endRaid(level, raid, false);
                iter.remove();
                continue;
            }

            // Clean up dead mobs
            raid.spawnedMobs.removeIf(mob -> !mob.isAlive());

            int totalWaves = getTotalWaves(raid.omenLevel);

            // Check if current wave is cleared or timed out
            boolean waveClear = raid.spawnedMobs.isEmpty() && raid.currentWave > 0;
            long ticksSinceLastWave = level.getGameTime() - raid.lastWaveTick;
            boolean waveTimedOut = ticksSinceLastWave >= WAVE_TIMEOUT && raid.currentWave > 0;

            // On timeout: spawn next wave WITHOUT discarding remaining mobs
            boolean shouldSpawnNext = waveClear || waveTimedOut;

            if (raid.currentWave < totalWaves && shouldSpawnNext) {
                if (raid.currentWave > 0) {
                    for (var p : level.players()) {
                        if (p.blockPosition().distSqr(raid.bellPos) < 100 * 100) {
                            p.sendSystemMessage(Component.literal("Wave " + raid.currentWave + " cleared!")
                                    .withColor(0x55FF55));
                        }
                    }
                }
                spawnWave(level, raid);
            }

            // All waves complete and all mobs dead = victory
            if (raid.currentWave >= totalWaves && raid.spawnedMobs.isEmpty()) {
                endRaid(level, raid, true);
                iter.remove();
                continue;
            }

            // Find nearest player once, then retarget all untargeted mobs
            ServerPlayer nearestPlayer = null;
            double nearestDist = Double.MAX_VALUE;
            for (var p : level.players()) {
                double d = p.distanceToSqr(Vec3.atCenterOf(raid.bellPos));
                if (d < nearestDist && d < 60 * 60) {
                    nearestPlayer = p;
                    nearestDist = d;
                }
            }
            for (Mob mob : raid.spawnedMobs) {
                if (mob.getTarget() == null || !mob.getTarget().isAlive()) {
                    if (nearestPlayer != null) {
                        mob.setTarget(nearestPlayer);
                    } else {
                        mob.getNavigation().moveTo(
                                raid.bellPos.getX() + 0.5,
                                raid.bellPos.getY(),
                                raid.bellPos.getZ() + 0.5, 1.0);
                    }
                }
            }
        }
    }

    /**
     * Total waves based on omen level (matching vanilla scaling).
     * Omen 1: 3 waves, Omen 2: 4 waves, Omen 3-5: 5 waves
     */
    private int getTotalWaves(int omenLevel) {
        return switch (omenLevel) {
            case 1 -> 3;
            case 2 -> 4;
            default -> 5;
        };
    }

    /**
     * Get the mobs to spawn for a given wave and omen level.
     * Based on vanilla raid composition with illager mobs.
     */
    private List<EntityType<?>> getWaveMobs(int wave, int omenLevel) {
        List<EntityType<?>> mobs = new ArrayList<>();

        switch (wave) {
            case 1: // Pillagers + Vindicator
                addMobs(mobs, EntityType.PILLAGER, 4);
                if (omenLevel >= 2) addMobs(mobs, EntityType.VINDICATOR, 1);
                break;
            case 2: // More Pillagers + Vindicator + Witch
                addMobs(mobs, EntityType.PILLAGER, 3 + omenLevel);
                addMobs(mobs, EntityType.VINDICATOR, 1);
                if (omenLevel >= 3) addMobs(mobs, EntityType.WITCH, 1);
                break;
            case 3: // Pillagers + Vindicators + Ravager
                addMobs(mobs, EntityType.PILLAGER, 4 + omenLevel);
                addMobs(mobs, EntityType.VINDICATOR, 1 + (omenLevel >= 3 ? 1 : 0));
                addMobs(mobs, EntityType.RAVAGER, 1);
                break;
            case 4: // Heavy wave
                addMobs(mobs, EntityType.PILLAGER, 4 + omenLevel);
                addMobs(mobs, EntityType.VINDICATOR, 2);
                addMobs(mobs, EntityType.WITCH, 1 + (omenLevel >= 4 ? 1 : 0));
                addMobs(mobs, EntityType.RAVAGER, 1);
                if (omenLevel >= 4) addMobs(mobs, EntityType.EVOKER, 1);
                break;
            case 5: // Boss wave
                addMobs(mobs, EntityType.PILLAGER, 4 + omenLevel);
                addMobs(mobs, EntityType.VINDICATOR, 3);
                addMobs(mobs, EntityType.EVOKER, 1 + (omenLevel >= 5 ? 1 : 0));
                addMobs(mobs, EntityType.WITCH, 2);
                addMobs(mobs, EntityType.RAVAGER, 1 + (omenLevel >= 5 ? 1 : 0));
                break;
        }

        return mobs;
    }

    private void addMobs(List<EntityType<?>> list, EntityType<?> type, int count) {
        for (int i = 0; i < count; i++) list.add(type);
    }

    private void spawnWave(ServerLevel level, RaidData raid) {
        raid.currentWave++;
        raid.lastWaveTick = level.getGameTime();

        List<EntityType<?>> mobsToSpawn = getWaveMobs(raid.currentWave, raid.omenLevel);
        var random = ThreadLocalRandom.current();

        for (EntityType<?> type : mobsToSpawn) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 20 + random.nextDouble() * 15;
            int spawnX = raid.bellPos.getX() + (int) (Math.cos(angle) * dist);
            int spawnZ = raid.bellPos.getZ() + (int) (Math.sin(angle) * dist);
            int spawnY = level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnX, spawnZ);

            var entity = type.create(level, null, new BlockPos(spawnX, spawnY, spawnZ),
                    EntitySpawnReason.EVENT, true, false);

            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
                mob.addTag("mlkymc_raid_mob");
                level.addFreshEntity(mob);
                raid.spawnedMobs.add(mob);

                mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        MobEffects.GLOWING, 6000, 0, false, false, false));
            }
        }

        int totalWaves = getTotalWaves(raid.omenLevel);
        for (var p : level.players()) {
            if (p.blockPosition().distSqr(raid.bellPos) < 100 * 100) {
                p.sendSystemMessage(Component.literal("Wave " + raid.currentWave + "/" + totalWaves +
                        " — " + mobsToSpawn.size() + " enemies approaching!").withColor(0xFF5555));
            }
        }

        level.playSound(null, raid.bellPos, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5f, 1.0f);
    }

    private void endRaid(ServerLevel level, RaidData raid, boolean victory) {
        // Kill remaining mobs
        for (Mob mob : raid.spawnedMobs) {
            if (mob.isAlive()) mob.discard();
        }
        raid.spawnedMobs.clear();

        // Announce
        for (var p : level.players()) {
            if (p.blockPosition().distSqr(raid.bellPos) < 100 * 100) {
                if (victory) {
                    p.sendSystemMessage(Component.literal("All raiders defeated! Victory!").withColor(0x55FF55));
                    // Reward: Hero of the Village effect
                    p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            MobEffects.HERO_OF_THE_VILLAGE, 48000, 0, false, true, true)); // 40 minutes
                } else {
                    p.sendSystemMessage(Component.literal("The raid was lost! Enemies overwhelmed the area.").withColor(0xFF5555));
                }
            }
        }

        if (victory) {
            level.playSound(null, raid.bellPos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.HOSTILE, 1.5f, 1.0f);
        }
    }

    private String makeKey(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier().toString() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
