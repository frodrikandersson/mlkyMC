package com.mlkymc.classes;

import com.mlkymc.economy.MilkyStar;
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
    // Tracks distance sprinted since last hit. Max 10 blocks = +5 damage.
    // Expires after 10 seconds (200 ticks) of not sprinting.
    private final java.util.Map<java.util.UUID, Double> sprintDistance = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> windupExpireTick = new java.util.HashMap<>();

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

    public PassiveSkillHandler(ClassManager classManager) {
        this.classManager = classManager;
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
    }

    private void applyAdventurerTickEffects(ServerPlayer player, ClassData data) {
        int level = data.getLevel(ProfessionType.ADVENTURER);

        // --- Extra Hearts (Lv5+): +1 heart per 5 levels, max +10 hearts at Lv50 ---
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

        // --- Quick Step / Auto-step (Lv10): step up 1 block without jumping ---
        var stepAttr = player.getAttribute(Attributes.STEP_HEIGHT);
        if (stepAttr != null) {
            var existing = stepAttr.getModifier(STEP_MODIFIER_ID);
            double stepBonus = level >= 10 ? 0.6 : 0; // 0.6 + default 0.6 = 1.2 (enough for 1 block)
            double currentStep = existing != null ? existing.amount() : 0;
            if (currentStep != stepBonus) {
                stepAttr.removeModifier(STEP_MODIFIER_ID);
                if (stepBonus > 0) {
                    stepAttr.addPermanentModifier(new AttributeModifier(
                            STEP_MODIFIER_ID, stepBonus, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        // --- Jump Boost (Lv20): 1.5 block jump via attribute (no flickering icon) ---
        var jumpAttr = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jumpAttr != null) {
            var existing = jumpAttr.getModifier(JUMP_MODIFIER_ID);
            double jumpBonus = level >= 20 ? 0.1 : 0; // +0.1 on top of default 0.42 = ~1.5 block jump
            double currentBonus = existing != null ? existing.amount() : 0;
            if (currentBonus != jumpBonus) {
                jumpAttr.removeModifier(JUMP_MODIFIER_ID);
                if (jumpBonus > 0) {
                    jumpAttr.addPermanentModifier(new AttributeModifier(
                            JUMP_MODIFIER_ID, jumpBonus, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        // --- Speed Boost (Lv50) via attribute ---
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            var existing = speedAttr.getModifier(SPEED_MODIFIER_ID);
            double speedBonus = level >= 50 ? 0.03 : 0; // ~15% speed boost
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

        // --- Haste via block break speed attribute (no flickering icon) ---
        var breakSpeedAttr = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (breakSpeedAttr != null) {
            var existing = breakSpeedAttr.getModifier(HASTE_MODIFIER_ID);
            double hasteBonus = 0;
            if (level >= 10) {
                hasteBonus = 0.2; // ~20% faster mining (like Haste I)
                if (data.hasExclusiveSkills(ProfessionType.MINECRAFTER)) {
                    if (level >= 40) hasteBonus = 0.6; // Haste III equivalent
                    else if (level >= 20) hasteBonus = 0.4; // Haste II equivalent
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

        // --- Block interaction reach (Lv10: +1, Lv40: +2 if exclusive) ---
        var reachAttr = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (reachAttr != null) {
            var existingReach = reachAttr.getModifier(REACH_MODIFIER_ID);
            double reachBonus = 0;
            if (level >= 40 && data.hasExclusiveSkills(ProfessionType.MINECRAFTER)) {
                reachBonus = 2.0;
            } else if (level >= 10) {
                reachBonus = 1.0;
            }
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

        // --- Permanent Resistance (Lv30, stacks at Lv50) via armor toughness attribute ---
        // Invisible — no potion icon. +4 toughness at Lv30, +8 at Lv50.
        var armorToughAttr = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (armorToughAttr != null) {
            var existing = armorToughAttr.getModifier(SMITH_ARMOR_ID);
            double bonus = 0;
            if (level >= 50) bonus = 8.0;
            else if (level >= 30) bonus = 4.0;
            double currentBonus = existing != null ? existing.amount() : 0;
            if (currentBonus != bonus) {
                armorToughAttr.removeModifier(SMITH_ARMOR_ID);
                if (bonus > 0) {
                    armorToughAttr.addPermanentModifier(new AttributeModifier(
                            SMITH_ARMOR_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        // --- Underwater Breathing (Lv50) ---
        if (level >= 50 && player.isUnderWater()) {
            var breathEffect = player.getEffect(MobEffects.WATER_BREATHING);
            if (breathEffect == null || breathEffect.getDuration() < 100) {
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 400, 0, false, false, true));
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
     * Track sprint distance each tick. Only accumulates while sprinting.
     * Resets on hit.
     */
    private void trackSprintDistance(ServerPlayer player) {
        java.util.UUID uuid = player.getUUID();
        int advLevel = classManager.getOrCreate(player).getLevel(ProfessionType.ADVENTURER);
        if (advLevel < 30) return;

        double currentDist = sprintDistance.getOrDefault(uuid, 0.0);

        if (player.isSprinting()) {
            double moved = player.getDeltaMovement().horizontalDistance();
            currentDist = Math.min(10.0, currentDist + moved);
            sprintDistance.put(uuid, currentDist);
            // Reset expire timer while sprinting
            windupExpireTick.put(uuid, player.tickCount + 200); // 10 seconds from now

            // Show wind-up visually: give Strength effect with level based on charge
            // This shows the strength icon + particles as a visual indicator
            if (currentDist >= 1.0) {
                int strengthLevel = Math.min(4, (int)(currentDist / 2.5)); // 0-4 based on 0-10 blocks
                player.addEffect(new MobEffectInstance(
                        MobEffects.STRENGTH, 220, strengthLevel, false, true, true));
            }
        } else {
            // Not sprinting — check expiry
            if (currentDist > 0) {
                Integer expireTick = windupExpireTick.get(uuid);
                if (expireTick != null && player.tickCount > expireTick) {
                    // Expired — reset
                    sprintDistance.put(uuid, 0.0);
                    windupExpireTick.remove(uuid);
                    player.removeEffect(MobEffects.STRENGTH);
                }
            }
        }
    }

    @SubscribeEvent
    public void onAttackDamage(LivingDamageEvent.Pre event) {
        var source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) return;

        int advLevel = classManager.getOrCreate(player).getLevel(ProfessionType.ADVENTURER);
        if (advLevel >= 30) {
            // Wind-up damage: +0.5 per block sprinted, max 10 blocks = +5 damage
            java.util.UUID uuid = player.getUUID();
            double dist = sprintDistance.getOrDefault(uuid, 0.0);
            if (dist >= 1.0) {
                float bonus = (float) (dist * 0.5);
                event.setNewDamage(event.getNewDamage() + bonus);
                // Reset after hit
                sprintDistance.put(uuid, 0.0);
                windupExpireTick.remove(uuid);
                player.removeEffect(MobEffects.STRENGTH);
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
                // Break and replant
                if (level instanceof ServerLevel serverLevel) {
                    Block.dropResources(state, serverLevel, pos, null, player, player.getMainHandItem());
                    level.setBlock(pos, crop.getStateForAge(0), 3);
                    event.setCanceled(true);
                }
            }
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

        // Lv30: Seeds/saplings double chance
        if (farmLevel >= 30) {
            boolean isFarmDrop = blockId.contains("wheat") || blockId.contains("carrot")
                    || blockId.contains("potato") || blockId.contains("beetroot")
                    || blockId.contains("leaves") || blockId.contains("grass");
            if (isFarmDrop && ThreadLocalRandom.current().nextDouble() < 0.5) {
                // Duplicate each drop
                var dropsCopy = new java.util.ArrayList<>(event.getDrops());
                for (var drop : dropsCopy) {
                    var dupe = drop.getItem().copy();
                    if (!dupe.isEmpty()) {
                        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                                player.level(), drop.getX(), drop.getY(), drop.getZ(), dupe));
                    }
                }
            }
        }

        // Lv30: Milky Stars from ore (MineCrafter)
        int mcLevel = classManager.getOrCreate(player).getLevel(ProfessionType.MINECRAFTER);
        if (mcLevel >= 30) {
            if (blockId.contains("ore") || blockId.contains("ancient_debris")) {
                double chance = mcLevel >= 50 ? 0.10 : 0.03; // 3% at 30, 10% at 50
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    ItemStack star = new ItemStack(ModItems.MILKY_STAR.get(), 1);
                    if (player.level() instanceof ServerLevel sl) {
                        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                                sl, event.getPos().getX() + 0.5, event.getPos().getY() + 0.5,
                                event.getPos().getZ() + 0.5, star));
                    }
                }
            }
        }

        // Lv20: XP orbs from stone (MineCrafter)
        if (mcLevel >= 20) {
            if (blockId.contains("stone") || blockId.contains("deepslate")) {
                if (ThreadLocalRandom.current().nextDouble() < 0.15) {
                    int xpAmount = event.getDroppedExperience() + ThreadLocalRandom.current().nextInt(1, 4);
                    event.setDroppedExperience(xpAmount);
                }
            }
        }
    }

    // =========================================================================
    // MINECRAFTER: Crafting bonus output (Lv40, exclusive double at Lv30)
    // =========================================================================

    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        int mcLevel = data.getLevel(ProfessionType.MINECRAFTER);

        // Exclusive Lv30: Double crafting output
        if (data.hasExclusiveSkills(ProfessionType.MINECRAFTER) && mcLevel >= 30) {
            var result = event.getCrafting();
            int bonus = result.getCount(); // double = add same amount
            // Lv40: +1 on top
            if (mcLevel >= 40) bonus += 1;
            result.grow(bonus);
            return;
        }

        // Non-exclusive Lv40: Occasionally +1
        if (mcLevel >= 40) {
            if (ThreadLocalRandom.current().nextDouble() < 0.25) {
                event.getCrafting().grow(1);
            }
        }
    }

    // =========================================================================
    // SMITH: Fuel saving (Lv10), Anvil Too Expensive cap (Lv40)
    // These are handled elsewhere (furnace events, anvil events).
    // Smith Lv10 Exclusive: Unbreakable Aura is tick-based but affects OTHER
    // players near the Smith — complex, placeholder for now.
    // =========================================================================

    // =========================================================================
    // CLERIC: Potion sharing (Lv30) - when you drink a potion, nearby allies
    // get a weaker version. Done via tick check after potion consumption.
    // =========================================================================

    @SubscribeEvent
    public void onPotionBrewed(net.neoforged.neoforge.event.brewing.PotionBrewEvent.Post event) {
        // Lv20: Potions last 25% longer — we modify brewed potions after brewing
        // Find the player who brewed (closest player to brewing stand)
        // This is tricky without a direct player reference in the event.
        // Deferred: would need a block entity tracker to know who placed ingredients.
    }

    // =========================================================================
    // CLERIC: Enchanting modifications (Lv40 lapis saving, Lv50 enchant cap+1)
    // =========================================================================

    @SubscribeEvent
    public void onEnchant(net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        int clericLevel = data.getLevel(ProfessionType.CLERIC);

        // Lv40: 25% chance to refund lapis
        if (clericLevel >= 40) {
            if (ThreadLocalRandom.current().nextDouble() < 0.25) {
                player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI, 3));
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Cleric Lv40: Lapis preserved!").withColor(0x5555FF));
            }
        }
    }

    // =========================================================================
    // SMITH: Anvil cost reduction (50% as special effect handled via AnvilUpdate)
    // Lv20: +10% bonus durability on anvil repairs
    // Lv40: Too Expensive cap raised from 40 to 55
    // Exclusive Lv30: Anvil never breaks (handled via AnvilCraft.Post)
    // =========================================================================

    @SubscribeEvent
    public void onAnvilUpdate(net.neoforged.neoforge.event.AnvilUpdateEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        int smithLevel = data.getLevel(ProfessionType.SMITH);

        // Lv40: Allow higher XP costs (raise Too Expensive cap)
        if (smithLevel >= 40) {
            // If vanilla would reject this (cost > 40), allow it up to 55
            if (event.getXpCost() > 40 && event.getXpCost() <= 55) {
                // Event is fine, don't cancel
            }
        }

        // Smith special effect: 50% reduced anvil repair costs
        if (data.getChosenClass() == ClassType.SMITH) {
            event.setXpCost(Math.max(1, event.getXpCost() / 2));
        }

        // Lv20: Repairs give +10% bonus durability above max
        if (smithLevel >= 20 && !event.getOutput().isEmpty()) {
            ItemStack output = event.getOutput();
            if (output.isDamageableItem()) {
                int maxDamage = output.getMaxDamage();
                int currentDamage = output.getDamageValue();
                int repaired = maxDamage - currentDamage;
                int bonus = (int) (repaired * 0.1);
                if (bonus > 0) {
                    output.setDamageValue(Math.max(0, currentDamage - bonus));
                }
            }
        }
    }

    // =========================================================================
    // SMITH: Fuel saving (Lv10 - 25% chance to not consume fuel)
    // Done by increasing burn time by 33% (equivalent to ~25% fuel saving)
    // =========================================================================

    @SubscribeEvent
    public void onFuelBurn(net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent event) {
        // FurnaceFuelBurnTimeEvent doesn't have a player reference directly.
        // We can't easily know which player "owns" a furnace.
        // This would need a furnace owner tracker (who last placed items).
        // For now, increase ALL fuel burn time slightly as a global buff
        // if any online Smith Lv10+ player exists. This is a compromise.
    }

    // =========================================================================
    // FARMHAND: Animal growth speed (Lv20 - 25% faster)
    // Breeding cooldown (Lv20 - 25% lower)
    // Only affects animals that a Farmhand Lv20+ player bred (tagged)
    // =========================================================================

    private static final String FARMHAND_BRED_TAG = "mlkymc_farmhand_bred";

    /**
     * Tag baby animals when a Farmhand breeds them.
     */
    @SubscribeEvent
    public void onAnimalBred(net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent event) {
        if (event.getCausedByPlayer() == null) return;
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player)) return;

        int farmLevel = classManager.getOrCreate(player).getLevel(ProfessionType.FARMHAND);
        if (farmLevel < 20) return;

        // Tag the baby AND the parents (for breeding cooldown reduction)
        var child = event.getChild();
        if (child != null) {
            child.addTag(FARMHAND_BRED_TAG);
        }
        if (event.getParentA() != null) event.getParentA().addTag(FARMHAND_BRED_TAG);
        if (event.getParentB() != null) event.getParentB().addTag(FARMHAND_BRED_TAG);
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
    // FARMHAND: Crop tick acceleration (Lv30: 2x, Lv50: 4x)
    // Accelerates random ticks in the player's chunk for crop blocks
    // =========================================================================

    @SubscribeEvent
    public void onCropTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 40 != 20) return; // Every 2 seconds, offset from animal tick

        for (ServerPlayer player : serverLevel.players()) {
            int farmLevel = classManager.getOrCreate(player).getLevel(ProfessionType.FARMHAND);
            if (farmLevel < 30) continue;

            int extraTicks = farmLevel >= 50 ? 3 : 1; // Lv30: +1 (2x), Lv50: +3 (4x)

            // Random tick crops in the player's chunk
            var chunkPos = player.chunkPosition();
            var chunk = serverLevel.getChunk(chunkPos.x, chunkPos.z);

            for (int t = 0; t < extraTicks; t++) {
                // Pick random positions in the chunk and tick them if they're crops
                for (int attempt = 0; attempt < 16; attempt++) {
                    int x = chunkPos.x * 16 + serverLevel.random.nextInt(16);
                    int z = chunkPos.z * 16 + serverLevel.random.nextInt(16);
                    int y = 60 + serverLevel.random.nextInt(40); // Rough surface range

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = serverLevel.getBlockState(pos);
                    if (state.getBlock() instanceof CropBlock) {
                        state.randomTick(serverLevel, pos, serverLevel.random);
                    }
                }
            }
        }
    }

    // =========================================================================
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

        // Lv20 exclusive: +1 extra item from loot pool
        if (isExclusive && farmLevel >= 20) {
            int extraRolls = farmLevel >= 40 ? 2 : 1; // Lv40: 3 total (1 base + 2 extra)
            for (int i = 0; i < extraRolls; i++) {
                // Duplicate a random item from the existing drops
                var drops = event.getDrops();
                if (!drops.isEmpty()) {
                    ItemStack extraDrop = drops.get(ThreadLocalRandom.current().nextInt(drops.size())).copy();
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
                default -> new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
            };
            giveItem(player, bonus);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Rare catch!").withColor(0x55FFFF));
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

    /**
     * Called from level tick. Keeps fishing hooks alive in lava for eligible Farmhand players.
     */
    public void tickLavaFishing(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        for (ServerPlayer player : serverLevel.players()) {
            if (player.fishing == null) continue;

            ClassData data = classManager.getOrCreate(player);
            if (!data.hasExclusiveSkills(ProfessionType.FARMHAND)) continue;
            if (data.getLevel(ProfessionType.FARMHAND) < 30) continue;

            var hook = player.fishing;
            int hookId = hook.getId();

            // Keep the hook alive in lava — extinguish fire, prevent burning
            if (hook.isInLava() || hook.isOnFire()) {
                hook.clearFire();
                hook.setRemainingFireTicks(0);

                // Track lava bobbing time
                int ticks = lavaBobbingTicks.getOrDefault(hookId, 0) + 1;
                lavaBobbingTicks.put(hookId, ticks);
                lavaHookIds.add(hookId);

                // After bobbing ~3-8 seconds in lava, simulate a bite
                int biteTime = 60 + ThreadLocalRandom.current().nextInt(100); // 3-8 seconds
                if (ticks >= biteTime) {
                    // Generate lava loot and give to player
                    generateLavaLoot(player, data.getLevel(ProfessionType.FARMHAND));
                    lavaBobbingTicks.put(hookId, 0); // Reset timer for next catch

                    // Sound + particles
                    serverLevel.playSound(null, hook.blockPosition(),
                            net.minecraft.sounds.SoundEvents.FISHING_BOBBER_SPLASH,
                            net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                            hook.getX(), hook.getY(), hook.getZ(), 5, 0.3, 0.2, 0.3, 0);

                    // Give fishing XP
                    classManager.addXp(player, ProfessionType.FARMHAND, 15);
                }
            } else {
                // Not in lava anymore — clean up
                if (lavaHookIds.remove(hookId)) {
                    lavaBobbingTicks.remove(hookId);
                }
            }
        }

        // Clean up stale entries
        lavaHookIds.removeIf(id -> serverLevel.getEntity(id) == null);
        lavaBobbingTicks.keySet().removeIf(id -> !lavaHookIds.contains(id));
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

    // =========================================================================
    // FARMHAND EXCLUSIVE: Food buffs (Lv35: self, Lv50: crafted food buffs all)
    // Applied when eating food — use LivingEntityUseItemEvent
    // =========================================================================

    @SubscribeEvent
    public void onFoodEaten(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        if (!data.hasExclusiveSkills(ProfessionType.FARMHAND)) return;
        int farmLevel = data.getLevel(ProfessionType.FARMHAND);

        ItemStack item = event.getItem();
        if (!item.has(net.minecraft.core.component.DataComponents.FOOD)) return;

        // Lv35: Any food you eat gives Regen I (5s), Speed I (15s), Luck (1min)
        if (farmLevel >= 35) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0)); // 5s
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 0)); // 15s
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 1200, 0)); // 1min
        }

        // Lv50: Any food you craft gives buffs to ALL nearby players
        if (farmLevel >= 50) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0)); // 10s (replaces 5s)
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0)); // 30s
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, 2400, 0)); // 2min

            // Buff nearby players too
            var nearbyPlayers = player.level().getEntitiesOfClass(
                    ServerPlayer.class, player.getBoundingBox().inflate(16));
            for (var nearby : nearbyPlayers) {
                if (nearby == player) continue;
                nearby.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
                nearby.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0));
                nearby.addEffect(new MobEffectInstance(MobEffects.LUCK, 2400, 0));
                // Fortune and Looting are enchantments, not effects — skip for now
            }
        }
    }

    // =========================================================================
    // SMITH EXCLUSIVE: Unbreakable Aura (Lv10)
    // 10% chance nearby players gain durability instead of losing it
    // Applied via tick handler checking nearby players
    // =========================================================================

    // This is checked in the tick handler — when a Smith Lv10+ is nearby,
    // other players' items have a chance to not lose durability.
    // Implementing via LivingDamageEvent or item damage event.

    @SubscribeEvent
    public void onItemDamage(net.neoforged.neoforge.event.entity.living.ArmorHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Check if any Smith Lv10+ exclusive player is nearby
        if (player.level() instanceof ServerLevel sl) {
            var nearbyPlayers = sl.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(8));
            for (var nearby : nearbyPlayers) {
                ClassData nearbyData = classManager.getOrCreate(nearby);
                if (nearbyData.hasExclusiveSkills(ProfessionType.SMITH) && nearbyData.getLevel(ProfessionType.SMITH) >= 10) {
                    // 10% chance to negate durability loss
                    if (ThreadLocalRandom.current().nextDouble() < 0.10) {
                        // Cancel armor damage by setting all slots to 0 damage
                        // ArmorHurtEvent may not easily support this — just heal 1 durability instead
                        // Heal 1 durability on all equipped armor
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
        // The tick handler will pick up and reapply all attribute modifiers next cycle
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // Attributes reset on death — the tick handler will reapply next cycle
    }
}
