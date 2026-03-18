package com.mlkymc.ghost;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class GhostListener {

    private final GhostManager ghostManager;

    public GhostListener(GhostManager ghostManager) {
        this.ghostManager = ghostManager;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            event.setCanceled(true);
            return;
        }

        event.setCanceled(true);

        // Create grave BEFORE becoming a ghost (captures inventory)
        if (player.level() instanceof ServerLevel level) {
            try {
                com.mlkymc.grave.GraveManager gm = com.mlkymc.MlkyMC.getGraveManager();
                if (gm != null) {
                    com.mlkymc.grave.GraveData grave = gm.createGrave(player, level);
                    if (grave != null) {
                        player.sendSystemMessage(Component.literal("Your items are in a grave at " +
                                grave.pos.getX() + ", " + grave.pos.getY() + ", " + grave.pos.getZ())
                                .withColor(0xFFAA00));
                    }
                }
            } catch (Exception e) {
                com.mlkymc.MlkyMC.LOGGER.error("Failed to create grave", e);
            }
        }

        ghostManager.makeGhost(player);

        // Broadcast death message
        Component deathMsg = Component.literal(player.getName().getString() + " has become a ghost!")
                .withColor(0xAAAAAA);
        player.level().getServer().getPlayerList().broadcastSystemMessage(deathMsg, false);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            // Re-apply ghost effects after respawn with a 1 tick delay
            player.level().getServer().execute(() -> ghostManager.applyGhostEffects(player));
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            ghostManager.applyGhostEffects(player);
        }
    }

    @SubscribeEvent
    public void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            // Allow doors, trapdoors, and fence gates
            BlockState state = event.getLevel().getBlockState(event.getPos());
            if (state.getBlock() instanceof DoorBlock
                    || state.getBlock() instanceof TrapDoorBlock
                    || state.getBlock() instanceof FenceGateBlock) {
                return; // Allow interaction
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerInteractItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemDrop(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (ghostManager.isGhost(serverPlayer.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            event.setInvulnerable(true);
        }
    }

    @SubscribeEvent
    public void onDamage(LivingDamageEvent.Pre event) {
        // Prevent ghosts from dealing damage to others
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer attacker) {
            if (ghostManager.isGhost(attacker.getUUID())) {
                event.setNewDamage(0);
            }
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMobTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer target) {
            if (ghostManager.isGhost(target.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            player.closeContainer();
        }
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            event.setCanPickup(net.minecraft.util.TriState.FALSE);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ghostManager.isGhost(player.getUUID())) return;

        // Ghosts can breathe underwater — reset air supply
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(player.getMaxAirSupply());
        }

        // Spider climb: walls act like ladders for ghosts
        if (isNextToWall(player)) {
            Vec3 motion = player.getDeltaMovement();
            double ySpeed = motion.y;

            // Ladder physics: clamp fall speed, climb with jump key
            if (ySpeed < -0.05) {
                ySpeed = -0.05; // Slow fall like a ladder
            }
            if (isJumping(player)) {
                ySpeed = 0.15; // Climb up when holding jump
            }

            player.setDeltaMovement(motion.x, ySpeed, motion.z);
            player.hurtMarked = true;
            player.fallDistance = 0;
        }
    }

    private boolean isJumping(ServerPlayer player) {
        return player.getLastClientInput().jump();
    }

    private boolean isNextToWall(ServerPlayer player) {
        Level level = player.level();

        // Don't climb in water or lava
        if (player.isInWater() || player.isInLava() || player.isUnderWater()) return false;

        // Also check if player is touching water at all
        if (level.getFluidState(player.blockPosition()).isSource()) return false;

        // Check blocks slightly outside the player's hitbox
        AABB playerBox = player.getBoundingBox();
        AABB checkBox = playerBox.inflate(0.05, 0, 0.05);

        int minX = (int) Math.floor(checkBox.minX);
        int maxX = (int) Math.floor(checkBox.maxX);
        int minZ = (int) Math.floor(checkBox.minZ);
        int maxZ = (int) Math.floor(checkBox.maxZ);
        int minY = (int) Math.floor(playerBox.minY);
        int maxY = (int) Math.floor(playerBox.maxY);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    var state = level.getBlockState(pos);
                    var shape = state.getCollisionShape(level, pos);
                    // Only climb on blocks with a substantial collision shape
                    // (filters out air, grass, flowers, water, signs, etc.)
                    if (!shape.isEmpty()
                            && shape.bounds().getSize() > 0.5 // At least half a block in size
                            && !playerBox.intersects(x, y, z, x + 1, y + 1, z + 1)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
