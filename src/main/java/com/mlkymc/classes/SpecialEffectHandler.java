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

    @SubscribeEvent
    public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 5 != 0) return; // Every 0.25s

        ClassData data = classManager.getOrCreate(player);
        ClassType chosen = data.getChosenClass();

        if (chosen == ClassType.MINECRAFTER) {
            // 50% chance to negate durability loss on pickaxe/axe (effective 2x durability)
            ItemStack mainHand = player.getMainHandItem();
            String itemName = mainHand.getItem().toString();
            if (mainHand.isDamageableItem() && (itemName.contains("pickaxe") || itemName.contains("axe"))
                    && mainHand.getDamageValue() > 0 && player.level().random.nextFloat() < 0.5f) {
                mainHand.setDamageValue(mainHand.getDamageValue() - 1);
            }
        }

        if (chosen == ClassType.ADVENTURER) {
            // 33% chance to negate durability loss on swords
            ItemStack mainHand = player.getMainHandItem();
            if (mainHand.isDamageableItem() && mainHand.getItem().toString().contains("sword")
                    && mainHand.getDamageValue() > 0 && player.level().random.nextFloat() < 0.33f) {
                mainHand.setDamageValue(mainHand.getDamageValue() - 1);
            }
        }

        if (chosen == ClassType.SMITH) {
            // 25% chance to negate durability loss on armor
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) {
                    ItemStack armor = player.getItemBySlot(slot);
                    if (armor.isDamageableItem() && armor.getDamageValue() > 0
                            && player.level().random.nextFloat() < 0.25f) {
                        armor.setDamageValue(armor.getDamageValue() - 1);
                    }
                }
            }
        }
    }
}
