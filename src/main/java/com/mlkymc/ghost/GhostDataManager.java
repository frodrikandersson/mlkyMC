package com.mlkymc.ghost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mlkymc.MlkyMC;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages per-ghost Spectral Energy data.
 * Handles passive SE gain, mimic expiry, haunt zone maintenance.
 */
public class GhostDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private Path dataFile;
    private final Map<UUID, GhostData> ghostData = new HashMap<>();

    // SE gain rates (per minute, checked every second)
    private static final net.minecraft.resources.Identifier MIMIC_FLIGHT_ID =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("mlkymc", "mimic_bat_flight");
    private static final int BASE_GAIN_PER_MIN = 30;
    private static final int NEAR_PLAYER_GAIN_PER_MIN = 90;
    private static final int NEAR_COMBAT_GAIN_PER_MIN = 150;
    private static final int HAUNT_COST_PER_MIN = 5;

    private long lastSaveMs = 0;
    private static final long SAVE_INTERVAL_MS = 60_000; // Save every 60 seconds

    public GhostDataManager(Path configDir) {
        this.dataFile = configDir.resolve("ghost_data.json");
    }

    public GhostData getOrCreate(UUID uuid) {
        return ghostData.computeIfAbsent(uuid, GhostData::new);
    }

    public GhostData get(UUID uuid) {
        return ghostData.get(uuid);
    }

    public void remove(UUID uuid) {
        ghostData.remove(uuid);
        save();
    }

    /**
     * Called every tick. Syncs mimic mob entity position to ghost player.
     */
    public void tickMimicEntities(net.minecraft.server.MinecraftServer server) {
        var ghostManager = MlkyMC.getGhostManager();
        if (ghostManager == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ghostManager.isGhost(player.getUUID())) continue;
            GhostData data = ghostData.get(player.getUUID());
            if (data == null || !data.isMimicking() || data.mimicEntity == null) continue;

            var mob = data.mimicEntity;
            if (mob.isRemoved()) {
                data.mimicEntity = null;
                continue;
            }

            // Teleport mob to ghost position
            mob.setPos(player.getX(), player.getY(), player.getZ());
            mob.setYRot(player.getYRot());
            mob.setXRot(player.getXRot());
            mob.setYHeadRot(player.getYHeadRot());
            mob.setYBodyRot(player.getYRot());

            // Keep bat in flying state (prevent AI from resetting to resting)
            if (mob instanceof net.minecraft.world.entity.ambient.Bat bat && bat.isResting()) {
                bat.setResting(false);
            }
        }
    }

    /**
     * Called every 20 ticks (1 second) from server tick.
     * Handles passive SE gain for all online ghosts.
     */
    public void tickSpectralEnergy(MinecraftServer server) {
        var ghostManager = MlkyMC.getGhostManager();
        if (ghostManager == null) return;

        long now = System.currentTimeMillis();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ghostManager.isGhost(player.getUUID())) continue;

            GhostData data = getOrCreate(player.getUUID());

            // Called every 20 ticks (1 second). Gain SE every 60 calls (1 minute of game ticks).
            data.gainTickCounter++;
            if (data.gainTickCounter >= 60) {
                data.gainTickCounter = 0;

                int gain = BASE_GAIN_PER_MIN;

                // Near living players bonus
                if (player.level() instanceof ServerLevel sl) {
                    var nearbyPlayers = sl.getEntitiesOfClass(ServerPlayer.class,
                            player.getBoundingBox().inflate(20));
                    boolean nearLiving = nearbyPlayers.stream()
                            .anyMatch(p -> !ghostManager.isGhost(p.getUUID()));
                    if (nearLiving) gain = NEAR_PLAYER_GAIN_PER_MIN;

                    // Near combat bonus (any damaged mob within 20 blocks)
                    var nearbyMobs = sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                            player.getBoundingBox().inflate(20));
                    boolean nearCombat = nearbyMobs.stream()
                            .anyMatch(e -> e.getLastHurtByMobTimestamp() > e.tickCount - 100);
                    if (nearCombat) gain = NEAR_COMBAT_GAIN_PER_MIN;
                }

                data.addSpectralEnergy(gain);

                // Haunt zone maintenance cost
                if (data.hasHauntZone()) {
                    data.addSpectralEnergy(-HAUNT_COST_PER_MIN);
                    if (data.spectralEnergy <= 0) {
                        // Zone collapses
                        data.hauntZonePos = null;
                        data.hauntZoneDimension = null;
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "Haunt Zone collapsed — out of Spectral Energy!").withColor(0xFF5555));
                    }
                }

                // Sync to client
                sendSpectralSync(player, data);
            }

            // Expire mimic (natural expiry) — check activeMimic directly, not isMimicking()
            if (data.activeMimic != null && now >= data.mimicExpiryMs) {
                boolean noDamage = !data.mimicTookDamage;
                endMimic(player, data, noDamage);
            }
            // Damage-based mimic break is now handled in GhostListener.onGhostDamage
        }

        // Periodic save (every 60 seconds instead of per-action)
        if (now - lastSaveMs >= SAVE_INTERVAL_MS) {
            save();
            lastSaveMs = now;
        }
    }

    /**
     * Apply haunt zone effects to nearby players and mobs.
     * Called every 20 ticks.
     */
    public void tickHauntZones(MinecraftServer server) {
        var ghostManager = MlkyMC.getGhostManager();
        if (ghostManager == null) return;

        for (GhostData data : ghostData.values()) {
            if (!data.hasHauntZone()) continue;
            if (!ghostManager.isGhost(data.ghostUuid)) continue;

            for (ServerLevel level : server.getAllLevels()) {
                String dim = level.dimension().identifier().toString();
                if (!dim.equals(data.hauntZoneDimension)) continue;

                BlockPos center = data.hauntZonePos;
                AABB area = new AABB(center).inflate(20);

                // Slowness I on living players in zone
                var players = level.getEntitiesOfClass(ServerPlayer.class, area);
                for (var player : players) {
                    if (ghostManager.isGhost(player.getUUID())) continue;
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, false));
                }

                // Increase mob aggression (glowing to make them visible)
                var mobs = level.getEntitiesOfClass(Monster.class, area);
                for (var mob : mobs) {
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false));
                }

                // Eerie sounds (play randomly ~every 5 seconds on average)
                if (level.random.nextInt(100) < 1) { // ~1% per tick at 20 tick rate = every ~5s
                    var sounds = new net.minecraft.sounds.SoundEvent[]{
                            net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value(),
                            net.minecraft.sounds.SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(),
                            net.minecraft.sounds.SoundEvents.AMBIENT_NETHER_WASTES_MOOD.value()
                    };
                    var sound = sounds[level.random.nextInt(sounds.length)];
                    double sx = center.getX() + level.random.nextGaussian() * 10;
                    double sy = center.getY() + level.random.nextGaussian() * 3;
                    double sz = center.getZ() + level.random.nextGaussian() * 10;
                    level.playSound(null, sx, sy, sz, sound,
                            net.minecraft.sounds.SoundSource.AMBIENT, 0.5f, 0.6f + level.random.nextFloat() * 0.4f);
                }

                // Faint fog particles throughout the zone
                if (level.random.nextInt(5) == 0) {
                    double px = center.getX() + (level.random.nextDouble() - 0.5) * 40;
                    double py = center.getY() + level.random.nextDouble() * 4;
                    double pz = center.getZ() + (level.random.nextDouble() - 0.5) * 40;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                            px, py, pz, 1, 0.5, 0.1, 0.5, 0.001);
                }
            }
        }
    }

    /**
     * Apply Spectral Vision: ghost sees all mobs/players glowing within 30 blocks.
     * Called every 20 ticks for each ghost.
     */
    /**
     * Spectral Vision: ghost sees entity outlines within 30 blocks.
     * Uses per-player entity data packets so only the ghost sees the glow,
     * not other living players.
     */
    private long lastPositionSaveTick = 0;

    /**
     * Periodically save ghost positions for crash recovery.
     * Called every 20 ticks (1 second). Saves every 60 seconds.
     */
    public void tickPositionSave(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        if (gameTime - lastPositionSaveTick < 1200) return; // every 60 seconds
        lastPositionSaveTick = gameTime;

        var ghostManager = MlkyMC.getGhostManager();
        if (ghostManager == null) return;

        boolean dirty = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ghostManager.isGhost(player.getUUID())) continue;
            GhostData data = getOrCreate(player.getUUID());
            data.logoutX = player.getX();
            data.logoutY = player.getY();
            data.logoutZ = player.getZ();
            data.logoutDimension = player.level().dimension().identifier().toString();
            dirty = true;
        }
        if (dirty) save();
    }

    public void tickSpectralVision(MinecraftServer server) {
        var ghostManager = MlkyMC.getGhostManager();
        if (ghostManager == null) return;
        var classManager = MlkyMC.getClassManager();

        for (ServerPlayer ghostPlayer : server.getPlayerList().getPlayers()) {
            if (!ghostManager.isGhost(ghostPlayer.getUUID())) continue;
            if (!(ghostPlayer.level() instanceof ServerLevel sl)) continue;

            var nearbyEntities = sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    ghostPlayer.getBoundingBox().inflate(30));
            for (var entity : nearbyEntities) {
                if (entity == ghostPlayer) continue;
                sendFakeGlowing(ghostPlayer, entity);
            }

            // Send nearest Cleric coordinates to ghost via skill sync
            sendNearestClericCoords(ghostPlayer, server, classManager);
        }
    }

    /**
     * Sends the nearest Cleric's coordinates to a ghost player via chat sync.
     */
    private void sendNearestClericCoords(ServerPlayer ghost, MinecraftServer server,
                                          com.mlkymc.classes.ClassManager classManager) {
        if (classManager == null) return;

        ServerPlayer nearestCleric = null;
        double nearestDist = Double.MAX_VALUE;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == ghost) continue;
            if (player.level() != ghost.level()) continue; // same dimension only
            var data = classManager.getOrCreate(player);
            if (data.getChosenClass() != com.mlkymc.classes.ClassType.CLERIC) continue;

            double dist = ghost.distanceToSqr(player);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestCleric = player;
            }
        }

        if (nearestCleric != null) {
            var pos = nearestCleric.blockPosition();
            int dist = (int) Math.sqrt(nearestDist);
            String msg = "[MLKYMC_CLERIC_POS:" + pos.getX() + "/" + pos.getY() + "/" + pos.getZ() + ":" + dist + "]";
            ghost.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg).withColor(0x000000));
        } else {
            ghost.sendSystemMessage(net.minecraft.network.chat.Component.literal("[MLKYMC_CLERIC_POS:NONE]").withColor(0x000000));
        }
    }

    /**
     * Sends a fake entity data packet to a specific player that sets the Glowing flag
     * without actually modifying the entity's state on the server.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> SHARED_FLAGS_ACCESSOR = initSharedFlags();

    @SuppressWarnings("unchecked")
    private static net.minecraft.network.syncher.EntityDataAccessor<Byte> initSharedFlags() {
        try {
            var field = net.minecraft.world.entity.Entity.class.getDeclaredField("DATA_SHARED_FLAGS_ID");
            field.setAccessible(true);
            return (net.minecraft.network.syncher.EntityDataAccessor<Byte>) field.get(null);
        } catch (Exception e) {
            MlkyMC.LOGGER.error("Failed to access DATA_SHARED_FLAGS_ID", e);
            return null;
        }
    }

    private void sendFakeGlowing(ServerPlayer viewer, net.minecraft.world.entity.LivingEntity target) {
        sendFakeGlowing(viewer, target, false);
    }

    /**
     * @param removeInvisible if true, clears the invisible flag so the viewer can see the entity
     */
    private void sendFakeGlowing(ServerPlayer viewer, net.minecraft.world.entity.LivingEntity target, boolean removeInvisible) {
        if (SHARED_FLAGS_ACCESSOR == null) return;
        try {
            byte currentFlags = target.getEntityData().get(SHARED_FLAGS_ACCESSOR);
            byte modifiedFlags = (byte) (currentFlags | 0x40); // Set glowing bit
            if (removeInvisible) {
                modifiedFlags = (byte) (modifiedFlags & ~0x20); // Clear invisible bit
            }

            var packedItems = new java.util.ArrayList<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>>();
            packedItems.add(net.minecraft.network.syncher.SynchedEntityData.DataValue.create(
                    SHARED_FLAGS_ACCESSOR, modifiedFlags));

            var packet = new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                    target.getId(), packedItems);
            viewer.connection.send(packet);
        } catch (Exception e) {
            // Silently fail — non-critical visual feature
        }
    }

    /**
     * Cleric Ghost Vision: Clerics see soul particles at ghost positions.
     * Green particles = ghost connected to this Cleric's soul altar.
     * Blue/white particles = other ghosts.
     * Particles are sent only to the Cleric's client.
     * Called every 5 ticks.
     */
    public void tickClericGhostVision(MinecraftServer server) {
        var ghostManager = MlkyMC.getGhostManager();
        if (ghostManager == null) return;
        var altarManager = MlkyMC.getSoulAltarManager();
        var classManager = MlkyMC.getClassManager();
        if (classManager == null) return;

        for (ServerPlayer cleric : server.getPlayerList().getPlayers()) {
            if (ghostManager.isGhost(cleric.getUUID())) continue;
            var data = classManager.getOrCreate(cleric);
            if (data.getChosenClass() != com.mlkymc.classes.ClassType.CLERIC) continue;
            if (!(cleric.level() instanceof ServerLevel sl)) continue;

            // Get this cleric's altar connected ghosts
            Set<UUID> altarGhosts = new HashSet<>();
            if (altarManager != null) {
                var altar = altarManager.getAltarByOwner(cleric.getUUID());
                if (altar != null) {
                    for (var gc : altar.connectedGhosts) {
                        altarGhosts.add(gc.ghostUuid);
                    }
                }
            }

            // Send particles at ghost positions
            for (ServerPlayer ghostPlayer : sl.players()) {
                if (!ghostManager.isGhost(ghostPlayer.getUUID())) continue;
                if (cleric.distanceTo(ghostPlayer) > 48) continue;

                boolean isConnected = altarGhosts.contains(ghostPlayer.getUUID());
                sendGhostParticles(cleric, ghostPlayer, isConnected);
            }
        }
    }

    /**
     * Send soul particles at a ghost's position, visible only to the viewer.
     * Connected ghosts get green soul particles, others get blue/cyan.
     */
    private void sendGhostParticles(ServerPlayer viewer, ServerPlayer ghost, boolean isConnected) {
        try {
            double x = ghost.getX();
            double y = ghost.getY() + 1.0; // chest height
            double z = ghost.getZ();

            // Connected = soul fire flame (green), other = soul (blue/cyan)
            var particleType = isConnected
                    ? net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME
                    : net.minecraft.core.particles.ParticleTypes.SOUL;

            // Spawn a few particles in a small area around the ghost
            var packet = new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(
                    particleType,
                    false,    // override limiter
                    true,     // always show (long distance)
                    x, y, z,
                    0.2f, 0.4f, 0.2f,  // spread X, Y, Z
                    0.01f,    // speed
                    3         // count
            );
            viewer.connection.send(packet);
        } catch (Exception e) {
            // Silently fail
        }
    }

    public void sendSpectralSync(ServerPlayer player, GhostData data) {
        String syncTag = "[MLKYMC_SPECTRAL:" + data.spectralEnergy + ":" + data.totalAccumulatedSE + "]";
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(syncTag).withColor(0x000000));
    }

    // --- Mimic ---

    public boolean startMimic(ServerPlayer player, GhostData data, String form) {
        if (data.isMimicking()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Already mimicking!").withColor(0xFF5555));
            return false;
        }
        // No cooldown — SE cost is the only gate

        int cost;
        int durationMs;
        switch (form.toUpperCase()) {
            case "BAT" -> { cost = 15; durationMs = 80_000; }      // 1m20s
            case "CREEPER" -> { cost = 20; durationMs = 90_000; }  // 1m30s
            case "ENDERMAN" -> { cost = 25; durationMs = 70_000; } // 1m10s
            default -> {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Unknown mimic form: " + form).withColor(0xFF5555));
                return false;
            }
        }

        if (data.spectralEnergy < cost) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Need " + cost + " SE! (Have: " + data.spectralEnergy + ")").withColor(0xFF5555));
            return false;
        }

        data.addSpectralEnergy(-cost);
        data.activeMimic = form.toUpperCase();
        data.mimicExpiryMs = System.currentTimeMillis() + durationMs;
        data.mimicStartHP = player.getHealth();
        data.mimicTookDamage = false;

        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            // Spawn visible mob entity at ghost position
            net.minecraft.world.entity.Mob mob = switch (data.activeMimic) {
                case "BAT" -> new net.minecraft.world.entity.ambient.Bat(
                        net.minecraft.world.entity.EntityType.BAT, sl);
                case "CREEPER" -> new net.minecraft.world.entity.monster.Creeper(
                        net.minecraft.world.entity.EntityType.CREEPER, sl);
                case "ENDERMAN" -> new net.minecraft.world.entity.monster.EnderMan(
                        net.minecraft.world.entity.EntityType.ENDERMAN, sl);
                default -> null;
            };
            if (mob != null) {
                mob.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                mob.setNoAi(true);
                mob.setSilent(true);
                mob.setInvulnerable(true);
                mob.setNoGravity(true);
                mob.noPhysics = true; // No collision with players or blocks
                mob.addTag("mlkymc_mimic");

                // Bat: force flying animation (not resting/hanging)
                if (mob instanceof net.minecraft.world.entity.ambient.Bat bat) {
                    bat.setResting(false);
                }

                sl.addFreshEntity(mob);
                data.mimicEntity = mob;
            }

            // Apply form-specific effects
            int ticks = durationMs / 50;
            switch (data.activeMimic) {
                case "BAT" -> {
                    // Grant creative flight via NeoForge CREATIVE_FLIGHT attribute
                    var flightAttr = player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.CREATIVE_FLIGHT);
                    if (flightAttr != null) {
                        data.hadFlightBefore = flightAttr.getValue() > 0;
                        if (!data.hadFlightBefore) {
                            flightAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                    MIMIC_FLIGHT_ID, 1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
                        }
                    }
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                }
                case "CREEPER" -> {
                    player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 0, false, false));
                }
                case "ENDERMAN" -> {
                    player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1, false, false));
                }
            }
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Spectral Mimic: " + form + "! (" + (durationMs / 1000) + "s)").withColor(0x55FFFF));
        sendSpectralSync(player, data);
        save();
        return true;
    }

    /**
     * End mimic form: set 60s cooldown, optionally award +15 SE if completed without damage.
     */
    public void endMimic(ServerPlayer player, GhostData data, boolean completedWithoutDamage) {
        // Remove mob entity
        if (data.mimicEntity != null && !data.mimicEntity.isRemoved()) {
            data.mimicEntity.discard();
        }
        data.mimicEntity = null;

        // Revoke bat flight via CREATIVE_FLIGHT attribute
        if ("BAT".equals(data.activeMimic)) {
            var flightAttr = player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.CREATIVE_FLIGHT);
            if (flightAttr != null && !data.hadFlightBefore) {
                flightAttr.removeModifier(MIMIC_FLIGHT_ID);
            }
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        data.activeMimic = null;
        // No cooldown — SE cost is the only gate
        player.removeEffect(MobEffects.SPEED);

        if (completedWithoutDamage) {
            data.addSpectralEnergy(15);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Mimic completed without damage! +15 SE").withColor(0x55FF55));
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Mimic form ended.").withColor(0xAAAAAA));
        sendSpectralSync(player, data);
        save();
    }

    // --- Haunt Zone ---

    public boolean toggleHauntZone(ServerPlayer player, GhostData data) {
        if (data.hasHauntZone()) {
            // Cancel existing zone, refund 10 SE
            data.hauntZonePos = null;
            data.hauntZoneDimension = null;
            data.addSpectralEnergy(10);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Haunt Zone removed. +10 SE refunded.").withColor(0xAAAAAA));
            sendSpectralSync(player, data);
            save();
            return true;
        }

        if (!data.canHaunt()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Need 150 accumulated SE to unlock Haunt Zone!").withColor(0xFF5555));
            return false;
        }

        if (data.spectralEnergy < 30) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Need 30 SE to place a Haunt Zone!").withColor(0xFF5555));
            return false;
        }

        data.addSpectralEnergy(-30);
        data.hauntZonePos = player.blockPosition();
        data.hauntZoneDimension = player.level().dimension().identifier().toString();
        data.hauntZoneStartMs = System.currentTimeMillis();

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Haunt Zone placed! Costs 5 SE/min to maintain.").withColor(0xAA55FF));
        sendSpectralSync(player, data);
        save();
        return true;
    }

    // --- Persistence ---

    public void load() {
        if (!Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> raw = GSON.fromJson(reader, type);
            if (raw == null) return;
            ghostData.clear();
            for (Map<String, Object> entry : raw) {
                try {
                    GhostData gd = GhostData.deserialize(entry);
                    ghostData.put(gd.ghostUuid, gd);
                } catch (Exception e) {
                    MlkyMC.LOGGER.warn("Failed to load ghost data entry: {}", e.getMessage());
                }
            }
            MlkyMC.LOGGER.info("Loaded ghost data for {} ghosts", ghostData.size());
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to load ghost data", e);
        }
    }

    public void save() {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (GhostData gd : ghostData.values()) {
            raw.add(gd.serialize());
        }
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(raw, writer);
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save ghost data", e);
        }
    }

    public void reload(Path dir) {
        this.dataFile = dir.resolve("ghost_data.json");
        load();
    }
}
