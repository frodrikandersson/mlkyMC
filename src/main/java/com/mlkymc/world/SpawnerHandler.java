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

    // Spawn limits per mob type (loaded from config)
    private static Map<EntityType<?>, Integer> spawnLimits;

    private void initLimits() {
        if (spawnLimits != null) return;
        spawnLimits = new HashMap<>();

        var limits = com.mlkymc.config.MlkyConfig.getSpawnerMobLimits();

        // Hostile
        spawnLimits.put(EntityType.ZOMBIE, limits.zombie);
        spawnLimits.put(EntityType.SKELETON, limits.skeleton);
        spawnLimits.put(EntityType.SPIDER, limits.spider);
        spawnLimits.put(EntityType.CAVE_SPIDER, limits.cave_spider);
        spawnLimits.put(EntityType.BLAZE, limits.blaze);
        spawnLimits.put(EntityType.SILVERFISH, limits.silverfish);
        spawnLimits.put(EntityType.MAGMA_CUBE, limits.magma_cube);

        // Farm animals
        spawnLimits.put(EntityType.COW, limits.cow);
        spawnLimits.put(EntityType.SHEEP, limits.sheep);
        spawnLimits.put(EntityType.CHICKEN, limits.chicken);
        spawnLimits.put(EntityType.PIG, limits.pig);
        spawnLimits.put(EntityType.RABBIT, limits.rabbit);

        // Utility
        spawnLimits.put(EntityType.WOLF, limits.wolf);
        spawnLimits.put(EntityType.BEE, limits.bee);
        spawnLimits.put(EntityType.IRON_GOLEM, limits.iron_golem);
        spawnLimits.put(EntityType.CAT, limits.cat);

        // Other neutrals
        spawnLimits.put(EntityType.HORSE, limits.horse);
        spawnLimits.put(EntityType.DONKEY, limits.donkey);
        spawnLimits.put(EntityType.LLAMA, limits.llama);
        spawnLimits.put(EntityType.FOX, limits.fox);
        spawnLimits.put(EntityType.FROG, limits.frog);
        spawnLimits.put(EntityType.TURTLE, limits.turtle);
        spawnLimits.put(EntityType.GOAT, limits.goat);
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

        int limit = spawnLimits.getOrDefault(mobType, com.mlkymc.config.MlkyConfig.getSpawnerDefaultMaxSpawns());
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
     * Crafted spawners drop themselves when broken. Natural spawners do not.
     * Reads the mlkymc_crafted flag directly from BlockDropsEvent.getBlockEntity(),
     * which NeoForge captures before the block is removed. This is robust across
     * server restarts (unlike an in-memory position tracker) and independent of
     * handler priority order.
     */
    @SubscribeEvent
    public void onBlockDrop(BlockDropsEvent event) {
        if (event.getState() == null || !event.getState().is(Blocks.SPAWNER)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        boolean isCrafted = event.getBlockEntity() instanceof SpawnerBlockEntity spawnerBE
                && spawnerBE.getPersistentData().getBooleanOr("mlkymc_crafted", false);

        if (!isCrafted) {
            // Natural spawner — strip any spawner drops added by other handlers
            event.getDrops().removeIf(e -> e.getItem().is(Items.SPAWNER));
            return;
        }

        // Crafted spawner — ensure it drops exactly once
        boolean alreadyDropping = event.getDrops().stream()
                .anyMatch(e -> e.getItem().is(Items.SPAWNER));
        if (!alreadyDropping) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                    level,
                    event.getPos().getX() + 0.5,
                    event.getPos().getY() + 0.5,
                    event.getPos().getZ() + 0.5,
                    new ItemStack(Items.SPAWNER)));
        }
    }

    /**
     * Spirit Ward: suppress hostile mob spawns within 30 blocks of active ward.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public void onSpiritWardCheck(FinalizeSpawnEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Monster)) return;
        if (event.getSpawnType() == EntitySpawnReason.SPAWNER) return; // Don't block player-placed spawners

        net.minecraft.core.BlockPos spawnPos = event.getEntity().blockPosition();
        if (com.mlkymc.classes.PowerHandler.isSpiritWardActive(spawnPos)) {
            event.setSpawnCancelled(true);
            event.setCanceled(true);
        }
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
                        // Nuke and recreate the block entity so it becomes a truly empty
                        // spawner (no mob assigned visually). BaseSpawner has no public API
                        // to clear an assigned entity, and setEntityId(PIG) just swaps the
                        // display to a pig spawner. Replacing the block produces a fresh,
                        // entity-less spawner. Preserve the crafted flag + limit on the new
                        // BE so the player can still pick it up, and reset the count.
                        int savedLimit = limit;
                        level.setBlock(checkPos, Blocks.AIR.defaultBlockState(), 3);
                        level.setBlock(checkPos, Blocks.SPAWNER.defaultBlockState(), 3);
                        if (level.getBlockEntity(checkPos) instanceof SpawnerBlockEntity freshBE) {
                            CompoundTag freshData = freshBE.getPersistentData();
                            freshData.putBoolean("mlkymc_crafted", true);
                            freshData.putInt("mlkymc_spawn_limit", savedLimit);
                            freshData.putInt("mlkymc_spawn_count", 0);
                            freshBE.setChanged();
                        }

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
