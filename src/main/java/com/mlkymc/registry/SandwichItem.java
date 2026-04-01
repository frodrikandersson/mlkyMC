package com.mlkymc.registry;

import com.mlkymc.classes.IngredientBuffHandler;
import com.mlkymc.classes.ItemBaseValues;
import com.mlkymc.classes.ItemBaseValues.AttributeBuff;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;

/**
 * Farmhand-exclusive Sandwich item with dynamic ingredient buffs.
 *
 * Recipe: Shapeless — 1 Bread + 1 Milky Star + 3 cooked food ingredients
 * Buffs are the SUM of the 3 food ingredients' AttributeBuffs.
 * Buffs are sorted alphabetically for consistent stacking.
 * Duration: 30s base + 15s per buffed ingredient.
 *
 * The sandwich gets a hue tint based on its buff combination via ItemColor.
 */
public class SandwichItem extends Item {

    public SandwichItem(Properties properties) {
        super(properties);
    }

    /**
     * Tag a freshly crafted sandwich with buff data from its 3 food ingredients.
     * Called from IngredientBuffHandler or a custom craft handler.
     */
    public static void tagSandwich(ItemStack sandwich, List<ItemStack> foodIngredients) {
        Map<AttributeBuff, Integer> buffCounts = new TreeMap<>(Comparator.comparing(Enum::name));
        int buffedCount = 0;

        for (ItemStack food : foodIngredients) {
            // Base buff from the ingredient type
            AttributeBuff baseBuff = ItemBaseValues.getAttributeBuff(food.getItem());
            if (baseBuff != AttributeBuff.NONE) {
                buffCounts.merge(baseBuff, 10, Integer::sum);
                buffedCount++;
            }

            // Inherited buffs from previous crafting steps
            if (IngredientBuffHandler.hasBuff(food)) {
                var inherited = IngredientBuffHandler.readBuffs(food);
                for (var entry : inherited.entrySet()) {
                    buffCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
                if (!inherited.isEmpty()) buffedCount++;
            }
        }

        // Duration: 30s base + 15s per buffed ingredient
        int durationTicks = (30 + 15 * Math.max(buffedCount, foodIngredients.size())) * 20;

        // Store buff data
        CompoundTag nbt = new CompoundTag();
        ListTag buffList = new ListTag();
        for (var entry : buffCounts.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", entry.getKey().name());
            tag.putInt("percent", entry.getValue());
            buffList.add(tag);
        }
        nbt.put("mlkymc_buffs", buffList);
        nbt.putInt("mlkymc_buff_duration", durationTicks);

        // Compute a color tint from the buff combination
        int tintColor = computeTintColor(buffCounts);
        nbt.putInt("mlkymc_sandwich_tint", tintColor);

        sandwich.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        // Enchantment glint
        sandwich.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Farmhand Sandwich").withColor(0x55FF55));
        for (var entry : buffCounts.entrySet()) {
            lore.add(Component.literal("  +" + entry.getValue() + "% " +
                    IngredientBuffHandler.formatBuffName(entry.getKey())).withColor(0xAAFFAA));
        }
        lore.add(Component.literal("  Duration: " + (durationTicks / 20) + "s").withColor(0xAAAAAA));
        sandwich.set(DataComponents.LORE, new ItemLore(lore));

        // Set tint color via CustomModelData (used by items/sandwich.json tint source)
        sandwich.set(DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(
                        List.of(), List.of(), List.of(), List.of(tintColor)));
    }

    /**
     * Compute a deterministic tint color from the buff combination.
     * Each buff type maps to a hue, and we average them.
     */
    private static int computeTintColor(Map<AttributeBuff, Integer> buffs) {
        if (buffs.isEmpty()) return 0xFFFFFF;

        float totalHue = 0;
        float totalWeight = 0;

        for (var entry : buffs.entrySet()) {
            float hue = getHueForBuff(entry.getKey());
            float weight = entry.getValue();
            totalHue += hue * weight;
            totalWeight += weight;
        }

        float avgHue = totalHue / totalWeight;
        // Convert HSB to RGB with saturation 0.6 and brightness 0.9
        int rgb = java.awt.Color.HSBtoRGB(avgHue / 360f, 0.6f, 0.9f);
        return rgb & 0x00FFFFFF; // strip alpha
    }

    private static float getHueForBuff(AttributeBuff buff) {
        return switch (buff) {
            case HEALTH -> 0;           // Red
            case DAMAGE -> 20;          // Orange-red
            case ATTACK_SPEED -> 40;    // Orange
            case ARMOR -> 210;          // Blue
            case ARMOR_TOUGHNESS -> 240;// Dark blue
            case SPEED -> 170;          // Cyan
            case JUMP -> 140;           // Teal
            case MINING_SPEED -> 50;    // Gold
            case REACH -> 280;          // Purple
            case ENTITY_REACH -> 300;   // Magenta
            case LUCK -> 120;           // Green
            case KNOCKBACK_RESISTANCE -> 230; // Steel blue
            case OXYGEN -> 190;         // Sea blue
            case SAFE_FALL -> 60;       // Yellow
            case FALL_DAMAGE -> 70;     // Light yellow
            case STEP_HEIGHT -> 90;     // Yellow-green
            case BURNING_TIME -> 10;    // Deep red
            case UNDERWATER_MINING -> 180; // Aqua
            case SNEAKING_SPEED -> 160; // Dark cyan
            default -> 0;
        };
    }

    /**
     * Get the tint color for rendering (called by ItemColor on client).
     */
    public static int getTintColor(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0xFFFFFF;
        return cd.copyTag().getIntOr("mlkymc_sandwich_tint", 0xFFFFFF);
    }
}
