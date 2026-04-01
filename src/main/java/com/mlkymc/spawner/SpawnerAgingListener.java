package com.mlkymc.spawner;

import com.mlkymc.MlkyMC;
import com.mlkymc.config.MlkyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.HashSet;
import java.util.Set;

public class SpawnerAgingListener {

    private final SpawnerAgingManager manager;
    // Track recently depleted spawner positions to ignore remaining batch spawns
    private final Set<String> recentlyDepleted = new HashSet<>();

    public SpawnerAgingListener(SpawnerAgingManager manager) {
        this.manager = manager;
    }

    @SubscribeEvent
    public void onMobSpawn(FinalizeSpawnEvent event) {
        if (!MlkyConfig.isSpawnerAgingEnabled()) return;
        if (event.getSpawnType() != EntitySpawnReason.SPAWNER) return;

        var spawnerEither = event.getSpawner();
        if (spawnerEither == null) return;

        BlockPos spawnerPos = spawnerEither.map(
                blockEntity -> blockEntity instanceof SpawnerBlockEntity ? blockEntity.getBlockPos() : null,
                entity -> null
        );
        if (spawnerPos == null) return;

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        String dimension = serverLevel.dimension().identifier().toString();
        String key = SpawnerAgingManager.makeKey(dimension, spawnerPos.getX(), spawnerPos.getY(), spawnerPos.getZ());

        // Skip if this spawner was recently depleted (remaining batch mobs still firing)
        if (recentlyDepleted.contains(key)) {
            event.setCanceled(true);
            return;
        }

        int count = manager.incrementAndGet(key);

        // Check per-mob limit first, fall back to default
        String mobId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(event.getEntity().getType()).getPath();
        int maxSpawns = MlkyConfig.getSpawnerMobLimits().getLimit(mobId,
                MlkyConfig.getSpawnerDefaultMaxSpawns());

        if (count >= maxSpawns) {
            // Reset spawner: set to AIR first to clear old block entity, then place fresh empty spawner
            serverLevel.setBlock(spawnerPos, Blocks.AIR.defaultBlockState(), 3);
            serverLevel.setBlock(spawnerPos, Blocks.SPAWNER.defaultBlockState(), 3);

            manager.remove(key);
            manager.save();
            recentlyDepleted.add(key);

            // Effects
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    spawnerPos.getX() + 0.5, spawnerPos.getY() + 0.5, spawnerPos.getZ() + 0.5,
                    15, 0.3, 0.3, 0.3, 0.02);
            serverLevel.playSound(null, spawnerPos, SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS, 0.5f, 1.0f);

            // Notify nearby players
            for (var player : serverLevel.players()) {
                if (player.distanceToSqr(spawnerPos.getX(), spawnerPos.getY(), spawnerPos.getZ()) < 400) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "A mob spawner has been depleted! Pick it up and insert a new spawn egg.").withColor(0xFFAA00));
                }
            }

            MlkyMC.LOGGER.info("Spawner at {} in {} depleted after {} spawns",
                    spawnerPos.toShortString(), dimension, maxSpawns);
        } else if (count % 50 == 0) {
            manager.save();
        }
    }

    /**
     * When a spawner block is broken by a player, drop it as an item.
     * Vanilla spawners don't drop themselves — we override that here.
     */
    @SubscribeEvent
    public void onSpawnerDrop(BlockDropsEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (event.getState().getBlock() != Blocks.SPAWNER) return;

        // No XP from breaking spawners (since we drop the block itself)
        event.setDroppedExperience(0);

        // Drop a spawner block item
        BlockPos pos = event.getPos();
        ItemStack spawnerItem = new ItemStack(Items.SPAWNER);
        ItemEntity itemEntity = new ItemEntity(sl,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, spawnerItem);
        event.getDrops().add(itemEntity);

        // Clean up aging data and depleted flag for this spawner
        String dimension = sl.dimension().identifier().toString();
        String key = SpawnerAgingManager.makeKey(dimension, pos.getX(), pos.getY(), pos.getZ());
        manager.remove(key);
        recentlyDepleted.remove(key);
        manager.save();
    }

    /**
     * When a spawn egg is used on a spawner, clear the depleted flag
     * and force a visual resync.
     */
    @SubscribeEvent
    public void onSpawnEggUse(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        BlockPos pos = event.getPos();
        if (sl.getBlockState(pos).getBlock() != Blocks.SPAWNER) return;

        ItemStack held = event.getEntity().getItemInHand(event.getHand());
        if (!(held.getItem() instanceof net.minecraft.world.item.SpawnEggItem)) return;

        // Clear depleted flag so the spawner can start counting again
        String dim = sl.dimension().identifier().toString();
        recentlyDepleted.remove(SpawnerAgingManager.makeKey(dim, pos.getX(), pos.getY(), pos.getZ()));

        // Schedule a resync 1 tick later (after vanilla processes the egg)
        sl.getServer().execute(() -> {
            var be = sl.getBlockEntity(pos);
            if (be instanceof SpawnerBlockEntity) {
                be.setChanged();
                sl.sendBlockUpdated(pos, sl.getBlockState(pos), sl.getBlockState(pos), 3);
            }
        });
    }
}
