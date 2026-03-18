package com.mlkymc.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles crafted spawners using vanilla spawner + vanilla spawn eggs:
 * 1. Right-click with vanilla spawner item: places spawner marked as "mlkymc_crafted"
 * 2. Right-click crafted spawner with vanilla spawn egg: sets mob type + spawn limit
 * 3. Breaking a crafted spawner drops it (vanilla ones don't drop)
 * 4. Spawner aging: when spawn count reaches limit, reverts to empty spawner
 */
public class SpawnerHandler {

    // Spawn limits per mob type
    private static Map<EntityType<?>, Integer> spawnLimits;

    private void initLimits() {
        if (spawnLimits != null) return;
        spawnLimits = new HashMap<>();

        // Hostile (64 spawns)
        spawnLimits.put(EntityType.ZOMBIE, 64);
        spawnLimits.put(EntityType.SKELETON, 64);
        spawnLimits.put(EntityType.SPIDER, 64);
        spawnLimits.put(EntityType.CAVE_SPIDER, 64);
        spawnLimits.put(EntityType.BLAZE, 64);
        spawnLimits.put(EntityType.SILVERFISH, 64);
        spawnLimits.put(EntityType.MAGMA_CUBE, 64);

        // Neutral farm (128 spawns)
        spawnLimits.put(EntityType.COW, 128);
        spawnLimits.put(EntityType.SHEEP, 128);
        spawnLimits.put(EntityType.CHICKEN, 128);
        spawnLimits.put(EntityType.PIG, 128);
        spawnLimits.put(EntityType.RABBIT, 128);

        // Neutral utility (32 spawns)
        spawnLimits.put(EntityType.WOLF, 32);
        spawnLimits.put(EntityType.BEE, 32);
        spawnLimits.put(EntityType.IRON_GOLEM, 32);

        // Neutral other (64 spawns)
        spawnLimits.put(EntityType.HORSE, 64);
        spawnLimits.put(EntityType.DONKEY, 64);
        spawnLimits.put(EntityType.LLAMA, 64);
        spawnLimits.put(EntityType.FOX, 64);
        spawnLimits.put(EntityType.FROG, 64);
        spawnLimits.put(EntityType.TURTLE, 64);
        spawnLimits.put(EntityType.GOAT, 64);
        spawnLimits.put(EntityType.CAT, 64);
    }

    /**
     * Right-click with vanilla spawner: place and mark as crafted.
     * Right-click crafted spawner with vanilla spawn egg: set mob type.
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        initLimits();

        ItemStack held = player.getMainHandItem();
        BlockPos pos = event.getPos();

        // Case 1: Place vanilla spawner item -> mark as crafted
        if (held.is(Items.SPAWNER)) {
            BlockPos placePos = pos.relative(event.getFace());
            if (level.getBlockState(placePos).isAir()) {
                level.setBlock(placePos, Blocks.SPAWNER.defaultBlockState(), 3);

                if (level.getBlockEntity(placePos) instanceof SpawnerBlockEntity spawnerBE) {
                    CompoundTag data = spawnerBE.getPersistentData();
                    data.putBoolean("mlkymc_crafted", true);
                    data.putInt("mlkymc_spawn_limit", 64);
                    data.putInt("mlkymc_spawn_count", 0);
                    spawnerBE.setChanged();
                }

                if (!player.isCreative()) held.shrink(1);
                player.sendSystemMessage(Component.literal("Spawner placed! Use a Spawn Egg to set the mob type.").withColor(0x55FF55));
                event.setCanceled(true);
            }
            return;
        }

        // Case 2: Apply vanilla spawn egg to crafted spawner
        if (!(held.getItem() instanceof SpawnEggItem spawnEgg)) return;

        if (!level.getBlockState(pos).is(Blocks.SPAWNER)) return;
        if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBE)) return;

        CompoundTag data = spawnerBE.getPersistentData();
        if (!data.getBooleanOr("mlkymc_crafted", false)) return; // Not our spawner, let vanilla handle it

        EntityType<?> mobType = spawnEgg.getType(held);
        BaseSpawner spawner = spawnerBE.getSpawner();
        spawner.setEntityId(mobType, level, level.random, pos);

        int limit = spawnLimits.getOrDefault(mobType, 64);
        data.putInt("mlkymc_spawn_limit", limit);
        data.putInt("mlkymc_spawn_count", 0);
        data.putString("mlkymc_mob_type", EntityType.getKey(mobType).toString());
        spawnerBE.setChanged();

        if (!player.isCreative()) held.shrink(1);

        String mobName = mobType.getDescription().getString();
        player.sendSystemMessage(Component.literal("Spawner set to " + mobName + "! (" + limit + " spawns)")
                .withColor(0x55FF55));
        event.setCanceled(true);
    }

    /**
     * Crafted spawners drop themselves when broken. Vanilla spawners don't.
     */
    @SubscribeEvent
    public void onBlockDrop(BlockDropsEvent event) {
        if (event.getState() == null || !event.getState().is(Blocks.SPAWNER)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (!(level.getBlockEntity(event.getPos()) instanceof SpawnerBlockEntity spawnerBE)) return;

        CompoundTag data = spawnerBE.getPersistentData();
        if (!data.getBooleanOr("mlkymc_crafted", false)) return;

        // Drop the spawner item
        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                level,
                event.getPos().getX() + 0.5,
                event.getPos().getY() + 0.5,
                event.getPos().getZ() + 0.5,
                new ItemStack(Items.SPAWNER)));
    }

    /**
     * Spawner aging: track spawns, revert to empty when limit reached.
     */
    @SubscribeEvent
    public void onMobSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getSpawnType() != EntitySpawnReason.SPAWNER) return;

        BlockPos mobPos = event.getEntity().blockPosition();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos checkPos = mobPos.offset(dx, dy, dz);
                    if (!level.getBlockState(checkPos).is(Blocks.SPAWNER)) continue;
                    if (!(level.getBlockEntity(checkPos) instanceof SpawnerBlockEntity spawnerBE)) continue;

                    CompoundTag data = spawnerBE.getPersistentData();
                    if (!data.getBooleanOr("mlkymc_crafted", false)) continue;

                    int count = data.getIntOr("mlkymc_spawn_count", 0) + 1;
                    int limit = data.getIntOr("mlkymc_spawn_limit", 64);
                    data.putInt("mlkymc_spawn_count", count);
                    spawnerBE.setChanged();

                    if (count >= limit) {
                        // Revert to empty crafted spawner
                        BaseSpawner spawner = spawnerBE.getSpawner();
                        spawner.setEntityId(EntityType.PIG, level, level.random, checkPos);
                        data.remove("mlkymc_mob_type");
                        data.putInt("mlkymc_spawn_count", 0);
                        spawnerBE.setChanged();

                        for (var p : level.players()) {
                            if (p.blockPosition().distSqr(checkPos) < 30 * 30) {
                                p.sendSystemMessage(Component.literal("A spawner has been expended and reverted to empty!")
                                        .withColor(0xFFAA00));
                            }
                        }
                    }
                    return;
                }
            }
        }
    }
}
