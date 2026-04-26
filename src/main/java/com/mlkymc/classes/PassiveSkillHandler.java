package com.mlkymc.classes;

import com.mlkymc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles ALL passive skill effects for all 5 classes.
 * Passive skills unlock at levels 5, 10, 15, 20, 25, 30, 35, 40, 45, 50.
 * Non-exclusive passives apply to ALL players. Exclusive passives only apply
 * if the player chose that specific class.
 *
 * Ticks every second (not every tick) for performance-sensitive effects.
 */
public class PassiveSkillHandler {
    private final ClassManager classManager;

    // Wind-up damage tracking for Adventurer Lv30
    // Tracks distance moved since last hit. Max 10 blocks = +5 damage.
    // Resets after 1 second (20 ticks) of not moving.
    private final java.util.Map<java.util.UUID, Double> sprintDistance = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> lastMovedTick = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, double[]> lastPosition = new java.util.HashMap<>();
    private static final net.minecraft.resources.Identifier WINDUP_DAMAGE_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:windup_damage");

    // Attribute modifier IDs (unique per effect)
    private static final net.minecraft.resources.Identifier HEALTH_MODIFIER_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:adventurer_hearts");
    private static final net.minecraft.resources.Identifier STEP_MODIFIER_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:adventurer_step");
    private static final net.minecraft.resources.Identifier SPEED_MODIFIER_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:adventurer_speed");
    private static final net.minecraft.resources.Identifier JUMP_MODIFIER_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:adventurer_jump");
    private static final net.minecraft.resources.Identifier HASTE_MODIFIER_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:minecrafter_haste");
    private static final net.minecraft.resources.Identifier SMITH_ARMOR_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:smith_resistance");
    private static final net.minecraft.resources.Identifier REACH_MODIFIER_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:minecrafter_reach");
    private static final net.minecraft.resources.Identifier SMITH_OXYGEN_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:smith_oxygen");

    private static PassiveSkillHandler instance;
    public static PassiveSkillHandler getInstance() { return instance; }

    public PassiveSkillHandler(ClassManager classManager) {
        this.classManager = classManager;
        instance = this;
    }

    // =========================================================================
    // TICK-BASED EFFECTS (applied as potion effects or attribute modifiers)
    // Runs every 20 ticks (1 second) to avoid performance issues.
    // =========================================================================

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Track sprint distance every tick for Adventurer wind-up damage
        trackSprintDistance(player);

        if (player.tickCount % 20 != 0) return; // Heavy effects once per second

        ClassData data = classManager.getOrCreate(player);

        applyAdventurerTickEffects(player, data);
        applyMineCrafterTickEffects(player, data);
        applySmithTickEffects(player, data);
        tickClericHpRegen(player, data);
        applyFarmhandTickEffects(player, data);
    }

    private void applyAdventurerTickEffects(ServerPlayer player, ClassData data) {
        int level = data.getLevel(ProfessionType.ADVENTURER);
        boolean isAdventurer = data.getChosenClass() == ClassType.ADVENTURER;

        // --- Extra Hearts (Lv5+): +1 heart per 5 levels, max +10 hearts at Lv50 ---
        // Applies to EVERYONE
        int heartBrackets = level / 5; // 0 at lv0-4, 1 at lv5-9, ..., 10 at lv50
        double extraHealth = heartBrackets * 2.0; // +2 HP per bracket (+1 heart)

        var healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            var existing = healthAttr.getModifier(HEALTH_MODIFIER_ID);
            double currentBonus = existing != null ? existing.amount() : 0;
            if (currentBonus != extraHealth) {
                healthAttr.removeModifier(HEALTH_MODIFIER_ID);
                if (extraHealth > 0) {
                    healthAttr.addPermanentModifier(new AttributeModifier(
                            HEALTH_MODIFIER_ID, extraHealth, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        // --- Quick Step (Lv10): ADVENTURER EXCLUSIVE ---
        var stepAttr = player.getAttribute(Attributes.STEP_HEIGHT);
        if (stepAttr != null) {
            var existing = stepAttr.getModifier(STEP_MODIFIER_ID);
            double stepBonus = (isAdventurer && level >= 10) ? 0.6 : 0;
            double currentStep = existing != null ? existing.amount() : 0;
            if (currentStep != stepBonus) {
                stepAttr.removeModifier(STEP_MODIFIER_ID);
                if (stepBonus > 0) {
                    stepAttr.addPermanentModifier(new AttributeModifier(
                            STEP_MODIFIER_ID, stepBonus, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        // --- Jump 1.5 blocks (Lv20): ADVENTURER EXCLUSIVE ---
        var jumpAttr = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jumpAttr != null) {
            var existing = jumpAttr.getModifier(JUMP_MODIFIER_ID);
            double jumpBonus = (isAdventurer && level >= 20) ? 0.1 : 0;
            double currentBonus = existing != null ? existing.amount() : 0;
            if (currentBonus != jumpBonus) {
                jumpAttr.removeModifier(JUMP_MODIFIER_ID);
                if (jumpBonus > 0) {
                    jumpAttr.addPermanentModifier(new AttributeModifier(
                            JUMP_MODIFIER_ID, jumpBonus, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        // --- Speed Boost (Lv50): ADVENTURER EXCLUSIVE ---
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            var existing = speedAttr.getModifier(SPEED_MODIFIER_ID);
            double speedBonus = (isAdventurer && level >= 50) ? 0.03 : 0;
            double currentBonus = existing != null ? existing.amount() : 0;
            if (currentBonus != speedBonus) {
                speedAttr.removeModifier(SPEED_MODIFIER_ID);
                if (speedBonus > 0) {
                    speedAttr.addPermanentModifier(new AttributeModifier(
                            SPEED_MODIFIER_ID, speedBonus, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }
    }

    private void applyMineCrafterTickEffects(ServerPlayer player, ClassData data) {
        int level = data.getLevel(ProfessionType.MINECRAFTER);
        boolean isMinecrafter = data.getChosenClass() == ClassType.MINECRAFTER;

        // --- Haste (Lv10 everyone: +1, Lv20 excl: +2, Lv40 excl: +3) ---
        var breakSpeedAttr = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (breakSpeedAttr != null) {
            var existing = breakSpeedAttr.getModifier(HASTE_MODIFIER_ID);
            double hasteBonus = 0;
            if (level >= 10) {
                hasteBonus = 0.2; // Haste I equivalent (everyone)
                if (isMinecrafter) {
                    if (level >= 40) hasteBonus = 0.6; // +Haste III (excl Lv20 + Lv40)
                    else if (level >= 20) hasteBonus = 0.4; // +Haste II (excl Lv20)
                }
            }
            double currentBonus = existing != null ? existing.amount() : 0;
            if (currentBonus != hasteBonus) {
                breakSpeedAttr.removeModifier(HASTE_MODIFIER_ID);
                if (hasteBonus > 0) {
                    breakSpeedAttr.addPermanentModifier(new AttributeModifier(
                            HASTE_MODIFIER_ID, hasteBonus, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        // --- Reach: Lv10 excl +1, Lv40 everyone +1 (stacks) ---
        var reachAttr = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (reachAttr != null) {
            var existingReach = reachAttr.getModifier(REACH_MODIFIER_ID);
            double reachBonus = 0;
            if (level >= 40) reachBonus = 1.0; // Everyone at Lv40
            if (isMinecrafter && level >= 10) reachBonus += 1.0; // Exclusive at Lv10 (+1 more)
            double currentReach = existingReach != null ? existingReach.amount() : 0;
            if (currentReach != reachBonus) {
                reachAttr.removeModifier(REACH_MODIFIER_ID);
                if (reachBonus > 0) {
                    reachAttr.addPermanentModifier(new AttributeModifier(
                            REACH_MODIFIER_ID, reachBonus, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }
    }

    private void applySmithTickEffects(ServerPlayer player, ClassData data) {
        int level = data.getLevel(ProfessionType.SMITH);

        // --- Permanent Armor bonus (Lv10: +1, Lv30: +2, Lv50: +3) ---
        // Uses ARMOR attribute so it shows on the visible armor bar.
        // 1 armor point = half a chestplate icon, so +2 points = +1 full icon.
        var armorAttr = player.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            var existing = armorAttr.getModifier(SMITH_ARMOR_ID);
            double bonus = 0;
            if (level >= 50) bonus = 6.0;      // +3 armor (3 full icons)
            else if (level >= 30) bonus = 4.0;  // +2 armor (2 full icons)
            else if (level >= 10) bonus = 2.0;  // +1 armor (1 full icon)
            double currentBonus = existing != null ? existing.amount() : 0;
            if (currentBonus != bonus) {
                armorAttr.removeModifier(SMITH_ARMOR_ID);
                if (bonus > 0) {
                    armorAttr.addPermanentModifier(new AttributeModifier(
                            SMITH_ARMOR_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        // --- Underwater Breathing (Lv50): +50% air supply via OXYGEN_BONUS attribute ---
        var oxygenAttr = player.getAttribute(Attributes.OXYGEN_BONUS);
        if (oxygenAttr != null) {
            var existing = oxygenAttr.getModifier(SMITH_OXYGEN_ID);
            double bonus = level >= 50 ? 1.0 : 0; // value of 1.0 = 50% chance to skip air loss each tick
            double currentBonus = existing != null ? existing.amount() : 0;
            if (currentBonus != bonus) {
                oxygenAttr.removeModifier(SMITH_OXYGEN_ID);
                if (bonus > 0) {
                    oxygenAttr.addPermanentModifier(new AttributeModifier(
                            SMITH_OXYGEN_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }
    }

    /**
     * Farmhand special effect: fishing lure/luck +1 (built-in, always active).
     * Applied as Luck I effect that refreshes every second.
     */
    private static final net.minecraft.resources.Identifier FARMHAND_LUCK_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:farmhand_luck");

    private void applyFarmhandTickEffects(ServerPlayer player, ClassData data) {
        if (data.getChosenClass() != ClassType.FARMHAND) return;

        // Built-in Luck +1 via attribute (invisible, no buff icon)
        var luckAttr = player.getAttribute(Attributes.LUCK);
        if (luckAttr != null) {
            var existing = luckAttr.getModifier(FARMHAND_LUCK_ID);
            if (existing == null) {
                luckAttr.addPermanentModifier(new AttributeModifier(
                        FARMHAND_LUCK_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    // =========================================================================
    // ADVENTURER: Fall Damage Reduction (Lv40)
    // =========================================================================

    @SubscribeEvent
    public void onFallDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Adventurer Lv40: Reduced fall damage (50% reduction)
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
            int advLevel = classManager.getOrCreate(player).getLevel(ProfessionType.ADVENTURER);
            if (advLevel >= 40) {
                event.setNewDamage(event.getNewDamage() * 0.5f);
            }
        }
    }

    // =========================================================================
    // ADVENTURER: Speed tracking + Speed-based Damage Bonus (Lv30)
    // Tracks recent speed with 0.2s (4 tick) decay delay so the bonus
    // doesn't vanish the instant the player decelerates from hitting a mob.
    // =========================================================================

    /**
     * Track movement distance each tick. Accumulates while moving (any speed).
     * Resets after 1 second of not moving, or on hit.
     */
    private void trackSprintDistance(ServerPlayer player) {
        java.util.UUID uuid = player.getUUID();
        int advLevel = classManager.getOrCreate(player).getLevel(ProfessionType.ADVENTURER);
        if (advLevel < 30) {
            removeWindupModifier(player);
            return;
        }

        double currentDist = sprintDistance.getOrDefault(uuid, 0.0);

        // Track actual position change between ticks (getDeltaMovement is unreliable on ground)
        double[] prev = lastPosition.get(uuid);
        double px = player.getX(), pz = player.getZ();
        double moved = 0;
        if (prev != null) {
            double dx = px - prev[0];
            double dz = pz - prev[1];
            moved = Math.sqrt(dx * dx + dz * dz);
        }
        lastPosition.put(uuid, new double[]{px, pz});

        if (moved > 0.05) {
            // Player is moving — accumulate distance
            currentDist = Math.min(20.0, currentDist + moved);
            sprintDistance.put(uuid, currentDist);
            lastMovedTick.put(uuid, player.tickCount);

            // Update attack damage attribute modifier based on distance
            applyWindupModifier(player, currentDist);
        } else {
            // Not moving — check if 1 second has passed
            if (currentDist > 0) {
                Integer lastMoved = lastMovedTick.get(uuid);
                if (lastMoved != null && player.tickCount - lastMoved >= 20) {
                    // 1 second idle — reset
                    sprintDistance.put(uuid, 0.0);
                    lastMovedTick.remove(uuid);
                    removeWindupModifier(player);
                }
            }
        }
    }

    private void applyWindupModifier(ServerPlayer player, double distance) {
        var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (attr == null) return;

        double bonus = Math.min(5.0, distance * 0.25); // +0.25 per block, max +5 at 20 blocks
        var existing = attr.getModifier(WINDUP_DAMAGE_ID);
        double existingVal = existing != null ? existing.amount() : 0;

        if (Math.abs(existingVal - bonus) > 0.1) {
            attr.removeModifier(WINDUP_DAMAGE_ID);
            if (bonus > 0) {
                attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        WINDUP_DAMAGE_ID, bonus,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    private void removeWindupModifier(ServerPlayer player) {
        var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (attr != null) {
            attr.removeModifier(WINDUP_DAMAGE_ID);
        }
    }

    @SubscribeEvent
    public void onAttackDamage(LivingDamageEvent.Pre event) {
        var source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) return;

        int advLevel = classManager.getOrCreate(player).getLevel(ProfessionType.ADVENTURER);
        if (advLevel >= 30) {
            // Wind-up damage: reset after hit (attribute modifier already applied the bonus)
            java.util.UUID uuid = player.getUUID();
            double dist = sprintDistance.getOrDefault(uuid, 0.0);
            if (dist >= 1.0) {
                sprintDistance.put(uuid, 0.0);
                lastMovedTick.remove(uuid);
                removeWindupModifier(player);
            }
        }
    }

    // =========================================================================
    // FARMHAND: Auto-replant (Lv10)
    // =========================================================================

    @SubscribeEvent
    public void onCropHarvest(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        int farmLevel = classManager.getOrCreate(player).getLevel(ProfessionType.FARMHAND);
        if (farmLevel < 10) return;

        BlockPos pos = event.getPos();
        var level = player.level();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof CropBlock crop) {
            if (crop.isMaxAge(state)) {
                if (level instanceof ServerLevel serverLevel) {
                    Block.dropResources(state, serverLevel, pos, null, player, player.getMainHandItem());
                    level.setBlock(pos, crop.getStateForAge(0), 3);
                    player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                    event.setCanceled(true);
                }
            }
        }
        // Cocoa beans: max age is 2
        if (state.getBlock() instanceof net.minecraft.world.level.block.CocoaBlock) {
            int age = state.getValue(net.minecraft.world.level.block.CocoaBlock.AGE);
            if (age >= 2) {
                if (level instanceof ServerLevel serverLevel) {
                    Block.dropResources(state, serverLevel, pos, null, player, player.getMainHandItem());
                    level.setBlock(pos, state.setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 0), 3);
                    player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                    event.setCanceled(true);
                }
            }
        }
    }

    // =========================================================================
    // FARMHAND Lv10 exclusive: Composters have 20% chance to drop a Milky Star
    // Right-click handler for manual harvest + tick watcher for hoppers
    // =========================================================================

    /**
     * Manual composter harvest: right-click a full composter (level 8 = ready).
     */
    @SubscribeEvent
    public void onComposterRightClick(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        var state = serverLevel.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.ComposterBlock)) return;
        if (state.getValue(net.minecraft.world.level.block.ComposterBlock.LEVEL) != 8) return;

        // Check owner from BlockOwnerData, fallback to clicking player
        var ownerData = com.mlkymc.world.BlockOwnerData.get(serverLevel.getServer());
        java.util.UUID ownerUUID = ownerData.getOwnerUUID(event.getPos());
        ServerPlayer checkPlayer = player;
        if (ownerUUID != null) {
            var owner = serverLevel.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) checkPlayer = owner;
        }

        ClassData data = classManager.getOrCreate(checkPlayer);
        if (!data.hasExclusiveSkills(ProfessionType.FARMHAND)) return;
        int farmLevel = data.getLevel(ProfessionType.FARMHAND);
        if (farmLevel < 10) return;

        // Chance scales with Farmhand level: 20% at Lv10, 40% at Lv30, 60% at Lv50
        double chance = farmLevel >= 50 ? 0.60 : farmLevel >= 30 ? 0.40 : 0.20;
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            var star = new ItemStack(com.mlkymc.registry.ModItems.MILKY_STAR.get(), 1);
            serverLevel.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                    serverLevel, event.getPos().getX() + 0.5, event.getPos().getY() + 1.0,
                    event.getPos().getZ() + 0.5, star));
        }
    }

    // Track composter levels: posLong -> last known level (for hopper detection)
    private final java.util.Map<Long, Integer> trackedComposters = new java.util.concurrent.ConcurrentHashMap<>();
    // Track furnace output slot counts to detect hopper extraction
    private final java.util.Map<Long, Integer> trackedFurnaceOutputs = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Called from level tick. Scans owned composters near players and detects when they empty.
     */
    public void tickComposters(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 10 != 0) return; // Every 0.5 seconds

        var ownerData = com.mlkymc.world.BlockOwnerData.get(serverLevel.getServer());
        var loadedOwned = ownerData.getLoadedOwned(serverLevel);

        for (var entry : loadedOwned.entrySet()) {
            BlockPos pos = entry.getKey();
            var state = serverLevel.getBlockState(pos);
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.ComposterBlock)) continue;

            long posKey = pos.asLong();
            int currentLevel = state.getValue(net.minecraft.world.level.block.ComposterBlock.LEVEL);
            int prevLevel = trackedComposters.getOrDefault(posKey, -1);
            trackedComposters.put(posKey, currentLevel);

            // Detect: was at level 7 or 8 (full/ready), now lower (emptied)
            if ((prevLevel == 7 || prevLevel == 8) && currentLevel < prevLevel) {
                onComposterEmptied(serverLevel, pos, ownerData);
            }
        }
    }

    private void onComposterEmptied(ServerLevel level, BlockPos pos,
                                     com.mlkymc.world.BlockOwnerData ownerData) {
        java.util.UUID ownerUUID = ownerData.getOwnerUUID(pos);
        if (ownerUUID == null) return;

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
        if (owner == null) return;

        ClassData data = classManager.getOrCreate(owner);
        if (!data.hasExclusiveSkills(ProfessionType.FARMHAND)) return;
        if (data.getLevel(ProfessionType.FARMHAND) < 10) return;

        // Only vanilla hoppers beneath the composter can extract Milky Stars.
        // Any other extraction method (Tom's Storage, hopper minecart, mod pipes,
        // etc.) produces zero stars. Manual right-click harvesting still works
        // via onComposterRightClick at the normal 20% rate, unaffected by this.
        var belowBe = level.getBlockEntity(pos.below());
        if (!(belowBe instanceof net.minecraft.world.level.block.entity.HopperBlockEntity hopper)) return;

        // Hopper-extracted composters: 10% chance per emptying, rate limited to 100/hr/player.
        if (ThreadLocalRandom.current().nextDouble() >= 0.10) return;
        if (!com.mlkymc.util.HopperStarRateLimit.tryGrant(ownerUUID)) return;

        var star = new ItemStack(com.mlkymc.registry.ModItems.MILKY_STAR.get(), 1);

        // Try to insert into the hopper below; if full, spawn on top
        boolean inserted = false;
        for (int i = 0; i < hopper.getContainerSize(); i++) {
            var slot = hopper.getItem(i);
            if (slot.isEmpty()) {
                hopper.setItem(i, star.copy());
                inserted = true;
                break;
            } else if (ItemStack.isSameItemSameComponents(slot, star)
                    && slot.getCount() < slot.getMaxStackSize()) {
                slot.grow(1);
                inserted = true;
                break;
            }
        }
        if (!inserted) {
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                    level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, star));
        }
    }

    /**
     * Called from level tick. Scans owned furnaces near players and detects when output is extracted.
     * Awards Milky Stars to the Smith owner (Lv10+ exclusive).
     */
    public void tickFurnaces(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 10 != 0) return; // Every 0.5 seconds

        var ownerData = com.mlkymc.world.BlockOwnerData.get(serverLevel.getServer());
        var loadedOwned = ownerData.getLoadedOwned(serverLevel);

        for (var entry : loadedOwned.entrySet()) {
            BlockPos pos = entry.getKey();
            var be = serverLevel.getBlockEntity(pos);
            if (!(be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace)) continue;

            long posKey = pos.asLong();

            // Track the output slot (slot 2) count
            int currentOutput = furnace.getItem(2).getCount();
            int prevOutput = trackedFurnaceOutputs.getOrDefault(posKey, -1);
            trackedFurnaceOutputs.put(posKey, currentOutput);

            // Detect: output decreased (items were extracted by hopper or player)
            if (prevOutput > 0 && currentOutput < prevOutput) {
                int extracted = prevOutput - currentOutput;
                onFurnaceOutputExtracted(serverLevel, pos, ownerData, extracted);
            }
        }
    }

    private void onFurnaceOutputExtracted(ServerLevel level, BlockPos pos,
                                           com.mlkymc.world.BlockOwnerData ownerData, int count) {
        java.util.UUID ownerUUID = ownerData.getOwnerUUID(pos);
        if (ownerUUID == null) return;

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
        if (owner == null) return;

        ClassData data = classManager.getOrCreate(owner);
        if (!data.hasExclusiveSkills(ProfessionType.SMITH)) return;
        if (data.getLevel(ProfessionType.SMITH) < 10) return;

        // Only vanilla hoppers beneath the furnace grant Milky Stars via automation.
        // Any other extraction method (Tom's Storage, hopper minecart, mod pipes,
        // etc.) produces zero stars here. Manual player extraction is handled
        // separately by ClassExpHandler.onItemSmelted (a PlayerEvent that only
        // fires for player-driven extraction).
        boolean hopperBelow = level.getBlockEntity(pos.below())
                instanceof net.minecraft.world.level.block.entity.HopperBlockEntity;
        if (!hopperBelow) return;

        // Hopper-extracted: 10% per item, rate limited to 100/hr/player.
        int starsFound = 0;
        for (int i = 0; i < count; i++) {
            if (ThreadLocalRandom.current().nextDouble() < 0.10) {
                if (!com.mlkymc.util.HopperStarRateLimit.tryGrant(ownerUUID)) {
                    break; // Hit hourly cap
                }
                starsFound++;
            }
        }

        if (starsFound > 0) {
            var star = new ItemStack(com.mlkymc.registry.ModItems.MILKY_STAR.get(), starsFound);
            if (!owner.getInventory().add(star)) {
                owner.spawnAtLocation((ServerLevel) owner.level(), star);
            }
            owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Found " + starsFound + " Milky Star" + (starsFound > 1 ? "s" : "") + " while smelting!").withColor(0xFFD700));
        }
    }

    // =========================================================================
    // FARMHAND: Double shearing/seeds (Lv30)
    // =========================================================================

    @SubscribeEvent
    public void onFarmBlockDrop(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;

        int farmLevel = classManager.getOrCreate(player).getLevel(ProfessionType.FARMHAND);

        String blockId = event.getState().getBlock().getDescriptionId();

        // Lv30: Double shearing drops + grass drops extra seed varieties
        if (farmLevel >= 30) {
            // Double drops from crops/leaves
            boolean isFarmDrop = blockId.contains("wheat") || blockId.contains("carrot")
                    || blockId.contains("potato") || blockId.contains("beetroot")
                    || blockId.contains("leaves");
            if (isFarmDrop && ThreadLocalRandom.current().nextDouble() < 0.5) {
                var dropsCopy = new java.util.ArrayList<>(event.getDrops());
                for (var drop : dropsCopy) {
                    var dupe = drop.getItem().copy();
                    if (!dupe.isEmpty()) {
                        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                                player.level(), drop.getX(), drop.getY(), drop.getZ(), dupe));
                    }
                }
            }

            // (Honeycomb shear doubling is handled in onHoneycombSpawn — beehive shears
            // don't break the block so don't fire BlockDropsEvent.)

            // Grass blocks (short_grass, tall_grass): chance to drop various farm seeds
            if (blockId.contains("grass") && !blockId.contains("block")) {
                double x = event.getPos().getX() + 0.5;
                double y = event.getPos().getY() + 0.5;
                double z = event.getPos().getZ() + 0.5;

                // 30% chance per grass to drop a random farm seed
                if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                    var seedPool = new net.minecraft.world.item.Item[] {
                        net.minecraft.world.item.Items.BEETROOT_SEEDS,
                        net.minecraft.world.item.Items.MELON_SEEDS,
                        net.minecraft.world.item.Items.PUMPKIN_SEEDS,
                        net.minecraft.world.item.Items.CARROT,
                        net.minecraft.world.item.Items.POTATO,
                    };
                    var seed = seedPool[ThreadLocalRandom.current().nextInt(seedPool.length)];
                    event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                            player.level(), x, y, z, new ItemStack(seed, 1)));
                }
            }
        }

        // MineCrafter exclusive: Milky Stars from mining
        // Lv10: 2% ore, 1% stone/logs
        // Lv30: 6% ore, 3% stone/logs
        // Lv50: 12% ore, 6% stone/logs
        int mcLevel = classManager.getOrCreate(player).getLevel(ProfessionType.MINECRAFTER);
        boolean isMC = classManager.getOrCreate(player).getChosenClass() == ClassType.MINECRAFTER;
        if (isMC && mcLevel >= 10) {
            boolean isOre = blockId.contains("ore") || blockId.contains("ancient_debris");
            boolean isStoneOrLog = blockId.contains("stone") || blockId.contains("deepslate")
                    || blockId.contains("log") || blockId.contains("wood");

            double chance = 0;
            if (isOre) {
                chance = mcLevel >= 50 ? 0.12 : mcLevel >= 30 ? 0.06 : 0.02;
            } else if (isStoneOrLog) {
                chance = mcLevel >= 50 ? 0.06 : mcLevel >= 30 ? 0.03 : 0.01;
            }

            if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance) {
                ItemStack star = new ItemStack(ModItems.MILKY_STAR.get(), 1);
                if (player.level() instanceof ServerLevel sl) {
                    event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                            sl, event.getPos().getX() + 0.5, event.getPos().getY() + 0.5,
                            event.getPos().getZ() + 0.5, star));
                }
            }
        }

        // Lv20+: XP orbs from mining natural stone (everyone)
        // Lv20: 5% (small), Lv30: 15% (medium), Lv50: 30% (good)
        if (mcLevel >= 20) {
            boolean isStone = blockId.contains("stone") || blockId.contains("deepslate");
            if (isStone) {
                // Check not player-placed
                boolean placed = false;
                if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    var tracker = com.mlkymc.world.PlacedOreTracker.get(sl.getServer());
                    String dim = sl.dimension().identifier().toString();
                    placed = tracker.isPlayerPlaced(dim, event.getPos());
                }
                if (!placed) {
                    double chance = mcLevel >= 50 ? 0.30 : mcLevel >= 30 ? 0.15 : 0.05;
                    if (ThreadLocalRandom.current().nextDouble() < chance) {
                        int xpAmount = event.getDroppedExperience() + ThreadLocalRandom.current().nextInt(1, 4);
                        event.setDroppedExperience(xpAmount);
                    }
                }
            }
        }
    }

    // (Ingredient return on craft removed)

    // =========================================================================
    // FARMHAND Lv30: Double beehive/bee nest shear drops
    // Honeycomb extraction via shears doesn't break the block, so BlockDropsEvent
    // never fires. We hook the honeycomb ItemEntity spawn instead and duplicate it
    // 50% of the time if a Farmhand Lv30+ is standing nearby a bee block.
    // =========================================================================

    @SubscribeEvent
    public void onHoneycombSpawn(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity itemEntity)) return;
        // Prevent infinite recursion — our own duplicates are tagged
        if (itemEntity.getTags().contains("mlkymc_farmhand_shear_dup")) return;
        if (!itemEntity.getItem().is(net.minecraft.world.item.Items.HONEYCOMB)) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        // Require an adjacent beehive or bee nest — beehive shears spawn honeycombs
        // right at the beehive's position, so a 1-block radius check is enough and
        // avoids false positives from player-dropped honeycombs elsewhere in the world.
        BlockPos pos = itemEntity.blockPosition();
        boolean nearBee = false;
        outer:
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    var state = sl.getBlockState(pos.offset(dx, dy, dz));
                    if (state.is(net.minecraft.world.level.block.Blocks.BEEHIVE)
                            || state.is(net.minecraft.world.level.block.Blocks.BEE_NEST)) {
                        nearBee = true;
                        break outer;
                    }
                }
            }
        }
        if (!nearBee) return;

        // Nearest player within shear reach is the presumed shearer
        var nearestPlayer = sl.getNearestPlayer(
                itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), 6.0, false);
        if (!(nearestPlayer instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getLevel(ProfessionType.FARMHAND) < 30) return;

        // 50% chance — match the existing crop/leaves double-shear chance
        if (ThreadLocalRandom.current().nextDouble() >= 0.5) return;

        var dupe = new net.minecraft.world.entity.item.ItemEntity(
                sl, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(),
                itemEntity.getItem().copy());
        dupe.addTag("mlkymc_farmhand_shear_dup");
        sl.addFreshEntity(dupe);
    }

    // =========================================================================
    // SMITH: Fuel saving (Lv10), Anvil Too Expensive cap (Lv40)
    // These are handled elsewhere (furnace events, anvil events).
    // Smith Lv20 Exclusive: Unbreakable Aura is tick-based but affects OTHER
    // players near the Smith — complex, placeholder for now.
    // =========================================================================

    // =========================================================================
    // CLERIC: HP regen (Lv10 everyone), Devoted Life, Potion sharing
    // =========================================================================

    // Devoted Life cooldown per player (game time when cooldown expires)
    private final java.util.Map<java.util.UUID, Long> devotedLifeCooldown = new java.util.HashMap<>();

    /** Get devoted life cooldown remaining in seconds, 0 = ready, -1 = doesn't have skill */
    public int getDevotedLifeCooldownSec(ServerPlayer player) {
        ClassData data = classManager.getOrCreate(player);
        boolean isCleric = data.getChosenClass() == ClassType.CLERIC;
        int clericLevel = data.getLevel(ProfessionType.CLERIC);
        // Devoted Life is now Cleric-class exclusive — non-Clerics never have the skill.
        boolean hasSkill = isCleric && clericLevel >= 10;
        if (!hasSkill) return -1;

        Long expiry = devotedLifeCooldown.get(player.getUUID());
        if (expiry == null || player.level().getGameTime() >= expiry) return 0;
        return (int) ((expiry - player.level().getGameTime()) / 20);
    }

    /**
     * Cleric Lv10 (everyone): Faster HP regen when food bar is full.
     * Called from the main onPlayerTick handler.
     */
    public void tickClericHpRegen(ServerPlayer player, ClassData data) {
        int clericLevel = data.getLevel(ProfessionType.CLERIC);

        // Devoted Life sync is now handled by SkillStatusHud via PowerHandler.syncSkillStatuses()

        if (clericLevel < 10) return;

        // Only when food is full (20) and health isn't max
        if (player.getFoodData().getFoodLevel() >= 20 && player.getHealth() < player.getMaxHealth()) {
            // Heal 0.5 HP per second (vanilla natural regen is ~0.5 HP per 4s at full food)
            player.heal(0.5f);
        }
    }

    /**
     * Cleric Lv20 (everyone): +10% food saturation when eating.
     */
    @SubscribeEvent
    public void onFoodEatenSaturation(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getItem().has(net.minecraft.core.component.DataComponents.FOOD)) return;

        int clericLevel = classManager.getOrCreate(player).getLevel(ProfessionType.CLERIC);
        if (clericLevel < 20) return;

        // Add 10% bonus saturation
        var foodData = player.getFoodData();
        float currentSat = foodData.getSaturationLevel();
        float bonus = currentSat * 0.10f;
        if (bonus > 0) {
            foodData.setSaturation(currentSat + bonus);
        }
    }

    /**
     * Send Devoted Life HUD hide message. Called on class reset, class change, and login.
     */
    public void sendDevotedLifeNone(ServerPlayer player) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[MLKYMC_DEVOTED:NONE]").withColor(0x000000));
    }

    /**
     * Try to activate Devoted Life for a player.
     * Called from GhostListener BEFORE ghost conversion.
     * Returns true if the player was saved (death should be cancelled).
     */
    public boolean tryDevotedLife(ServerPlayer player) {
        ClassData data = classManager.getOrCreate(player);
        int clericLevel = data.getLevel(ProfessionType.CLERIC);
        boolean isCleric = data.getChosenClass() == ClassType.CLERIC;

        // Devoted Life is now Cleric-class exclusive. Non-Clerics with Cleric profession
        // Lv30+ no longer get a watered-down version — the ability only activates when the
        // player has actually chosen Cleric as their class.
        if (!isCleric) return false;

        // Determine Devoted Life tier by Cleric profession level
        float revivePercent = 0;
        int cooldownMinutes = 0;

        if (clericLevel >= 50) { revivePercent = 0.8f; cooldownMinutes = 20; }
        else if (clericLevel >= 40) { revivePercent = 0.6f; cooldownMinutes = 30; }
        else if (clericLevel >= 20) { revivePercent = 0.4f; cooldownMinutes = 45; }
        else if (clericLevel >= 10) { revivePercent = 0.2f; cooldownMinutes = 60; }

        if (revivePercent <= 0) return false;

        // Check cooldown
        Long expiry = devotedLifeCooldown.get(player.getUUID());
        if (expiry != null && player.level().getGameTime() < expiry) {
            long remainingTicks = expiry - player.level().getGameTime();
            int remainingMin = (int) (remainingTicks / 20 / 60);
            int remainingSec = (int) (remainingTicks / 20 % 60);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Devoted Life on cooldown! " + remainingMin + "m " + remainingSec + "s remaining")
                    .withColor(0xFF5555));
            return false;
        }

        // Revive the player
        player.setHealth(player.getMaxHealth() * revivePercent);
        player.removeAllEffects();
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Devoted Life! Revived with " + (int)(revivePercent * 100) + "% HP")
                .withColor(0x55FF55));

        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.TOTEM_USE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.0f);
        }

        // Set cooldown
        long cooldownTicks = (long) cooldownMinutes * 60 * 20;
        devotedLifeCooldown.put(player.getUUID(), player.level().getGameTime() + cooldownTicks);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Devoted Life cooldown: " + cooldownMinutes + " minutes")
                .withColor(0xFFAA00));
        return true;
    }

    /**
     * Cleric Lv30 exclusive: Potion sharing.
     * When a Cleric drinks a potion, nearby allies get a weaker (1 tier lower) version.
     */
    // Guard against recursive potion sharing
    private boolean sharingPotion = false;

    @SubscribeEvent
    public void onPotionDrink(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) {
        if (sharingPotion) return; // Prevent recursive sharing
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.CLERIC) return;
        if (data.getLevel(ProfessionType.CLERIC) < 30) return;

        var effect = event.getEffectInstance();
        if (effect == null) return;

        // Only share beneficial effects (not poison, wither, etc.)
        if (!effect.getEffect().value().isBeneficial()) return;

        // Don't share effects from our own tick handlers (short duration = auto-applied)
        if (effect.getDuration() <= 100) return; // Skip effects under 5 seconds

        // Create weaker version: 1 tier lower amplifier, half duration
        int weakAmp = Math.max(0, effect.getAmplifier() - 1);
        int weakDur = effect.getDuration() / 2;
        if (weakDur < 40) return;

        double radius = 8.0;
        var nearby = player.level().getEntitiesOfClass(ServerPlayer.class,
                player.getBoundingBox().inflate(radius),
                p -> !p.getUUID().equals(player.getUUID())
                        && !com.mlkymc.pvp.PvPTagManager.isPvPTagged(p.getUUID()));

        if (!nearby.isEmpty()) {
            sharingPotion = true;
            try {
                for (var ally : nearby) {
                    ally.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            effect.getEffect(), weakDur, weakAmp, false, true, true));
                }
            } finally {
                sharingPotion = false;
            }
        }
    }

    // =========================================================================
    // CLERIC SPECIAL EFFECTS:
    // - Enchanting costs reduced (fewer levels required on table)
    // - Chance to not consume XP levels after enchanting (scaling with Cleric level)
    // - Lv40 (everyone): 25% chance to refund lapis
    // - Lv50 exclusive: enchant can exceed max by +1
    // =========================================================================

    /**
     * Cleric special effect: Enchanting table shows reduced level costs.
     * Cleric class gets ~25% reduced enchanting level requirements.
     */
    @SubscribeEvent
    public void onEnchantLevelSet(net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent event) {
        // Find the nearest player to the enchanting table
        var level = event.getLevel();
        if (level.isClientSide()) return;
        var pos = event.getPos();

        ServerPlayer nearest = null;
        double minDist = 10;
        for (var player : ((net.minecraft.server.level.ServerLevel) level).players()) {
            double d = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (d < minDist) { minDist = d; nearest = player; }
        }
        if (nearest == null) return;

        ClassData data = classManager.getOrCreate(nearest);
        if (data.getChosenClass() == ClassType.CLERIC) {
            // Reduce enchanting level cost by ~25%
            int reduced = Math.max(1, (int) (event.getEnchantLevel() * 0.75));
            event.setEnchantLevel(reduced);

            // Lv50 (everyone): higher chance of better enchants — boost offered level by +2
            if (data.getLevel(ProfessionType.CLERIC) >= 50) {
                event.setEnchantLevel(event.getEnchantLevel() + 2);
            }
        }
    }

    /**
     * After enchanting: chance to refund XP levels + lapis saving + Lv50 enchant boost.
     */
    @SubscribeEvent
    public void onEnchant(net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        int clericLevel = data.getLevel(ProfessionType.CLERIC);
        boolean isCleric = data.getChosenClass() == ClassType.CLERIC;

        // Cleric special effect: chance to not consume XP levels (scaling with level)
        // 5% base + 1% per Cleric level = 5% at Lv0, 55% at Lv50
        if (isCleric) {
            double saveChance = 0.05 + clericLevel * 0.01;
            if (ThreadLocalRandom.current().nextDouble() < saveChance) {
                // Refund the levels spent (approximate: give back 1-3 levels)
                int refund = Math.max(1, event.getEnchantments().size());
                player.giveExperienceLevels(refund);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Cleric blessing: XP levels preserved!").withColor(0x55FFFF));
            }
        }

        // Lv40 (everyone): 25% chance to refund lapis
        if (clericLevel >= 40) {
            if (ThreadLocalRandom.current().nextDouble() < 0.25) {
                player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI, 3));
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Lapis preserved!").withColor(0x5555FF));
            }
        }

        // Lv50 exclusive: enchanting can exceed normal max by +1 level
        if (isCleric && clericLevel >= 50) {
            var enchantedItem = event.getEnchantedItem();
            for (var instance : event.getEnchantments()) {
                int currentLevel = enchantedItem.getEnchantmentLevel(instance.enchantment());
                int maxLevel = instance.enchantment().value().getMaxLevel();
                if (currentLevel == maxLevel) {
                    enchantedItem.enchant(instance.enchantment(), currentLevel + 1);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Cleric Mastery: Enchantment boosted to Lv" + (currentLevel + 1) + "!")
                            .withColor(0xAA55FF));
                }
            }
        }
    }

    // (Old Cleric potion amplifier/duration boost removed — replaced by Concoction system)

    // =========================================================================
    // SMITH: Anvil cost reduction (50% as special effect handled via AnvilUpdate)
    // Lv20: +10% bonus durability on anvil repairs
    // Lv40: Too Expensive cap raised from 40 to 55
    // Exclusive Lv30: Anvil never breaks (handled via AnvilCraft.Post)
    // =========================================================================

    @SubscribeEvent
    public void onAnvilUpdate(net.neoforged.neoforge.event.AnvilUpdateEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        // Block Adaptive enchantment on non-head armor
        var right = event.getRight();
        if (right.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
            var stored = right.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
            if (stored != null) {
                var enchReg = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                var adaptiveHolder = enchReg.get(PowerHandler.ADAPTIVE_ENCHANT).orElse(null);
                if (adaptiveHolder != null && stored.getLevel(adaptiveHolder) > 0) {
                    var left = event.getLeft();
                    // Check if the target item is head armor
                    var equipSlot = player.getEquipmentSlotForItem(left);
                    if (equipSlot != net.minecraft.world.entity.EquipmentSlot.HEAD) {
                        event.setOutput(net.minecraft.world.item.ItemStack.EMPTY);
                        return;
                    }
                }
            }
        }

        ClassData data = classManager.getOrCreate(player);
        int smithLevel = data.getLevel(ProfessionType.SMITH);
        boolean isSmith = data.getChosenClass() == ClassType.SMITH;

        // Lv40: Allow higher XP costs (raise Too Expensive cap)
        if (smithLevel >= 40) {
            if (event.getXpCost() > 40 && event.getXpCost() <= 55) {
                // Allow it
            }
        }

        // Smith special effect: 50% reduced anvil repair costs
        if (isSmith) {
            event.setXpCost(Math.max(1, event.getXpCost() / 2));
        }

        // Lv20: Repairs give +10% bonus durability (reduce damage by 10% of max)
        if (smithLevel >= 20 && !event.getOutput().isEmpty()) {
            ItemStack output = event.getOutput();
            if (output.isDamageableItem()) {
                int maxDurability = output.getMaxDamage();
                int bonus = Math.max(1, (int) (maxDurability * 0.1)); // 10% of max durability
                int newDamage = Math.max(0, output.getDamageValue() - bonus);
                output.setDamageValue(newDamage);
            }
        }

        // Lv30 Cleric exclusive: Allow combining conflicting enchantments at 3x cost
        ClassData clericData = classManager.getOrCreate(player);
        boolean isCleric = clericData.getChosenClass() == ClassType.CLERIC;
        int clericLevel = clericData.getLevel(ProfessionType.CLERIC);
        if (isCleric && clericLevel >= 30) {
            tryConflictingEnchantCombine(event);
        }

        // Preserve over-max enchantment levels from the left input
        // (prevents Cleric's +1 enchant bonus from being capped by vanilla anvil logic)
        preserveOverMaxEnchants(event);
    }

    /**
     * If the left input has any enchantments above their normal max level,
     * ensure the output keeps those levels instead of capping them.
     */
    private void preserveOverMaxEnchants(net.neoforged.neoforge.event.AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack output = event.getOutput();
        if (left.isEmpty() || output.isEmpty()) return;

        var leftEnchants = left.getTagEnchantments();
        var outputEnchants = output.getTagEnchantments();
        if (leftEnchants.isEmpty()) return;

        boolean changed = false;
        var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(outputEnchants);

        for (var entry : leftEnchants.entrySet()) {
            var enchant = entry.getKey();
            int leftLevel = entry.getIntValue();
            int maxLevel = enchant.value().getMaxLevel();

            // Only fix over-max enchantments
            if (leftLevel > maxLevel) {
                int outputLevel = outputEnchants.getLevel(enchant);
                if (outputLevel < leftLevel) {
                    // Output was capped — restore the over-max level
                    mutable.set(enchant, leftLevel);
                    changed = true;
                }
            }
        }

        if (changed) {
            output.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, mutable.toImmutable());
        }
    }

    /**
     * Cleric Lv30 exclusive: Combine enchanted books with conflicting enchants.
     * e.g. Smite + Sharpness. Costs 3x normal XP.
     */
    private void tryConflictingEnchantCombine(net.neoforged.neoforge.event.AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        // Only when combining enchanted book onto an item
        if (!right.getItem().toString().contains("enchanted_book")) return;
        if (left.isEmpty()) return;

        // Check if vanilla rejected this (output is empty) due to conflicting enchants
        if (!event.getOutput().isEmpty()) return;

        // Get enchantments from both
        var leftEnchants = left.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        var rightBookTag = right.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        if (rightBookTag == null || rightBookTag.isEmpty()) return;

        // Check if any right enchants conflict with left enchants
        boolean hasConflict = false;
        for (var entry : rightBookTag.entrySet()) {
            var rightEnch = entry.getKey();
            for (var leftEntry : leftEnchants.entrySet()) {
                if (!net.minecraft.world.item.enchantment.Enchantment.areCompatible(leftEntry.getKey(), rightEnch)) {
                    hasConflict = true;
                    break;
                }
            }
            if (hasConflict) break;
        }

        if (!hasConflict) return;

        // Build output: copy left item, add all enchants from right book
        ItemStack output = left.copy();
        for (var entry : rightBookTag.entrySet()) {
            var ench = entry.getKey();
            int rightLevel = entry.getIntValue();
            int leftLevel = output.getEnchantmentLevel(ench);
            int newLevel = Math.max(leftLevel, rightLevel);
            // If same level, bump by 1 (standard anvil behavior)
            if (leftLevel == rightLevel && leftLevel > 0) {
                newLevel = Math.min(leftLevel + 1, ench.value().getMaxLevel());
            }
            output.enchant(ench, newLevel);
        }

        // Calculate cost: base 10 + 3x per conflicting enchant level
        int cost = 10;
        for (var entry : rightBookTag.entrySet()) {
            cost += entry.getIntValue() * 3;
        }

        event.setOutput(output);
        event.setXpCost(cost);
        event.setMaterialCost(1);
    }

    // =========================================================================
    // SMITH: Fuel saving (Lv10 - 25% chance to not consume fuel)
    // Done by increasing burn time by 33% (equivalent to ~25% fuel saving)
    // =========================================================================

    @SubscribeEvent
    public void onFuelBurn(net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent event) {
        // Lv10: 25% fuel saving — increase burn time by 33% if any Smith Lv10+ is online
        if (event.getBurnTime() <= 0) return;

        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (var player : server.getPlayerList().getPlayers()) {
            int smithLevel = classManager.getOrCreate(player).getLevel(ProfessionType.SMITH);
            if (smithLevel >= 10) {
                event.setBurnTime((int) (event.getBurnTime() * 1.33));
                return;
            }
        }
    }

    /**
     * Smith Lv30 exclusive: Anvil never breaks.
     * After using an anvil, if it would degrade/break, restore the anvil block.
     */
    @SubscribeEvent
    public void onAnvilUsed(net.neoforged.neoforge.event.entity.player.AnvilCraftEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.SMITH) return;
        if (data.getLevel(ProfessionType.SMITH) < 30) return;

        // The anvil may have degraded or broken after this craft.
        // Restore it to a normal anvil at the menu's access position.
        var menu = event.getMenu();
        if (menu instanceof net.minecraft.world.inventory.AnvilMenu) {
            // Access the anvil position via ContainerLevelAccess
            // The anvil block may have been damaged or removed — restore it
            player.level().getServer().execute(() -> {
                // Find the anvil block near the player (within 5 blocks)
                var pos = player.blockPosition();
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dz = -5; dz <= 5; dz++) {
                            var checkPos = pos.offset(dx, dy, dz);
                            var state = player.level().getBlockState(checkPos);
                            String blockName = state.getBlock().getDescriptionId();
                            // If it's a chipped or damaged anvil, restore to normal
                            if (blockName.contains("chipped_anvil") || blockName.contains("damaged_anvil")) {
                                player.level().setBlock(checkPos,
                                        net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState()
                                                .setValue(net.minecraft.world.level.block.AnvilBlock.FACING,
                                                        state.getValue(net.minecraft.world.level.block.AnvilBlock.FACING)),
                                        3);
                                return;
                            }
                            // If the anvil broke (air where anvil was), place new one
                            // This is harder to detect, skip for now — the restore on damage covers most cases
                        }
                    }
                }
            });
        }
    }

    // =========================================================================
    // FARMHAND: Animal growth speed (Lv20 - 25% faster)
    // Breeding cooldown (Lv20 - 25% lower)
    // Only affects animals that a Farmhand Lv20+ player bred (tagged)
    // =========================================================================

    private static final String FARMHAND_BRED_TAG = "mlkymc_farmhand_bred";

    /**
     * Tag baby animals when a Farmhand breeds them.
     * Farmhand special effect: twin breeding chance (scaling with level).
     */
    @SubscribeEvent
    public void onAnimalBred(net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent event) {
        if (event.getCausedByPlayer() == null) return;
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        int farmLevel = data.getLevel(ProfessionType.FARMHAND);

        // Lv20+ tagging for growth/cooldown bonuses
        if (farmLevel >= 20) {
            var child = event.getChild();
            if (child != null) {
                child.addTag(FARMHAND_BRED_TAG);
            }
            if (event.getParentA() != null) event.getParentA().addTag(FARMHAND_BRED_TAG);
            if (event.getParentB() != null) event.getParentB().addTag(FARMHAND_BRED_TAG);
        }

        // Farmhand special effect: twin breeding chance
        // 5% base + 0.5% per level = 5% at Lv0, 30% at Lv50
        if (data.getChosenClass() == ClassType.FARMHAND && event.getChild() != null) {
            double twinChance = 0.05 + farmLevel * 0.005;
            if (player.level().random.nextDouble() < twinChance) {
                // Spawn a second baby at the PARENT's location. BabyEntitySpawnEvent fires
                // BEFORE vanilla calls snapTo() on the child, so event.getChild().getX/Y/Z()
                // returns (0,0,0). We use parentA's position which is always valid.
                var child = event.getChild();
                var parent = event.getParentA();
                if (child != null && parent != null && player.level() instanceof ServerLevel sl) {
                    var twin = child.getType().create(sl, net.minecraft.world.entity.EntitySpawnReason.BREEDING);
                    if (twin != null) {
                        twin.snapTo(parent.getX(), parent.getY(), parent.getZ(), 0f, 0f);
                        if (twin instanceof net.minecraft.world.entity.animal.Animal babyTwin) {
                            babyTwin.setBaby(true);
                        }
                        sl.addFreshEntity(twin);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "Twins!").withColor(0x55FF55));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onBabyAnimalTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 100 != 0) return; // Every 5 seconds

        // Only process tagged animals near online players (ones bred by a Farmhand)
        for (ServerPlayer player : serverLevel.players()) {
            for (net.minecraft.world.entity.animal.Animal animal : serverLevel.getEntitiesOfClass(
                    net.minecraft.world.entity.animal.Animal.class,
                    player.getBoundingBox().inflate(32),
                    a -> a.getTags().contains(FARMHAND_BRED_TAG))) {

                // Speed up baby growth by 25%
                if (animal.isBaby()) {
                    int age = animal.getAge();
                    if (age < 0) {
                        animal.setAge(age + (int)(Math.abs(age) * 0.05));
                    }
                }
                // Reduce breeding cooldown by 25%
                if (animal.getAge() > 0) {
                    animal.setAge((int)(animal.getAge() * 0.95));
                }
            }
        }
    }

    // =========================================================================
    // FARMHAND SPECIAL EFFECT: Passive crop tick acceleration
    // Always-on for Farmhand class. Stronger at Lv30 and Lv50.
    // Stacks with Nature's Call and other effects.
    // =========================================================================

    @SubscribeEvent
    public void onCropTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 40 != 20) return; // Every 2 seconds, offset from animal tick

        for (ServerPlayer player : serverLevel.players()) {
            ClassData data = classManager.getOrCreate(player);
            if (data.getChosenClass() != ClassType.FARMHAND) continue;

            int farmLevel = data.getLevel(ProfessionType.FARMHAND);
            // Always-on for Farmhand: base 1 tick, Lv50: 4x tick speed
            int extraTicks = 1;
            if (farmLevel >= 50) extraTicks = 4;
            else if (farmLevel >= 30) extraTicks = 2;

            // Random-sample crops in 10 block radius (efficient vs full scan)
            int radius = 10;
            BlockPos center = player.blockPosition();
            var random = serverLevel.getRandom();
            int samples = 8 * extraTicks; // More samples at higher levels

            for (int i = 0; i < samples; i++) {
                int dx = random.nextInt(radius * 2 + 1) - radius;
                int dy = random.nextInt(5) - 2;
                int dz = random.nextInt(radius * 2 + 1) - radius;
                BlockPos pos = center.offset(dx, dy, dz);
                if (!serverLevel.isLoaded(pos)) continue;
                BlockState state = serverLevel.getBlockState(pos);
                if (state.getBlock() instanceof CropBlock
                        || state.getBlock() instanceof net.minecraft.world.level.block.StemBlock
                        || state.getBlock() instanceof net.minecraft.world.level.block.SugarCaneBlock
                        || state.getBlock() instanceof net.minecraft.world.level.block.NetherWartBlock
                        || state.getBlock() instanceof net.minecraft.world.level.block.CocoaBlock) {
                    state.randomTick(serverLevel, pos, random);
                }
            }
        }
    }

    // =========================================================================
    /**
     * Nature's Call fishing boost: when active, fish bite 50% faster.
     * Called from server tick.
     */
    public void tickNaturesCallFishing(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;
        var powerHandler = com.mlkymc.MlkyMC.getPowerHandler();
        if (powerHandler == null) return;

        for (ServerPlayer player : sl.players()) {
            if (player.fishing == null) continue;
            if (!powerHandler.isNaturesCallActive(player.getUUID())) continue;

            var hook = player.fishing;
            // Only speed up timeUntilLured (wait before fish approaches).
            // Don't touch timeUntilHooked — that controls the bite animation
            // and messing with it causes phantom fish trail visuals.
            if (hook.timeUntilLured > 0) {
                hook.timeUntilLured = Math.max(0, hook.timeUntilLured - 1);
            }
        }
    }

    // FARMHAND: Fishing bonuses
    // Lv20 exclusive: +1 items per catch (2 total)
    // Lv40: rare items + exclusive: +1 more (3 total)
    // =========================================================================

    @SubscribeEvent
    public void onFishCaught(net.neoforged.neoforge.event.entity.player.ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        int farmLevel = data.getLevel(ProfessionType.FARMHAND);
        boolean isExclusive = data.hasExclusiveSkills(ProfessionType.FARMHAND);

        // Lv20 exclusive: +1 extra item from loot pool (non-treasure only)
        if (isExclusive && farmLevel >= 20) {
            int extraRolls = farmLevel >= 40 ? 2 : 1; // Lv40: 3 total (1 base + 2 extra)
            var drops = event.getDrops();
            var nonTreasure = drops.stream()
                    .filter(stack -> !isFishingTreasure(stack))
                    .toList();
            for (int i = 0; i < extraRolls; i++) {
                if (!nonTreasure.isEmpty()) {
                    ItemStack extraDrop = nonTreasure.get(ThreadLocalRandom.current().nextInt(nonTreasure.size())).copy();
                    giveItem(player, extraDrop);
                }
            }
        }

        // Lv40: Chance for bonus rare items
        if (farmLevel >= 40 && ThreadLocalRandom.current().nextDouble() < 0.15) {
            ItemStack bonus = switch (ThreadLocalRandom.current().nextInt(5)) {
                case 0 -> new ItemStack(net.minecraft.world.item.Items.SADDLE);
                case 1 -> new ItemStack(net.minecraft.world.item.Items.NAME_TAG);
                case 2 -> new ItemStack(net.minecraft.world.item.Items.NAUTILUS_SHELL);
                case 3 -> new ItemStack(net.minecraft.world.item.Items.HEART_OF_THE_SEA);
                default -> createRandomEnchantedBook(player);
            };
            giveItem(player, bonus);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Rare catch!").withColor(0x55FFFF));
        }

        // Very low chance: Totem of Undying (0.1%)
        if (ThreadLocalRandom.current().nextDouble() < 0.001) {
            giveItem(player, new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Incredible catch! Totem of Undying!").withColor(0xFF55FF));
        }

        // Low chance: Milky Star Jar with 5-100 stars
        if (ThreadLocalRandom.current().nextDouble() < 0.02) {
            int stars = 5 + ThreadLocalRandom.current().nextInt(96); // 5-100
            ItemStack jar = com.mlkymc.economy.MilkyStar.createJar(player.getName().getString());
            com.mlkymc.economy.MilkyStar.setJarBalance(jar, stars);
            giveItem(player, jar);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "You fished up a Milky Star Jar with " + stars + " stars!").withColor(0xFFD700));
        }
    }

    // =========================================================================
    // FARMHAND Lv30 exclusive: Lava fishing
    // Keeps fishing hooks alive in lava and generates custom loot on retrieve.
    // =========================================================================

    // Track hooks that are bobbing in lava
    private final java.util.Set<Integer> lavaHookIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Track how long a hook has been bobbing in lava (entity ID -> tick count in lava)
    private final java.util.Map<Integer, Integer> lavaBobbingTicks = new java.util.concurrent.ConcurrentHashMap<>();
    // Track when a bite should happen (entity ID -> bite tick threshold)
    private final java.util.Map<Integer, Integer> lavaBiteTimes = new java.util.concurrent.ConcurrentHashMap<>();
    // Track hooks currently biting in lava (hook entity ID -> player UUID)
    private final java.util.Map<Integer, java.util.UUID> lavaBitingHooks = new java.util.concurrent.ConcurrentHashMap<>();
    // Store the lava surface Y for each tracked hook (set once on first contact)
    private final java.util.Map<Integer, Double> lavaTargetY = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Make fishing hooks invulnerable when cast by a Lv30+ Farmhand.
     * This prevents lava from killing the hook before our tick handler can process it.
     */
    @SubscribeEvent
    public void onFishingHookSpawn(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.projectile.FishingHook hook)) return;

        var owner = hook.getPlayerOwner();
        if (owner instanceof ServerPlayer sp) {
            ClassData data = classManager.getOrCreate(sp);
            if (data.hasExclusiveSkills(ProfessionType.FARMHAND)
                    && data.getLevel(ProfessionType.FARMHAND) >= 30) {
                hook.setInvulnerable(true);
            }
        }
    }

    // No PreTick handler — let vanilla tick run normally.
    // The original floating approach worked visually; only the tick counter was broken.

    /**
     * Called from level tick. Keeps fishing hooks alive in lava for eligible Farmhand players.
     */
    public void tickLavaFishing(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        for (ServerPlayer player : serverLevel.players()) {
            ClassData data = classManager.getOrCreate(player);
            if (!data.hasExclusiveSkills(ProfessionType.FARMHAND)) continue;
            if (data.getLevel(ProfessionType.FARMHAND) < 30) continue;
            if (player.fishing == null) continue;

            // Lava fishing only works in the Nether
            if (!serverLevel.dimension().equals(net.minecraft.world.level.Level.NETHER)) continue;

            var hook = player.fishing;
            int hookId = hook.getId();

            // Only start tracking once the hook has actually touched lava
            boolean isInLava = hook.isInLava();
            boolean isTracked = lavaHookIds.contains(hookId);

            if (!isTracked && !isInLava) continue; // Still flying, not in lava yet
            if (!isTracked) lavaHookIds.add(hookId); // First lava contact

            // Keep hook alive
            hook.setInvulnerable(true);
            hook.clearFire();
            hook.setRemainingFireTicks(0);

            // Discard if player moved too far (vanilla check we bypassed)
            if (hook.distanceToSqr(player) > 1024) {
                hook.discard();
                lavaHookIds.remove(hookId);
                lavaBobbingTicks.remove(hookId);
                lavaBiteTimes.remove(hookId);
                lavaBitingHooks.remove(hookId);
                continue;
            }

            // Float on lava surface — original simple approach that looked good visually
            var pos = hook.blockPosition();
            var lavaState = serverLevel.getFluidState(pos);
            if (!lavaState.is(net.minecraft.tags.FluidTags.LAVA)) {
                lavaState = serverLevel.getFluidState(pos.below());
                if (lavaState.is(net.minecraft.tags.FluidTags.LAVA)) pos = pos.below();
            }
            if (lavaState.is(net.minecraft.tags.FluidTags.LAVA)) {
                float lavaHeight = pos.getY() + lavaState.getHeight(serverLevel, pos);
                double surfaceTarget = lavaHeight - 0.1;
                if (hook.getY() < surfaceTarget - 0.02) {
                    // Below surface — strong push up to counteract vanilla -0.03 gravity
                    hook.setDeltaMovement(hook.getDeltaMovement().x * 0.3,
                            0.08, hook.getDeltaMovement().z * 0.3);
                } else {
                    // At or near surface — snap to position and kill vertical movement
                    hook.setDeltaMovement(hook.getDeltaMovement().x * 0.3,
                            0.0, hook.getDeltaMovement().z * 0.3);
                    hook.setPos(hook.getX(), surfaceTarget, hook.getZ());
                }
            }

            // During a bite, add downward tugs
            if (lavaBitingHooks.containsKey(hookId)) {
                hook.setDeltaMovement(hook.getDeltaMovement().add(
                        0, -0.1 * serverLevel.random.nextFloat() * serverLevel.random.nextFloat(), 0));
            }

            // Increment bobbing timer
            int ticks = lavaBobbingTicks.getOrDefault(hookId, 0) + 1;
            lavaBobbingTicks.put(hookId, ticks);

            // Set bite threshold once per cycle. Nature's Call halves the wait time,
            // making lava fishing significantly faster when the skill is toggled on.
            if (!lavaBiteTimes.containsKey(hookId)) {
                var ph = com.mlkymc.MlkyMC.getPowerHandler();
                boolean naturesCall = ph != null && ph.isNaturesCallActive(player.getUUID());
                int baseWait = naturesCall ? 100 + ThreadLocalRandom.current().nextInt(100) // 5-10s
                                           : 200 + ThreadLocalRandom.current().nextInt(200); // 10-20s
                lavaBiteTimes.put(hookId, baseWait);
            }
            int biteTime = lavaBiteTimes.get(hookId);

            if (lavaBitingHooks.containsKey(hookId)) {
                // Bite in progress — check if window expired
                int biteElapsed = ticks - biteTime;
                if (biteElapsed >= 60) {
                    // Missed it — reset
                    lavaBitingHooks.remove(hookId);
                    lavaBobbingTicks.put(hookId, 0);
                    lavaBiteTimes.remove(hookId);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "The catch got away...").withColor(0xFF5555));
                }
            } else if (ticks >= biteTime) {
                // Signal bite — once only
                lavaBitingHooks.put(hookId, player.getUUID());
                serverLevel.playSound(null, hook.blockPosition(),
                        net.minecraft.sounds.SoundEvents.FISHING_BOBBER_SPLASH,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                        hook.getX(), hook.getY(), hook.getZ(), 8, 0.3, 0.2, 0.3, 0);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Something's tugging! Reel in now!").withColor(0xFFAA00));
            }
        }

        // Check for reeled-in hooks that were biting — give loot
        var bitingIter = lavaBitingHooks.entrySet().iterator();
        while (bitingIter.hasNext()) {
            var entry = bitingIter.next();
            int id = entry.getKey();
            java.util.UUID playerUUID = entry.getValue();
            ServerPlayer fisher = serverLevel.getServer().getPlayerList().getPlayer(playerUUID);
            if (fisher == null) { bitingIter.remove(); continue; }

            if (fisher.fishing == null || fisher.fishing.getId() != id) {
                // Reeled in during bite — catch!
                ClassData fisherData = classManager.getOrCreate(fisher);
                generateLavaLoot(fisher, fisherData.getLevel(ProfessionType.FARMHAND));
                classManager.addXp(fisher, ProfessionType.FARMHAND, 15, "lava fishing catch");

                // Durability cost
                ItemStack mainHand = fisher.getMainHandItem();
                ItemStack offHand = fisher.getOffhandItem();
                if (mainHand.getItem() instanceof net.minecraft.world.item.FishingRodItem) {
                    mainHand.hurtAndBreak(1, fisher, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                } else if (offHand.getItem() instanceof net.minecraft.world.item.FishingRodItem) {
                    offHand.hurtAndBreak(1, fisher, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                }
                bitingIter.remove();
            }
        }

        // Clean up stale entries
        var activeHookIds = new java.util.HashSet<Integer>();
        for (ServerPlayer p : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (p.fishing != null) activeHookIds.add(p.fishing.getId());
        }
        lavaHookIds.removeIf(id -> !activeHookIds.contains(id));
        lavaBobbingTicks.keySet().removeIf(id -> !lavaHookIds.contains(id));
        lavaBiteTimes.keySet().removeIf(id -> !lavaHookIds.contains(id));
        lavaTargetY.keySet().removeIf(id -> !lavaHookIds.contains(id));
    }

    private void generateLavaLoot(ServerPlayer player, int farmLevel) {
        // Roll loot table — weighted random
        double roll = ThreadLocalRandom.current().nextDouble();
        ItemStack loot;

        if (roll < 0.001) {
            // Netherite scrap — 1/1000
            loot = new ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Incredible catch! Netherite Scrap!").withColor(0xFF55FF));
        } else if (roll < 0.011) {
            // Blaze rod — 1%
            loot = new ItemStack(net.minecraft.world.item.Items.BLAZE_ROD);
        } else if (roll < 0.061) {
            // Nether wart — 5%
            loot = new ItemStack(net.minecraft.world.item.Items.NETHER_WART, 1 + ThreadLocalRandom.current().nextInt(3));
        } else if (roll < 0.111) {
            // Ominous Bottle — 5% (level scales with farmhand level)
            int ominousLevel = Math.min(4, farmLevel / 10);
            var ominous = new ItemStack(net.minecraft.world.item.Items.OMINOUS_BOTTLE);
            // Set the ominous level via amplifier component
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putInt("ominous_bottle_amplifier", ominousLevel);
            ominous.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
            loot = ominous;
        } else if (roll < 0.311) {
            // Gold nugget — 20%
            loot = new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, 2 + ThreadLocalRandom.current().nextInt(5));
        } else if (roll < 0.331) {
            // Gold ingot — 2%
            loot = new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT);
        } else if (roll < 0.531) {
            // Nether quartz — 20%
            loot = new ItemStack(net.minecraft.world.item.Items.QUARTZ, 1 + ThreadLocalRandom.current().nextInt(4));
        } else if (roll < 0.551) {
            // Armor trims — 2% (Sentry, Wild, Snout, Rib)
            var trims = new net.minecraft.world.item.Item[]{
                    net.minecraft.world.item.Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
                    net.minecraft.world.item.Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
                    net.minecraft.world.item.Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
                    net.minecraft.world.item.Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            };
            loot = new ItemStack(trims[ThreadLocalRandom.current().nextInt(trims.length)]);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Rare catch! Armor Trim!").withColor(0xFFAA00));
        } else if (roll < 0.556) {
            // Camera from Camerapture mod — 0.5% (try to find it, fallback to spyglass)
            var cameraOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.Identifier.parse("camerapture:camera"));
            if (cameraOpt.isPresent()) {
                loot = new ItemStack(cameraOpt.get());
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Incredible catch! Camera!").withColor(0xFF55FF));
            } else {
                loot = new ItemStack(net.minecraft.world.item.Items.SPYGLASS);
            }
        } else if (roll < 0.576) {
            // Bell — 2%
            loot = new ItemStack(net.minecraft.world.item.Items.BELL);
        } else if (roll < 0.581) {
            // Totem of Undying — 0.5%
            loot = new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Incredible catch! Totem of Undying!").withColor(0xFF55FF));
        } else if (roll < 0.611) {
            // Enchanted Book — 3%
            loot = createRandomEnchantedBook(player);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Rare catch! Enchanted Book!").withColor(0xFFAA00));
        } else {
            // Magma cream — filler (remaining ~42%)
            loot = new ItemStack(net.minecraft.world.item.Items.MAGMA_CREAM, 1 + ThreadLocalRandom.current().nextInt(3));
        }

        giveItem(player, loot);

        // Farmhand Lv20/40 exclusive fishing bonuses apply here too
        ClassData data = classManager.getOrCreate(player);
        boolean isExclusive = data.hasExclusiveSkills(ProfessionType.FARMHAND);
        if (isExclusive && farmLevel >= 20) {
            int extraRolls = farmLevel >= 40 ? 2 : 1;
            for (int i = 0; i < extraRolls; i++) {
                // Re-roll the loot table for extra drops
                generateLavaLootSingle(player, farmLevel);
            }
        }
    }

    /**
     * Single loot roll for extra fishing bonus — avoids recursion with the bonus check.
     */
    private void generateLavaLootSingle(ServerPlayer player, int farmLevel) {
        double roll = ThreadLocalRandom.current().nextDouble();
        ItemStack loot;
        if (roll < 0.001) {
            loot = new ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP);
        } else if (roll < 0.011) {
            loot = new ItemStack(net.minecraft.world.item.Items.BLAZE_ROD);
        } else if (roll < 0.061) {
            loot = new ItemStack(net.minecraft.world.item.Items.NETHER_WART, 1 + ThreadLocalRandom.current().nextInt(3));
        } else if (roll < 0.311) {
            loot = new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, 2 + ThreadLocalRandom.current().nextInt(5));
        } else if (roll < 0.531) {
            loot = new ItemStack(net.minecraft.world.item.Items.QUARTZ, 1 + ThreadLocalRandom.current().nextInt(4));
        } else {
            loot = new ItemStack(net.minecraft.world.item.Items.MAGMA_CREAM);
        }
        giveItem(player, loot);
    }

    private void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                    player.level(), player.getX(), player.getY(), player.getZ(), stack));
        }
    }

    /**
     * Create an enchanted book with a random enchantment at a random valid level.
     */
    private static boolean isFishingTreasure(ItemStack stack) {
        var item = stack.getItem();
        return item == net.minecraft.world.item.Items.ENCHANTED_BOOK
                || item == net.minecraft.world.item.Items.BOW
                || item == net.minecraft.world.item.Items.FISHING_ROD
                || item == net.minecraft.world.item.Items.NAME_TAG
                || item == net.minecraft.world.item.Items.SADDLE
                || item == net.minecraft.world.item.Items.NAUTILUS_SHELL
                || item == net.minecraft.world.item.Items.TOTEM_OF_UNDYING
                || stack.has(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS)
                || stack.isEnchanted();
    }

    private ItemStack createRandomEnchantedBook(ServerPlayer player) {
        ItemStack book = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
        var registry = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var allEnchants = new java.util.ArrayList<>(registry.listElements().toList());
        if (!allEnchants.isEmpty()) {
            var chosen = allEnchants.get(ThreadLocalRandom.current().nextInt(allEnchants.size()));
            int maxLevel = chosen.value().getMaxLevel();
            int level = 1 + ThreadLocalRandom.current().nextInt(maxLevel);
            var storedEnchants = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                    net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            storedEnchants.set(chosen, level);
            book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, storedEnchants.toImmutable());
        }
        return book;
    }

    // (Farmhand food buff system removed — replaced by ingredient attribute % system)

    // =========================================================================
    // SMITH EXCLUSIVE: Unbreakable Aura (Lv20)
    // 10% chance nearby players gain durability instead of losing it
    // Applied via tick handler checking nearby players
    // =========================================================================

    // Cached set of online Smith Lv20+ players (refreshed every 2 seconds in tick)
    private final java.util.Set<java.util.UUID> onlineSmithLv10 = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private long lastSmithCacheRefresh = 0;

    private void refreshSmithCache(net.minecraft.server.MinecraftServer server) {
        onlineSmithLv10.clear();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ClassData d = classManager.getOrCreate(sp);
            if (d.hasExclusiveSkills(ProfessionType.SMITH) && d.getLevel(ProfessionType.SMITH) >= 20) {
                onlineSmithLv10.add(sp.getUUID());
            }
        }
    }

    @SubscribeEvent
    public void onItemDamage(net.neoforged.neoforge.event.entity.living.ArmorHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (onlineSmithLv10.isEmpty()) return; // Fast path: no Smiths online

        // Refresh cache every 2 seconds
        if (player.level() instanceof ServerLevel sl) {
            long gameTime = sl.getGameTime();
            if (gameTime - lastSmithCacheRefresh >= 40) {
                refreshSmithCache(sl.getServer());
                lastSmithCacheRefresh = gameTime;
            }

            // Check if any cached Smith is within 10 blocks
            for (java.util.UUID smithId : onlineSmithLv10) {
                ServerPlayer smith = sl.getServer().getPlayerList().getPlayer(smithId);
                if (smith != null && smith.level() == player.level()
                        && smith.distanceToSqr(player) <= 100
                        && !com.mlkymc.pvp.PvPTagManager.isPvPTagged(player.getUUID())) { // 10 blocks, skip PvP
                    // Skip if player has Tempered Mind active (no durability loss = nothing to repair)
                    if (PowerHandler.isTemperedMindActive(player.getUUID())) break;

                    if (ThreadLocalRandom.current().nextDouble() < 0.10) {
                        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                            if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) {
                                ItemStack armor = player.getItemBySlot(slot);
                                if (armor.isDamageableItem() && armor.getDamageValue() > 0) {
                                    armor.setDamageValue(armor.getDamageValue() - 1);
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    // =========================================================================
    // LOGIN: Reapply persistent effects when player joins
    // =========================================================================

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ClassData data = classManager.getOrCreate(sp);
        int clericLv = data.getLevel(ProfessionType.CLERIC);
        boolean isCleric = data.getChosenClass() == ClassType.CLERIC;
        // Devoted Life is Cleric-class exclusive — only Clerics Lv10+ have it.
        boolean hasDevoted = isCleric && clericLv >= 10;
        if (!hasDevoted) {
            sendDevotedLifeNone(sp);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // Attributes reset on death — the tick handler will reapply next cycle
    }
}
