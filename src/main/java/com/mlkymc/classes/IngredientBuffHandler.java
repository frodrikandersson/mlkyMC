package com.mlkymc.classes;

import com.mlkymc.classes.ItemBaseValues.AttributeBuff;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.*;

/**
 * Tags food items with attribute buffs when a Farmhand Lv30+ crafts/cooks them.
 * Tags potions with attribute buffs when a Cleric Lv20+ brews them.
 *
 * The buff data is stored in CUSTOM_DATA as:
 *   mlkymc_buffs: [{type: "HEALTH", percent: 10}, {type: "SPEED", percent: 10}]
 *   mlkymc_buff_duration: 900 (ticks)
 *
 * Buffs are sorted alphabetically by type for consistent stacking/display.
 */
public class IngredientBuffHandler {

    private final ClassManager classManager;

    public IngredientBuffHandler(ClassManager classManager) {
        this.classManager = classManager;
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ItemStack output = event.getCrafting();

        // Special handling for Sandwich — tag with the 3 food ingredients' buffs
        if (output.getItem() instanceof com.mlkymc.registry.SandwichItem) {
            ClassData sData = classManager.getOrCreate(player);
            if (sData.getChosenClass() != ClassType.FARMHAND) return;
            // Collect the 3 food ingredients (not bread, not milky star)
            var grid = event.getInventory();
            java.util.List<ItemStack> foodIngredients = new java.util.ArrayList<>();
            for (int i = 0; i < grid.getContainerSize(); i++) {
                ItemStack slot = grid.getItem(i);
                if (slot.isEmpty()) continue;
                if (slot.is(net.minecraft.world.item.Items.BREAD)) continue;
                if (slot.is(com.mlkymc.registry.ModItems.MILKY_STAR.get())) continue;
                if (slot.has(DataComponents.FOOD)) {
                    foodIngredients.add(slot.copy());
                }
            }
            if (!foodIngredients.isEmpty()) {
                com.mlkymc.registry.SandwichItem.tagSandwich(output, foodIngredients);
            }
            return;
        }

        // Only buff food items
        if (!output.has(DataComponents.FOOD)) return;

        // Check if crafter is Farmhand Lv30+
        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.FARMHAND) return;
        if (data.getLevel(ProfessionType.FARMHAND) < 30) return;

        // Collect attribute buffs from ingredients:
        // 1. Each ingredient's base AttributeBuff from ItemBaseValues
        // 2. Any existing mlkymc_buffs tag on the ingredient (from previous crafting steps)
        var grid = event.getInventory();
        Map<AttributeBuff, Integer> buffCounts = new LinkedHashMap<>();
        int buffedIngredientCount = 0;

        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack slot = grid.getItem(i);
            if (slot.isEmpty()) continue;

            // 1. Base buff from the item type itself
            AttributeBuff baseBuff = ItemBaseValues.getAttributeBuff(slot.getItem());
            if (baseBuff != AttributeBuff.NONE) {
                buffCounts.merge(baseBuff, 10, Integer::sum); // +10% per ingredient
                buffedIngredientCount++;
            }

            // 2. Inherited buffs from previous crafting steps (multi-step accumulation)
            if (hasBuff(slot)) {
                var inherited = readBuffs(slot);
                for (var entry : inherited.entrySet()) {
                    buffCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
                if (!inherited.isEmpty()) buffedIngredientCount++;
            }
        }

        if (buffCounts.isEmpty()) return;

        // Sort alphabetically for consistent stacking/display
        var sortedBuffs = new LinkedHashMap<AttributeBuff, Integer>();
        buffCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
                .forEach(e -> sortedBuffs.put(e.getKey(), e.getValue()));
        buffCounts = sortedBuffs;

        // Calculate duration: 30s base + 15s per buffed ingredient
        int durationTicks = (30 + 15 * buffedIngredientCount) * 20;

        // Store buff data in CUSTOM_DATA
        CompoundTag nbt = new CompoundTag();
        ListTag buffList = new ListTag();
        for (var entry : buffCounts.entrySet()) {
            CompoundTag buffTag = new CompoundTag();
            buffTag.putString("type", entry.getKey().name());
            buffTag.putInt("percent", entry.getValue()); // already accumulated (10% per base ingredient)
            buffList.add(buffTag);
        }
        nbt.put("mlkymc_buffs", buffList);
        nbt.putInt("mlkymc_buff_duration", durationTicks);

        // Merge with existing custom data if any
        CustomData existing = output.get(DataComponents.CUSTOM_DATA);
        if (existing != null) {
            CompoundTag merged = existing.copyTag();
            merged.put("mlkymc_buffs", buffList);
            merged.putInt("mlkymc_buff_duration", durationTicks);
            output.set(DataComponents.CUSTOM_DATA, CustomData.of(merged));
        } else {
            output.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        }

        // Add enchantment glint
        output.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        // Add tooltip lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Farmhand Enhanced").withColor(0x55FF55));
        for (var entry : buffCounts.entrySet()) {
            int pct = entry.getValue();
            lore.add(Component.literal("  +" + pct + "% " + formatBuffName(entry.getKey()))
                    .withColor(0xAAFFAA));
        }
        lore.add(Component.literal("  Duration: " + (durationTicks / 20) + "s").withColor(0xAAAAAA));
        output.set(DataComponents.LORE, new ItemLore(lore));
    }

    /**
     * Format buff enum name to display name: ATTACK_SPEED -> "Attack Speed"
     */
    public static String formatBuffName(AttributeBuff buff) {
        String name = buff.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
            if (c == ' ') capitalize = true;
        }
        return sb.toString();
    }

    /**
     * Check if an ItemStack has mlkymc buff data.
     */
    public static boolean hasBuff(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return false;
        return cd.copyTag().contains("mlkymc_buffs");
    }

    /**
     * Read buff entries from an item's CUSTOM_DATA.
     * Returns a map of AttributeBuff -> percent (e.g., HEALTH -> 20 means +20%)
     */
    public static Map<AttributeBuff, Integer> readBuffs(ItemStack stack) {
        Map<AttributeBuff, Integer> result = new LinkedHashMap<>();
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return result;
        CompoundTag nbt = cd.copyTag();
        if (!nbt.contains("mlkymc_buffs")) return result;

        var list = nbt.getListOrEmpty("mlkymc_buffs");
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof CompoundTag tag) {
                try {
                    AttributeBuff type = AttributeBuff.valueOf(tag.getStringOr("type", "NONE"));
                    int percent = tag.getIntOr("percent", 0);
                    if (type != AttributeBuff.NONE && percent > 0) {
                        result.put(type, percent);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }

    /**
     * Read buff duration in ticks from an item's CUSTOM_DATA.
     */
    public static int readDuration(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        return cd.copyTag().getIntOr("mlkymc_buff_duration", 0);
    }
}
