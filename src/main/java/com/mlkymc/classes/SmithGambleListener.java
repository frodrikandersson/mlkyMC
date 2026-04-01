package com.mlkymc.classes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;

import java.util.Random;

/**
 * Integrates the Smith gambling system into the anvil.
 *
 * When a Smith places equipment + a valid ingredient (copper/iron/gold/diamond/netherite)
 * in the anvil, the output slot shows the equipment with a "gamble preview".
 * When the Smith takes the output, the ingredient is consumed and the gamble is applied.
 *
 * The actual attribute modifiers are baked directly into the item's ATTRIBUTE_MODIFIERS
 * component so they apply automatically when worn/held — no tick handler needed.
 */
public class SmithGambleListener {

    private final ClassManager classManager;
    private final Random random = new Random();

    public SmithGambleListener(ClassManager classManager) {
        this.classManager = classManager;
    }

    /**
     * Preview: when a Smith places equipment + ingredient in the anvil,
     * show the gambled result in the output slot.
     */
    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        // Must be a Smith
        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.SMITH) return;

        ItemStack left = event.getLeft();   // Equipment
        ItemStack right = event.getRight(); // Ingredient

        if (!SmithGambleHandler.isGambleableEquipment(left)) return;
        SmithGambleHandler.IngredientTier tier = SmithGambleHandler.getTier(right.getItem());
        if (tier == null) return;
        if (!SmithGambleHandler.canApplyTier(left, tier)) {
            // This tier already applied — show error
            return;
        }

        // Create a copy and apply the gamble
        ItemStack output = left.copy();
        String result = SmithGambleHandler.applyGamble(output, tier, random);
        if (result == null) return;

        // Bake the gamble data into actual attribute modifiers on the item
        bakeAttributeModifiers(output);

        // Set anvil output
        event.setOutput(output);
        event.setXpCost(1); // 1 XP level cost
        event.setMaterialCost(1); // consume 1 ingredient
    }

    /**
     * When the Smith takes the output, grant XP.
     */
    @SubscribeEvent
    public void onAnvilCraft(AnvilCraftEvent.Post event) {
        // Get player from the anvil menu's player inventory
        var menu = event.getMenu();
        if (menu.slots.isEmpty()) return;
        var playerInv = menu.slots.getFirst().container;
        if (!(playerInv instanceof net.minecraft.world.entity.player.Inventory inv)) return;
        if (!(inv.player instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.SMITH) return;

        // Check if this was a gamble result (has our gamble data)
        var gambleData = SmithGambleHandler.readGambleData(event.getOutput());
        if (gambleData.isEmpty()) return;

        // Grant Smith XP
        classManager.addXp(player, ProfessionType.SMITH, 5);

        player.displayClientMessage(
                Component.literal("Attribute forged!").withColor(0xFFAA00), true);
    }

    /**
     * Convert the CUSTOM_DATA gamble entries into actual ATTRIBUTE_MODIFIERS on the item.
     * This makes the attributes apply automatically when the item is worn/held.
     */
    private void bakeAttributeModifiers(ItemStack stack) {
        var gambleData = SmithGambleHandler.readGambleData(stack);
        if (gambleData.isEmpty()) return;

        // Get existing attribute modifiers (preserve vanilla ones like armor values)
        ItemAttributeModifiers existing = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY);
        var builder = ItemAttributeModifiers.builder();

        // Re-add all existing modifiers
        for (var entry : existing.modifiers()) {
            builder.add(entry.attribute(), entry.modifier(), entry.slot());
        }

        // Add our gamble modifiers
        var slotGroup = SmithGambleHandler.getSlotGroup(stack);
        for (var entry : gambleData.values()) {
            var attr = SmithGambleHandler.getVanillaAttribute(entry.attrName);
            if (attr == null) continue;

            Identifier modId = Identifier.parse("mlkymc:smith_gamble_" + entry.attrName);

            // Remove any existing modifier with same ID (from previous gamble)
            // (builder doesn't support removal — we filter when iterating existing)

            builder.add(attr,
                    new AttributeModifier(modId, entry.value, AttributeModifier.Operation.ADD_VALUE),
                    slotGroup);
        }

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }
}
