package com.mlkymc.spawner;

import com.mlkymc.MlkyMC;
import com.mlkymc.config.MlkyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

public class SpawnerAgingListener {

    private final SpawnerAgingManager manager;

    public SpawnerAgingListener(SpawnerAgingManager manager) {
        this.manager = manager;
    }

    @SubscribeEvent
    public void onMobSpawn(FinalizeSpawnEvent event) {
        if (!MlkyConfig.isSpawnerAgingEnabled()) return;
        if (event.getSpawnType() != EntitySpawnReason.SPAWNER) return;

        // Get the spawner block entity directly from the event
        var spawnerEither = event.getSpawner();
        if (spawnerEither == null) return;

        // Extract the SpawnerBlockEntity (left = BlockEntity, right = Entity like minecart spawner)
        BlockPos spawnerPos = spawnerEither.map(
                blockEntity -> blockEntity instanceof SpawnerBlockEntity ? blockEntity.getBlockPos() : null,
                entity -> null // Ignore minecart spawners
        );
        if (spawnerPos == null) return;

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        String dimension = serverLevel.dimension().identifier().toString();
        String key = SpawnerAgingManager.makeKey(dimension, spawnerPos.getX(), spawnerPos.getY(), spawnerPos.getZ());

        int count = manager.incrementAndGet(key);
        int maxSpawns = MlkyConfig.getSpawnerDefaultMaxSpawns();

        if (count >= maxSpawns) {
            // Break the spawner
            serverLevel.setBlock(spawnerPos, Blocks.AIR.defaultBlockState(), 3);
            manager.remove(key);
            manager.save();

            // Effects
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    spawnerPos.getX() + 0.5, spawnerPos.getY() + 0.5, spawnerPos.getZ() + 0.5,
                    20, 0.3, 0.3, 0.3, 0.05);
            serverLevel.playSound(null, spawnerPos, SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.BLOCKS, 0.5f, 1.0f);

            MlkyMC.LOGGER.info("Spawner at {} in {} expired after {} spawns",
                    spawnerPos.toShortString(), dimension, maxSpawns);
        } else if (count % 50 == 0) {
            manager.save();
        }
    }
}
