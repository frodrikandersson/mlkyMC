package com.mlkymc.classes;

import com.mlkymc.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
    // ADVENTURER: 2x hostile mob drops
    // =========================================================================

    @SubscribeEvent
    public void onMobDrop(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        var source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Monster)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.ADVENTURER) return;

        // Double all drops from hostile mobs
        for (var drop : event.getDrops()) {
            ItemStack item = drop.getItem();
            item.setCount(item.getCount() * 2);
        }
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

    @SubscribeEvent
    public void onBlockDrop(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.MINECRAFTER) return;

        String blockId = event.getState().getBlock().getDescriptionId();

        // Fortune +1: increase ore drops by ~33%
        if (blockId.contains("ore") || blockId.contains("ancient_debris")) {
            for (var drop : event.getDrops()) {
                ItemStack item = drop.getItem();
                // ~33% chance to add 1 extra
                if (player.level().random.nextFloat() < 0.33f) {
                    item.grow(1);
                }
            }
        }
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
    private final java.util.Map<java.util.UUID, int[]> prevArmorDamage = new java.util.HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        ClassType chosen = data.getChosenClass();
        java.util.UUID pid = player.getUUID();

        // Track mainhand damage
        ItemStack mainHand = player.getMainHandItem();
        int currentDmg = mainHand.isDamageableItem() ? mainHand.getDamageValue() : 0;
        Integer prevDmg = prevMainHandDamage.getOrDefault(pid, 0);

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
            if (mainHand.getItem().toString().contains("sword") && currentDmg > prevDmg) {
                if (player.level().random.nextFloat() < 0.33f) {
                    mainHand.setDamageValue(prevDmg);
                    currentDmg = prevDmg;
                }
            }
        }

        prevMainHandDamage.put(pid, currentDmg);

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
                    if (player.level().random.nextFloat() < 0.25f) {
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
