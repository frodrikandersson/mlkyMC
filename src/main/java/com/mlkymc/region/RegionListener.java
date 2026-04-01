package com.mlkymc.region;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class RegionListener {

    private final RegionManager manager;

    public RegionListener(RegionManager manager) {
        this.manager = manager;
    }

    // ---- Wand selection (stick): left-click = pos1, right-click = pos2 ----

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getMainHandItem().is(Items.STICK)) return;
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return;

        BlockPos pos = event.getPos();
        String dim = player.level().dimension().identifier().toString();
        manager.setPos1(player.getUUID(), dim, pos.getX(), pos.getY(), pos.getZ());
        player.sendSystemMessage(Component.literal("Pos 1 set: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withColor(0x55FF55));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getMainHandItem().is(Items.STICK)) return;
        if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return;

        BlockPos pos = event.getPos();
        String dim = player.level().dimension().identifier().toString();
        manager.setPos2(player.getUUID(), dim, pos.getX(), pos.getY(), pos.getZ());
        player.sendSystemMessage(Component.literal("Pos 2 set: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withColor(0xFFFF55));
        event.setCanceled(true);
    }

    // ---- Block breaking ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (canBypass(player)) return;

        String dim = player.level().dimension().identifier().toString();
        BlockPos pos = event.getPos();
        if (manager.isFlagActiveAt(RegionFlag.NO_BREAK, dim, pos.getX(), pos.getY(), pos.getZ())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("You can't break blocks in this region.").withColor(0xFF5555));
        }
    }

    // ---- Block placing ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (canBypass(player)) return;

        String dim = player.level().dimension().identifier().toString();
        BlockPos pos = event.getPos();
        if (manager.isFlagActiveAt(RegionFlag.NO_PLACE, dim, pos.getX(), pos.getY(), pos.getZ())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("You can't place blocks in this region.").withColor(0xFF5555));
        }
    }

    // ---- PvP and fall damage ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        String dim = victim.level().dimension().identifier().toString();
        int x = victim.blockPosition().getX();
        int y = victim.blockPosition().getY();
        int z = victim.blockPosition().getZ();

        // No PvP
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            if (!canBypass(attacker) && manager.isFlagActiveAt(RegionFlag.NO_PVP, dim, x, y, z)) {
                event.setNewDamage(0);
                attacker.sendSystemMessage(Component.literal("PvP is disabled in this region.").withColor(0xFF5555));
                return;
            }
        }

        // No fall damage
        if (event.getSource().is(DamageTypes.FALL)) {
            if (manager.isFlagActiveAt(RegionFlag.NO_FALL_DAMAGE, dim, x, y, z)) {
                event.setNewDamage(0);
            }
        }
    }

    // ---- Explosions ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        String dim = serverLevel.dimension().identifier().toString();

        // Remove blocks in protected regions from the explosion
        event.getAffectedBlocks().removeIf(pos ->
                manager.isFlagActiveAt(RegionFlag.NO_EXPLOSIONS, dim, pos.getX(), pos.getY(), pos.getZ()));

        // Remove entities in protected regions from explosion damage
        event.getAffectedEntities().removeIf(entity -> {
            BlockPos pos = entity.blockPosition();
            return manager.isFlagActiveAt(RegionFlag.NO_EXPLOSIONS, dim, pos.getX(), pos.getY(), pos.getZ());
        });
    }

    // ---- Mob spawning ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMobSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        Mob mob = event.getEntity();

        // Only block hostile mobs
        if (mob.getType().getCategory() != MobCategory.MONSTER) return;

        String dim = serverLevel.dimension().identifier().toString();
        int x = (int) event.getX();
        int y = (int) event.getY();
        int z = (int) event.getZ();

        if (manager.isFlagActiveAt(RegionFlag.NO_MOB_SPAWN, dim, x, y, z)) {
            event.setSpawnCancelled(true);
        }
    }

    // ---- Hunger drain ----

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Only check every 20 ticks (1 second)
        if (player.tickCount % 20 != 0) return;

        String dim = player.level().dimension().identifier().toString();
        BlockPos pos = player.blockPosition();
        if (manager.isFlagActiveAt(RegionFlag.NO_HUNGER, dim, pos.getX(), pos.getY(), pos.getZ())) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5.0f);
        }
    }

    // ---- Container/door interaction ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getMainHandItem().is(Items.STICK)
                && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return; // Wand handled above
        if (canBypass(player)) return;

        BlockPos pos = event.getPos();
        String dim = player.level().dimension().identifier().toString();
        if (manager.isFlagActiveAt(RegionFlag.NO_INTERACT, dim, pos.getX(), pos.getY(), pos.getZ())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("You can't interact in this region.").withColor(0xFF5555));
        }
    }

    // ---- Helpers ----

    private boolean canBypass(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /**
     * Teleport new players (first join) to the exact spawn point.
     */
    @SubscribeEvent
    public void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!manager.hasExactSpawn()) return;

        // Only teleport if the player has no bed/respawn point set (first join or bed destroyed)
        if (player.getRespawnConfig() != null) return;

        player.level().getServer().execute(() -> {
            player.teleportTo(
                    (ServerLevel) player.level(),
                    manager.getSpawnX(), manager.getSpawnY(), manager.getSpawnZ(),
                    java.util.Set.of(), manager.getSpawnYaw(), manager.getSpawnPitch(), false);
        });
    }

    /**
     * Teleport players to exact spawn on respawn (when they have no bed).
     */
    @SubscribeEvent
    public void onPlayerRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!manager.hasExactSpawn()) return;

        // Only override if they respawned at world spawn (no bed)
        // Check if they're near the world spawn (within 32 blocks = vanilla spawn area)
        var worldSpawn = player.level().getServer().overworld().getLevelData().getRespawnData().pos();
        double distSq = player.blockPosition().distSqr(worldSpawn);
        if (distSq > 1024) return; // They respawned at a bed, don't override

        player.level().getServer().execute(() -> {
            player.teleportTo(
                    player.level().getServer().overworld(),
                    manager.getSpawnX(), manager.getSpawnY(), manager.getSpawnZ(),
                    java.util.Set.of(), manager.getSpawnYaw(), manager.getSpawnPitch(), false);
        });
    }
}
