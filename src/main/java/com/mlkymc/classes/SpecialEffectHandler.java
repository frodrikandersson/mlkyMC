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
        double bonusPercent = getAdventurerDropBonus(level);
        if (bonusPercent <= 0) return;

        // Apply bonus: each drop item has bonusPercent chance to be doubled
        var random = player.level().random;
        for (var drop : event.getDrops()) {
            ItemStack item = drop.getItem();
            if (random.nextFloat() < bonusPercent) {
                item.setCount(item.getCount() * 2);
            }
        }
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

        // Clean up placed ore tracking for ANY player breaking an ore
        boolean isOre = blockId.contains("ore") || blockId.contains("ancient_debris");
        boolean isPlayerPlacedOre = false;
        if (isOre && player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            var tracker = com.mlkymc.world.PlacedOreTracker.get(sl.getServer());
            String dim = sl.dimension().identifier().toString();
            if (tracker.isPlayerPlaced(dim, event.getPos())) {
                isPlayerPlacedOre = true;
                tracker.markBroken(dim, event.getPos());
            }
        }

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.MINECRAFTER) return;

        int level = data.getLevel(ProfessionType.MINECRAFTER);
        boolean isMiningBlock = isOre
                || blockId.contains("stone") || blockId.contains("deepslate")
                || blockId.contains("log") || blockId.contains("wood");

        if (isOre && !isPlayerPlacedOre) {
            int fortuneBonus = 1; // Base +1 for MineCrafter class
            if (level >= 20) fortuneBonus = 2; // Lv20 exclusive: +1 more
            if (level >= 40) fortuneBonus = 3; // Lv40 exclusive: +1 more
            for (var drop : event.getDrops()) {
                ItemStack item = drop.getItem();
                for (int f = 0; f < fortuneBonus; f++) {
                    if (player.level().random.nextFloat() < 0.33f) {
                        item.grow(1);
                    }
                }
            }
        }

        // Exclusive drop bonus: +20/40/60/80/100% at Lv5/15/25/35/45
        // Skip on player-placed ores
        if (isMiningBlock && !(isOre && isPlayerPlacedOre)) {
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
    }

    private static double getMinecrafterDropBonus(int level) {
        if (level >= 45) return 1.0;
        if (level >= 35) return 0.8;
        if (level >= 25) return 0.6;
        if (level >= 15) return 0.4;
        if (level >= 5)  return 0.2;
        return 0.0;
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
                || blockId.contains("sweet_berr");
        if (!isFarmBlock) return;

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

    @SubscribeEvent
    public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        ClassType chosen = data.getChosenClass();
        java.util.UUID pid = player.getUUID();

        // Tempered Mind: negate ALL durability loss while active
        // Can apply to non-Smiths via Lv30 AoE sharing
        if (PowerHandler.isTemperedMindActive(pid)) {
            // Restore all equipment to previous damage values
            ItemStack mh = player.getMainHandItem();
            if (mh.isDamageableItem()) {
                int prev = prevMainHandDamage.containsKey(pid)
                        ? prevMainHandDamage.get(pid)
                        : mh.getDamageValue(); // Initialize to current, not 0
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
            // Still update tracking values for when Tempered Mind expires
            prevMainHandDamage.put(pid, mh.isDamageableItem() ? mh.getDamageValue() : 0);
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

        // Smith: armor durability
        if (chosen == ClassType.SMITH) {
            int[] prev = prevArmorDamage.computeIfAbsent(pid, k -> new int[4]);
            int idx = 0;
            for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                    net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                ItemStack armor = player.getItemBySlot(slot);
                int armorDmg = armor.isDamageableItem() ? armor.getDamageValue() : 0;
                if (armor.isDamageableItem() && armorDmg > prev[idx]) {
                    if (player.level().random.nextFloat() < 0.50f) { // Half durability damage
                        armor.setDamageValue(prev[idx]);
                        armorDmg = prev[idx];
                    }
                }
                prev[idx] = armorDmg;
                idx++;
            }
        }
    }
}
