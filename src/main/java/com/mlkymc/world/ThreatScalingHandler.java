package com.mlkymc.world;

import com.mlkymc.classes.ClassData;
import com.mlkymc.classes.ClassManager;
import com.mlkymc.classes.ProfessionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Hack-and-slash elite mob system. Tiers NO LONGER buff normal mobs — they only
 * control the spawn rate of elite variants. Normal mobs are always vanilla strength.
 *
 * <p>10 elite types, each with ONE distinct ability and visual identification.
 * At T4, 30% of elites get a second type (dual-elite). Blight is eligible for dual.
 *
 * <p>Elite types:
 * <ul>
 *   <li>Blight — 20% max HP damage ignoring armor</li>
 *   <li>Volatile — explodes on death, destroys blocks in small radius</li>
 *   <li>Binder — hits apply Slowness III + Weakness I</li>
 *   <li>Stormcaller — summons lightning every 5s near the nearest player</li>
 *   <li>Watcher — nearby mobs get Strength I + Speed I while it lives</li>
 *   <li>Pack Leader — spawns weakened half-size minions of its own type</li>
 *   <li>Leech — heals 25% of max HP when any nearby mob dies</li>
 *   <li>Necromancer — 40% chance to raise a zombie when any mob dies nearby</li>
 *   <li>Shielded — permanent Resistance I, iron armor + shield visual</li>
 *   <li>Berserker — Speed + Strength scale up as HP drops</li>
 * </ul>
 */
public class ThreatScalingHandler {

    private static ThreatScalingHandler INSTANCE;

    public static ThreatScalingHandler getInstance() {
        return INSTANCE;
    }

    /** Convenience accessor for PowerHandler.syncSkillStatuses. Returns 0 if uninitialized. */
    public static int getGearScoreFor(ServerPlayer player) {
        return INSTANCE != null ? INSTANCE.getOrComputeGearScore(player) : 0;
    }

    private final ClassManager classManager;

    public ThreatScalingHandler(ClassManager classManager) {
        this.classManager = classManager;
        INSTANCE = this;
    }

    // --- Elite tags ---
    private static final String TAG_BLIGHT = "mlkymc_blight";
    private static final String TAG_VOLATILE = "mlkymc_volatile";
    private static final String TAG_BINDER = "mlkymc_binder";
    private static final String TAG_STORMCALLER = "mlkymc_stormcaller";
    private static final String TAG_WATCHER = "mlkymc_watcher";
    private static final String TAG_PACKLEADER = "mlkymc_packleader";
    private static final String TAG_LEECH = "mlkymc_leech";
    private static final String TAG_NECROMANCER = "mlkymc_necromancer";
    private static final String TAG_SHIELDED = "mlkymc_shielded";
    private static final String TAG_BERSERKER = "mlkymc_berserker";
    private static final String TAG_CORROSIVE = "mlkymc_corrosive";
    private static final String TAG_SPLITTER = "mlkymc_splitter";
    private static final String TAG_SCREAMER = "mlkymc_screamer";
    private static final String TAG_POISONOUS = "mlkymc_poisonous";
    private static final String TAG_ENRAGED = "mlkymc_enraged";
    // Gear-hunter elites — scale damage against heavily geared players
    private static final String TAG_REGENBANE = "mlkymc_regenbane";
    private static final String TAG_ARMORBREAKER = "mlkymc_armorbreaker";
    private static final String TAG_ARCANIST = "mlkymc_arcanist";
    private static final String TAG_FLUX = "mlkymc_flux";
    private static final String TAG_SILENCER = "mlkymc_silencer";
    private static final String TAG_MINION = "mlkymc_minion";
    private static final String TAG_ELITE = "mlkymc_elite";

    // Set of gear-hunter tags — these elites get per-target damage scaling and priority targeting.
    private static final java.util.Set<String> GEAR_HUNTER_TAGS = java.util.Set.of(
            TAG_REGENBANE, TAG_ARMORBREAKER, TAG_ARCANIST, TAG_FLUX, TAG_SILENCER);

    // Tier tags for XP purposes
    private static final String TAG_TIER_1 = "mlkymc_threat_scaled_t1";
    private static final String TAG_TIER_2 = "mlkymc_threat_scaled_t2";
    private static final String TAG_TIER_3 = "mlkymc_threat_scaled_t3";
    private static final String TAG_TIER_4 = "mlkymc_threat_scaled_t4";

    // Attribute modifier IDs
    private static final Identifier MINION_HP_ID = Identifier.parse("mlkymc:minion_hp");
    private static final Identifier MINION_DMG_ID = Identifier.parse("mlkymc:minion_dmg");
    private static final Identifier MINION_SCALE_ID = Identifier.parse("mlkymc:minion_scale");
    private static final Identifier BERSERKER_HP_ID = Identifier.parse("mlkymc:berserker_hp");

    // Weighted elite pool: {tag, weight, namePrefix, nameColor}
    private static final String[][] ELITE_POOL = {
            {TAG_BLIGHT,       "1", "Blight",       "AA0000"},
            {TAG_VOLATILE,     "3", "Volatile",     "FF6600"},
            {TAG_BINDER,       "3", "Binder",       "AA00FF"},
            {TAG_STORMCALLER,  "2", "Stormcaller",  "00CCFF"},
            {TAG_WATCHER,      "2", "Watcher",      "FFCC00"},
            {TAG_PACKLEADER,   "1", "Pack Leader",  "00FF00"},
            {TAG_LEECH,        "2", "Leech",        "990000"},
            {TAG_NECROMANCER,  "1", "Necromancer",  "6600AA"},
            {TAG_SHIELDED,     "3", "Shielded",     "CCCCCC"},
            {TAG_BERSERKER,    "3", "Berserker",    "FF3333"},
            {TAG_CORROSIVE,    "2", "Corrosive",    "66FF00"},
            {TAG_SPLITTER,     "2", "Splitter",     "00FFAA"},
            {TAG_SCREAMER,     "1", "Screamer",     "FFAAFF"},
            {TAG_POISONOUS,    "2", "Poisonous",    "338800"},
            {TAG_ENRAGED,      "2", "Enraged",      "FF8800"},
            // Gear-hunter elites — scale damage against heavily geared players
            {TAG_REGENBANE,    "2", "Regenbane",    "88FF88"},
            {TAG_ARMORBREAKER, "2", "Armor-breaker","BB7733"},
            {TAG_ARCANIST,     "2", "Arcanist",     "55AAFF"},
            {TAG_FLUX,         "2", "Flux",         "FF55CC"},
            {TAG_SILENCER,     "1", "Silencer",     "AA88FF"},
    };
    private static int totalWeight = 0;
    static {
        for (var e : ELITE_POOL) totalWeight += Integer.parseInt(e[1]);
    }

    // =========================================================================
    // SPAWN: roll elite type based on tier
    // =========================================================================

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onMobSpawn(FinalizeSpawnEvent event) {
        if (event.isCanceled() || event.isSpawnCancelled()) return;

        EntitySpawnReason reason = event.getSpawnType();
        if (reason == EntitySpawnReason.SPAWNER
                || reason == EntitySpawnReason.MOB_SUMMONED
                || reason == EntitySpawnReason.CONVERSION
                || reason == EntitySpawnReason.JOCKEY
                || reason == EntitySpawnReason.BREEDING
                || reason == EntitySpawnReason.LOAD
                || reason == EntitySpawnReason.REINFORCEMENT) {
            return;
        }

        if (!(event.getEntity() instanceof Monster mob)) return;
        if (mob instanceof WitherBoss || mob instanceof Warden) return;
        if (mob.getTags().contains("mlkymc_ore_marker")) return;
        if (mob.getTags().contains("mlkymc_mimic")) return;
        if (mob.getTags().contains("mlkymc_raid_mob")) return;
        if (mob.getTags().contains(TAG_MINION)) return;

        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        Player nearest = sl.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), 64.0, false);
        if (!(nearest instanceof ServerPlayer sp)) return;

        int threat = computeThreat(sp);
        int tier = tierForThreat(threat);
        if (tier > 0) tagForTier(mob, tier);

        // Elite spawn rate based on tier
        double eliteChance = switch (tier) {
            case 1 -> 0.05;
            case 2 -> 0.10;
            case 3 -> 0.15;
            case 4 -> 0.20;
            default -> 0.0;
        };
        if (eliteChance <= 0) return;

        var rng = ThreadLocalRandom.current();
        if (rng.nextDouble() >= eliteChance) return;

        // Roll first elite type
        String firstType = rollEliteType(rng);
        applyEliteType(mob, firstType, sl);

        // T4: 30% chance of dual-elite
        if (tier >= 4 && rng.nextDouble() < 0.30) {
            String secondType;
            int attempts = 0;
            do {
                secondType = rollEliteType(rng);
                attempts++;
            } while (secondType.equals(firstType) && attempts < 10);
            if (!secondType.equals(firstType)) {
                applyEliteType(mob, secondType, sl);
            }
        }

        // Particle trail is handled in the level tick instead of Glowing — less
        // intrusive than the full outline glow while still making elites visually distinct.
        mob.addTag(TAG_ELITE);
    }

    private String rollEliteType(ThreadLocalRandom rng) {
        int roll = rng.nextInt(totalWeight);
        int cumulative = 0;
        for (var e : ELITE_POOL) {
            cumulative += Integer.parseInt(e[1]);
            if (roll < cumulative) return e[0];
        }
        return TAG_VOLATILE; // fallback
    }

    // =========================================================================
    // Apply an elite type to a mob — visual + spawn-time setup
    // =========================================================================

    private void applyEliteType(Mob mob, String type, ServerLevel sl) {
        mob.addTag(type);

        // Find the display info for this type
        String prefix = "Elite";
        int color = 0xFF5555;
        for (var e : ELITE_POOL) {
            if (e[0].equals(type)) {
                prefix = e[2];
                color = Integer.parseInt(e[3], 16);
                break;
            }
        }

        // Build name — append to existing custom name if dual-elite
        String baseName = mob.getType().getDescription().getString();
        Component existingName = mob.getCustomName();
        Component newName;
        if (existingName != null && !existingName.getString().equals(baseName)) {
            // Already has a custom name from a previous elite type — append
            newName = existingName.copy().append(Component.literal(" " + prefix).withColor(color));
        } else {
            newName = Component.literal(prefix + " " + baseName).withColor(color);
        }
        mob.setCustomName(newName);
        mob.setCustomNameVisible(true);

        // Type-specific spawn-time setup
        switch (type) {
            case TAG_SHIELDED -> {
                mob.addEffect(new MobEffectInstance(
                        MobEffects.RESISTANCE, Integer.MAX_VALUE, 1, false, false, false));
                mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                mob.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                mob.setDropChance(EquipmentSlot.HEAD, 0.0f);
                mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                mob.setDropChance(EquipmentSlot.CHEST, 0.0f);
            }
            case TAG_BERSERKER -> {
                // Berserkers need extra HP so the rage phases (50% and 25% thresholds)
                // actually have time to trigger before the mob dies. +100% HP = double
                // vanilla, so a zombie Berserker has 40 HP instead of 20.
                var hpAttr = mob.getAttribute(Attributes.MAX_HEALTH);
                if (hpAttr != null) {
                    hpAttr.addPermanentModifier(new AttributeModifier(
                            BERSERKER_HP_ID, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    mob.setHealth(mob.getMaxHealth());
                }
            }
            case TAG_PACKLEADER -> {
                // Spawn initial wave of 10 minions
                spawnMinions(mob, sl, 10);
            }
            case TAG_SILENCER -> {
                // Aura cloud is spawned each tick by tickSilencerClouds —
                // no spawn-time setup needed here.
            }
        }
    }

    // =========================================================================
    // DAMAGE: Blight + Binder
    // =========================================================================

    @SubscribeEvent
    public void onEliteDamage(LivingDamageEvent.Pre event) {
        var sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) return;
        LivingEntity victim = event.getEntity();

        // Blight: 20% max HP ignoring armor (only vs players)
        if (attacker.getTags().contains(TAG_BLIGHT) && victim instanceof Player) {
            event.setNewDamage(victim.getMaxHealth() * 0.20f);
        }

        // Arcanist-specific: armor/enchant-bypassing scaled damage. Uses
        // getOriginalDamage() as the baseline so Protection enchants and armor
        // don't whittle the value down before we multiply by the gear-score
        // multiplier. setNewDamage OVERRIDES vanilla's reduced result.
        // Only Arcanist gets this — other gear-hunters use vanilla damage and
        // rely on their utility mechanics (regen suppression, durability, etc).
        if (victim instanceof ServerPlayer sp && attacker.getTags().contains(TAG_ARCANIST)) {
            float baseline = event.getOriginalDamage();
            float mult = gearScoreToMultiplier(getOrComputeGearScore(sp));
            event.setNewDamage(baseline * mult);
        }

        // Suppressed-enchant penalty: applies to ANY elite hit (not just gear-hunters)
        // when the player's armor enchants have been silenced. Adds back the damage
        // that Protection-type enchants would normally absorb.
        if (victim instanceof ServerPlayer sp2 && isGearHunter(attacker)) {
            int suppressedLevels = suppressedProtectionLevels(sp2);
            if (suppressedLevels > 0) {
                float penalty = event.getNewDamage() * (suppressedLevels * 0.04f);
                event.setNewDamage(event.getNewDamage() + penalty);
            }
        }
    }

    private static boolean isGearHunter(LivingEntity attacker) {
        for (String tag : GEAR_HUNTER_TAGS) {
            if (attacker.getTags().contains(tag)) return true;
        }
        return false;
    }

    // =========================================================================
    // TARGETING: all hostile mobs pursue highest-gear player unless melee-range override
    // =========================================================================

    private static final double HUNTER_AGGRO_RADIUS = 24.0;
    private static final double HUNTER_MELEE_OVERRIDE_RADIUS = 2.0;

    @SubscribeEvent
    public void onMobRetarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer)) return;

        ServerPlayer best = pickBestTarget(mob);
        if (best != null) {
            event.setNewAboutToBeSetTarget(best);
        }
    }

    private void tickHostileTargeting(ServerLevel sl) {
        for (var entity : sl.getEntities().getAll()) {
            if (!(entity instanceof Monster mob) || !mob.isAlive()) continue;
            if (!(mob.getTarget() instanceof ServerPlayer current)) continue;

            ServerPlayer best = pickBestTarget(mob);
            if (best != null && best != current) {
                mob.setTarget(best);
            }
        }
    }

    // =========================================================================
    // SILENCER: persistent area cloud that suppresses all armor enchants inside
    // =========================================================================

    private static final float SILENCER_RADIUS = 4.0f;

    private void tickSilencerClouds(ServerLevel sl) {
        // Discard any old cloud entities — we re-spawn them every tick at the
        // mob's current position because AreaEffectCloud doesn't sync position
        // updates to clients after spawn. Spawning a fresh cloud is the simplest
        // way to make the visual follow the mob.
        for (var entity : sl.getEntities().getAll()) {
            if (entity instanceof net.minecraft.world.entity.AreaEffectCloud cloud
                    && cloud.getTags().contains("mlkymc_silencer_cloud")) {
                cloud.discard();
            }
        }

        // Spawn a fresh cloud at each living Silencer's current position
        double radSq = SILENCER_RADIUS * SILENCER_RADIUS;
        for (var entity : sl.getEntities().getAll()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (!mob.getTags().contains(TAG_SILENCER)) continue;

            var cloud = new net.minecraft.world.entity.AreaEffectCloud(
                    sl, mob.getX(), mob.getY(), mob.getZ());
            cloud.setRadius(SILENCER_RADIUS);
            cloud.setWaitTime(0);
            cloud.setDuration(20); // short — we respawn it every 10 ticks anyway
            cloud.setRadiusPerTick(0);
            cloud.setRadiusOnUse(0);
            cloud.addTag("mlkymc_silencer_cloud");
            sl.addFreshEntity(cloud);

            // Apply whole-body enchant suppression to any player within the radius
            var nearbyPlayers = sl.getEntitiesOfClass(ServerPlayer.class,
                    mob.getBoundingBox().inflate(SILENCER_RADIUS + 1),
                    p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
            for (var player : nearbyPlayers) {
                if (player.distanceToSqr(mob) > radSq) continue;
                long expiry = sl.getGameTime() + 15L;
                boolean wasSilenced = player.getPersistentData().getLongOr(NBT_SILENCED_UNTIL, 0L) > sl.getGameTime();
                player.getPersistentData().putLong(NBT_SILENCED_UNTIL, expiry);
                if (!wasSilenced) {
                    player.displayClientMessage(
                            Component.literal("Enchantments suppressed!").withColor(0xAA88FF), true);
                }
            }
        }
    }

    // =========================================================================
    // ARCANIST: line-of-sight magic bolts that bypass armor
    // =========================================================================

    private void tickArcanists(ServerLevel sl) {
        for (var entity : sl.getEntities().getAll()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (!mob.getTags().contains(TAG_ARCANIST)) continue;
            if (!(mob.getTarget() instanceof ServerPlayer target)) continue;
            if (mob.distanceToSqr(target) > 16 * 16) continue; // 16-block max range

            // Only fire if we can actually see the target (not through walls)
            if (!mob.getSensing().hasLineOfSight(target)) continue;

            // Deal mob attack damage with the Arcanist as source. Base 4 HP (2 hearts).
            // The actual armor/enchant bypass is handled in onEliteDamage, which
            // overrides the post-reduction value with originalDamage * gear-score multiplier
            // for all gear-hunter elites — see TAG_ARCANIST in onEliteDamage.
            target.hurtServer(sl, sl.damageSources().mobAttack(mob), 1.5f);

            // Particle trail from mob to target
            double x = mob.getX();
            double y = mob.getY() + mob.getBbHeight() * 0.8;
            double z = mob.getZ();
            double dx = (target.getX() - x) / 8.0;
            double dy = (target.getY() + target.getBbHeight() * 0.5 - y) / 8.0;
            double dz = (target.getZ() - z) / 8.0;
            for (int i = 0; i < 8; i++) {
                sl.sendParticles(ParticleTypes.ENCHANT,
                        x + dx * i, y + dy * i, z + dz * i, 2, 0.05, 0.05, 0.05, 0.0);
            }
            sl.playSound(null, mob.blockPosition(),
                    net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL,
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.5f);
        }
    }

    private ServerPlayer pickBestTarget(Mob mob) {
        if (!(mob.level() instanceof ServerLevel sl)) return null;
        var candidates = sl.getEntitiesOfClass(ServerPlayer.class,
                mob.getBoundingBox().inflate(HUNTER_AGGRO_RADIUS),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
        if (candidates.isEmpty()) return null;

        // Priority 1: any player within 2 blocks (melee safety — mob punches what's in its face)
        double meleeRadiusSq = HUNTER_MELEE_OVERRIDE_RADIUS * HUNTER_MELEE_OVERRIDE_RADIUS;
        ServerPlayer closest = null;
        double closestDistSq = Double.MAX_VALUE;
        for (var p : candidates) {
            double distSq = mob.distanceToSqr(p);
            if (distSq <= meleeRadiusSq && distSq < closestDistSq) {
                closest = p;
                closestDistSq = distSq;
            }
        }
        if (closest != null) return closest;

        // Priority 2: highest gear score within aggro range
        ServerPlayer highest = candidates.get(0);
        int bestScore = getOrComputeGearScore(highest);
        for (var p : candidates) {
            int s = getOrComputeGearScore(p);
            if (s > bestScore) {
                bestScore = s;
                highest = p;
            }
        }
        return highest;
    }

    // NBT key on armor pieces: expiry game-time after which the suppression lifts.
    private static final String NBT_ENCH_SUPPRESSED_UNTIL = "mlkymc_ench_suppressed_until";
    // Player-level tag for whole-body enchant suppression from Silencer clouds.
    private static final String NBT_SILENCED_UNTIL = "mlkymc_silenced_until";

    /**
     * Returns the total Protection-type enchant levels currently suppressed on a player.
     * A piece is suppressed if its NBT expiry is still in the future, or if the player
     * is standing in a Silencer cloud (whole-body suppression).
     */
    private static int suppressedProtectionLevels(ServerPlayer sp) {
        long now = sp.level().getGameTime();
        boolean wholeBodySilenced = sp.getPersistentData().getLongOr(NBT_SILENCED_UNTIL, 0L) > now;

        int total = 0;
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = sp.getItemBySlot(slot);
            if (piece.isEmpty()) continue;

            boolean pieceSilenced = wholeBodySilenced;
            if (!pieceSilenced) {
                long expiry = piece.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                                net.minecraft.world.item.component.CustomData.EMPTY)
                        .copyTag().getLongOr(NBT_ENCH_SUPPRESSED_UNTIL, 0L);
                pieceSilenced = expiry > now;
            }
            if (!pieceSilenced) continue;

            var enchants = piece.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                    net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            for (var entry : enchants.entrySet()) {
                String enchId = entry.getKey().getRegisteredName();
                if (enchId.contains("protection")) {
                    total += entry.getIntValue();
                }
            }
        }
        return total;
    }

    @SubscribeEvent
    public void onEliteHit(LivingDamageEvent.Post event) {
        var sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        // Binder: hits apply Slowness III (3s) + Weakness I (2s)
        if (attacker.getTags().contains(TAG_BINDER)) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2, false, true, true));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, true, true));
        }

        // Corrosive: each hit deals 5 extra durability damage to every worn armor piece.
        // This eats through gear fast — a Corrosive zombie hitting you 10 times costs 50
        // extra durability across each armor slot on top of vanilla's normal damage.
        if (attacker.getTags().contains(TAG_CORROSIVE) && victim instanceof ServerPlayer sp) {
            for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack armor = sp.getItemBySlot(slot);
                if (armor.isDamageableItem()) {
                    armor.hurtAndBreak(5, sp, slot);
                }
            }
        }

        // Regenbane: apply Hunger II for 10s. Hunger prevents natural regen by draining
        // the food bar below the 18/20 threshold vanilla requires for health regen.
        if (attacker.getTags().contains(TAG_REGENBANE)) {
            victim.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1, false, true, true));
        }

        // Silencer: normal melee hits also apply whole-body enchant suppression
        // for 5 seconds (100 ticks). Re-applied on every hit, so staying close
        // means the suppression never wears off.
        if (attacker.getTags().contains(TAG_SILENCER) && victim instanceof ServerPlayer sp) {
            long expiry = sp.level().getGameTime() + 100L;
            boolean wasSilenced = sp.getPersistentData().getLongOr(NBT_SILENCED_UNTIL, 0L) > sp.level().getGameTime();
            sp.getPersistentData().putLong(NBT_SILENCED_UNTIL, expiry);
            if (!wasSilenced) {
                sp.displayClientMessage(Component.literal("Enchantments suppressed!").withColor(0xAA88FF), true);
            }
        }

        // Armor-breaker: pick one random armor piece, deal 15 durability damage,
        // and mark that piece as enchant-suppressed for 10s (200 ticks).
        if (attacker.getTags().contains(TAG_ARMORBREAKER) && victim instanceof ServerPlayer sp) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET};
            // Pick a random slot that has a damageable item
            java.util.List<EquipmentSlot> wearing = new java.util.ArrayList<>();
            for (var slot : slots) {
                if (sp.getItemBySlot(slot).isDamageableItem()) wearing.add(slot);
            }
            if (!wearing.isEmpty()) {
                EquipmentSlot chosen = wearing.get(ThreadLocalRandom.current().nextInt(wearing.size()));
                ItemStack piece = sp.getItemBySlot(chosen);
                piece.hurtAndBreak(15, sp, chosen);
                // Mark the piece as enchant-suppressed
                long expiry = sp.level().getGameTime() + 200L;
                var cd = piece.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                cd.putLong(NBT_ENCH_SUPPRESSED_UNTIL, expiry);
                piece.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(cd));
                sp.displayClientMessage(Component.literal("Armor enchant suppressed!").withColor(0xFFAA55), true);
            }
        }

        // Flux: pick a random Protection enchant and temporarily invert its effect.
        // Implemented by setting a player-level flag that extends enchant suppression
        // for 5 seconds, scoped via a per-target cooldown.
        if (attacker.getTags().contains(TAG_FLUX) && victim instanceof ServerPlayer sp) {
            long now = sp.level().getGameTime();
            Long lastFlux = fluxCooldown.get(sp.getUUID());
            if (lastFlux == null || now - lastFlux >= 160L) { // 8s cooldown (160 ticks)
                // Find a random piece with any protection enchant
                EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS, EquipmentSlot.FEET};
                java.util.List<EquipmentSlot> eligible = new java.util.ArrayList<>();
                for (var slot : slots) {
                    ItemStack piece = sp.getItemBySlot(slot);
                    if (piece.isEmpty()) continue;
                    var enchants = piece.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                            net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
                    for (var entry : enchants.entrySet()) {
                        if (entry.getKey().getRegisteredName().contains("protection")) {
                            eligible.add(slot);
                            break;
                        }
                    }
                }
                if (!eligible.isEmpty()) {
                    EquipmentSlot chosen = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
                    ItemStack piece = sp.getItemBySlot(chosen);
                    long expiry = now + 100L; // 5s window
                    var cd = piece.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                    cd.putLong(NBT_ENCH_SUPPRESSED_UNTIL, expiry);
                    piece.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.of(cd));
                    fluxCooldown.put(sp.getUUID(), now);
                    sp.displayClientMessage(Component.literal("Flux surge! Enchant inverted!").withColor(0xFF55CC), true);
                }
            }
        }
    }

    // Per-target cooldown for Flux to prevent chain-stacking the inversion.
    private final java.util.Map<java.util.UUID, Long> fluxCooldown = new java.util.HashMap<>();

    // =========================================================================
    // Minion drops: clear item + XP drops entirely so Pack Leader farms are worthless
    // =========================================================================

    @SubscribeEvent
    public void onMinionDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity().getTags().contains(TAG_MINION)) {
            event.getDrops().clear();
        }
    }

    @SubscribeEvent
    public void onMinionXpDrop(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        if (event.getEntity().getTags().contains(TAG_MINION)) {
            event.setDroppedExperience(0);
        }
    }

    // =========================================================================
    // DEATH: Volatile explosion + Leech heal + Necromancer raise
    // =========================================================================

    @SubscribeEvent
    public void onMobDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob dead)) return;
        if (!(dead.level() instanceof ServerLevel sl)) return;

        // Volatile: explode on death, destroy blocks
        if (dead.getTags().contains(TAG_VOLATILE)) {
            BlockPos pos = dead.blockPosition();
            // Respect region protection
            String dim = sl.dimension().identifier().toString();
            var regionManager = com.mlkymc.MlkyMC.getRegionManager();
            boolean canExplode = regionManager == null
                    || !regionManager.isFlagActiveAt(com.mlkymc.region.RegionFlag.NO_EXPLOSIONS,
                    dim, pos.getX(), pos.getY(), pos.getZ());
            if (canExplode) {
                sl.explode(null, dead.getX(), dead.getY(), dead.getZ(),
                        2.0f, Level.ExplosionInteraction.BLOCK);
            } else {
                // Still do damage, just no block destruction
                sl.explode(null, dead.getX(), dead.getY(), dead.getZ(),
                        2.0f, Level.ExplosionInteraction.NONE);
            }
        }

        // Splitter: on death, split into 2-3 half-size copies of itself
        if (dead.getTags().contains(TAG_SPLITTER)) {
            int copies = 2 + ThreadLocalRandom.current().nextInt(2); // 2-3
            spawnMinions(dead, sl, copies);
        }

        // Enraged: on death, all mobs within 16 blocks get Speed I + Strength I for 10s
        if (dead.getTags().contains(TAG_ENRAGED)) {
            var nearbyMobs = sl.getEntitiesOfClass(Mob.class,
                    dead.getBoundingBox().inflate(16.0),
                    m -> m != dead && m.isAlive() && m instanceof Monster);
            for (var mob : nearbyMobs) {
                mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0, false, false, false));
                mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 0, false, false, false));
            }
            // Visual feedback: angry particles burst
            sl.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    dead.getX(), dead.getY() + 1, dead.getZ(),
                    12, 2.0, 1.0, 2.0, 0.1);
        }

        // Scan for nearby Leech and Necromancer elites reacting to this death
        if (!dead.getTags().contains(TAG_ELITE)) {
            // Only react to non-elite mob deaths (prevent chain reactions)
            var nearby = sl.getEntitiesOfClass(Mob.class,
                    dead.getBoundingBox().inflate(10.0), Mob::isAlive);

            for (var mob : nearby) {
                // Leech: heal 25% of dead mob's max HP
                if (mob.getTags().contains(TAG_LEECH)) {
                    mob.heal(dead.getMaxHealth() * 0.25f);
                    sl.sendParticles(ParticleTypes.HEART,
                            mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(),
                            4, 0.3, 0.3, 0.3, 0.05);
                }

                // Necromancer: 40% chance to raise a zombie
                if (mob.getTags().contains(TAG_NECROMANCER)) {
                    if (ThreadLocalRandom.current().nextDouble() < 0.40) {
                        spawnRaisedZombie(sl, dead.blockPosition());
                    }
                }
            }
        }
    }

    // =========================================================================
    // TICK: Pack Leader spawning, Stormcaller lightning, Watcher aura, Berserker
    // =========================================================================

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        long tick = sl.getGameTime();

        // Run different elite ticks at different intervals to spread load
        if (tick % 100 == 0)  tickStormcallers(sl);       // every 5s
        if (tick % 40 == 0)   tickWatchers(sl);            // every 2s
        if (tick % 40 == 0)   tickBerserkers(sl);          // every 2s
        if (tick % 40 == 10)  tickPackLeaders(sl);         // every 2s (offset)
        if (tick % 200 == 20) tickScreamers(sl);           // every 10s (offset)
        if (tick % 20 == 5)   tickPoisonous(sl);           // every 1s (offset)
        if (tick % 10 == 0)   tickEliteParticles(sl);      // every 0.5s
        if (tick % 20 == 15)  tickHostileTargeting(sl);     // every 1s (offset)
        if (tick % 40 == 20)  tickArcanists(sl);           // every 2s (offset)
        if (tick % 10 == 5)   tickSilencerClouds(sl);      // every 0.5s (offset)
    }

    private void tickEliteParticles(ServerLevel sl) {
        for (var entity : sl.getEntities().getAll()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (!mob.getTags().contains(TAG_ELITE)) continue;

            double x = mob.getX();
            double y = mob.getY() + mob.getBbHeight() * 0.5;
            double z = mob.getZ();

            // Pick particle based on the first elite tag found — each type has a
            // distinct faint trail so players can identify the threat at a glance.
            var particle = ParticleTypes.SMOKE; // fallback
            if (mob.getTags().contains(TAG_BLIGHT))       particle = ParticleTypes.SOUL;
            else if (mob.getTags().contains(TAG_VOLATILE))    particle = ParticleTypes.FLAME;
            else if (mob.getTags().contains(TAG_BINDER))      particle = ParticleTypes.WITCH;
            else if (mob.getTags().contains(TAG_STORMCALLER)) particle = ParticleTypes.ELECTRIC_SPARK;
            else if (mob.getTags().contains(TAG_WATCHER))     particle = ParticleTypes.ENCHANT;
            else if (mob.getTags().contains(TAG_PACKLEADER))  particle = ParticleTypes.HAPPY_VILLAGER;
            else if (mob.getTags().contains(TAG_LEECH))       particle = ParticleTypes.DAMAGE_INDICATOR;
            else if (mob.getTags().contains(TAG_NECROMANCER)) particle = ParticleTypes.SOUL_FIRE_FLAME;
            else if (mob.getTags().contains(TAG_SHIELDED))    particle = ParticleTypes.ASH;
            else if (mob.getTags().contains(TAG_BERSERKER))   particle = ParticleTypes.LAVA;
            else if (mob.getTags().contains(TAG_CORROSIVE))   particle = ParticleTypes.ITEM_SLIME;
            else if (mob.getTags().contains(TAG_SPLITTER))    particle = ParticleTypes.END_ROD;
            else if (mob.getTags().contains(TAG_SCREAMER))    particle = ParticleTypes.NOTE;
            else if (mob.getTags().contains(TAG_POISONOUS))   particle = ParticleTypes.ITEM_SLIME;
            else if (mob.getTags().contains(TAG_ENRAGED))     particle = ParticleTypes.LAVA;
            else if (mob.getTags().contains(TAG_REGENBANE))   particle = ParticleTypes.HEART;
            else if (mob.getTags().contains(TAG_ARMORBREAKER)) particle = ParticleTypes.CRIT;
            else if (mob.getTags().contains(TAG_ARCANIST))    particle = ParticleTypes.ENCHANT;
            else if (mob.getTags().contains(TAG_FLUX))        particle = ParticleTypes.PORTAL;
            else if (mob.getTags().contains(TAG_SILENCER))    particle = ParticleTypes.WITCH;

            sl.sendParticles(particle, x, y, z, 2, 0.2, 0.3, 0.2, 0.01);
        }
    }

    private void tickStormcallers(ServerLevel sl) {
        for (var entity : sl.getEntities().getAll()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (!mob.getTags().contains(TAG_STORMCALLER)) continue;
            // Only strike when the Stormcaller is actively hunting a player —
            // mob.getTarget() is set by vanilla AI when the mob has aggro.
            // Prevents idle Stormcallers underground from zapping surface players.
            if (!(mob.getTarget() instanceof Player target)) continue;
            // Strike lightning near the player (offset by 0-3 blocks randomly)
            var rng = ThreadLocalRandom.current();
            double lx = target.getX() + rng.nextInt(7) - 3;
            double lz = target.getZ() + rng.nextInt(7) - 3;
            var bolt = EntityType.LIGHTNING_BOLT.create(sl, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(lx, target.getY(), lz, 0, 0);
                sl.addFreshEntity(bolt);
            }
        }
    }

    private void tickWatchers(ServerLevel sl) {
        for (var entity : sl.getEntities().getAll()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (!mob.getTags().contains(TAG_WATCHER)) continue;
            // Buff nearby mobs with Strength I + Speed I
            var nearby = sl.getEntitiesOfClass(Mob.class,
                    mob.getBoundingBox().inflate(8.0),
                    m -> m != mob && m.isAlive() && m instanceof Monster);
            for (var ally : nearby) {
                ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, false, false, false));
                ally.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, false, false, false));
            }
        }
    }

    private void tickBerserkers(ServerLevel sl) {
        for (var entity : sl.getEntities().getAll()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (!mob.getTags().contains(TAG_BERSERKER)) continue;
            float hpPercent = mob.getHealth() / mob.getMaxHealth();
            if (hpPercent <= 0.25f) {
                mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, false, false, false));
                mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 1, false, false, false));
            } else if (hpPercent <= 0.50f) {
                mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, false, false, false));
                mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, false, false, false));
            }
        }
    }

    private void tickPackLeaders(ServerLevel sl) {
        for (var entity : sl.getEntities().getAll()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (!mob.getTags().contains(TAG_PACKLEADER)) continue;
            // Count existing minions nearby — cap at 15
            long minionCount = sl.getEntitiesOfClass(Mob.class,
                    mob.getBoundingBox().inflate(16.0),
                    m -> m.getTags().contains(TAG_MINION) && m.isAlive()).size();
            if (minionCount < 15) {
                spawnMinions(mob, sl, 1);
            }
        }
    }

    private void tickScreamers(ServerLevel sl) {
        for (var entity : sl.getEntities().getAll()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (!mob.getTags().contains(TAG_SCREAMER)) continue;
            // Only scream when actively targeting a player
            if (!(mob.getTarget() instanceof Player target)) continue;

            // Rally all hostile mobs within 32 blocks toward the player's position
            var nearby = sl.getEntitiesOfClass(Mob.class,
                    mob.getBoundingBox().inflate(32.0),
                    m -> m != mob && m.isAlive() && m instanceof Monster);
            for (var ally : nearby) {
                ally.getNavigation().moveTo(target, 1.2);
            }
            // Audio + visual feedback
            sl.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    net.minecraft.sounds.SoundEvents.RAVAGER_ROAR,
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.5f, 1.5f);
            sl.sendParticles(ParticleTypes.SONIC_BOOM,
                    mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(),
                    3, 0.5, 0.5, 0.5, 0.0);
        }
    }

    private void tickPoisonous(ServerLevel sl) {
        for (var entity : sl.getEntities().getAll()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive()) continue;
            if (!mob.getTags().contains(TAG_POISONOUS)) continue;

            // Any player within 1.5 blocks of the mob gets Poison I for 3s.
            var nearbyPlayers = sl.getEntitiesOfClass(ServerPlayer.class,
                    mob.getBoundingBox().inflate(1.5), Player::isAlive);
            for (var player : nearbyPlayers) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.POISON, 60, 0, false, true, true));
            }
            // Green particle trail behind the mob
            sl.sendParticles(ParticleTypes.ITEM_SLIME,
                    mob.getX(), mob.getY() + 0.2, mob.getZ(),
                    3, 0.2, 0.1, 0.2, 0.01);
        }
    }

    // =========================================================================
    // Minion spawning (Pack Leader + Splitter + Necromancer)
    // =========================================================================

    private void spawnMinions(Mob leader, ServerLevel sl, int count) {
        for (int i = 0; i < count; i++) {
            var rng = ThreadLocalRandom.current();
            var entity = leader.getType().create(sl, EntitySpawnReason.MOB_SUMMONED);
            if (!(entity instanceof Mob minion)) continue;

            minion.snapTo(leader.getX(), leader.getY() + 1, leader.getZ(), rng.nextFloat() * 360, 0);
            minion.addTag(TAG_MINION);

            // Slightly smaller than normal (65% size) — visually distinct but not tiny
            var scaleAttr = minion.getAttribute(Attributes.SCALE);
            if (scaleAttr != null) {
                scaleAttr.addPermanentModifier(new AttributeModifier(
                        MINION_SCALE_ID, -0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            // Quarter HP (1/4 of normal)
            var hpAttr = minion.getAttribute(Attributes.MAX_HEALTH);
            if (hpAttr != null) {
                hpAttr.addPermanentModifier(new AttributeModifier(
                        MINION_HP_ID, -0.75, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                minion.setHealth(minion.getMaxHealth());
            }
            // Half damage
            var dmgAttr = minion.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmgAttr != null) {
                dmgAttr.addPermanentModifier(new AttributeModifier(
                        MINION_DMG_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            // Slowness I
            minion.addEffect(new MobEffectInstance(
                    MobEffects.SLOWNESS, Integer.MAX_VALUE, 0, false, false, false));

            sl.addFreshEntity(minion);
        }
    }

    private void spawnRaisedZombie(ServerLevel sl, BlockPos pos) {
        var entity = EntityType.ZOMBIE.create(sl, EntitySpawnReason.MOB_SUMMONED);
        if (!(entity instanceof Mob zombie)) return;
        zombie.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        zombie.addTag(TAG_MINION);
        if (zombie instanceof net.minecraft.world.entity.monster.zombie.Zombie z) {
            z.setBaby(true); // small risen corpse visual
        }
        sl.addFreshEntity(zombie);
    }

    // =========================================================================
    // XP: elites drop bonus XP
    // =========================================================================

    @SubscribeEvent
    public void onXpDrop(LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!mob.getTags().contains(TAG_ELITE)) return;

        // +5 XP per elite type on the mob
        int bonus = 0;
        for (var e : ELITE_POOL) {
            if (mob.getTags().contains(e[0])) bonus += 5;
        }
        event.setDroppedExperience(event.getDroppedExperience() + bonus);
    }

    // =========================================================================
    // Threat computation (unchanged — still needed for elite rate)
    // =========================================================================

    private int computeThreat(ServerPlayer player) {
        ClassData data = classManager.getOrCreate(player);
        int maxLevel = 0;
        for (ProfessionType prof : ProfessionType.values()) {
            maxLevel = Math.max(maxLevel, data.getLevel(prof));
        }
        int armorTotal = 0;
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            armorTotal += armorTierValue(player.getItemBySlot(slot));
        }
        return maxLevel + armorTotal;
    }

    private int armorTierValue(ItemStack piece) {
        if (piece.isEmpty()) return 0;
        String id = piece.getItem().toString();
        if (id.contains("netherite_")) return 6;
        if (id.contains("diamond_"))   return 4;
        if (id.contains("iron_"))      return 2;
        if (id.contains("golden_"))    return 1;
        if (id.contains("chainmail_")) return 1;
        return 0;
    }

    private int tierForThreat(int threat) {
        if (threat >= 70) return 4;
        if (threat >= 45) return 3;
        if (threat >= 25) return 2;
        if (threat >= 10) return 1;
        return 0;
    }

    private void tagForTier(Mob mob, int tier) {
        switch (tier) {
            case 1 -> mob.addTag(TAG_TIER_1);
            case 2 -> mob.addTag(TAG_TIER_2);
            case 3 -> mob.addTag(TAG_TIER_3);
            case 4 -> mob.addTag(TAG_TIER_4);
        }
    }

    // =========================================================================
    // Gear score — scales damage from gear-hunter elites on a per-target basis
    // =========================================================================

    private final java.util.Map<java.util.UUID, Integer> gearScoreCache = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent
    public void onEquipmentChange(net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            gearScoreCache.remove(sp.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            gearScoreCache.remove(sp.getUUID());
        }
    }

    private int getOrComputeGearScore(ServerPlayer player) {
        return gearScoreCache.computeIfAbsent(player.getUUID(), u -> computeGearScore(player));
    }

    /**
     * Weighted sum across all four armor slots.
     * Material score + (enchant score + smith gamble score) * (1 + fletcher boost).
     * A full leather player with no enchants is ~8. Full netherite + Prot 4 + Smith
     * gambles + Fletcher boost tops out around 280+.
     */
    private int computeGearScore(ServerPlayer player) {
        int total = 0;
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            total += computePieceScore(player.getItemBySlot(slot));
        }
        return total;
    }

    private int computePieceScore(ItemStack piece) {
        if (piece.isEmpty()) return 0;

        // Material base
        int materialScore;
        String id = piece.getItem().toString();
        if (id.contains("netherite_"))      materialScore = 40;
        else if (id.contains("diamond_"))   materialScore = 25;
        else if (id.contains("iron_"))      materialScore = 12;
        else if (id.contains("chainmail_")) materialScore = 6;
        else if (id.contains("golden_"))    materialScore = 4;
        else if (id.contains("leather_"))   materialScore = 2;
        else                                materialScore = 0;

        // Enchantments
        double enchantScore = 0;
        var enchants = piece.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            int level = entry.getIntValue();
            String enchId = entry.getKey().getRegisteredName();
            if (enchId.contains("protection") && !enchId.contains("blast") && !enchId.contains("fire")
                    && !enchId.contains("projectile")) {
                enchantScore += level * 10; // general Protection
            } else if (enchId.contains("blast_protection") || enchId.contains("fire_protection")
                    || enchId.contains("projectile_protection")) {
                enchantScore += level * 6;
            } else if (enchId.contains("thorns") || enchId.contains("unbreaking")) {
                enchantScore += level * 3;
            } else {
                enchantScore += level * 2;
            }
        }

        // Smith gamble attributes
        double smithScore = 0;
        var gambles = com.mlkymc.classes.SmithGambleHandler.readGambleData(piece);
        for (var entry : gambles.values()) {
            switch (entry.attrName) {
                case "armor"              -> smithScore += entry.value * 8;
                case "armor_toughness"    -> smithScore += entry.value * 6;
                case "knockback_resistance" -> smithScore += entry.value * 5;
                case "health"             -> smithScore += entry.value * 4;
                default                   -> smithScore += entry.value * 1;
            }
        }

        // Fletcher modifier — multiplies enchant + smith gamble contribution
        double fletcherBoost = readFletcherModifier(piece);
        double bonusTotal = (enchantScore + smithScore) * (1.0 + fletcherBoost);

        return (int) Math.round(materialScore + bonusTotal);
    }

    /** Inline copy of FletcherModifierHandler.readModifier (which is private). */
    private static double readFletcherModifier(ItemStack stack) {
        var cd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        return cd.copyTag().getDoubleOr("mlkymc_fletcher_modifier", 0);
    }

    /**
     * Linear interpolation from 1.0x at score <= 50 to 3.0x at score >= 250.
     * Returns the multiplier to apply to damage dealt by gear-hunter elites.
     */
    private static float gearScoreToMultiplier(int score) {
        if (score <= 50) return 1.0f;
        if (score >= 250) return 3.0f;
        float t = (score - 50) / 200f;
        return 1.0f + t * 2.0f;
    }
}
