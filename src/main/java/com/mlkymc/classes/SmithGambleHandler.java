package com.mlkymc.classes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;

/**
 * Smith-exclusive attribute gambling system for anvils.
 *
 * Place armor/tool + ingredient (copper/iron/gold/diamond/netherite) in anvil.
 * Smith clicks the gamble button to apply a random attribute from the ingredient's pool.
 * Each ingredient tier can only be applied once per equipment piece.
 * Values are randomized with steep curve: 60% low, 25% medium, 12% good, 3% max.
 */
public class SmithGambleHandler {

    private static final String NBT_KEY = "mlkymc_smith_gamble";
    // =========================================================================
    // Ingredient tiers and attribute pools
    // =========================================================================

    public enum IngredientTier {
        COPPER, IRON, GOLD, DIAMOND, NETHERITE
    }

    public record AttributeRange(String attrName, double min, double max, boolean armorOnly, boolean toolOnly) {}

    private static final Map<IngredientTier, List<AttributeRange>> ATTRIBUTE_POOLS = new LinkedHashMap<>();

    static {
        // COPPER
        ATTRIBUTE_POOLS.put(IngredientTier.COPPER, List.of(
                new AttributeRange("safe_fall", 0.5, 3.0, true, false),
                new AttributeRange("step_height", 0.01, 1.0, true, false),
                new AttributeRange("luck", 0.5, 3.0, false, true),
                new AttributeRange("sneaking_speed", 0.01, 0.15, true, false)
        ));

        // IRON
        ATTRIBUTE_POOLS.put(IngredientTier.IRON, List.of(
                new AttributeRange("health", 0.5, 3.5, true, false),
                new AttributeRange("attack_speed", 0.1, 0.8, false, true),
                new AttributeRange("reach", 0.5, 2.5, false, true),
                new AttributeRange("burning_time", -0.5, -0.1, true, false)
        ));

        // GOLD
        ATTRIBUTE_POOLS.put(IngredientTier.GOLD, List.of(
                new AttributeRange("jump", 0.02, 0.15, true, false),
                new AttributeRange("entity_reach", 0.5, 2.0, false, true),
                new AttributeRange("mining_speed", 0.5, 3.0, false, true),
                new AttributeRange("speed", 0.01, 0.06, true, false)
        ));

        // DIAMOND (6 attributes — 5 armor, 1 tool, 1 shared)
        ATTRIBUTE_POOLS.put(IngredientTier.DIAMOND, List.of(
                new AttributeRange("damage", 0.5, 3.0, false, true),
                new AttributeRange("armor_toughness", 0.5, 3.0, true, false),
                new AttributeRange("armor", 0.5, 3.0, true, false),
                new AttributeRange("underwater_mining", 1.0, 5.0, false, true),
                new AttributeRange("oxygen", 0.2, 1.5, true, false),
                new AttributeRange("knockback_resistance", 0.05, 0.3, true, false)
        ));

        // NETHERITE — special: any attribute, handled in code
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Get the ingredient tier for an item, or null if not a valid gamble ingredient.
     */
    public static IngredientTier getTier(Item item) {
        if (item == Items.COPPER_INGOT) return IngredientTier.COPPER;
        if (item == Items.IRON_INGOT) return IngredientTier.IRON;
        if (item == Items.GOLD_INGOT) return IngredientTier.GOLD;
        if (item == Items.DIAMOND) return IngredientTier.DIAMOND;
        if (item == Items.NETHERITE_INGOT) return IngredientTier.NETHERITE;
        return null;
    }

    /**
     * Check if an equipment item can accept a gamble from the given tier.
     */
    public static boolean canApplyTier(ItemStack equipment, IngredientTier tier) {
        var data = readGambleData(equipment);
        if (tier == IngredientTier.NETHERITE) {
            // Netherite: can always apply (re-rolls if 4 attributes already exist)
            return true;
        }
        // Other tiers: can only apply if this tier hasn't been used yet
        return !data.containsKey(tier.name());
    }

    /**
     * Check if the item is armor or a tool/weapon (not shields).
     * Uses string-based detection since 1.21.11 restructured item classes.
     */
    public static boolean isGambleableEquipment(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return isArmor(stack) || isTool(stack);
    }

    public static boolean isArmor(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // Check Equippable component (1.21.11 way)
        if (stack.has(DataComponents.EQUIPPABLE)) return true;
        // Fallback: string detection
        String id = stack.getItem().getDescriptionId().toLowerCase();
        return id.contains("helmet") || id.contains("chestplate") || id.contains("leggings")
                || id.contains("boots") || id.contains("_cap") || id.contains("_tunic");
    }

    public static boolean isTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = stack.getItem().getDescriptionId().toLowerCase();
        return id.contains("sword") || id.contains("pickaxe") || id.contains("_axe")
                || id.contains("shovel") || id.contains("_hoe")
                || id.contains("bow") || id.contains("crossbow")
                || id.contains("trident") || id.contains("mace");
    }

    /**
     * Apply a random attribute gamble from the given tier to the equipment.
     * Returns a description of what was applied, or null if failed.
     */
    public static String applyGamble(ItemStack equipment, IngredientTier tier, Random random) {
        boolean isArmorPiece = isArmor(equipment);
        var existingData = readGambleData(equipment);

        if (tier == IngredientTier.NETHERITE) {
            return applyNetherite(equipment, existingData, random, isArmorPiece);
        }

        // Check if tier already applied
        if (existingData.containsKey(tier.name())) return null;

        // Get valid attributes for this equipment type
        var pool = ATTRIBUTE_POOLS.get(tier);
        if (pool == null) return null;

        List<AttributeRange> valid = new ArrayList<>();
        for (AttributeRange ar : pool) {
            if (isArmorPiece && ar.toolOnly()) continue;
            if (!isArmorPiece && ar.armorOnly()) continue;
            valid.add(ar);
        }
        if (valid.isEmpty()) return null;

        // Pick random attribute
        AttributeRange chosen = valid.get(random.nextInt(valid.size()));

        // Roll value with steep curve
        double value = rollSteepCurve(chosen.min(), chosen.max(), random);

        // Store in custom data
        existingData.put(tier.name(), new GambleEntry(chosen.attrName(), value));
        writeGambleData(equipment, existingData);
        updateLore(equipment, existingData);

        return formatAttribute(chosen.attrName(), value);
    }

    // =========================================================================
    // Netherite special logic
    // =========================================================================

    private static String applyNetherite(ItemStack equipment, Map<String, GambleEntry> existingData,
                                         Random random, boolean isArmorPiece) {
        int appliedCount = existingData.size();

        if (appliedCount >= 4) {
            // Already has 4 attributes — re-roll ALL values (not attributes)
            for (var entry : existingData.values()) {
                var pool = findRangeForAttr(entry.attrName);
                if (pool != null) {
                    entry.value = rollSteepCurve(pool.min(), pool.max(), random);
                }
            }
            writeGambleData(equipment, existingData);
            updateLore(equipment, existingData);
            return "Re-rolled all attribute values!";
        }

        // Determine which tier pools are still available
        Set<IngredientTier> usedTiers = new HashSet<>();
        for (String key : existingData.keySet()) {
            try { usedTiers.add(IngredientTier.valueOf(key)); } catch (Exception ignored) {}
        }

        // Collect all valid attributes from unused tier pools
        List<AttributeRange> candidates = new ArrayList<>();
        for (var poolEntry : ATTRIBUTE_POOLS.entrySet()) {
            if (usedTiers.contains(poolEntry.getKey())) continue;
            for (AttributeRange ar : poolEntry.getValue()) {
                if (isArmorPiece && ar.toolOnly()) continue;
                if (!isArmorPiece && ar.armorOnly()) continue;
                candidates.add(ar);
            }
        }

        if (candidates.isEmpty()) {
            // All pools used — just re-roll
            for (var entry : existingData.values()) {
                var pool = findRangeForAttr(entry.attrName);
                if (pool != null) {
                    entry.value = rollSteepCurve(pool.min(), pool.max(), random);
                }
            }
            writeGambleData(equipment, existingData);
            updateLore(equipment, existingData);
            return "Re-rolled all attribute values!";
        }

        // Pick random from candidates
        AttributeRange chosen = candidates.get(random.nextInt(candidates.size()));
        double value = rollSteepCurve(chosen.min(), chosen.max(), random);

        // Determine which tier this attribute belongs to
        String tierKey = "NETHERITE_" + appliedCount;
        for (var poolEntry : ATTRIBUTE_POOLS.entrySet()) {
            if (poolEntry.getValue().contains(chosen)) {
                tierKey = poolEntry.getKey().name();
                break;
            }
        }

        existingData.put(tierKey, new GambleEntry(chosen.attrName(), value));

        // Re-roll ALL existing values when netherite is applied
        for (var entry : existingData.values()) {
            var pool = findRangeForAttr(entry.attrName);
            if (pool != null) {
                entry.value = rollSteepCurve(pool.min(), pool.max(), random);
            }
        }

        writeGambleData(equipment, existingData);
        updateLore(equipment, existingData);
        return "Applied " + formatAttribute(chosen.attrName(), value) + " (all values re-rolled!)";
    }

    // =========================================================================
    // Probability curve
    // =========================================================================

    /**
     * Roll a value with steep curve: 60% low, 25% medium, 12% good, 3% max.
     */
    private static double rollSteepCurve(double min, double max, Random random) {
        double range = max - min;
        double roll = random.nextDouble();
        double t;

        if (roll < 0.60) {
            // Low tier: 0-30% of range
            t = random.nextDouble() * 0.30;
        } else if (roll < 0.85) {
            // Medium tier: 30-60% of range
            t = 0.30 + random.nextDouble() * 0.30;
        } else if (roll < 0.97) {
            // Good tier: 60-85% of range
            t = 0.60 + random.nextDouble() * 0.25;
        } else {
            // Max tier: 85-100% of range
            t = 0.85 + random.nextDouble() * 0.15;
        }

        double value = min + range * t;
        // Round to 2 decimal places
        return Math.round(value * 100.0) / 100.0;
    }

    // =========================================================================
    // Data storage (CUSTOM_DATA NBT)
    // =========================================================================

    public static class GambleEntry {
        public String attrName;
        public double value;

        public GambleEntry(String attrName, double value) {
            this.attrName = attrName;
            this.value = value;
        }
    }

    public static Map<String, GambleEntry> readGambleData(ItemStack stack) {
        Map<String, GambleEntry> result = new LinkedHashMap<>();
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return result;
        CompoundTag nbt = cd.copyTag();
        if (!nbt.contains(NBT_KEY)) return result;

        CompoundTag gambleTag = nbt.getCompoundOrEmpty(NBT_KEY);
        for (String key : gambleTag.keySet()) {
            CompoundTag entry = gambleTag.getCompoundOrEmpty(key);
            String attr = entry.getStringOr("attr", "");
            double val = entry.getDoubleOr("value", 0.0);
            if (!attr.isEmpty()) {
                result.put(key, new GambleEntry(attr, val));
            }
        }
        return result;
    }

    private static void writeGambleData(ItemStack stack, Map<String, GambleEntry> data) {
        CompoundTag gambleTag = new CompoundTag();
        for (var entry : data.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("attr", entry.getValue().attrName);
            entryTag.putDouble("value", entry.getValue().value);
            gambleTag.put(entry.getKey(), entryTag);
        }

        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag nbt = existing != null ? existing.copyTag() : new CompoundTag();
        nbt.put(NBT_KEY, gambleTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }

    private static void updateLore(ItemStack equipment, Map<String, GambleEntry> data) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Smith Forged:").withColor(0xFFAA00));
        for (var entry : data.values()) {
            String display = formatAttribute(entry.attrName, entry.value);
            lore.add(Component.literal("  " + display).withColor(0xFFDD55));
        }

        // Merge with existing non-gamble lore
        ItemLore existingLore = equipment.get(DataComponents.LORE);
        List<Component> finalLore = new ArrayList<>(lore);
        if (existingLore != null) {
            for (Component line : existingLore.lines()) {
                String text = line.getString();
                if (!text.startsWith("Smith Forged") && !text.startsWith("  +") && !text.startsWith("  -")) {
                    finalLore.add(line);
                }
            }
        }
        equipment.set(DataComponents.LORE, new ItemLore(finalLore));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static AttributeRange findRangeForAttr(String attrName) {
        for (var pool : ATTRIBUTE_POOLS.values()) {
            for (AttributeRange ar : pool) {
                if (ar.attrName().equals(attrName)) return ar;
            }
        }
        return null;
    }

    static String formatAttribute(String attrName, double value) {
        String name = attrName.replace('_', ' ');
        name = name.substring(0, 1).toUpperCase() + name.substring(1);
        // Format with sign
        String sign = value >= 0 ? "+" : "";
        return sign + String.format("%.2f", value) + " " + name;
    }

    /**
     * Map attribute name to vanilla Attribute holder for applying modifiers.
     */
    public static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> getVanillaAttribute(String attrName) {
        return switch (attrName) {
            case "health" -> Attributes.MAX_HEALTH;
            case "damage" -> Attributes.ATTACK_DAMAGE;
            case "attack_speed" -> Attributes.ATTACK_SPEED;
            case "armor" -> Attributes.ARMOR;
            case "armor_toughness" -> Attributes.ARMOR_TOUGHNESS;
            case "speed" -> Attributes.MOVEMENT_SPEED;
            case "jump" -> Attributes.JUMP_STRENGTH;
            case "mining_speed" -> Attributes.BLOCK_BREAK_SPEED;
            case "reach" -> Attributes.BLOCK_INTERACTION_RANGE;
            case "entity_reach" -> Attributes.ENTITY_INTERACTION_RANGE;
            case "luck" -> Attributes.LUCK;
            case "knockback_resistance" -> Attributes.KNOCKBACK_RESISTANCE;
            case "oxygen" -> Attributes.OXYGEN_BONUS;
            case "safe_fall" -> Attributes.SAFE_FALL_DISTANCE;
            case "burning_time" -> Attributes.BURNING_TIME;
            case "step_height" -> Attributes.STEP_HEIGHT;
            case "underwater_mining" -> Attributes.SUBMERGED_MINING_SPEED;
            case "sneaking_speed" -> Attributes.SNEAKING_SPEED;
            default -> null;
        };
    }

    /**
     * Get the appropriate EquipmentSlot group for the modifier.
     * Armor attributes apply in the armor slot, tool attributes in mainhand.
     */
    public static net.minecraft.world.entity.EquipmentSlotGroup getSlotGroup(ItemStack stack) {
        if (isArmor(stack)) {
            String id = stack.getItem().getDescriptionId().toLowerCase();
            if (id.contains("helmet") || id.contains("_cap")) return net.minecraft.world.entity.EquipmentSlotGroup.HEAD;
            if (id.contains("chestplate") || id.contains("_tunic")) return net.minecraft.world.entity.EquipmentSlotGroup.CHEST;
            if (id.contains("leggings")) return net.minecraft.world.entity.EquipmentSlotGroup.LEGS;
            if (id.contains("boots")) return net.minecraft.world.entity.EquipmentSlotGroup.FEET;
            return net.minecraft.world.entity.EquipmentSlotGroup.ARMOR;
        }
        return net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND;
    }
}
