package com.mlkymc.classes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Phase 4: Special Effects — permanent bonuses only for the chosen class.
 *
 * Adventurer: 2x EXP, weapon durability+, 2x hostile drops, minimap (separate)
 * Cleric: 2x EXP, cheaper enchanting, stronger potions, save enchant levels
 * Farmhand: 2x EXP, crop tick accel, twin breeding, fishing luck (handled in passives)
 * MineCrafter: 2x EXP, ingredient return, Fortune+1, 2x pick/axe durability
 * Smith: 2x EXP, anvil 50% cheaper (handled in passives), faster smelt, armor durability
 *
 * Note: 2x EXP is already handled in ClassData.addXp().
 * This handler covers: durability bonuses, Fortune, hostile drop doubling.
 */
public class SpecialEffectHandler {
    private final ClassManager classManager;

    public SpecialEffectHandler(ClassManager classManager) {
        this.classManager = classManager;
    }

    // =========================================================================
    // ADVENTURER EXCLUSIVE: Hostile mob drop-rate bonus
    // Lv5: +20%, Lv15: +40%, Lv25: +60%, Lv35: +80%, Lv45: +100%
    // =========================================================================

    @SubscribeEvent
    public void onMobDrop(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        var source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Monster)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.ADVENTURER) return;

        int level = data.getLevel(ProfessionType.ADVENTURER);

        // Drop bonus doubling
        double bonusPercent = getAdventurerDropBonus(level);
        if (bonusPercent > 0) {
            var random = player.level().random;
            for (var drop : event.getDrops()) {
                ItemStack item = drop.getItem();
                if (random.nextFloat() < bonusPercent) {
                    item.setCount(item.getCount() * 2);
                }
            }
        }

        // Adventurer exclusive: Milky Star drop from mobs
        // Lv10: 3%, Lv30: 10%, Lv50: 20%
        double starChance = getMilkyStarDropChance(level);
        if (starChance > 0 && player.level().random.nextFloat() < starChance) {
            var entity = event.getEntity();
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                    entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                    new ItemStack(com.mlkymc.registry.ModItems.MILKY_STAR.get())));
        }
    }

    private static double getMilkyStarDropChance(int level) {
        if (level >= 50) return 0.50;
        if (level >= 30) return 0.30;
        if (level >= 10) return 0.10;
        return 0.0;
    }

    private static double getAdventurerDropBonus(int level) {
        if (level >= 45) return 1.0;  // +100%
        if (level >= 35) return 0.8;  // +80%
        if (level >= 25) return 0.6;  // +60%
        if (level >= 15) return 0.4;  // +40%
        if (level >= 5)  return 0.2;  // +20%
        return 0.0;
    }

    // =========================================================================
    // ADVENTURER + SMITH: Durability bonuses
    // Adventurer: weapons last longer
    // Smith: armor takes less durability damage
    // MineCrafter: pickaxes/axes 2x durability
    //
    // These are best handled via item damage events, but NeoForge 1.21.11
    // doesn't have a clean per-item durability event. Instead, we grant
    // Unbreaking-equivalent via the tick handler or item NBT.
    //
    // For now: apply as attribute modifiers or check in damage events.
    // =========================================================================

    // MineCrafter: Fortune +1 built-in on pickaxe
    // This is handled by adding the Fortune enchantment effect when mining.
    // The passive handler already gives Haste via attribute. Fortune is harder
    // since it's an enchantment, not an attribute. We handle it by modifying
    // block drops in BlockDropsEvent (already in PassiveSkillHandler via
    // the Milky Star ore drop chance).

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public void onBlockDrop(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;

        String blockId = event.getState().getBlock().getDescriptionId();

        // Check if player-placed (don't markBroken here — ClassExpHandler handles that)
        boolean isOre = blockId.contains("ore") || blockId.contains("ancient_debris");
        boolean isPlayerPlacedBlock = false;
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            var tracker = com.mlkymc.world.PlacedOreTracker.get(sl.getServer());
            String dim = sl.dimension().identifier().toString();
            if (tracker.isPlayerPlaced(dim, event.getPos())) {
                isPlayerPlacedBlock = true;
            }
        }

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.MINECRAFTER) return;

        int level = data.getLevel(ProfessionType.MINECRAFTER);
        boolean isMiningBlock = isOre
                || blockId.contains("stone") || blockId.contains("deepslate")
                || blockId.contains("log") || blockId.contains("wood");

        // MineCrafter class bonus: baseline +1 Fortune on ore drops (33% chance to +1).
        // The Lv20 and Lv40 exclusive extra Fortune bumps have been removed — they
        // stacked too aggressively with the existing drop-bonus percentages, the
        // Lv30 bonus-stone-materials path, and the Milky Star ore chance.
        if (isOre && !isPlayerPlacedBlock) {
            for (var drop : event.getDrops()) {
                ItemStack item = drop.getItem();
                if (player.level().random.nextFloat() < 0.33f) {
                    item.grow(1);
                }
            }
        }

        // Exclusive drop bonus: +20/40/60/80/100% at Lv5/15/25/35/45
        // Skip on player-placed ores
        if (isMiningBlock && !(isOre && isPlayerPlacedBlock)) {
            double bonusPercent = getMinecrafterDropBonus(level);
            if (bonusPercent > 0) {
                var random = player.level().random;
                for (var drop : event.getDrops()) {
                    ItemStack item = drop.getItem();
                    if (random.nextFloat() < bonusPercent) {
                        item.setCount(item.getCount() * 2);
                    }
                }
            }
        }

        // Lv30 Exclusive: Bonus materials from mining natural stone
        if (level >= 30 && com.mlkymc.world.PlacedOreTracker.isStone(blockId) && !isPlayerPlacedBlock) {
            dropBonusStoneMaterials(player, event);
        }

        // Lv50 Exclusive: Bonus materials from mining natural netherrack in the Nether
        if (level >= 50 && blockId.contains("netherrack") && !isPlayerPlacedBlock
                && player.level().dimension() == net.minecraft.world.level.Level.NETHER) {
            dropBonusNetherMaterials(player, event);
        }
    }

    private static double getMinecrafterDropBonus(int level) {
        if (level >= 45) return 1.0;
        if (level >= 35) return 0.8;
        if (level >= 25) return 0.6;
        if (level >= 15) return 0.4;
        if (level >= 5)  return 0.2;
        return 0.0;
    }

    /**
     * Lv30 MineCrafter Exclusive: Mining natural stone has a chance to drop bonus ores.
     * Raw copper (8%), Raw iron (5%), Coal (5%), Raw gold (2%), Emerald ore (0.5%), Diamond ore (0.3%)
     */
    private void dropBonusStoneMaterials(ServerPlayer player, BlockDropsEvent event) {
        var random = player.level().random;
        float roll = random.nextFloat();

        net.minecraft.world.item.Item bonusItem = null;
        if (roll < 0.003f) {
            bonusItem = net.minecraft.world.item.Items.DIAMOND;
        } else if (roll < 0.008f) {
            bonusItem = net.minecraft.world.item.Items.EMERALD;
        } else if (roll < 0.028f) {
            bonusItem = net.minecraft.world.item.Items.RAW_GOLD;
        } else if (roll < 0.048f) {
            bonusItem = net.minecraft.world.item.Items.RAW_IRON;
        } else if (roll < 0.58f) {
            bonusItem = net.minecraft.world.item.Items.COAL;
        } else if (roll < 0.88f) {
            bonusItem = net.minecraft.world.item.Items.RAW_COPPER;
        }

        if (bonusItem != null) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                    player.level(),
                    event.getPos().getX() + 0.5, event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5,
                    new ItemStack(bonusItem)));
        }
    }

    /**
     * Lv50 MineCrafter Exclusive: Mining natural netherrack in the Nether drops bonus materials.
     * Gold nuggets (10%), Quartz (6%), Netherite scrap (0.2%)
     */
    private void dropBonusNetherMaterials(ServerPlayer player, BlockDropsEvent event) {
        var random = player.level().random;
        float roll = random.nextFloat();

        net.minecraft.world.item.Item bonusItem = null;
        if (roll < 0.002f) {
            bonusItem = net.minecraft.world.item.Items.NETHERITE_SCRAP;
        } else if (roll < 0.062f) {
            bonusItem = net.minecraft.world.item.Items.QUARTZ;
        } else if (roll < 0.162f) {
            bonusItem = net.minecraft.world.item.Items.GOLD_NUGGET;
        }

        if (bonusItem != null) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                    player.level(),
                    event.getPos().getX() + 0.5, event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5,
                    new ItemStack(bonusItem)));
        }
    }

    // =========================================================================
    // FARMHAND: Drop debuff (everyone) + exclusive drop bonus
    // Affects: neutral mob drops, crop drops
    // Fishing is handled separately in PassiveSkillHandler
    // =========================================================================

    /**
     * Farmhand debuff: reduce neutral mob drops.
     * Farmhand exclusive: bonus neutral mob drops (+20/40/60/80/100%).
     */
    @SubscribeEvent
    public void onAnimalDrop(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        var source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.animal.Animal)) return;

        ClassData data = classManager.getOrCreate(player);
        int farmLevel = data.getLevel(ProfessionType.FARMHAND);

        // Debuff: chance to clear drops (everyone)
        double debuff = data.getDebuffPercent(ProfessionType.FARMHAND);
        if (debuff > 0 && player.level().random.nextDouble() < debuff) {
            event.getDrops().clear();
            return;
        }

        // Exclusive bonus: chance to double drops
        if (data.getChosenClass() == ClassType.FARMHAND) {
            double bonus = getFarmhandDropBonus(farmLevel);
            if (bonus > 0) {
                for (var drop : event.getDrops()) {
                    if (player.level().random.nextFloat() < bonus) {
                        drop.getItem().setCount(drop.getItem().getCount() * 2);
                    }
                }
            }
        }
    }

    /**
     * Farmhand debuff + bonus on crop drops.
     * Applied in addition to MineCrafter's onBlockDrop (they don't overlap — MC is ores/stone, Farm is crops).
     */
    @SubscribeEvent
    public void onCropDrop(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;

        String blockId = event.getState().getBlock().getDescriptionId();
        boolean isFarmBlock = blockId.contains("wheat") || blockId.contains("carrot")
                || blockId.contains("potato") || blockId.contains("beetroot")
                || blockId.contains("nether_wart") || blockId.contains("melon")
                || blockId.contains("pumpkin") || blockId.contains("cocoa")
                || blockId.contains("sweet_berr") || blockId.contains("sugar_cane");
        if (!isFarmBlock) return;

        ClassData data = classManager.getOrCreate(player);
        int farmLevel = data.getLevel(ProfessionType.FARMHAND);

        // Debuff is handled by DebuffHandler — don't duplicate it here

        // Exclusive bonus: chance to double drops
        if (data.getChosenClass() == ClassType.FARMHAND) {
            double bonus = getFarmhandDropBonus(farmLevel);
            if (bonus > 0) {
                for (var drop : event.getDrops()) {
                    if (player.level().random.nextFloat() < bonus) {
                        drop.getItem().setCount(drop.getItem().getCount() * 2);
                    }
                }
            }
        }
    }

    private static double getFarmhandDropBonus(int level) {
        if (level >= 45) return 1.0;
        if (level >= 35) return 0.8;
        if (level >= 25) return 0.6;
        if (level >= 15) return 0.4;
        if (level >= 5)  return 0.2;
        return 0.0;
    }

    // =========================================================================
    // MINECRAFTER: Tool durability — 2x pickaxe/axe durability
    // ADVENTURER: Weapon durability increase
    // SMITH: Armor takes less durability damage
    //
    // Handle by intercepting item damage. In 1.21.11 there's no direct
    // item durability event, so we use a trick: on player tick, check if
    // tool durability decreased and heal it back sometimes.
    // =========================================================================

    // Track previous damage values per player to detect actual durability loss
    private final java.util.Map<java.util.UUID, Integer> prevMainHandDamage = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, net.minecraft.world.item.Item> prevMainHandItem = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, int[]> prevArmorDamage = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, net.minecraft.world.item.Item[]> prevArmorItems = new java.util.HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        ClassType chosen = data.getChosenClass();
        java.util.UUID pid = player.getUUID();

        // Tempered Mind: negate ALL durability loss while active
        // Can apply to non-Smiths via Lv30 AoE sharing
        if (PowerHandler.isTemperedMindActive(pid)) {
            // Restore mainhand to previous damage value — but only if it's the SAME item.
            // If the player switched items, the tracked damage belongs to the old item
            // and would incorrectly "repair" the new one down to the old value.
            ItemStack mh = player.getMainHandItem();
            net.minecraft.world.item.Item mhItem = mh.getItem();
            net.minecraft.world.item.Item lastMhItem = prevMainHandItem.get(pid);
            if (lastMhItem == null || lastMhItem != mhItem) {
                // Item changed — reset tracking to the new item's current damage
                prevMainHandDamage.put(pid, mh.isDamageableItem() ? mh.getDamageValue() : 0);
                prevMainHandItem.put(pid, mhItem);
            } else if (mh.isDamageableItem()) {
                int prev = prevMainHandDamage.get(pid);
                if (mh.getDamageValue() > prev) mh.setDamageValue(prev);
            }
            int[] prevArmor = prevArmorDamage.get(pid);
            if (prevArmor != null) {
                int idx = 0;
                for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                        net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                        net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                    ItemStack armor = player.getItemBySlot(slot);
                    if (armor.isDamageableItem() && armor.getDamageValue() > prevArmor[idx]) {
                        armor.setDamageValue(prevArmor[idx]);
                    }
                    idx++;
                }
            }
            // Update tracking value for the (possibly-restored) current item,
            // so expiry hands off cleanly to the non-TM branch next tick.
            prevMainHandDamage.put(pid, mh.isDamageableItem() ? mh.getDamageValue() : 0);
            prevMainHandItem.put(pid, mhItem);
            return;
        }

        // Track mainhand damage — reset when item changes (prevents repair on item switch)
        ItemStack mainHand = player.getMainHandItem();
        int currentDmg = mainHand.isDamageableItem() ? mainHand.getDamageValue() : 0;
        net.minecraft.world.item.Item currentItem = mainHand.getItem();
        net.minecraft.world.item.Item lastItem = prevMainHandItem.get(pid);

        if (lastItem == null || lastItem != currentItem) {
            // Item changed — reset tracking to current damage
            prevMainHandDamage.put(pid, currentDmg);
            prevMainHandItem.put(pid, currentItem);
        }
        int prevDmg = prevMainHandDamage.get(pid);

        if (chosen == ClassType.MINECRAFTER && mainHand.isDamageableItem()) {
            String itemName = mainHand.getItem().toString();
            if ((itemName.contains("pickaxe") || itemName.contains("axe")) && currentDmg > prevDmg) {
                // Durability just decreased — 50% chance to negate
                if (player.level().random.nextFloat() < 0.5f) {
                    mainHand.setDamageValue(prevDmg); // Restore to previous
                    currentDmg = prevDmg;
                }
            }
        }

        if (chosen == ClassType.ADVENTURER && mainHand.isDamageableItem()) {
            String itemName = mainHand.getItem().toString();
            if ((itemName.contains("sword") || itemName.contains("bow") || itemName.contains("crossbow"))
                    && currentDmg > prevDmg) {
                if (player.level().random.nextFloat() < 0.33f) {
                    mainHand.setDamageValue(prevDmg);
                    currentDmg = prevDmg;
                }
            }
        }

        prevMainHandDamage.put(pid, currentDmg);
        prevMainHandItem.put(pid, currentItem);

        // Smith: armor durability — 50% chance to negate durability loss
        if (chosen == ClassType.SMITH) {
            int[] prev = prevArmorDamage.get(pid);
            net.minecraft.world.item.Item[] prevItems = prevArmorItems.get(pid);
            if (prev == null) prev = new int[]{-1, -1, -1, -1};
            if (prevItems == null) prevItems = new net.minecraft.world.item.Item[4];

            int idx = 0;
            for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                    net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                ItemStack armor = player.getItemBySlot(slot);
                int armorDmg = armor.isDamageableItem() ? armor.getDamageValue() : 0;
                net.minecraft.world.item.Item armorItem = armor.getItem();

                // Reset tracking when item in slot changes (equip/swap)
                if (prevItems[idx] != armorItem) {
                    prev[idx] = armorDmg;
                    prevItems[idx] = armorItem;
                } else if (prev[idx] == -1) {
                    prev[idx] = armorDmg;
                } else if (armor.isDamageableItem() && armorDmg > prev[idx]) {
                    // Actual damage taken — 50% chance to negate
                    if (player.level().random.nextFloat() < 0.50f) {
                        armor.setDamageValue(prev[idx]);
                        armorDmg = prev[idx];
                    }
                }
                prev[idx] = armorDmg;
                idx++;
            }
            prevArmorDamage.put(pid, prev);
            prevArmorItems.put(pid, prevItems);
        }

        // Soul Fracture + armor wear debuffs: run once per second to keep overhead low.
        if (player.tickCount % 20 == 0) {
            syncSoulFractureModifier(player, data);
            syncArmorWearDebuffs(player);
        }
    }

    // =========================================================================
    // Soul Fracture attribute modifier — reads ClassData.soulFractureStacks and
    // applies a −1 max HP per stack (min 4 HP total floor) to the player. Cleared
    // only when the stack count changes, so this is idempotent per second.
    // =========================================================================

    private static final net.minecraft.resources.Identifier SOUL_FRACTURE_HP_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:soul_fracture_hp");

    private void syncSoulFractureModifier(ServerPlayer player, ClassData data) {
        int stacks = data.getSoulFractureStacks();
        var hpAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hpAttr == null) return;

        var existing = hpAttr.getModifier(SOUL_FRACTURE_HP_ID);
        double desired = -stacks; // negative: reduces max HP

        if (existing != null && existing.amount() == desired) return; // already correct
        if (existing != null) hpAttr.removeModifier(SOUL_FRACTURE_HP_ID);
        if (stacks > 0) {
            hpAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    SOUL_FRACTURE_HP_ID, desired,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            // Clamp current HP to new max so the player visibly loses hearts on application
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    // =========================================================================
    // Armor wear debuffs — diamond and netherite armor apply movement, attack
    // speed, and mining speed penalties. Stacks additively per piece.
    //   Diamond  (per piece): -0.5% move, -0.5% attack speed, -1% mining speed
    //   Netherite (per piece): -1%   move, -1%   attack speed, -2% mining speed
    // Full diamond ≈ -2%/-2%/-4%. Full netherite ≈ -4%/-4%/-8%. Netherite keeps
    // its vanilla fire/lava resistance as the upside tradeoff.
    // =========================================================================

    private static final net.minecraft.resources.Identifier WEAR_MOVE_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:wear_move");
    private static final net.minecraft.resources.Identifier WEAR_ATK_SPEED_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:wear_atk_speed");
    private static final net.minecraft.resources.Identifier WEAR_MINING_ID =
            net.minecraft.resources.Identifier.parse("mlkymc:wear_mining");

    private void syncArmorWearDebuffs(ServerPlayer player) {
        double moveReduction = 0.0;
        double atkSpeedReduction = 0.0;
        double miningReduction = 0.0;

        for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET}) {
            ItemStack piece = player.getItemBySlot(slot);
            if (piece.isEmpty()) continue;
            String id = piece.getItem().toString();
            if (id.contains("netherite_")) {
                // Per piece: −2.5% move, −2% attack speed, −5% mining speed.
                // Full set totals: −10% / −8% / −20%. Netherite keeps vanilla fire/lava
                // immunity as the upside tradeoff.
                moveReduction -= 0.025;
                atkSpeedReduction -= 0.02;
                miningReduction -= 0.05;
            } else if (id.contains("diamond_")) {
                // Per piece: −1% move, −1% attack speed, −2% mining speed.
                // Full set totals: −4% / −4% / −8%.
                moveReduction -= 0.01;
                atkSpeedReduction -= 0.01;
                miningReduction -= 0.02;
            }
        }

        applyWearModifier(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                WEAR_MOVE_ID, moveReduction);
        applyWearModifier(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                WEAR_ATK_SPEED_ID, atkSpeedReduction);
        applyWearModifier(player, net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED,
                WEAR_MINING_ID, miningReduction);
    }

    private void applyWearModifier(ServerPlayer player,
                                    net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                    net.minecraft.resources.Identifier id,
                                    double value) {
        var attr = player.getAttribute(attribute);
        if (attr == null) return;
        var existing = attr.getModifier(id);
        if (value == 0.0) {
            if (existing != null) attr.removeModifier(id);
            return;
        }
        if (existing != null && existing.amount() == value) return; // already correct
        if (existing != null) attr.removeModifier(id);
        attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                id, value,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
