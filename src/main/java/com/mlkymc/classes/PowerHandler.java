package com.mlkymc.classes;

import com.mlkymc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side handler for Primary/Secondary power keybinds.
 * Listens for [MLKYMC_PRIMARY:charge] and [MLKYMC_SECONDARY] chat messages
 * sent by the client keybind system, cancels them, and routes to the correct skill.
 */
public class PowerHandler {
    private final ClassManager classManager;
    private com.mlkymc.grave.GraveManager graveManager;

    // Cooldowns
    private final Map<UUID, Long> resurrectionCooldown = new HashMap<>();
    // Ghost mimic: pending form selection (Secondary cycles, Primary confirms)
    private final Map<UUID, String> ghostPendingMimic = new HashMap<>();
    private final Map<UUID, Long> endermanTeleportCooldown = new HashMap<>();

    // Adaptive enchantment ResourceKey
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> ADAPTIVE_ENCHANT =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("mlkymc", "adaptive"));

    /** Check if the player is wearing a helmet with the Adaptive enchantment. */
    public static boolean hasAdaptiveHelmet(net.minecraft.server.level.ServerPlayer player) {
        var helmet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (helmet.isEmpty()) return false;
        var registry = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var holder = registry.get(ADAPTIVE_ENCHANT).orElse(null);
        if (holder == null) return false;
        return helmet.getTagEnchantments().getLevel(holder) > 0;
    }

    public PowerHandler(ClassManager classManager) {
        this.classManager = classManager;
        instance = this;
    }

    public void setGraveManager(com.mlkymc.grave.GraveManager graveManager) {
        this.graveManager = graveManager;
    }

    private static PowerHandler instance;

    public static PowerHandler getInstance() { return instance; }

    public void clearGhostState(java.util.UUID uuid) {
        ghostPendingMimic.remove(uuid);
    }

    /**
     * Clear all active power states for a player (ore scan, nature's call, jackhammer, etc).
     * Called on class reset.
     */
    public void clearAllStates(ServerPlayer player) {
        UUID uuid = player.getUUID();
        oreScanActive.remove(uuid);
        oreScanNextCoalTick.remove(uuid);
        clearOreMarkers(uuid);
        naturesCallActive.remove(uuid);
        jackhammerExpiryTick.remove(uuid);

        // Remove lingering effects
        player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);

        // Remove jackhammer modifiers
        var breakAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
        if (breakAttr != null) {
            breakAttr.removeModifier(JACKHAMMER_SPEED_ID);
        }
        var dmgAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.removeModifier(JACKHAMMER_DAMAGE_ID);
        }
    }

    /**
     * On login: clear stale ore scan glowing effect (state is lost on relog).
     */
    @SubscribeEvent
    public void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();

        // Ore scan state is in-memory only — always OFF after relog
        if (oreScanActive.remove(uuid)) {
            oreScanNextCoalTick.remove(uuid);
            clearOreMarkers(uuid);
        }
        // Remove stale Glowing effect from previous session
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) {
            player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
        }
        // Clean up any stale ore scan markers near the player (chunks now loaded)
        if (player.level() instanceof ServerLevel sl) {
            var stale = sl.getEntitiesOfClass(net.minecraft.world.entity.monster.Shulker.class,
                    player.getBoundingBox().inflate(30),
                    e -> e.getTags().contains("mlkymc_ore_marker"));
            for (var marker : stale) {
                marker.discard();
            }
        }
        // Clear pending ghost mimic on relog
        ghostPendingMimic.remove(uuid);
        // Sync soul energy state to client
        classManager.sendSoulSync(player);
    }

    @SubscribeEvent
    public void onMilkyStarRightClick(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.CLERIC) return;
        if (!data.isSoulEnergyMode()) return;

        var held = player.getMainHandItem();
        if (!held.is(com.mlkymc.registry.ModItems.MILKY_STAR.get())) return;

        if (data.getSoulEnergy() >= 100) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Soul Energy is already full!").withColor(0xAA55FF));
            return;
        }

        held.shrink(1);
        classManager.addSoulEnergy(player, 20);
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.5f);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("+20 Soul Energy").withColor(0xAA55FF), true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        String raw = event.getRawText();

        if (raw.startsWith("[MLKYMC_PRIMARY:") && raw.endsWith("]")) {
            event.setCanceled(true);
            handlePrimary(event.getPlayer(), raw);
        } else if (raw.equals("[MLKYMC_SECONDARY]")) {
            event.setCanceled(true);
            handleSecondary(event.getPlayer());
        } else if (raw.startsWith("[MLKYMC_CLASS_SELECT:") && raw.endsWith("]")) {
            event.setCanceled(true);
            String className = raw.substring(21, raw.length() - 1);
            try {
                ClassType classType = ClassType.valueOf(className);
                classManager.selectClass(event.getPlayer(), classType);
            } catch (IllegalArgumentException ignored) {}
        }

        // Ghost mimic form selection via chat
        var gm = com.mlkymc.MlkyMC.getGhostManager();
        if (gm != null && gm.isGhost(event.getPlayer().getUUID())) {
            String lower = raw.toLowerCase().trim();
            if (lower.equals("bat") || lower.equals("creeper") || lower.equals("enderman")) {
                event.setCanceled(true);
                var gdm = com.mlkymc.MlkyMC.getGhostDataManager();
                if (gdm != null) {
                    var data = gdm.getOrCreate(event.getPlayer().getUUID());
                    gdm.startMimic(event.getPlayer(), data, lower);
                }
            }
        }
    }

    private void handlePrimary(ServerPlayer player, String raw) {
        // Ghost uses Primary for mimic-specific actions (Creeper knockback)
        var gm = com.mlkymc.MlkyMC.getGhostManager();
        if (gm != null && gm.isGhost(player.getUUID())) {
            handleGhostPrimary(player);
            return;
        }

        ClassData data = classManager.getOrCreate(player);
        if (!data.hasChosenClass()) return;

        int charge;
        try {
            charge = Integer.parseInt(raw.substring(16, raw.length() - 1));
        } catch (NumberFormatException e) {
            return;
        }
        charge = Math.max(0, Math.min(charge, 100));

        switch (data.getChosenClass()) {
            case ADVENTURER -> adventurerPrimary(player, data, charge);
            case CLERIC -> clericPrimary(player, data);
            case FARMHAND -> farmhandPrimary(player, data);
            case MINECRAFTER -> minecrafterPrimary(player, data);
            case SMITH -> smithPrimary(player, data);
            default -> {}
        }
    }

    // --- Ghost Abilities via keybinds ---

    private void handleGhostPrimary(ServerPlayer player) {
        var gdm = com.mlkymc.MlkyMC.getGhostDataManager();
        if (gdm == null) return;
        var data = gdm.getOrCreate(player.getUUID());

        // Creeper mimic: knockback explosion (ends mimic early, no completion bonus)
        if (data.isMimicking() && "CREEPER".equals(data.activeMimic)) {
            if (player.level() instanceof ServerLevel sl) {
                var nearby = sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        player.getBoundingBox().inflate(2));
                for (var entity : nearby) {
                    if (entity == player) continue;
                    double dx = entity.getX() - player.getX();
                    double dz = entity.getZ() - player.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > 0) {
                        entity.push(dx / dist * 1.5, 0.5, dz / dist * 1.5);
                    }
                }
                sl.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                player.displayClientMessage(Component.literal("Creeper blast! Knockback!").withColor(0x55FF55), true);
            }
            gdm.endMimic(player, data, false); // ends mimic, sets 60s cooldown
            return;
        }

        // Enderman mimic: teleport to looked-at block (ground only, 2s cooldown)
        if (data.isMimicking() && "ENDERMAN".equals(data.activeMimic)) {
            if (!player.onGround()) {
                player.sendSystemMessage(Component.literal("Must be on the ground to teleport!").withColor(0xFF5555));
                return;
            }
            Long lastTp = endermanTeleportCooldown.get(player.getUUID());
            long now = System.currentTimeMillis();
            if (lastTp != null && now - lastTp < 2000) {
                int remaining = (int) ((2000 - (now - lastTp)) / 1000) + 1;
                player.sendSystemMessage(Component.literal("Teleport ready in " + remaining + "s").withColor(0xFF5555));
                return;
            }
            if (player.level() instanceof ServerLevel sl) {
                var hitResult = player.pick(20.0, 0, false);
                if (hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                    BlockPos target = blockHit.getBlockPos().above();
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                            player.getX(), player.getY() + 1, player.getZ(),
                            20, 0.5, 1.0, 0.5, 0.1);
                    player.connection.teleport(target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                            player.getYRot(), player.getXRot());
                    sl.playSound(null, target, SoundEvents.ENDERMAN_TELEPORT,
                            SoundSource.PLAYERS, 1.0f, 1.0f);
                    endermanTeleportCooldown.put(player.getUUID(), now);
                }
            }
            return;
        }

        // No mimic active — nothing to do
        player.sendSystemMessage(Component.literal("No active mimic ability. Use Secondary to select a form.").withColor(0xAAAAAA));
    }

    private void handleGhostSecondary(ServerPlayer player) {
        var gdm = com.mlkymc.MlkyMC.getGhostDataManager();
        if (gdm == null) return;
        var data = gdm.getOrCreate(player.getUUID());

        // Crouching: toggle Haunt Zone
        if (player.isShiftKeyDown()) {
            gdm.toggleHauntZone(player, data);
            return;
        }

        // Not crouching: cycle mimic selection
        if (!data.canMimic()) {
            player.sendSystemMessage(Component.literal("Need 50 accumulated SE to unlock Spectral Mimic.").withColor(0xFF5555));
            return;
        }
        if (data.isMimicking()) {
            player.sendSystemMessage(Component.literal("Already mimicking! Wait for it to expire.").withColor(0xFF5555));
            return;
        }

        // Open mimic selection GUI
        com.mlkymc.ghost.MimicSelectionMenu.open(player);
    }

    private void handleSecondary(ServerPlayer player) {
        // Ghost uses Secondary for mimic selection / haunt zone
        var gm = com.mlkymc.MlkyMC.getGhostManager();
        if (gm != null && gm.isGhost(player.getUUID())) {
            handleGhostSecondary(player);
            return;
        }

        ClassData data = classManager.getOrCreate(player);
        if (!data.hasChosenClass()) return;

        switch (data.getChosenClass()) {
            case ADVENTURER -> adventurerSecondary(player, data);
            case CLERIC -> clericSecondary(player, data);
            case FARMHAND -> farmhandSecondary(player, data);
            case MINECRAFTER -> minecrafterSecondary(player, data);
            case SMITH -> smithSecondary(player, data);
            default -> {}
        }
    }

    // --- Cooldown helpers ---

    private boolean isOnCooldown(Map<UUID, Long> cdMap, ServerPlayer player) {
        Long expiry = cdMap.get(player.getUUID());
        return expiry != null && player.level().getGameTime() < expiry;
    }

    private long getCooldownRemaining(Map<UUID, Long> cdMap, ServerPlayer player) {
        Long expiry = cdMap.get(player.getUUID());
        if (expiry == null) return 0;
        long remaining = expiry - player.level().getGameTime();
        return remaining > 0 ? remaining / 20 : 0;
    }

    // =========================================================================
    // ADVENTURER SECONDARY: Lifesteal
    // Active for 10s, 60s cooldown. Siphons 20% of damage dealt as healing.
    // Minimum 1 HP (half heart) per hit to prevent punch-spam exploit.
    // =========================================================================

    private final Map<UUID, Long> lifestealCooldown = new HashMap<>();
    private static final Map<UUID, Long> lifestealExpiry = new HashMap<>();

    public static boolean isLifestealActive(UUID uuid) {
        Long expiry = lifestealExpiry.get(uuid);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    private void adventurerSecondary(ServerPlayer player, ClassData data) {
        if (isOnCooldown(lifestealCooldown, player)) {
            player.sendSystemMessage(Component.literal("Lifesteal on cooldown! " +
                    getCooldownRemaining(lifestealCooldown, player) + "s").withColor(0xFF5555));
            return;
        }

        lifestealExpiry.put(player.getUUID(), System.currentTimeMillis() + 10_000); // 10s duration
        lifestealCooldown.put(player.getUUID(), player.level().getGameTime() + 1200); // 60s cooldown

        player.displayClientMessage(Component.literal("Lifesteal: Active for 10s!").withColor(0xFF5555), true);
        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.blockPosition(),
                    SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.3f, 1.8f);
        }
    }

    /**
     * Lifesteal healing on damage dealt. Called from damage event.
     */
    @SubscribeEvent
    public void onLifestealHit(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        var source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;
        if (!isLifestealActive(player.getUUID())) return;

        // Use original damage (before armor reduction) so Sharpness etc. is reflected
        float damage = event.getOriginalDamage();
        if (damage <= 0) return;

        // 20% of pre-armor damage, minimum 1 HP (half heart)
        float heal = Math.max(1.0f, damage * 0.2f);
        player.heal(heal);
    }

    // =========================================================================
    // ADVENTURER PRIMARY: Dash
    // Adaptive enchantment: allows one air dash (resets on landing)
    // =========================================================================

    private final java.util.Set<UUID> airDashUsed = new java.util.HashSet<>();

    private void adventurerPrimary(ServerPlayer player, ClassData data, int chargePercent) {
        if (chargePercent < 10) return;

        boolean adaptive = hasAdaptiveHelmet(player);

        if (!player.onGround()) {
            if (adaptive && !airDashUsed.contains(player.getUUID())) {
                // Air dash — allowed once
                airDashUsed.add(player.getUUID());
            } else {
                player.sendSystemMessage(Component.literal(
                        adaptive ? "Air dash already used! Land to reset." : "Must be on solid ground to dash!")
                        .withColor(0xFF5555));
                return;
            }
        }

        double power = 0.5 + (chargePercent / 100.0) * 2.0;

        // Dash in the full look direction (including up/down)
        Vec3 look = player.getLookAngle();
        player.push(look.x * power, look.y * power, look.z * power);
        player.hurtMarked = true;

        // Reset fall distance on air dash to prevent fall damage from the dash itself
        if (!player.onGround()) {
            player.fallDistance = 0;
        }

        float pitch = 1.0f + (chargePercent / 100f) * 0.5f;
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5f, pitch);
    }

    /**
     * Reset air dash when player lands. Called from server tick.
     */
    public void tickAirDashReset(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 5 != 0) return; // Check every 5 ticks

        var toReset = new java.util.ArrayList<UUID>();
        for (UUID uuid : airDashUsed) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(uuid);
            if (player == null || player.onGround()) {
                toReset.add(uuid);
            }
        }
        toReset.forEach(airDashUsed::remove);
    }

    // =========================================================================
    // CLERIC PRIMARY: Healing Pulse
    // Uses 1 Milky Star (from jar, shulker, bundle, or inventory) + player XP
    // Heals all nearby players. Radius and amount scale with Cleric level.
    // =========================================================================

    private void clericPrimary(ServerPlayer player, ClassData data) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        int selectedAbility = data.getSelectedAltarAbility();

        // Route to altar abilities if selected
        if (selectedAbility > 0) {
            castAltarAbility(player, data, serverLevel, selectedAbility);
            return;
        }

        // Base Soul Skill: 40 SE, heals 3 hearts. No cooldown — gated by SE only.
        int seCost = 40;
        if (!spendSE(player, data, seCost)) return;

        double radius = 8.0;

        var nearbyPlayers = new java.util.ArrayList<>(serverLevel.getEntitiesOfClass(ServerPlayer.class,
                player.getBoundingBox().inflate(radius)));
        // Filter out PvP-tagged players (except caster)
        nearbyPlayers.removeIf(p -> !p.getUUID().equals(player.getUUID())
                && com.mlkymc.pvp.PvPTagManager.isPvPTagged(p.getUUID()));
        int othersHealed = 0;
        for (var nearby : nearbyPlayers) {
            nearby.heal(6.0f); // 3 hearts
            if (!nearby.getUUID().equals(player.getUUID())) {
                othersHealed++;
                nearby.sendSystemMessage(Component.literal("Healed by " +
                        player.getName().getString() + "'s Heal!").withColor(0x55FF55));
            }
        }

        if (othersHealed > 0) {
            player.displayClientMessage(Component.literal("Heal! Healed " +
                    othersHealed + " player(s) for 3 hearts").withColor(0xAA55FF), true);
        } else {
            player.displayClientMessage(Component.literal("Heal! (no nearby allies)").withColor(0xAA55FF), true);
        }
        serverLevel.playSound(null, player.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
        classManager.addXp(player, ProfessionType.CLERIC, 3 + othersHealed * 8, "heal pulse");
    }

    /**
     * Spend SE from personal bar first, then altar pool if needed.
     * Returns true if cost was paid.
     */
    private boolean spendSE(ServerPlayer player, ClassData data, int cost) {
        int personal = data.getSoulEnergy();
        if (personal >= cost) {
            data.addSoulEnergy(-cost);
            classManager.sendSoulSync(player);
            awardLapisFromSE(player, cost);
            return true;
        }

        // Try altar pool
        var altarManager = com.mlkymc.MlkyMC.getSoulAltarManager();
        if (altarManager != null) {
            var altar = altarManager.getAltarByOwner(player.getUUID());
            if (altar != null) {
                // Use personal SE first, remainder from altar
                int fromPersonal = personal;
                int fromAltar = cost - fromPersonal;
                if (altarManager.spendAltarSE(altar, fromAltar)) {
                    data.addSoulEnergy(-fromPersonal);
                    classManager.sendSoulSync(player);
                    awardLapisFromSE(player, cost); // counts altar SE too
                    return true;
                }
            }
        }

        player.sendSystemMessage(Component.literal("Need " + cost + " Soul Energy! (Current: " +
                personal + ")").withColor(0xFF5555));
        return false;
    }

    /**
     * Cleric chance to gain lapis lazuli when spending Soul Energy.
     * 4% chance per 1 SE spent. Does NOT count for channeling SE into altar.
     */
    private void awardLapisFromSE(ServerPlayer player, int seSpent) {
        int lapisGained = 0;

        for (int i = 0; i < seSpent; i++) {
            if (player.level().random.nextFloat() < 0.04f) {
                lapisGained++;
            }
        }
        if (lapisGained > 0) {
            var lapis = new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.LAPIS_LAZULI, lapisGained);
            if (!player.getInventory().add(lapis)) {
                player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), lapis);
            }
        }
    }

    /**
     * Cast an altar tier ability based on the selected index.
     */
    private void castAltarAbility(ServerPlayer player, ClassData data, ServerLevel level, int abilityIndex) {
        switch (abilityIndex) {
            case 1 -> altarEnhancedHeal(player, data, level);
            case 2 -> altarSpiritWard(player, data, level);
            case 3 -> altarSoulMend(player, data, level);
            case 4 -> altarDeathSense(player, data, level);
            case 5 -> altarAuraOfProtection(player, data, level);
            case 6 -> altarCurseOfUnrest(player, data, level);
            case 7 -> altarResurrection(player, data, level);
            default -> player.sendSystemMessage(Component.literal("Unknown ability.").withColor(0xFF5555));
        }
    }

    // --- Tier 1: Enhanced Heal (80 SE, 5 hearts, 14 blocks) ---
    private void altarEnhancedHeal(ServerPlayer player, ClassData data, ServerLevel level) {
        if (!spendSE(player, data, 80)) return;
        var nearby = new java.util.ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(14)));
        nearby.removeIf(p -> !p.getUUID().equals(player.getUUID())
                && com.mlkymc.pvp.PvPTagManager.isPvPTagged(p.getUUID()));
        int ehCount = 0;
        for (var p : nearby) {
            p.heal(10.0f); // 5 hearts
            if (!p.getUUID().equals(player.getUUID())) {
                ehCount++;
                p.sendSystemMessage(Component.literal("Healed by " + player.getName().getString() + "!").withColor(0x55FF55));
            }
        }
        if (ehCount > 0) {
            player.displayClientMessage(Component.literal("Enhanced Heal! " + ehCount + " player(s), 5 hearts").withColor(0xAA55FF), true);
        } else {
            player.displayClientMessage(Component.literal("Enhanced Heal! (no nearby allies)").withColor(0xAA55FF), true);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.2f);
        classManager.addXp(player, ProfessionType.CLERIC, 10 + nearby.size() * 12, "enhanced heal");
    }

    // --- Tier 1: Spirit Ward (120 SE, reduce mob spawns 30 blocks, 3 min) ---
    private static final java.util.Map<java.util.UUID, Long> spiritWardExpiry = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, net.minecraft.core.BlockPos> spiritWardPos = new java.util.HashMap<>();

    public static boolean isSpiritWardActive(net.minecraft.core.BlockPos spawnPos) {
        long now = System.currentTimeMillis();
        for (var entry : spiritWardExpiry.entrySet()) {
            if (now > entry.getValue()) continue;
            var wardPos = spiritWardPos.get(entry.getKey());
            if (wardPos != null && wardPos.distSqr(spawnPos) <= 900) { // 30 block radius squared
                return true;
            }
        }
        return false;
    }

    private void altarSpiritWard(ServerPlayer player, ClassData data, ServerLevel level) {
        if (!spendSE(player, data, 120)) return;
        // Weaken existing hostile mobs in range
        var mobs = level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                player.getBoundingBox().inflate(30));
        for (var mob : mobs) {
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WEAKNESS, 3600, 9)); // 3 min severe weakness
        }
        // Set up spawn suppression aura for 3 minutes
        spiritWardExpiry.put(player.getUUID(), System.currentTimeMillis() + 180_000); // 3 min
        spiritWardPos.put(player.getUUID(), player.blockPosition());
        player.displayClientMessage(Component.literal(
                "Spirit Ward! Weakened " + mobs.size() + " mobs + spawn suppression for 3 min (30 blocks)")
                .withColor(0xAA55FF), true);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 0.6f);
        classManager.addXp(player, ProfessionType.CLERIC, 15, "spirit ward");
    }

    // --- Tier 2: Soul Mend (60 SE, HoT 10s, 2 hearts/2s on target) ---
    private void altarSoulMend(ServerPlayer player, ClassData data, ServerLevel level) {
        if (!spendSE(player, data, 60)) return;
        var nearby = new java.util.ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(8)));
        nearby.removeIf(p -> !p.getUUID().equals(player.getUUID())
                && com.mlkymc.pvp.PvPTagManager.isPvPTagged(p.getUUID()));
        for (var p : nearby) {
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.REGENERATION, 200, 1)); // Regen II, 10s
        }
        player.displayClientMessage(Component.literal("Soul Mend! Regen II for 10s on " + nearby.size() + " players").withColor(0xAA55FF), true);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
        classManager.addXp(player, ProfessionType.CLERIC, 8, "soul mend");
    }

    // --- Tier 2: Death Sense (50 SE, see through ghost's eyes 15s) ---
    private void altarDeathSense(ServerPlayer player, ClassData data, ServerLevel level) {
        if (!spendSE(player, data, 50)) return;
        // Find closest connected ghost
        var altarManager = com.mlkymc.MlkyMC.getSoulAltarManager();
        var altar = altarManager.getAltarByOwner(player.getUUID());
        if (altar == null || altar.connectedGhosts.isEmpty()) {
            data.addSoulEnergy(50); // refund
            player.sendSystemMessage(Component.literal("No connected ghosts!").withColor(0xFF5555));
            return;
        }
        // Give glowing + night vision for 15s to simulate awareness
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.NIGHT_VISION, 300, 0));
        // Send ghost positions to Cleric
        var ghostConn = altar.connectedGhosts.get(0);
        var ghostPlayer = level.getServer().getPlayerList().getPlayer(ghostConn.ghostUuid);
        if (ghostPlayer != null) {
            player.sendSystemMessage(Component.literal("Death Sense: " + ghostConn.name + " is at " +
                    ghostPlayer.blockPosition().toShortString()).withColor(0x55FFFF));
        }
        player.displayClientMessage(Component.literal("Death Sense active for 15s!").withColor(0xAA55FF), true);
        classManager.addXp(player, ProfessionType.CLERIC, 5, "death sense");
    }

    // --- Tier 3: Aura of Protection (200 SE, Resistance I + Absorption I, 20 blocks, 30s) ---
    private void altarAuraOfProtection(ServerPlayer player, ClassData data, ServerLevel level) {
        if (!spendSE(player, data, 200)) return;
        var nearby = new java.util.ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(20)));
        nearby.removeIf(p -> !p.getUUID().equals(player.getUUID())
                && com.mlkymc.pvp.PvPTagManager.isPvPTagged(p.getUUID()));
        for (var p : nearby) {
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.RESISTANCE, 600, 0)); // Resistance I, 30s
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.ABSORPTION, 600, 0)); // Absorption I, 30s
        }
        player.displayClientMessage(Component.literal("Aura of Protection! " + nearby.size() + " players buffed for 30s").withColor(0xAA55FF), true);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 0.8f);
        classManager.addXp(player, ProfessionType.CLERIC, 20, "aura of protection");
    }

    // --- Tier 3: Curse of Unrest (80 SE, 1-minute aura that tags nearby hostile mobs for double XP) ---
    private static final java.util.Map<java.util.UUID, Long> curseAuraExpiry = new java.util.HashMap<>();
    private static final java.util.Set<Integer> cursedMobIds = new java.util.HashSet<>();

    public static boolean isCursedMob(int entityId) {
        return cursedMobIds.contains(entityId);
    }

    public static void removeCursedMob(int entityId) {
        cursedMobIds.remove(entityId);
    }

    public static boolean isCurseAuraActive(java.util.UUID playerUuid) {
        Long expiry = curseAuraExpiry.get(playerUuid);
        return expiry != null && expiry > 0;
    }

    /** Called from tick handler to apply curse to nearby mobs and cleanup expired auras */
    public void tickCurseAura(ServerLevel level) {
        long gameTime = level.getGameTime();
        // Cleanup expired auras
        curseAuraExpiry.entrySet().removeIf(e -> gameTime > e.getValue());
        // Cleanup cursed mobs that no longer exist
        cursedMobIds.removeIf(id -> level.getEntity(id) == null || level.getEntity(id).isRemoved());

        if (gameTime % 20 != 0) return; // Only tag mobs every second

        for (var entry : curseAuraExpiry.entrySet()) {
            if (gameTime > entry.getValue()) continue;
            var player = level.getPlayerByUUID(entry.getKey());
            if (player == null) continue;
            // Tag all hostile mobs (including Phantoms) within 15 blocks
            var mobs = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    player.getBoundingBox().inflate(15),
                    e -> e instanceof net.minecraft.world.entity.monster.Monster
                            || e instanceof net.minecraft.world.entity.monster.Phantom);
            for (var mob : mobs) {
                if (!cursedMobIds.contains(mob.getId())) {
                    cursedMobIds.add(mob.getId());
                    // Visual: give glowing effect for the duration
                    int remaining = (int)(entry.getValue() - gameTime);
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.GLOWING, remaining, 0, false, false));
                }
            }

            // #2: Spawn glinting particles around the Cleric to show aura radius
            double px = player.getX();
            double py = player.getY() + 0.5;
            double pz = player.getZ();
            for (int p = 0; p < 8; p++) {
                double angle = (gameTime * 0.05 + p * Math.PI / 4) % (2 * Math.PI);
                double rx = px + Math.cos(angle) * 15;
                double rz = pz + Math.sin(angle) * 15;
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                        rx, py + 1, rz, 2, 0.3, 0.5, 0.3, 0.01);
            }
            // Also particles closer to player
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                    px, py + 1.5, pz, 3, 0.5, 0.3, 0.5, 0.02);
        }
    }

    public void cleanupCursedMobs(long gameTime) {
        // Legacy compatibility — real cleanup is in tickCurseAura
    }

    private void altarCurseOfUnrest(ServerPlayer player, ClassData data, ServerLevel level) {
        // #3: Don't allow recast while active
        Long expiry = curseAuraExpiry.get(player.getUUID());
        if (expiry != null && level.getGameTime() <= expiry) {
            int remaining = (int)((expiry - level.getGameTime()) / 20);
            player.sendSystemMessage(Component.literal(
                    "Curse of Unrest already active! " + remaining + "s remaining.").withColor(0xFF5555));
            return;
        }

        if (!spendSE(player, data, 80)) return;
        // Activate 1-minute aura
        curseAuraExpiry.put(player.getUUID(), level.getGameTime() + 1200); // 60 seconds
        player.displayClientMessage(Component.literal(
                "Curse of Unrest activated! Nearby mobs cursed 60s. Double XP!")
                .withColor(0xAA55FF), true);
        // #1: Level-up style sound
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.5f);
        classManager.addXp(player, ProfessionType.CLERIC, 10, "curse of unrest");
    }

    // --- Tier 4: Resurrection (full altar wipe, revive connected ghost at altar) ---
    private void altarResurrection(ServerPlayer player, ClassData data, ServerLevel level) {
        var altarManager = com.mlkymc.MlkyMC.getSoulAltarManager();
        var altar = altarManager.getAltarByOwner(player.getUUID());
        if (altar == null) {
            player.sendSystemMessage(Component.literal("You need a Soul Altar!").withColor(0xFF5555));
            return;
        }
        if (altar.connectedGhosts.isEmpty()) {
            player.sendSystemMessage(Component.literal("No ghosts connected to altar!").withColor(0xFF5555));
            return;
        }
        // Requires 100,000 SE in altar
        if (altar.storedSE < 100_000) {
            player.sendSystemMessage(Component.literal("Altar needs 100,000 SE to resurrect! (Current: " + altar.storedSE + ")").withColor(0xFF5555));
            return;
        }

        // Requires Totem of Resurrection
        boolean hasTotem = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(com.mlkymc.registry.ModItems.TOTEM_OF_RESURRECTION.get())) {
                player.getInventory().getItem(i).shrink(1);
                hasTotem = true;
                break;
            }
        }
        if (!hasTotem) {
            player.sendSystemMessage(Component.literal("Need a Totem of Resurrection!").withColor(0xFF5555));
            return;
        }

        // Wipe altar SE
        altar.storedSE = 0;
        altarManager.save();

        // Revive first connected ghost
        var ghostConn = altar.connectedGhosts.get(0);
        var ghostManager = com.mlkymc.MlkyMC.getGhostManager();
        var ghostPlayer = level.getServer().getPlayerList().getPlayer(ghostConn.ghostUuid);
        if (ghostPlayer != null && ghostManager.isGhost(ghostPlayer.getUUID())) {
            // Teleport ghost to altar
            ghostPlayer.teleportTo(level, altar.capstonePos.getX() + 0.5, altar.capstonePos.getY() + 1,
                    altar.capstonePos.getZ() + 0.5, java.util.Set.of(), ghostPlayer.getYRot(), ghostPlayer.getXRot(), false);
            ghostManager.revivePlayer(ghostPlayer);
            ghostPlayer.setHealth(ghostPlayer.getMaxHealth() * 0.5f);
            ghostPlayer.sendSystemMessage(Component.literal("You have been resurrected at the Soul Altar!").withColor(0x55FF55));
        }

        // Disconnect the ghost
        altarManager.disconnectGhost(altar, ghostConn.ghostUuid);

        player.sendSystemMessage(Component.literal("Altar Resurrection complete! " + ghostConn.name + " revived. Altar SE wiped.").withColor(0xAA55FF));
        level.playSound(null, altar.capstonePos, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 2.0f, 1.0f);
        classManager.addXp(player, ProfessionType.CLERIC, 100, "altar resurrection");
    }

    public java.util.Set<Integer> getCursedMobIds() { return cursedMobIds; }

    // =========================================================================
    // CLERIC SECONDARY: Resurrection
    // Activate while looking at a grave. Within 5min = free, after = needs Totem.
    // 10min cooldown (halved at Lv50 exclusive).
    // =========================================================================

    private void clericSecondary(ServerPlayer player, ClassData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // Dual-function: if looking at a grave → resurrect, otherwise → toggle Soul Energy mode
        var hitResult = player.pick(5.0, 0, false);
        boolean lookingAtGrave = false;
        BlockPos gravePos = null;
        com.mlkymc.grave.GraveData grave = null;

        if (hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit && graveManager != null) {
            gravePos = blockHit.getBlockPos();
            String dim = level.dimension().identifier().toString();
            grave = graveManager.getGrave(dim, gravePos);
            lookingAtGrave = grave != null;
        }

        if (!lookingAtGrave) {
            // Toggle Soul Energy Mode
            classManager.toggleSoulMode(player);
            return;
        }

        // --- Resurrection logic ---
        if (isOnCooldown(resurrectionCooldown, player)) {
            player.sendSystemMessage(Component.literal("Resurrection on cooldown! " +
                    getCooldownRemaining(resurrectionCooldown, player) + "s").withColor(0xFF5555));
            return;
        }

        BlockPos pos = gravePos;

        long currentTime = level.getGameTime();
        boolean withinWindow = grave.isWithinResurrectionWindow(currentTime);

        if (withinWindow) {
            // Free resurrection within 5min (no cost)
        } else {
            // After 5min: costs 1 Totem of Resurrection
            boolean hasTotem = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(ModItems.TOTEM_OF_RESURRECTION.get())) {
                    player.getInventory().getItem(i).shrink(1);
                    hasTotem = true;
                    break;
                }
            }
            if (!hasTotem) {
                player.sendSystemMessage(Component.literal("Need a Totem of Resurrection! (5min window passed)").withColor(0xFF5555));
                return;
            }
        }

        // Find the dead player
        ServerPlayer deadPlayer = level.getServer().getPlayerList().getPlayer(grave.ownerUUID);

        // Don't resurrect if they haven't pressed Respawn yet (still on death screen)
        if (deadPlayer != null && com.mlkymc.ghost.GhostListener.isPendingGhost(deadPlayer.getUUID())) {
            player.sendSystemMessage(Component.literal(
                    grave.ownerName + " hasn't respawned yet — wait for them to press Respawn.").withColor(0xFF5555));
            return;
        }

        // Dead player must be online to resurrect
        if (deadPlayer == null) {
            player.sendSystemMessage(Component.literal(
                    grave.ownerName + " is offline — can't resurrect.").withColor(0xFF5555));
            return;
        }

        // Don't resurrect if the player is already alive (not a ghost). Their grave
        // head may still exist in the world but they've already been revived via
        // Devoted Life, Soul Altar, or another Cleric. Don't waste the skill or
        // consume a totem on an already-alive player.
        var ghostCheck = com.mlkymc.MlkyMC.getGhostManager();
        if (ghostCheck != null && !ghostCheck.isGhost(deadPlayer.getUUID())) {
            player.sendSystemMessage(Component.literal(
                    grave.ownerName + " is already alive! They can reclaim their grave themselves.").withColor(0xFFAA00));
            return;
        }

        // Give items back
        graveManager.claimGrave(deadPlayer, grave, level);

        if (deadPlayer != null) {
            // Teleport revived player to the Cleric's location
            deadPlayer.teleportTo(level, player.getX(), player.getY(), player.getZ(),
                    java.util.Set.of(), player.getYRot(), player.getXRot(), false);

            // Revive from ghost state if they're a ghost
            var gm = com.mlkymc.MlkyMC.getGhostManager();
            if (gm != null && gm.isGhost(deadPlayer.getUUID())) {
                gm.revivePlayer(deadPlayer);
            }
            // Set health to half
            deadPlayer.setHealth(deadPlayer.getMaxHealth() * 0.5f);
            // 15s invisibility so the revived player doesn't get targeted by mobs immediately
            deadPlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.INVISIBILITY, 300, 0, false, true, true));
            deadPlayer.sendSystemMessage(Component.literal("You were resurrected by " +
                    player.getName().getString() + "! (15s invisibility)").withColor(0x55FF55));

            // Soul Fracture: applied ONLY on the free (<5min) resurrection. Totem-consumed
            // resurrections skip this — the totem cost is already the price for late revival.
            // Stacks are persistent and only cleared via the Cauldron Transmutation ritual
            // (64 Milky Stars per stack), not by time or sleep.
            if (withinWindow) {
                ClassData revivedData = classManager.getOrCreate(deadPlayer);
                int newStacks = revivedData.addSoulFractureStack();
                classManager.save();
                deadPlayer.displayClientMessage(Component.literal(
                        "Soul Fracture: " + newStacks + "/5").withColor(0x888888), true);
            }
        }

        player.sendSystemMessage(Component.literal("Resurrected " + grave.ownerName + "!" +
                (withinWindow ? " (Free - within 5min)" : " (Totem consumed)")).withColor(0x55FF55));
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.0f);

        // Cleric XP
        classManager.addXp(player, ProfessionType.CLERIC, 50, "grave resurrection");

        // Cleric exclusive: Milky Stars from resurrection
        if (data.getChosenClass() == ClassType.CLERIC) {
            int starAmount = 10 + level.random.nextInt(21); // 10-30
            var star = com.mlkymc.economy.MilkyStar.create(starAmount);
            if (!player.getInventory().add(star)) {
                player.spawnAtLocation(level, star);
            }
            player.sendSystemMessage(Component.literal(
                    "+" + starAmount + " Milky Stars for saving a soul!").withColor(0xFFD700));
        }

        // Cleric exclusive: 1/100 chance of Totem of Undying from free resurrection
        if (withinWindow && data.getChosenClass() == ClassType.CLERIC) {
            if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) == 0) {
                var totem = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
                if (!player.getInventory().add(totem)) {
                    player.spawnAtLocation(level, totem);
                }
                player.sendSystemMessage(Component.literal(
                        "A Totem of Undying materializes!").withColor(0xFF55FF));
            }
        }

        // 10 minute cooldown (halved at Lv50 exclusive)
        int clericLevel = data.getLevel(ProfessionType.CLERIC);
        boolean isCleric = data.getChosenClass() == ClassType.CLERIC;
        int cdTicks = (isCleric && clericLevel >= 50) ? 6000 : 12000;
        resurrectionCooldown.put(player.getUUID(), level.getGameTime() + cdTicks);
    }

    // =========================================================================
    // FARMHAND PRIMARY: Nature's Call (toggle)
    // Activates crop growth boost in radius. Growth scales with level.
    // Lv0: +50%, Lv10 excl: +100%, Lv20 excl: +150%, Lv30 excl: +200%,
    // Lv40 excl: +250%, Lv50 excl: +300% + radius 5->10
    // =========================================================================

    private final java.util.Set<UUID> naturesCallActive = new java.util.HashSet<>();

    public boolean isNaturesCallActive(UUID uuid) {
        return naturesCallActive.contains(uuid);
    }

    private void farmhandPrimary(ServerPlayer player, ClassData data) {
        UUID uuid = player.getUUID();
        if (naturesCallActive.contains(uuid)) {
            naturesCallActive.remove(uuid);
            player.displayClientMessage(Component.literal("Nature's Call: OFF").withColor(0xFF5555), true);
        } else {
            // Adaptive enchantment: no bonemeal cost (but half growth in tick handler)
            if (!hasAdaptiveHelmet(player) && !hasBonemeal(player)) {
                player.sendSystemMessage(Component.literal("Nature's Call requires Bone Meal!").withColor(0xFF5555));
                return;
            }
            naturesCallActive.add(uuid);
            player.displayClientMessage(Component.literal("Nature's Call: ON").withColor(0x55FF55), true);
            if (player.level() instanceof ServerLevel sl) {
                sl.playSound(null, player.blockPosition(),
                        SoundEvents.FLOWERING_AZALEA_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
            }
        }
    }

    /**
     * Tick Nature's Call for all active Farmhand players. Called from server tick.
     */
    public void tickNaturesCall(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 4 != 0) return; // Every 4 ticks

        for (var player : serverLevel.players()) {
            if (!(player instanceof ServerPlayer sp)) continue;
            if (!naturesCallActive.contains(sp.getUUID())) continue;

            ClassData data = classManager.getOrCreate(sp);
            if (data.getChosenClass() != ClassType.FARMHAND) {
                naturesCallActive.remove(sp.getUUID());
                continue;
            }

            // Consume 1 Bone Meal every second (20 ticks) — Adaptive skips cost
            boolean adaptiveNature = hasAdaptiveHelmet(sp);
            if (!adaptiveNature && serverLevel.getGameTime() % 20 == 0) {
                if (!consumeBonemeal(sp)) {
                    naturesCallActive.remove(sp.getUUID());
                    sp.displayClientMessage(Component.literal("Nature's Call: Out of Bone Meal!").withColor(0xFF5555), true);
                    continue;
                }
            }

            int farmLevel = data.getLevel(ProfessionType.FARMHAND);

            // Slow the player while Nature's Call is active (Slowness I)
            sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOWNESS, 10, 0, false, false, false));

            // Determine how many random ticks to apply per cycle based on level
            int growthTicks = 1; // +50% base
            if (farmLevel >= 50) growthTicks = 6;      // +300%
            else if (farmLevel >= 40) growthTicks = 5;  // +250%
            else if (farmLevel >= 30) growthTicks = 4;  // +200%
            else if (farmLevel >= 20) growthTicks = 3;  // +150%
            else if (farmLevel >= 10) growthTicks = 2;  // +100%

            // Adaptive enchantment: halve growth effect
            if (adaptiveNature) growthTicks = Math.max(1, growthTicks / 2);

            // Radius: 5 blocks, 10 at Lv50 exclusive
            int radius = (farmLevel >= 50) ? 10 : 5;

            net.minecraft.core.BlockPos center = sp.blockPosition();
            var random = serverLevel.getRandom();

            // Scan ALL blocks in range for growable crops and tick them
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        net.minecraft.core.BlockPos pos = center.offset(dx, dy, dz);
                        var state = serverLevel.getBlockState(pos);
                        if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock
                                || state.getBlock() instanceof net.minecraft.world.level.block.StemBlock
                                || state.getBlock() instanceof net.minecraft.world.level.block.SugarCaneBlock
                                || state.getBlock() instanceof net.minecraft.world.level.block.NetherWartBlock
                                || state.getBlock() instanceof net.minecraft.world.level.block.CocoaBlock
                                || state.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock
                                || state.getBlock() instanceof net.minecraft.world.level.block.BambooStalkBlock) {
                            for (int t = 0; t < growthTicks; t++) {
                                state.randomTick(serverLevel, pos, random);
                            }
                        }
                    }
                }
            }

            // Particles every 20 ticks
            if (serverLevel.getGameTime() % 20 == 0) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                        sp.getX(), sp.getY() + 0.5, sp.getZ(), 8, radius * 0.5, 0.5, radius * 0.5, 0);
            }
        }
    }

    // =========================================================================
    // FARMHAND SECONDARY: Whisperer
    // AoE charm hostile mobs (10s fight for you) + friendly mobs follow (1min)
    // 10 block radius. 1min cooldown.
    // =========================================================================

    private final Map<UUID, Long> whispererCooldown = new HashMap<>();

    private void farmhandSecondary(ServerPlayer player, ClassData data) {
        if (isOnCooldown(whispererCooldown, player)) {
            player.sendSystemMessage(Component.literal("Whisperer on cooldown! " +
                    getCooldownRemaining(whispererCooldown, player) + "s").withColor(0xFF5555));
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        double radius = 10.0;
        int affected = 0;

        // Hostile mobs: charm to fight other hostile mobs for you (10s)
        // Catches all Enemy types including modded mobs. Excludes bosses.
        String charmTag = "mlkymc_charmed_" + player.getUUID();
        for (var mob : serverLevel.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(radius),
                m -> m instanceof net.minecraft.world.entity.monster.Enemy && !isWhispererImmune(m))) {
            mob.addTag(charmTag);
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING, 200, 0, false, false, true));
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SPEED, 200, 0, false, false, true));

            // Immediately target nearest non-charmed hostile mob
            retargetCharmedMob(serverLevel, mob, charmTag);
            affected++;
        }

        // Neutral/passive mobs: follow for 1 minute
        // Catches all Mob types that aren't hostile and aren't bosses (animals, golems, dolphins, etc.)
        for (var mob : serverLevel.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(radius),
                m -> !(m instanceof net.minecraft.world.entity.monster.Enemy)
                        && !isWhispererImmune(m))) {
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SPEED, 1200, 0, false, false, true));
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING, 1200, 0, false, false, true));
            mob.addTag("mlkymc_following_" + player.getUUID());
            var ash = com.mlkymc.MlkyMC.getActiveSkillHandler();
            if (ash != null) {
                ash.trackFollowingAnimal(mob.getId(), player.getUUID());
            }
            affected++;
        }

        if (affected > 0) {
            player.displayClientMessage(Component.literal("Whisperer: Calmed " + affected + " creatures!").withColor(0x55FF55), true);
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 0.8f);
            whispererCooldown.put(player.getUUID(), player.level().getGameTime() + 1200);
        } else {
            player.sendSystemMessage(Component.literal("No creatures nearby.").withColor(0xAAAAAA));
        }
    }

    /**
     * Bosses and special mobs immune to Whisperer charm/follow.
     */
    private static boolean isWhispererImmune(net.minecraft.world.entity.Entity entity) {
        return entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || entity instanceof net.minecraft.world.entity.monster.warden.Warden
                || entity instanceof net.minecraft.world.entity.monster.ElderGuardian;
    }

    /**
     * Find the nearest non-charmed hostile mob and set it as the charmed mob's target.
     */
    private void retargetCharmedMob(ServerLevel level, net.minecraft.world.entity.Mob charmedMob, String charmTag) {
        double closest = 16.0;
        net.minecraft.world.entity.LivingEntity bestTarget = null;

        // Target nearest non-charmed hostile mob (any Enemy type)
        for (var other : level.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                charmedMob.getBoundingBox().inflate(16),
                m -> m != charmedMob && m instanceof net.minecraft.world.entity.monster.Enemy
                        && !m.getTags().contains(charmTag))) {
            double dist = charmedMob.distanceTo(other);
            if (dist < closest) {
                closest = dist;
                bestTarget = other;
            }
        }

        if (bestTarget != null) {
            charmedMob.setTarget(bestTarget);
            charmedMob.setAggressive(true);
        }
    }

    /**
     * Tick charmed mobs: retarget them to nearby non-charmed hostiles.
     * Also clean up expired charms. Called from server tick.
     */
    public void tickCharmedMobs(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 10 != 0) return; // Every 0.5s

        for (var player : serverLevel.players()) {
            if (!(player instanceof ServerPlayer sp)) continue;
            String charmTag = "mlkymc_charmed_" + sp.getUUID();

            for (var mob : serverLevel.getEntitiesOfClass(
                    net.minecraft.world.entity.Mob.class,
                    sp.getBoundingBox().inflate(20),
                    m -> m.getTags().contains(charmTag))) {

                // Check if charm expired (no glowing = expired)
                if (!mob.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) {
                    mob.removeTag(charmTag);
                    mob.setTarget(null);
                    continue;
                }

                // Retarget if current target is dead, gone, or is the player
                var target = mob.getTarget();
                if (target == null || !target.isAlive() || target instanceof ServerPlayer) {
                    retargetCharmedMob(serverLevel, mob, charmTag);
                }
            }
        }
    }

    /**
     * Prevent charmed mobs from targeting the Farmhand who charmed them.
     */
    @SubscribeEvent
    public void onMobTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Monster mob)) return;
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer targetPlayer)) return;

        // If this mob is charmed by this player, prevent targeting them
        String charmTag = "mlkymc_charmed_" + targetPlayer.getUUID();
        if (mob.getTags().contains(charmTag)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    // =========================================================================
    // MINECRAFTER PRIMARY: Jackhammer
    // 10s instant-mine + knockback III on hits. 1min cooldown.
    // Lv30 exclusive: 30s duration + bonus damage (+4 = 2 hearts).
    // =========================================================================

    private static final net.minecraft.resources.Identifier JACKHAMMER_SPEED_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:jackhammer_speed");
    private static final net.minecraft.resources.Identifier JACKHAMMER_DAMAGE_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:jackhammer_damage");

    private final Map<UUID, Long> jackhammerCooldown = new HashMap<>();
    private final Map<UUID, Long> jackhammerExpiryTick = new HashMap<>();

    public boolean isJackhammerActive(UUID uuid) {
        Long expiry = jackhammerExpiryTick.get(uuid);
        return expiry != null && expiry > 0;
    }

    private void minecrafterPrimary(ServerPlayer player, ClassData data) {
        if (isOnCooldown(jackhammerCooldown, player)) {
            player.sendSystemMessage(Component.literal("Jackhammer on cooldown! " +
                    getCooldownRemaining(jackhammerCooldown, player) + "s").withColor(0xFF5555));
            return;
        }

        int level = data.getLevel(ProfessionType.MINECRAFTER);
        boolean isExclusive30 = data.getChosenClass() == ClassType.MINECRAFTER && level >= 30;

        // Duration: 10s base, 30s at Lv30 exclusive
        int durationTicks = isExclusive30 ? 600 : 200;

        // Apply high block break speed via attribute (preserves arm animation + durability)
        var breakAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
        if (breakAttr != null) {
            breakAttr.removeModifier(JACKHAMMER_SPEED_ID);
            breakAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    JACKHAMMER_SPEED_ID, 3.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        }

        // Lv30 exclusive: +4 attack damage (+2 hearts)
        if (isExclusive30) {
            var dmgAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (dmgAttr != null) {
                dmgAttr.removeModifier(JACKHAMMER_DAMAGE_ID);
                dmgAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        JACKHAMMER_DAMAGE_ID, 4.0,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // Track expiry in game ticks
        jackhammerExpiryTick.put(player.getUUID(), player.level().getGameTime() + durationTicks);

        player.displayClientMessage(Component.literal("Jackhammer: " + (durationTicks / 20) + "s!").withColor(0x55FFFF), true);
        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.blockPosition(),
                    SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8f, 1.5f);
        }

        // 1 minute cooldown
        jackhammerCooldown.put(player.getUUID(), player.level().getGameTime() + 1200);
    }

    /**
     * Tick Jackhammer expiry: remove the speed boost when time runs out.
     * Called from tickSmithPassives (reuses the server tick loop).
     *
     * Delays expiry if the player is currently mid-break on a block: vanilla's
     * ServerPlayerGameMode.incrementDestroyProgress reads the block break speed fresh every
     * tick, so yanking the JACKHAMMER_SPEED_ID modifier mid-break rolls the progress backward
     * and visually resets the destroy. Waiting until the player is no longer destroying a
     * block avoids the desync.
     */
    public void tickJackhammer(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 20 != 0) return;

        var toExpire = new java.util.ArrayList<UUID>();
        for (var entry : jackhammerExpiryTick.entrySet()) {
            ServerPlayer p = serverLevel.getServer().getPlayerList().getPlayer(entry.getKey());
            if (p == null || p.level() != serverLevel) continue; // Only process in player's dimension
            if (p.level().getGameTime() >= entry.getValue()) {
                // Skip expiry if the player is currently destroying a block — avoids
                // mid-break progress desync. Will retry on the next 20-tick pass.
                if (isDestroyingBlock(p)) continue;
                toExpire.add(entry.getKey());
            }
        }

        for (UUID uuid : toExpire) {
            jackhammerExpiryTick.remove(uuid);
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                var breakAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
                if (breakAttr != null) {
                    breakAttr.removeModifier(JACKHAMMER_SPEED_ID);
                }
                var dmgAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (dmgAttr != null) {
                    dmgAttr.removeModifier(JACKHAMMER_DAMAGE_ID);
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Jackhammer expired.").withColor(0xAAAAAA));
            }
        }
    }

    // Reflection handle for ServerPlayerGameMode.isDestroyingBlock — used by jackhammer
    // expiry to avoid interrupting an in-progress break.
    private static java.lang.reflect.Field gameModeIsDestroyingField;
    private static boolean gameModeReflectionFailed;

    private static boolean isDestroyingBlock(ServerPlayer player) {
        if (gameModeReflectionFailed) return false;
        try {
            if (gameModeIsDestroyingField == null) {
                gameModeIsDestroyingField = net.minecraft.server.level.ServerPlayerGameMode.class
                        .getDeclaredField("isDestroyingBlock");
                gameModeIsDestroyingField.setAccessible(true);
            }
            return gameModeIsDestroyingField.getBoolean(player.gameMode);
        } catch (Exception e) {
            gameModeReflectionFailed = true;
            return false;
        }
    }

    /**
     * Jackhammer knockback III: when active, hits against mobs apply massive knockback.
     */
    @SubscribeEvent
    public void onJackhammerHit(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        var source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;
        if (!isJackhammerActive(player.getUUID())) return;

        var target = event.getEntity();
        if (target instanceof ServerPlayer) return; // Don't knockback players

        // Apply knockback III equivalent (large push away from player)
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0) {
            double strength = 1.5; // Knockback III equivalent
            target.push(dx / dist * strength, 0.4, dz / dist * strength);
            if (target instanceof net.minecraft.world.entity.Mob mob) {
                mob.hurtMarked = true;
            }
        }
    }

    // =========================================================================
    // MINECRAFTER SECONDARY: Ore Scan Toggle + Light
    // Scans for exposed ores (air-adjacent) in 20-block radius.
    // Highlights ores with glowing entity outlines visible through walls.
    // Adaptive enchantment: also reveals non-exposed ores within 5-block radius.
    // Consumes 1 coal per 80 seconds while active.
    // =========================================================================

    private final java.util.Set<UUID> oreScanActive = new java.util.HashSet<>();
    private final Map<UUID, Long> oreScanNextCoalTick = new HashMap<>();
    private final Map<UUID, Long> oreScanRemainingTicks = new HashMap<>(); // saved remainder when toggled off
    private final Map<UUID, java.util.List<net.minecraft.world.entity.Entity>> oreScanMarkers = new HashMap<>();
    private final Map<UUID, BlockPos> oreScanLastPos = new HashMap<>();

    public boolean isOreScanActive(UUID uuid) {
        return oreScanActive.contains(uuid);
    }

    private void minecrafterSecondary(ServerPlayer player, ClassData data) {
        UUID uuid = player.getUUID();
        if (oreScanActive.contains(uuid)) {
            // Save remaining ticks until next coal consumption
            Long nextCoal = oreScanNextCoalTick.get(uuid);
            if (nextCoal != null) {
                long remaining = nextCoal - player.level().getGameTime();
                if (remaining > 0) {
                    oreScanRemainingTicks.put(uuid, remaining);
                }
            }
            oreScanActive.remove(uuid);
            clearOreMarkers(uuid);
            player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
            player.displayClientMessage(Component.literal("Ore Scan: OFF").withColor(0xFF5555), true);
        } else {
            if (!hasCoal(player)) {
                player.sendSystemMessage(Component.literal("Need coal in inventory!").withColor(0xFF5555));
                return;
            }
            // Restore saved timer or consume a new coal
            Long saved = oreScanRemainingTicks.remove(uuid);
            if (saved != null && saved > 0) {
                // Resume with remaining time — no coal consumed
                oreScanNextCoalTick.put(uuid, player.level().getGameTime() + saved);
            } else {
                // First activation or timer expired — consume coal
                consumeCoal(player);
                oreScanNextCoalTick.put(uuid, player.level().getGameTime() + 1600); // 80s
            }
            oreScanActive.add(uuid);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false, false));
            player.displayClientMessage(Component.literal("Ore Scan: ON (uses 1 coal/80s)").withColor(0x55FF55), true);
            scanAndHighlightOres(player);
        }
    }

    /**
     * Tick ore scan: coal consumption + periodic rescan. Called from server tick.
     */
    public void tickOreScan(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 20 != 0) return; // Check every second

        var toRemove = new java.util.ArrayList<UUID>();
        for (UUID uuid : oreScanActive) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) { toRemove.add(uuid); continue; }
            if (player.level() != serverLevel) continue;

            // Coal consumption
            Long nextCoal = oreScanNextCoalTick.get(uuid);
            if (nextCoal != null && serverLevel.getGameTime() >= nextCoal) {
                if (consumeCoal(player)) {
                    oreScanNextCoalTick.put(uuid, serverLevel.getGameTime() + 1600);
                } else {
                    toRemove.add(uuid);
                    player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                    player.sendSystemMessage(Component.literal("Ore Scan: OFF (out of coal)").withColor(0xFF5555));
                    continue;
                }
            }

            // Rescan every 3 seconds or if player moved 3+ blocks
            BlockPos lastPos = oreScanLastPos.get(uuid);
            boolean shouldRescan = serverLevel.getGameTime() % 1 == 0 || lastPos == null;
            if (!shouldRescan && lastPos != null) {
                shouldRescan = lastPos.distSqr(player.blockPosition()) >= 9;
            }
            if (shouldRescan) {
                scanAndHighlightOres(player);
            }
        }
        for (UUID uuid : toRemove) {
            oreScanActive.remove(uuid);
            clearOreMarkers(uuid);
        }
    }

    /**
     * Scan for ores around the player and spawn glowing markers.
     * Base: exposed ores (air-adjacent) in 20-block radius.
     * Adaptive enchantment: also non-exposed ores within 5-block radius.
     */
    private void scanAndHighlightOres(ServerPlayer player) {
        oreScanLastPos.put(player.getUUID(), player.blockPosition().immutable());

        if (!(player.level() instanceof ServerLevel sl)) return;

        boolean adaptive = hasAdaptiveHelmet(player);
        // Adaptive: xray (no air check) in small radius. Normal: air-exposed in large radius.
        int radius = adaptive ? 3 : 10;
        BlockPos center = player.blockPosition();
        var orePositions = new java.util.ArrayList<BlockPos>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!isOreBlock(sl.getBlockState(pos))) continue;

                    if (adaptive || isExposedToAir(sl, pos)) {
                        orePositions.add(pos.immutable());
                    }
                }
            }
        }

        // Cap at closest 64
        if (orePositions.size() > 64) {
            orePositions.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(center)));
            orePositions = new java.util.ArrayList<>(orePositions.subList(0, 64));
        }

        // Build set of new ore positions
        var newPosSet = new java.util.HashSet<Long>();
        for (BlockPos pos : orePositions) {
            newPosSet.add(pos.asLong());
        }

        // Check existing markers — keep ones that are still valid, remove stale ones
        var existingMarkers = oreScanMarkers.getOrDefault(player.getUUID(), new java.util.ArrayList<>());
        var existingPosSet = new java.util.HashSet<Long>();
        var keptMarkers = new java.util.ArrayList<net.minecraft.world.entity.Entity>();

        for (var marker : existingMarkers) {
            long markerPos = BlockPos.containing(marker.getX(), marker.getY(), marker.getZ()).asLong();
            if (newPosSet.contains(markerPos) && marker.isAlive()) {
                // Still valid — keep it
                keptMarkers.add(marker);
                existingPosSet.add(markerPos);
            } else {
                // Stale — remove
                marker.discard();
            }
        }

        // Spawn new markers only for positions that don't already have one
        for (BlockPos pos : orePositions) {
            if (existingPosSet.contains(pos.asLong())) continue;

            var marker = new net.minecraft.world.entity.monster.Shulker(
                    net.minecraft.world.entity.EntityType.SHULKER, sl);
            marker.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            marker.setNoAi(true);
            marker.setInvisible(true);
            marker.setGlowingTag(true);
            marker.setSilent(true);
            marker.setInvulnerable(true);
            marker.setNoGravity(true);
            marker.noPhysics = true;
            marker.addTag("mlkymc_ore_marker");
            var oreColor = getOreColor(sl.getBlockState(pos).getBlock().getDescriptionId());
            if (oreColor != null) {
                try {
                    var colorField = net.minecraft.world.entity.monster.Shulker.class.getDeclaredField("DATA_COLOR_ID");
                    colorField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    var accessor = (net.minecraft.network.syncher.EntityDataAccessor<Byte>) colorField.get(null);
                    marker.getEntityData().set(accessor, (byte) oreColor.getId());
                } catch (Exception ignored) {}
            }
            sl.addFreshEntity(marker);
            keptMarkers.add(marker);
        }

        oreScanMarkers.put(player.getUUID(), keptMarkers);
    }

    private void clearOreMarkers(UUID uuid) {
        var markers = oreScanMarkers.remove(uuid);
        if (markers != null) {
            for (var marker : markers) {
                marker.discard();
            }
        }
    }

    private static net.minecraft.world.item.DyeColor getOreColor(String blockId) {
        if (blockId.contains("coal")) return net.minecraft.world.item.DyeColor.BLACK;
        if (blockId.contains("iron")) return net.minecraft.world.item.DyeColor.LIGHT_GRAY;
        if (blockId.contains("copper")) return net.minecraft.world.item.DyeColor.ORANGE;
        if (blockId.contains("gold")) return net.minecraft.world.item.DyeColor.YELLOW;
        if (blockId.contains("diamond")) return net.minecraft.world.item.DyeColor.LIGHT_BLUE;
        if (blockId.contains("emerald")) return net.minecraft.world.item.DyeColor.GREEN;
        if (blockId.contains("lapis")) return net.minecraft.world.item.DyeColor.BLUE;
        if (blockId.contains("redstone")) return net.minecraft.world.item.DyeColor.RED;
        if (blockId.contains("quartz")) return net.minecraft.world.item.DyeColor.WHITE;
        if (blockId.contains("ancient_debris")) return net.minecraft.world.item.DyeColor.BROWN;
        return null;
    }

    private static boolean isOreBlock(net.minecraft.world.level.block.state.BlockState state) {
        String id = state.getBlock().getDescriptionId();
        return id.contains("ore") || id.contains("ancient_debris");
    }

    private static boolean isExposedToAir(net.minecraft.world.level.Level level, BlockPos pos) {
        return level.getBlockState(pos.above()).isAir()
                || level.getBlockState(pos.below()).isAir()
                || level.getBlockState(pos.north()).isAir()
                || level.getBlockState(pos.south()).isAir()
                || level.getBlockState(pos.east()).isAir()
                || level.getBlockState(pos.west()).isAir();
    }

    // =========================================================================
    // SKILL STATUS SYNC — sends [MLKYMC_SKILLS:...] to all online players
    // =========================================================================

    public void syncSkillStatuses(net.minecraft.server.MinecraftServer server,
                                   ClassManager cm, PassiveSkillHandler psh,
                                   ActiveSkillHandler ash) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ClassData data = cm.getOrCreate(player);

            java.util.UUID uuid = player.getUUID();
            long gameTime = player.level().getGameTime();
            long now = System.currentTimeMillis();
            StringBuilder sb = new StringBuilder();

            // Grave timer — show regardless of class
            var graveManager = com.mlkymc.MlkyMC.getGraveManager();
            if (graveManager != null) {
                var grave = graveManager.getGraveByOwner(uuid);
                if (grave != null) {
                    long elapsed = gameTime - grave.deathTime;
                    long graveLifeTicks = 72000; // 60 minutes
                    long remaining = graveLifeTicks - elapsed;
                    if (remaining > 0) {
                        int remainingSec = (int)(remaining / 20);
                        if (sb.length() > 0) sb.append(",");
                        sb.append("grave=A").append(remainingSec)
                           .append(":").append(grave.pos.getX())
                           .append("/").append(grave.pos.getY())
                           .append("/").append(grave.pos.getZ())
                           .append(":").append(grave.dimension);
                    }
                }
            }

            // Soul Fracture — shown as a stack-based debuff icon whenever the player
            // has 1+ stacks, regardless of chosen class. Zero stacks = not emitted,
            // so the client's per-sync clear naturally hides it.
            int fractureStacks = data.getSoulFractureStacks();
            if (fractureStacks > 0) {
                if (sb.length() > 0) sb.append(",");
                sb.append("soulfracture=S").append(fractureStacks);
            }

            // Milky Curse — fixed badge + numeric stack label. Applies to any player
            // (class or no class) wearing gear with conflicting enchants.
            int curseStacks = com.mlkymc.classes.MilkyCurseHandler.getStacks(player);
            if (curseStacks > 0) {
                if (sb.length() > 0) sb.append(",");
                sb.append("milkycurse=N").append(curseStacks);
            }

            // Gear Score — letter-tier badge that swaps icon based on gear value.
            // Always shown if score > 0, regardless of class.
            int gearScore = com.mlkymc.world.ThreatScalingHandler.getGearScoreFor(player);
            if (gearScore > 0) {
                String letter;
                if (gearScore >= 250)      letter = "s";
                else if (gearScore >= 200) letter = "a";
                else if (gearScore >= 150) letter = "b";
                else if (gearScore >= 100) letter = "c";
                else if (gearScore >= 50)  letter = "d";
                else                       letter = "e";
                if (sb.length() > 0) sb.append(",");
                sb.append("gearscore=L").append(letter);
            }

            if (!data.hasChosenClass()) {
                // No class — send grave timer if present, or empty sync to clear old state
                player.sendSystemMessage(Component.literal("[MLKYMC_SKILLS:" + sb + "]").withColor(0x000000));
                continue;
            }

            // Devoted Life
            int devotedSec = psh.getDevotedLifeCooldownSec(player);
            if (devotedSec >= 0) {
                if (sb.length() > 0) sb.append(",");
                sb.append("devoted=").append(devotedSec == 0 ? "R" : "C" + devotedSec);
            }

            // Cleric skills
            if (data.getChosenClass() == ClassType.CLERIC) {
                // Resurrection cooldown
                Long resCd = resurrectionCooldown.get(uuid);
                boolean resOnCd = resCd != null && gameTime < resCd;
                if (sb.length() > 0) sb.append(",");
                if (resOnCd) {
                    sb.append("resurrection=C").append(Math.max(0, (int)((resCd - gameTime) / 20)));
                } else {
                    sb.append("resurrection=R");
                }

                // Spirit Ward (active duration, millis-based)
                Long swExpiry = spiritWardExpiry.get(uuid);
                boolean swActive = swExpiry != null && now < swExpiry;
                if (swActive) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append("spiritward=A").append(Math.max(0, (int)((swExpiry - now) / 1000)));
                }

                // Curse of Unrest (active duration, game tick-based)
                Long caExpiry = curseAuraExpiry.get(uuid);
                boolean caActive = caExpiry != null && gameTime < caExpiry;
                if (caActive) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append("curse=A").append(Math.max(0, (int)((caExpiry - gameTime) / 20)));
                }
            }

            // Lifesteal (Adventurer)
            if (data.getChosenClass() == ClassType.ADVENTURER) {
                Long lsExpiry = lifestealExpiry.get(uuid);
                boolean lsActive = lsExpiry != null && now < lsExpiry;
                Long lsCd = lifestealCooldown.get(uuid);
                boolean lsOnCd = lsCd != null && gameTime < lsCd;

                if (sb.length() > 0) sb.append(",");
                if (lsActive) {
                    sb.append("lifesteal=A").append(Math.max(0, (int)((lsExpiry - now) / 1000)));
                } else if (lsOnCd) {
                    sb.append("lifesteal=C").append(Math.max(0, (int)((lsCd - gameTime) / 20)));
                } else {
                    sb.append("lifesteal=R");
                }

                // Quick-Charge
                if (ash != null) {
                    long qcExpiry = ash.getQuickChargeCooldownExpiry(uuid);
                    if (sb.length() > 0) sb.append(",");
                    if (qcExpiry > gameTime) {
                        sb.append("quickcharge=C").append(Math.max(0, (int)((qcExpiry - gameTime) / 20)));
                    } else {
                        sb.append("quickcharge=R");
                    }
                }
            }

            // Jackhammer (MineCrafter)
            if (data.getChosenClass() == ClassType.MINECRAFTER) {
                Long jhExpiry = jackhammerExpiryTick.get(uuid);
                boolean jhActive = jhExpiry != null && gameTime < jhExpiry;
                Long jhCd = jackhammerCooldown.get(uuid);
                boolean jhOnCd = jhCd != null && gameTime < jhCd;

                if (sb.length() > 0) sb.append(",");
                if (jhActive) {
                    sb.append("jackhammer=A").append(Math.max(0, (int)((jhExpiry - gameTime) / 20)));
                } else if (jhOnCd) {
                    sb.append("jackhammer=C").append(Math.max(0, (int)((jhCd - gameTime) / 20)));
                } else {
                    sb.append("jackhammer=R");
                }

                // Ore Scan (toggle)
                if (sb.length() > 0) sb.append(",");
                sb.append("orescan=").append(oreScanActive.contains(uuid) ? "ON" : "X");
            }

            // Nature's Call (Farmhand toggle)
            if (data.getChosenClass() == ClassType.FARMHAND) {
                if (sb.length() > 0) sb.append(",");
                sb.append("nature=").append(naturesCallActive.contains(uuid) ? "ON" : "X");

                // Whisperer cooldown
                Long whCd = whispererCooldown.get(uuid);
                boolean whOnCd = whCd != null && gameTime < whCd;
                if (sb.length() > 0) sb.append(",");
                if (whOnCd) {
                    sb.append("whisperer=C").append(Math.max(0, (int)((whCd - gameTime) / 20)));
                } else {
                    sb.append("whisperer=R");
                }
            }

            // Tempered Mind (Smith)
            if (data.getChosenClass() == ClassType.SMITH) {
                Long tmExpiry = temperedMindExpiry.get(uuid);
                boolean tmActive = tmExpiry != null && System.currentTimeMillis() < tmExpiry;
                Long tmCd = temperedMindCooldown.get(uuid);
                boolean tmOnCd = tmCd != null && gameTime < tmCd;

                if (sb.length() > 0) sb.append(",");
                if (tmActive) {
                    sb.append("tempmind=A").append(Math.max(0, (int)((tmExpiry - now) / 1000)));
                } else if (tmOnCd) {
                    sb.append("tempmind=C").append(Math.max(0, (int)((tmCd - gameTime) / 20)));
                } else {
                    sb.append("tempmind=R");
                }

                // Tempered Body (Smith secondary)
                Long tbCd = temperedBodyCooldown.get(uuid);
                boolean tbOnCd = tbCd != null && gameTime < tbCd;
                if (sb.length() > 0) sb.append(",");
                if (tbOnCd) {
                    sb.append("tempbody=C").append(Math.max(0, (int)((tbCd - gameTime) / 20)));
                } else {
                    sb.append("tempbody=R");
                }

                // Forge Heat (Smith crouch+RMB furnace)
                if (ash != null) {
                    long fhExpiry = ash.getForgeHeatCooldownExpiry(uuid);
                    if (sb.length() > 0) sb.append(",");
                    if (fhExpiry > gameTime) {
                        sb.append("forgeheat=C").append(Math.max(0, (int)((fhExpiry - gameTime) / 20)));
                    } else {
                        sb.append("forgeheat=R");
                    }
                }
            }

            // Food buff (sandwich/enhanced food) — any class
            int foodBuffSec = IngredientBuffApplier.getRemainingSeconds(uuid, gameTime);
            if (foodBuffSec > 0) {
                if (sb.length() > 0) sb.append(",");
                sb.append("sandwich=A").append(foodBuffSec);
            }

            if (sb.length() > 0) {
                player.sendSystemMessage(Component.literal("[MLKYMC_SKILLS:" + sb + "]").withColor(0x000000));
            }

            // Cleric: sync active graves within 5-min resurrection window.
            // Only show graves whose owners are still ghosts — if the player was
            // revived (by Cleric, Soul Altar, or self-respawn), their grave should
            // disappear from the tracker even if the head block is still in the world.
            if (data.getChosenClass() == ClassType.CLERIC) {
                var gm2 = com.mlkymc.MlkyMC.getGraveManager();
                var ghostMgr = com.mlkymc.MlkyMC.getGhostManager();
                if (gm2 != null) {
                    StringBuilder graveSb = new StringBuilder();
                    for (var grave : gm2.getAllGraves()) {
                        if (!grave.isWithinResurrectionWindow(gameTime)) continue;
                        // Skip graves of players who are no longer ghosts (already revived)
                        if (ghostMgr != null && !ghostMgr.isGhost(grave.ownerUUID)) continue;
                        long remaining = 6000 - (gameTime - grave.deathTime);
                        int remainingSec = Math.max(0, (int) (remaining / 20));
                        if (graveSb.length() > 0) graveSb.append("|");
                        graveSb.append(grave.ownerName)
                                .append(",").append(grave.pos.getX())
                                .append(",").append(grave.pos.getY())
                                .append(",").append(grave.pos.getZ())
                                .append(",").append(remainingSec)
                                .append(",").append(grave.dimension);
                    }
                    player.sendSystemMessage(Component.literal(
                            "[MLKYMC_GRAVES:" + graveSb + "]").withColor(0x000000));
                }
            }
        }
    }

    private boolean isFuel(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
    }

    private boolean hasCoal(ServerPlayer player) {
        // Check direct inventory (coal first, then charcoal)
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isFuel(player.getInventory().getItem(i))) return true;
        }
        // Check shulker boxes and pouches
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (hasFuelInContainer(slot)) return true;
        }
        return false;
    }

    private boolean consumeCoal(ServerPlayer player) {
        // Priority 1: coal in direct inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.is(Items.COAL)) {
                stack.shrink(1);
                if (stack.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
                return true;
            }
        }
        // Priority 2: charcoal in direct inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.is(Items.CHARCOAL)) {
                stack.shrink(1);
                if (stack.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
                return true;
            }
        }
        // Priority 3: coal/charcoal inside shulker boxes or pouches
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (consumeFuelFromContainer(slot, player, i)) return true;
        }
        return false;
    }

    private boolean hasFuelInContainer(ItemStack container) {
        var contents = container.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (contents != null) {
            for (ItemStack inner : contents.nonEmptyItems()) {
                if (isFuel(inner)) return true;
            }
        }
        return false;
    }

    private boolean consumeFuelFromContainer(ItemStack container, ServerPlayer player, int containerSlot) {
        var contents = container.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (contents == null) return false;

        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (ItemStack inner : contents.nonEmptyItems()) {
            items.add(inner.copy());
        }

        // Try coal first, then charcoal
        for (var target : new net.minecraft.world.item.Item[]{Items.COAL, Items.CHARCOAL}) {
            for (int i = 0; i < items.size(); i++) {
                ItemStack inner = items.get(i);
                if (inner.is(target)) {
                    inner.shrink(1);
                    if (inner.isEmpty()) items.set(i, ItemStack.EMPTY);
                    container.set(net.minecraft.core.component.DataComponents.CONTAINER,
                            net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasBonemeal(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(Items.BONE_MEAL)) return true;
        }
        return false;
    }

    private boolean consumeBonemeal(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.is(Items.BONE_MEAL)) {
                stack.shrink(1);
                if (stack.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // SMITH PRIMARY: Tempered Mind
    // Stop all durability loss for equipped + hotbar items for 20s. 40s CD.
    // Lv50 exclusive: AoE 10 blocks — nearby players also gain the effect.
    // =========================================================================

    private final Map<UUID, Long> temperedMindCooldown = new HashMap<>();
    private static final Map<UUID, Long> temperedMindExpiry = new HashMap<>();

    public static boolean isTemperedMindActive(UUID uuid) {
        Long expiry = temperedMindExpiry.get(uuid);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    private void smithPrimary(ServerPlayer player, ClassData data) {
        if (isOnCooldown(temperedMindCooldown, player)) {
            player.sendSystemMessage(Component.literal("Tempered Mind on cooldown! " +
                    getCooldownRemaining(temperedMindCooldown, player) + "s").withColor(0xFF5555));
            return;
        }

        int level = data.getLevel(ProfessionType.SMITH);
        boolean isSmith = data.getChosenClass() == ClassType.SMITH;

        // 20 second duration
        long durationMs = 20_000;
        long now = System.currentTimeMillis();
        temperedMindExpiry.put(player.getUUID(), now + durationMs);

        player.displayClientMessage(Component.literal("Tempered Mind: No durability loss for 20s!").withColor(0xFFAA00), true);
        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.blockPosition(),
                    SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6f, 1.2f);
        }

        // Lv50 exclusive: AoE — nearby players also get the buff (skip PvP-tagged)
        if (isSmith && level >= 50 && player.level() instanceof ServerLevel sl2) {
            var nearby = sl2.getEntitiesOfClass(ServerPlayer.class,
                    player.getBoundingBox().inflate(10),
                    p -> !p.getUUID().equals(player.getUUID())
                            && !com.mlkymc.pvp.PvPTagManager.isPvPTagged(p.getUUID()));
            for (var ally : nearby) {
                temperedMindExpiry.put(ally.getUUID(), now + durationMs);
                ally.sendSystemMessage(Component.literal(
                        "Tempered Mind from " + player.getName().getString() + "!").withColor(0xFFAA00));
            }
        }

        // 40 second cooldown
        temperedMindCooldown.put(player.getUUID(), player.level().getGameTime() + 800);
    }

    // =========================================================================
    // SMITH SECONDARY: Tempered Body
    // Passive: fire resistance (half damage). Activate: extinguish fire. 10s CD.
    // Lv50 exclusive: AoE extinguish + fire resist to nearby players.
    // =========================================================================

    private final Map<UUID, Long> temperedBodyCooldown = new HashMap<>();

    private void smithSecondary(ServerPlayer player, ClassData data) {
        if (isOnCooldown(temperedBodyCooldown, player)) {
            player.sendSystemMessage(Component.literal("Tempered Body on cooldown! " +
                    getCooldownRemaining(temperedBodyCooldown, player) + "s").withColor(0xFF5555));
            return;
        }

        int level = data.getLevel(ProfessionType.SMITH);
        boolean isSmith = data.getChosenClass() == ClassType.SMITH;

        // Adaptive enchantment: ignite nearby mobs and players instead of extinguishing
        if (hasAdaptiveHelmet(player)) {
            if (player.level() instanceof ServerLevel sl) {
                var entities = sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        player.getBoundingBox().inflate(5),
                        e -> !e.getUUID().equals(player.getUUID()));
                int ignited = 0;
                for (var entity : entities) {
                    entity.igniteForSeconds(5);
                    ignited++;
                }
                sl.playSound(null, player.blockPosition(),
                        SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.8f);
                player.sendSystemMessage(Component.literal(
                        "Tempered Body: Ignited " + ignited + " entities!").withColor(0xFFAA00));
            }
        } else {
            // Normal: Extinguish self instantly
            player.extinguishFire();
            player.displayClientMessage(Component.literal("Tempered Body: Fire extinguished!").withColor(0xFFAA00), true);

            if (player.level() instanceof ServerLevel sl) {
                sl.playSound(null, player.blockPosition(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 1.0f);
            }

            // Lv50 exclusive: AoE extinguish for nearby players too (skip PvP-tagged)
            if (isSmith && level >= 50 && player.level() instanceof ServerLevel sl2) {
                var nearby = sl2.getEntitiesOfClass(ServerPlayer.class,
                        player.getBoundingBox().inflate(10),
                        p -> !p.getUUID().equals(player.getUUID())
                                && !com.mlkymc.pvp.PvPTagManager.isPvPTagged(p.getUUID()));
                for (var ally : nearby) {
                    ally.extinguishFire();
                    ally.sendSystemMessage(Component.literal(
                            "Tempered Body from " + player.getName().getString() + "!").withColor(0xFFAA00));
                }
            }
        }

        // Boost nearby furnace smelting speed by 100% for 10s (4 block radius).
        // We also call speedUpFurnace inline here so the cookingTotalTime shrink + timer
        // clamp happen immediately on activation, not on the next tickSmithPassives pass.
        // Otherwise a mid-smelt furnace could get stuck if the Smith walks away or the
        // smelt finishes awkwardly before the next 40-tick scheduler pass.
        if (player.level() instanceof ServerLevel sl3) {
            BlockPos center = player.blockPosition();
            int boosted = 0;
            // Match tickSmithSmeltingSpeed's extraTicks derivation for consistency
            double speedBonus = getSmithSmeltBonus(level);
            int extraTicks = (int) (speedBonus * 2);
            if (extraTicks <= 0 && speedBonus > 0) extraTicks = 1;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        BlockPos check = center.offset(dx, dy, dz);
                        var be = sl3.getBlockEntity(check);
                        if (be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace) {
                            ActiveSkillHandler.markForgeHeated(check.asLong(), 10000L); // 10 seconds
                            // Apply the speedup + clamp right now so no stuck-timer window exists
                            speedUpFurnace(furnace, extraTicks);
                            boosted++;
                        }
                    }
                }
            }
            if (boosted > 0) {
                player.sendSystemMessage(Component.literal(
                        "Tempered Body: " + boosted + " furnace(s) boosted for 10s!").withColor(0xFFAA00));
            }
        }

        // 10 second cooldown
        temperedBodyCooldown.put(player.getUUID(), player.level().getGameTime() + 200);
    }

    /**
     * Tick Smith passives: fire resistance, mob slowdown, pressure anvils, smelting speed.
     */
    public void tickSmithPassives(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 40 != 0) return; // Every 2 seconds

        for (var player : serverLevel.players()) {
            if (!(player instanceof ServerPlayer sp)) continue;
            ClassData data = classManager.getOrCreate(sp);
            if (data.getChosenClass() != ClassType.SMITH) continue;

            // Fire resistance is handled via onSmithFireDamage event (50% reduction, not immunity)

            int smithLevel = data.getLevel(ProfessionType.SMITH);

            // Lv40 exclusive: slow hostile mobs + pressure anvil aura
            if (smithLevel >= 40) {
                var mobs = serverLevel.getEntitiesOfClass(
                        net.minecraft.world.entity.monster.Monster.class,
                        sp.getBoundingBox().inflate(10));

                for (var mob : mobs) {
                    // Slowdown aura
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 1, false, false, false));

                    // Pressure aura: 5% chance per tick cycle to "crush" the mob
                    if (serverLevel.random.nextFloat() < 0.05f) {
                        crushMob(serverLevel, mob);
                    }
                }

                // Also affect PvP-tagged players (slow + crush)
                var pvpPlayers = serverLevel.getEntitiesOfClass(ServerPlayer.class,
                        sp.getBoundingBox().inflate(10),
                        p -> !p.getUUID().equals(sp.getUUID())
                                && com.mlkymc.pvp.PvPTagManager.isPvPTagged(p.getUUID()));
                for (var target : pvpPlayers) {
                    target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 1, false, false, false));
                    if (serverLevel.random.nextFloat() < 0.05f) {
                        crushPlayer(serverLevel, target);
                    }
                }
            }

            // Smelting speed boost: tick furnaces near Smith faster
            tickSmithSmeltingSpeed(serverLevel, sp, smithLevel);
        }

        // Smelting debuff: slow furnaces owned by non-Smith players (runs every tick cycle)
        tickSmeltingDebuff(serverLevel);

        // Restore crushed mob scale after squish duration
        tickCrushRestore(serverLevel);

        // Tick Curse of Unrest aura — tags nearby mobs and cleans up expired entries
        tickCurseAura(serverLevel);
    }

    /**
     * Initialize furnace reflection fields if they haven't been yet.
     */
    private static void ensureFurnaceFields() {
        if (furnaceLitField != null) return;
        try {
            furnaceLitField = net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.class
                    .getDeclaredField("litTimeRemaining");
            furnaceLitField.setAccessible(true);
            furnaceCookTimerField = net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.class
                    .getDeclaredField("cookingTimer");
            furnaceCookTimerField.setAccessible(true);
            furnaceCookTotalField = net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.class
                    .getDeclaredField("cookingTotalTime");
            furnaceCookTotalField.setAccessible(true);
        } catch (Exception ignored) {}
    }

    private static final String FURNACE_OWNER_TAG = "mlkymc_last_user";

    /**
     * Tag furnace with the UUID of whoever right-clicks it (opens it).
     * Persists in the block entity NBT — survives restarts.
     */
    @SubscribeEvent
    public void onFurnaceInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var be = player.level().getBlockEntity(event.getPos());
        if (be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) {
            be.getPersistentData().putString(FURNACE_OWNER_TAG, player.getUUID().toString());
        }
    }

    /**
     * Smelting debuff: scan loaded furnaces near online players and slow them
     * if the tagged owner has a Smith debuff.
     */
    private void tickSmeltingDebuff(ServerLevel level) {
        ensureFurnaceFields();
        if (furnaceLitField == null) return;

        var server = level.getServer();

        // Only scan furnaces in the chunk the player is in + adjacent chunks (3x3 = 9 chunks max per player)
        var processedChunks = new java.util.HashSet<Long>();
        for (var player : level.players()) {
            if (!(player instanceof ServerPlayer sp)) continue;
            BlockPos center = sp.blockPosition();
            int chunkMinX = (center.getX() - 16) >> 4;
            int chunkMaxX = (center.getX() + 16) >> 4;
            int chunkMinZ = (center.getZ() - 16) >> 4;
            int chunkMaxZ = (center.getZ() + 16) >> 4;

            for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                    long chunkKey = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                    if (!processedChunks.add(chunkKey)) continue; // skip already processed
                    if (!level.hasChunk(cx, cz)) continue;
                    var chunk = level.getChunk(cx, cz);
                    if (chunk == null) continue;
                    for (var be : chunk.getBlockEntities().values()) {
                        if (!(be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace)) continue;
                        // Skip smokers — debuff only applies to furnaces/blast furnaces (ore/stone smelting)
                        if (be instanceof net.minecraft.world.level.block.entity.SmokerBlockEntity) continue;

                        // Read the tagged owner UUID
                        if (!be.getPersistentData().contains(FURNACE_OWNER_TAG)) continue;
                        String uuidStr = be.getPersistentData().getStringOr(FURNACE_OWNER_TAG, "");
                        if (uuidStr.isEmpty()) continue;
                        UUID ownerUUID;
                        try { ownerUUID = UUID.fromString(uuidStr); }
                        catch (IllegalArgumentException e) { continue; }

                        // Look up owner's class data
                        ClassData data;
                        ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
                        if (owner != null) {
                            data = classManager.getOrCreate(owner);
                        } else {
                            data = classManager.getOrCreate(ownerUUID);
                        }

                        // Smiths get the boost (handled in tickSmithSmeltingSpeed), skip
                        if (data.getChosenClass() == ClassType.SMITH) continue;

                        double debuff = data.getDebuffPercent(ProfessionType.SMITH);
                        if (debuff <= 0) continue;

                        // Slow furnace by increasing total cook time
                        try {
                            int lit = furnaceLitField.getInt(furnace);
                            if (lit <= 0) continue;
                            int total = furnaceCookTotalField.getInt(furnace);
                            if (total <= 0) continue;

                            long posKey = furnace.getBlockPos().asLong();
                            originalCookTotals.putIfAbsent(posKey, total);
                            int originalTotal = originalCookTotals.get(posKey);

                            // debuff 0.5 = 2x slower, 0.4 = 1.67x slower, etc.
                            double slowMult = 1.0 + debuff;
                            int newTotal = (int) (originalTotal * slowMult);
                            if (newTotal != total) {
                                furnaceCookTotalField.setInt(furnace, newTotal);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    /**
     * Spawn an invisible falling anvil 2 blocks above a mob.
     * Deals damage on landing, then vanishes. Cannot hurt players.
     */
    private static final net.minecraft.resources.Identifier CRUSH_SCALE_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:pressure_crush");

    /**
     * "Crush" a mob: squish it visually (reduce scale 20%), play a fall-damage sound,
     * deal damage, then restore scale after 10 ticks.
     */
    // Track mobs being crushed so we can restore their scale
    private final Map<Integer, Long> crushedMobs = new HashMap<>();

    private void crushMob(ServerLevel level, net.minecraft.world.entity.Mob target) {
        // Deal 4 damage (2 hearts) as generic crush
        target.hurtServer(level, level.damageSources().generic(), 4.0f);

        // Play the fall damage sound (big fall)
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_BIG_FALL, SoundSource.HOSTILE, 0.8f, 0.8f);

        // Squish: reduce scale by 20%
        var scaleAttr = target.getAttribute(
                net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (scaleAttr != null && scaleAttr.getModifier(CRUSH_SCALE_ID) == null) {
            scaleAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    CRUSH_SCALE_ID, -0.2,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

            // Track for restoration after 10 ticks
            crushedMobs.put(target.getId(), level.getGameTime() + 10);
        }
    }

    private void crushPlayer(ServerLevel level, ServerPlayer target) {
        target.hurtServer(level, level.damageSources().generic(), 4.0f);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_BIG_FALL, SoundSource.HOSTILE, 0.8f, 0.8f);
        var scaleAttr = target.getAttribute(
                net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (scaleAttr != null && scaleAttr.getModifier(CRUSH_SCALE_ID) == null) {
            scaleAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    CRUSH_SCALE_ID, -0.2,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            crushedMobs.put(target.getId(), level.getGameTime() + 10);
        }
    }

    /**
     * Called from tickSmithPassives to restore crushed mob scale after the squish duration.
     */
    private void tickCrushRestore(ServerLevel level) {
        if (crushedMobs.isEmpty()) return;
        long now = level.getGameTime();
        var iterator = crushedMobs.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now >= entry.getValue()) {
                var entity = level.getEntity(entry.getKey());
                if (entity instanceof net.minecraft.world.entity.Mob mob && mob.isAlive()) {
                    var attr = mob.getAttribute(
                            net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
                    if (attr != null) {
                        attr.removeModifier(CRUSH_SCALE_ID);
                    }
                }
                iterator.remove();
            }
        }
    }

    /**
     * Speed up furnaces near a Smith player.
     * Exclusive bonus: +20/40/60/80/100% at Lv5/15/25/35/45
     */
    private void tickSmithSmeltingSpeed(ServerLevel level, ServerPlayer player, int smithLevel) {
        double speedBonus = getSmithSmeltBonus(smithLevel);
        if (speedBonus <= 0) return;

        int extraTicks = (int) (speedBonus * 2); // 0.2 -> 0, 0.4 -> 0, 0.6 -> 1, 0.8 -> 1, 1.0 -> 2
        if (extraTicks <= 0 && speedBonus > 0) extraTicks = 1; // Minimum 1 at any bonus level

        // Find furnace block entities within 16 blocks and speed them up via reflection.
        // ONLY boost furnaces owned by this Smith player — otherwise we fight with
        // tickSmeltingDebuff which is trying to slow down non-Smith-owned furnaces.
        // The two handlers writing different cookingTotalTime values every cycle causes
        // the timer to ping-pong and the smelt to never finish.
        BlockPos center = player.blockPosition();
        int chunkMinX = (center.getX() - 16) >> 4;
        int chunkMaxX = (center.getX() + 16) >> 4;
        int chunkMinZ = (center.getZ() - 16) >> 4;
        int chunkMaxZ = (center.getZ() + 16) >> 4;
        String playerUuid = player.getStringUUID();
        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                var chunk = level.getChunk(cx, cz);
                if (chunk == null) continue;
                for (var be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace)) continue;
                    BlockPos fPos = be.getBlockPos();
                    if (fPos.distSqr(center) > 16 * 16) continue;

                    // Only speed up furnaces this Smith owns (tagged via right-click)
                    String ownerTag = be.getPersistentData().getStringOr(FURNACE_OWNER_TAG, "");
                    if (!playerUuid.equals(ownerTag)) continue;

                    speedUpFurnace(furnace, extraTicks);
                }
            }
        }
    }

    private static java.lang.reflect.Field furnaceLitField;
    private static java.lang.reflect.Field furnaceCookTimerField;
    private static java.lang.reflect.Field furnaceCookTotalField;

    // Original recipe total time cache: blockPos.asLong() -> original cookingTotalTime
    private static final Map<Long, Integer> originalCookTotals = new java.util.concurrent.ConcurrentHashMap<>();
    // Last value we set on the furnace — detects vanilla recipe changes
    private static final Map<Long, Integer> lastSetTotals = new java.util.concurrent.ConcurrentHashMap<>();

    private static void speedUpFurnace(
            net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace, int extraTicks) {
        try {
            ensureFurnaceFields();
            if (furnaceLitField == null) return;
            int lit = furnaceLitField.getInt(furnace);
            if (lit <= 0) return;
            int total = furnaceCookTotalField.getInt(furnace);
            if (total <= 0) return;

            long posKey = furnace.getBlockPos().asLong();

            // Detect recipe change: if vanilla's current total doesn't match the value
            // we last set, vanilla wrote a new recipe total (different item being smelted).
            // Refresh the cached original so the speedup is based on the NEW recipe, not
            // whatever short/long recipe this furnace first saw.
            Integer lastSet = lastSetTotals.get(posKey);
            if (lastSet == null || total != lastSet) {
                originalCookTotals.put(posKey, total);
            }
            int originalTotal = originalCookTotals.get(posKey);

            // Calculate speed multiplier
            double speedMult = 1.0 + (extraTicks * 0.5); // extraTicks 1 = 1.5x, 2 = 2x, 3 = 2.5x
            if (ActiveSkillHandler.isForgeHeated(posKey)) {
                speedMult += 1.0; // Forge Heat adds another 1x (doubling)
            }

            // Reduce total time (faster smelting)
            int newTotal = Math.max(2, (int) (originalTotal / speedMult));
            if (newTotal != total) {
                furnaceCookTotalField.setInt(furnace, newTotal);
            }
            lastSetTotals.put(posKey, newTotal);

            // Clamp cookingTimer to stay within newTotal. Vanilla's AbstractFurnaceBlockEntity
            // uses `cookingTimer++; if (cookingTimer == cookingTotalTime)` — post-increment then
            // exact-equality check. Two cases must be handled:
            //   (a) timer > newTotal : already past the reduced total, recipe hangs.
            //   (b) timer == newTotal: next vanilla tick increments to newTotal+1, misses equality.
            // In both cases we must set timer to newTotal-1 so the very next vanilla tick
            // increments it to exactly newTotal and fires the completion branch.
            int timer = furnaceCookTimerField.getInt(furnace);
            if (timer >= newTotal) {
                furnaceCookTimerField.setInt(furnace, Math.max(0, newTotal - 1));
            }
        } catch (Exception ignored) {}
    }

    private static double getSmithSmeltBonus(int level) {
        if (level >= 45) return 1.0;
        if (level >= 35) return 0.8;
        if (level >= 25) return 0.6;
        if (level >= 15) return 0.4;
        if (level >= 5)  return 0.2;
        return 0.0;
    }

    /**
     * Smith Tempered Body passive: 50% reduced fire/lava damage (not immunity).
     * Applies to: fire, lava, on_fire, in_fire, blaze fireballs.
     */
    @SubscribeEvent
    public void onSmithFireDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.SMITH) return;

        var source = event.getSource();
        String msgId = source.getMsgId();
        // Check if damage is fire-related
        boolean isFireDamage = msgId.equals("inFire") || msgId.equals("onFire")
                || msgId.equals("lava") || msgId.equals("hotFloor")
                || msgId.equals("fireball") || msgId.equals("fireworks")
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);

        if (isFireDamage) {
            event.setNewDamage(event.getNewDamage() * 0.5f);
        }
    }
}
