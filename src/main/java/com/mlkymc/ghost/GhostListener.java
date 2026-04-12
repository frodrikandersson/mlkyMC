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

    // Players who died and are waiting to press Respawn to become ghosts
    // Persisted to disk so it survives server restarts
    private static final java.util.Set<java.util.UUID> pendingGhosts =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static java.nio.file.Path pendingGhostsFile;

    // Death positions — used to teleport ghost back to where they died after respawn
    private static final java.util.Map<java.util.UUID, DeathLocation> deathPositions =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record DeathLocation(String dimension, double x, double y, double z) {}

    public static void setPendingGhostsFile(java.nio.file.Path file) {
        pendingGhostsFile = file;
        loadPendingGhosts();
    }

    public static boolean isPendingGhost(java.util.UUID uuid) {
        return pendingGhosts.contains(uuid);
    }

    private static void savePendingGhosts() {
        if (pendingGhostsFile == null) return;
        try {
            var list = new java.util.ArrayList<String>();
            for (var uuid : pendingGhosts) list.add(uuid.toString());
            java.nio.file.Files.writeString(pendingGhostsFile,
                    new com.google.gson.Gson().toJson(list));
        } catch (Exception e) {
            com.mlkymc.MlkyMC.LOGGER.warn("Failed to save pending_ghosts.json", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadPendingGhosts() {
        if (pendingGhostsFile == null || !java.nio.file.Files.exists(pendingGhostsFile)) return;
        try {
            String json = java.nio.file.Files.readString(pendingGhostsFile);
            var list = new com.google.gson.Gson().fromJson(json,
                    new com.google.gson.reflect.TypeToken<java.util.List<String>>(){}.getType());
            if (list != null) {
                for (String s : (java.util.List<String>) list) {
                    pendingGhosts.add(java.util.UUID.fromString(s));
                }
            }
        } catch (Exception e) {
            com.mlkymc.MlkyMC.LOGGER.warn("Failed to load pending_ghosts.json", e);
        }
    }

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

        // Check Devoted Life BEFORE ghost conversion
        var passiveHandler = com.mlkymc.classes.PassiveSkillHandler.getInstance();
        if (passiveHandler != null && passiveHandler.tryDevotedLife(player)) {
            event.setCanceled(true);
            return; // Player survived via Devoted Life, don't become ghost
        }

        // Don't cancel death — let the player see the death screen and press Respawn
        // Create grave NOW (captures inventory before vanilla drops it)
        if (player.level() instanceof ServerLevel level) {
            try {
                com.mlkymc.grave.GraveManager gm = com.mlkymc.MlkyMC.getGraveManager();
                if (gm != null) {
                    gm.createGrave(player, level);
                }
            } catch (Exception e) {
                com.mlkymc.MlkyMC.LOGGER.error("Failed to create grave", e);
            }
        }

        // Store death position so the ghost spawns here after respawn
        String dim = player.level().dimension().identifier().toString();
        deathPositions.put(player.getUUID(), new DeathLocation(dim, player.getX(), player.getY(), player.getZ()));

        // Mark player as pending ghost — they'll become a ghost when they press Respawn
        pendingGhosts.add(player.getUUID());
        savePendingGhosts();

        // Show grave location on death screen
        var gm = com.mlkymc.MlkyMC.getGraveManager();
        var grave = gm != null ? gm.getGraveByOwner(player.getUUID()) : null;
        if (grave != null) {
            player.sendSystemMessage(Component.literal(
                    "Your items are in a grave at " + grave.pos.getX() + ", " + grave.pos.getY() + ", " + grave.pos.getZ())
                    .withColor(0xFFAA00));
        }
        player.sendSystemMessage(Component.literal("Press Respawn to become a ghost.").withColor(0xAAAAAA));
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Player pressed Respawn after dying — convert to ghost now
        if (pendingGhosts.remove(player.getUUID())) {
            savePendingGhosts();
            // Convert to ghost, then teleport to death location after ghost mode is applied
            DeathLocation deathLoc = deathPositions.remove(player.getUUID());
            player.level().getServer().execute(() -> {
                ghostManager.makeGhost(player);

                // Teleport AFTER makeGhost — needs another tick delay so spectator mode is settled
                if (deathLoc != null) {
                    player.level().getServer().execute(() -> {
                        var dimKey = net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION,
                                net.minecraft.resources.Identifier.parse(deathLoc.dimension));
                        var deathLevel = player.level().getServer().getLevel(dimKey);
                        if (deathLevel != null) {
                            player.teleportTo(deathLevel, deathLoc.x, deathLoc.y, deathLoc.z,
                                    java.util.Set.of(), player.getYRot(), player.getXRot(), false);
                        }
                    });
                }

                // Create ghost data entry
                var ghostDataManager = com.mlkymc.MlkyMC.getGhostDataManager();
                if (ghostDataManager != null) {
                    var ghostData = ghostDataManager.getOrCreate(player.getUUID());
                    ghostDataManager.save();
                    // Send initial SE sync so the bar shows immediately
                    ghostDataManager.sendSpectralSync(player, ghostData);
                }

                // Broadcast
                Component broadcastMsg = Component.literal(player.getName().getString() + " has become a ghost!")
                        .withColor(0xAAAAAA);
                for (ServerPlayer online : player.level().getServer().getPlayerList().getPlayers()) {
                    if (online != player) online.sendSystemMessage(broadcastMsg);
                }

                // Death message and ghost guide
                player.sendSystemMessage(Component.literal("You are now a ghost. Find someone to revive you!")
                        .withColor(0xFF5555));

                // Ghost ability guide
                player.sendSystemMessage(Component.literal("--- Ghost Abilities ---").withColor(0x55FFFF));
                player.sendSystemMessage(Component.literal("You gain Spectral Energy passively (+3/min near living players).").withColor(0xAAAAAA));
                player.sendSystemMessage(Component.literal("Secondary: Select Mimic form (Bat/Creeper/Enderman) | Crouch+Secondary: Haunt Zone").withColor(0xAAAAAA));
                player.sendSystemMessage(Component.literal("Primary: Confirm Mimic / use Mimic ability").withColor(0xAAAAAA));
                player.sendSystemMessage(Component.literal("Find a Cleric or Soul Altar to get revived!").withColor(0x55FF55));

                // Direction to nearest Cleric
                var cm = com.mlkymc.MlkyMC.getClassManager();
                if (cm != null && player.level() instanceof ServerLevel sl) {
                    ServerPlayer nearestCleric = null;
                    double nearestDist = Double.MAX_VALUE;
                    for (ServerPlayer online : sl.getServer().getPlayerList().getPlayers()) {
                        if (online == player) continue;
                        var cd = cm.getOrCreate(online);
                        if (cd.getChosenClass() == com.mlkymc.classes.ClassType.CLERIC) {
                            double dist = online.distanceToSqr(player);
                            if (dist < nearestDist) {
                                nearestDist = dist;
                                nearestCleric = online;
                            }
                        }
                    }
                    if (nearestCleric != null) {
                        int dist = (int) Math.sqrt(nearestDist);
                        player.sendSystemMessage(Component.literal(
                                "Nearest Cleric: " + nearestCleric.getName().getString() + " (" + dist + " blocks away)")
                                .withColor(0xAA55FF));
                    } else {
                        player.sendSystemMessage(Component.literal(
                                "No Cleric is online. Find a Soul Altar to connect with one.")
                                .withColor(0xAAAAAA));
                    }
                }
            });
            return;
        }

        if (ghostManager.isGhost(player.getUUID())) {
            // Re-apply ghost effects after respawn with a 1 tick delay
            player.level().getServer().execute(() -> ghostManager.applyGhostEffects(player));
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ghostManager.isGhost(player.getUUID())) return;

        // Save ghost's current position for restore on login
        var gdm = com.mlkymc.MlkyMC.getGhostDataManager();
        if (gdm != null) {
            var data = gdm.getOrCreate(player.getUUID());
            data.logoutX = player.getX();
            data.logoutY = player.getY();
            data.logoutZ = player.getZ();
            data.logoutDimension = player.level().dimension().identifier().toString();
            gdm.save();
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            ghostManager.applyGhostEffects(player);

            // Restore ghost to their logout position
            var gdm = com.mlkymc.MlkyMC.getGhostDataManager();
            if (gdm != null) {
                var data = gdm.getOrCreate(player.getUUID());
                if (data.logoutDimension != null) {
                    // Delay teleport by a few ticks to ensure player is fully loaded
                    // and vanilla respawn logic has finished
                    final double lx = data.logoutX, ly = data.logoutY, lz = data.logoutZ;
                    final String lDim = data.logoutDimension;
                    var server = player.level().getServer();
                    server.execute(() -> server.execute(() -> server.execute(() -> {
                        for (var sl : server.getAllLevels()) {
                            if (sl.dimension().identifier().toString().equals(lDim)) {
                                player.teleportTo(sl, lx, ly, lz,
                                        java.util.Set.of(), player.getYRot(), player.getXRot(), false);
                                break;
                            }
                        }
                    })));
                }
                gdm.sendSpectralSync(player, data);
            }
        } else {
            // Check if player was pending ghost (died before server restart, never pressed Respawn)
            if (pendingGhosts.contains(player.getUUID())) {
                // They died before restart — convert to ghost now
                pendingGhosts.remove(player.getUUID());
                savePendingGhosts();
                player.level().getServer().execute(() -> {
                    ghostManager.makeGhost(player);
                    var ghostDataManager = com.mlkymc.MlkyMC.getGhostDataManager();
                    if (ghostDataManager != null) {
                        ghostDataManager.getOrCreate(player.getUUID());
                        ghostDataManager.save();
                    }
                    player.sendSystemMessage(Component.literal(
                            "You died before the server restarted. You are now a ghost — find someone to revive you!")
                            .withColor(0xFF5555));
                    var gm = com.mlkymc.MlkyMC.getGraveManager();
                    if (gm != null) {
                        var grave = gm.getGraveByOwner(player.getUUID());
                        if (grave != null) {
                            player.sendSystemMessage(Component.literal(
                                    "Your grave is at " + grave.pos.getX() + ", " + grave.pos.getY() + ", " + grave.pos.getZ())
                                    .withColor(0xFFAA00));
                        }
                    }
                });
            } else {
                // Not a ghost, not pending — tell client to reset ghost HUDs
                player.sendSystemMessage(Component.literal("[MLKYMC_REVIVED:" + player.getName().getString() + "]")
                        .withColor(0x000000));
            }
        }
    }

    @SubscribeEvent
    public void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST, receiveCanceled = true)
    public void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            // Allow doors, trapdoors, fence gates, Soul Altar Capstone, and ghost statue
            BlockState state = event.getLevel().getBlockState(event.getPos());
            if (state.getBlock() instanceof DoorBlock
                    || state.getBlock() instanceof TrapDoorBlock
                    || state.getBlock() instanceof FenceGateBlock
                    || state.getBlock() instanceof com.mlkymc.registry.SoulAltarCapstoneBlock) {
                event.setCanceled(false);
                return;
            }
            // Check if this is the ghost statue position
            var rm = com.mlkymc.MlkyMC.getReviveManager();
            if (rm != null) {
                var statueLoc = rm.getStatueLocation();
                if (statueLoc != null) {
                    BlockPos statuePos = new BlockPos(
                            (int) Math.floor(statueLoc.x),
                            (int) Math.floor(statueLoc.y),
                            (int) Math.floor(statueLoc.z));
                    // Check within 2 blocks to account for coordinate rounding
                    if (event.getPos().distManhattan(statuePos) <= 2) {
                        event.setCanceled(false);
                        return;
                    }
                }
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

    // Ghost UUIDs temporarily allowed to open the next container menu
    private static final java.util.Set<java.util.UUID> ghostMenuAllowlist =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Allow a ghost to open the next container menu (call before openMenu). */
    public static void allowNextMenu(java.util.UUID ghostUuid) {
        ghostMenuAllowlist.add(ghostUuid);
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            // Check one-time allowlist (set before openMenu calls)
            if (ghostMenuAllowlist.remove(player.getUUID())) {
                return; // Allowed
            }
            // Always allow ghost donation menu
            if (event.getContainer() instanceof com.mlkymc.altar.GhostDonationMenu) {
                return;
            }
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

    /**
     * Track damage during mimic — sets flag to prevent +15 SE completion bonus.
     * Also breaks mimic if 4+ hearts (8 HP) of damage taken.
     */
    @SubscribeEvent
    public void onGhostDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ghostManager.isGhost(player.getUUID())) return;

        var gdm = com.mlkymc.MlkyMC.getGhostDataManager();
        if (gdm == null) return;
        var data = gdm.get(player.getUUID());
        if (data == null || data.activeMimic == null) return;

        data.mimicTookDamage = true;

        // Break mimic on 4+ hearts damage in one hit
        if (event.getNewDamage() >= 8.0f) {
            player.sendSystemMessage(Component.literal("Mimic broken by heavy damage!").withColor(0xFF5555));
            gdm.endMimic(player, data, false);
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
        double wallTopY = getHighestAdjacentWallTop(player);
        if (wallTopY > 0) {
            Vec3 motion = player.getDeltaMovement();
            double feetY = player.getBoundingBox().minY;
            double ySpeed = motion.y;

            // Ladder physics: clamp fall speed
            if (ySpeed < -0.05) {
                ySpeed = -0.05;
            }

            // Climb with jump — but stop when feet reach wall top
            if (isJumping(player) && feetY < wallTopY - 0.1) {
                ySpeed = 0.15;
            } else if (isJumping(player) && feetY >= wallTopY - 0.1) {
                // At the top — give a small boost to step onto the wall
                ySpeed = Math.max(ySpeed, 0.05);
            }

            player.setDeltaMovement(motion.x, ySpeed, motion.z);
            player.hurtMarked = true;
            player.fallDistance = 0;
        }
    }

    private boolean isJumping(ServerPlayer player) {
        return player.getLastClientInput().jump();
    }

    /**
     * Returns the highest wall top Y adjacent to the player, or -1 if no wall.
     */
    private double getHighestAdjacentWallTop(ServerPlayer player) {
        Level level = player.level();

        if (player.isInWater() || player.isInLava() || player.isUnderWater()) return -1;
        if (level.getFluidState(player.blockPosition()).isSource()) return -1;

        AABB playerBox = player.getBoundingBox();
        AABB checkBox = playerBox.inflate(0.05, 0, 0.05);

        int minX = (int) Math.floor(checkBox.minX);
        int maxX = (int) Math.floor(checkBox.maxX);
        int minZ = (int) Math.floor(checkBox.minZ);
        int maxZ = (int) Math.floor(checkBox.maxZ);
        int minY = (int) Math.floor(playerBox.minY);
        int maxY = (int) Math.floor(playerBox.maxY) + 1; // check 1 above head too

        double highestTop = -1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    var state = level.getBlockState(pos);
                    var shape = state.getCollisionShape(level, pos);
                    if (!shape.isEmpty()
                            && shape.bounds().getSize() > 0.5
                            && !playerBox.intersects(x, y, z, x + 1, y + 1, z + 1)) {
                        double blockTopY = y + shape.bounds().maxY;
                        if (blockTopY > highestTop) {
                            highestTop = blockTopY;
                        }
                    }
                }
            }
        }
        return highestTop;
    }

    /**
     * Ghost voice chat: costs 5 SE per message.
     * Spawns subtle particles at ghost location visible to nearby living players.
     * If not enough SE, block the message.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onGhostChat(net.neoforged.neoforge.event.ServerChatEvent event) {
        if (event.isCanceled()) return;
        ServerPlayer player = event.getPlayer();
        if (!ghostManager.isGhost(player.getUUID())) return;

        // Skip internal command messages and mimic form selection (handled by PowerHandler)
        String msg = event.getRawText().trim();
        if (msg.startsWith("[MLKYMC_")) return;
        String lower = msg.toLowerCase();
        if (lower.equals("bat") || lower.equals("creeper") || lower.equals("enderman")) return;

        var gdm = com.mlkymc.MlkyMC.getGhostDataManager();
        if (gdm == null) return;
        var data = gdm.getOrCreate(player.getUUID());

        if (data.spectralEnergy < 5) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("Need 5 Spectral Energy to speak! (Have: " +
                    data.spectralEnergy + ")").withColor(0xFF5555));
            return;
        }

        data.addSpectralEnergy(-5);
        gdm.sendSpectralSync(player, data);

        // Spawn particles at ghost location visible to nearby living players
        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                    player.getX(), player.getY() + 1.5, player.getZ(),
                    5, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /**
     * When a living player starts tracking a ghost, send a remove entity packet
     * so the ghost entity is destroyed on their client. This hides the ghost
     * from Cosmetica and all other client-side renderers.
     */
    @SubscribeEvent
    public void onStartTracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer tracker)) return;
        if (!(event.getTarget() instanceof ServerPlayer target)) return;

        // Don't hide ghosts from other ghosts
        if (ghostManager.isGhost(tracker.getUUID())) return;

        // If the target is a ghost, send a remove packet to the living player
        if (ghostManager.isGhost(target.getUUID())) {
            tracker.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(target.getId()));
        }
    }

    /**
     * Prevent ghosts from trampling farmland.
     */
    @SubscribeEvent
    public void onFarmlandTrample(net.neoforged.neoforge.event.level.BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) {
            event.setCanceled(true);
        }
    }
}
