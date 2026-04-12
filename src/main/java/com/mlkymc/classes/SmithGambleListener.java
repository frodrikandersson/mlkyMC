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

        // Check if this tier can be applied (new attribute) or rerolled (same tier, different value)
        var existingData = SmithGambleHandler.readGambleData(left);
        boolean isReroll = existingData.containsKey(tier.name()) && tier != SmithGambleHandler.IngredientTier.NETHERITE;
        boolean isNewTier = !existingData.containsKey(tier.name());
        boolean isNetherite = tier == SmithGambleHandler.IngredientTier.NETHERITE;

        if (!isReroll && !isNewTier && !isNetherite) return;

        // Pre-roll the gamble result and store it in CUSTOM_DATA,
        // but show ??? in lore until taken from the anvil
        ItemStack output = left.copy();

        // Apply the gamble NOW (result stored in custom data)
        if (isReroll) {
            var rerollData = SmithGambleHandler.readGambleData(output);
            var entry = rerollData.get(tier.name());
            if (entry != null) {
                var range = SmithGambleHandler.findRangeForAttrPublic(entry.attrName);
                if (range != null) {
                    entry.value = SmithGambleHandler.rollSteepCurvePublic(range.min(), range.max(), random);
                    SmithGambleHandler.writeGambleDataPublic(output, rerollData);
                }
            }
        } else {
            SmithGambleHandler.applyGamble(output, tier, random);
        }

        // Bake attribute modifiers so they show in "When on Feet/etc"
        bakeAttributeModifiers(output);

        // Show ??? in lore (hide the actual result until taken)
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.literal("Smith Forged:").withColor(0xFFAA00));
        var allData = SmithGambleHandler.readGambleData(output);
        for (var mapEntry : allData.entrySet()) {
            String tierKey = mapEntry.getKey();
            var entry = mapEntry.getValue();
            // Show real values for previously applied tiers, ??? for the new/rerolled one
            if (tierKey.equals(tier.name())) {
                if (isReroll) {
                    lore.add(Component.literal("  ??? ?????? (reroll)").withColor(0xFF5555));
                } else {
                    lore.add(Component.literal("  ??? ??????").withColor(0xAAAAFF));
                }
            } else {
                lore.add(Component.literal("  " + SmithGambleHandler.formatAttribute(entry.attrName, entry.value))
                        .withColor(0xFFDD55));
            }
        }
        output.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));

        // Mark as needing lore reveal
        net.minecraft.nbt.CompoundTag nbt = output.getOrDefault(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        nbt.putBoolean("mlkymc_pending_reveal", true);
        output.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(nbt));

        event.setOutput(output);
        event.setXpCost(isReroll ? 2 : 1);
        event.setMaterialCost(1);
    }

    /**
     * When the Smith takes the output, grant XP and show result.
     * The gamble was already applied in AnvilUpdateEvent — we just need to reveal the lore.
     */
    @SubscribeEvent
    public void onAnvilCraft(AnvilCraftEvent.Post event) {
        var menu = event.getMenu();
        if (menu.slots.isEmpty()) return;
        var playerInv = menu.slots.getFirst().container;
        if (!(playerInv instanceof net.minecraft.world.entity.player.Inventory inv)) return;
        if (!(inv.player instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.SMITH) return;

        // Check if this was a gamble craft (output copy has the reveal tag)
        ItemStack outputCopy = event.getOutput();
        var cd = outputCopy.get(DataComponents.CUSTOM_DATA);
        if (cd == null || !cd.copyTag().getBooleanOr("mlkymc_pending_reveal", false)) return;

        // Grant Smith XP
        classManager.addXp(player, ProfessionType.SMITH, 5, "smith gamble");

        // Show result on action bar
        var gambleData = SmithGambleHandler.readGambleData(outputCopy);
        StringBuilder msg = new StringBuilder("Forged: ");
        for (var e : gambleData.values()) {
            msg.append(SmithGambleHandler.formatAttribute(e.attrName, e.value)).append(" ");
        }
        player.displayClientMessage(
                Component.literal(msg.toString().trim()).withColor(0xFFAA00), true);
    }

    /**
     * Tick: reveal ??? lore on gambled items in player inventories.
     * Called from server tick. Finds items with mlkymc_pending_reveal and updates their lore.
     */
    public void tickRevealGambles(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Check cursor
            ItemStack carried = player.containerMenu.getCarried();
            if (needsReveal(carried)) {
                revealItem(carried);
            }
            // Check inventory
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack slot = player.getInventory().getItem(i);
                if (needsReveal(slot)) {
                    revealItem(slot);
                }
            }
        }
    }

    private boolean needsReveal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return false;
        return cd.copyTag().getBooleanOr("mlkymc_pending_reveal", false);
    }

    private void revealItem(ItemStack stack) {
        // Update lore with real values
        var gambleData = SmithGambleHandler.readGambleData(stack);
        SmithGambleHandler.updateLorePublic(stack, gambleData);

        // Remove the pending reveal flag
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            var nbt = cd.copyTag();
            nbt.remove("mlkymc_pending_reveal");
            stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(nbt));
        }
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

        // Re-add existing modifiers EXCEPT our old gamble modifiers
        for (var entry : existing.modifiers()) {
            String modId = entry.modifier().id().toString();
            if (modId.startsWith("mlkymc:smith_gamble_")) continue; // skip old gamble modifiers
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

        // If this item has a Fletcher modifier baked in, re-apply it on top of the
        // fresh gamble values. The Fletcher works by multiplying the gamble base
        // values and writing them back with the same "mlkymc:smith_gamble_*" IDs,
        // so the re-bake above just overwrote the boosted values with unboosted ones.
        // Re-applying the Fletcher modifier restores the boost.
        if (com.mlkymc.classes.FletcherModifierHandler.hasModifier(stack)) {
            // Read the stored fletcher modifier percentage from CUSTOM_DATA
            var cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd != null) {
                double fletcherMod = cd.copyTag().getDoubleOr("mlkymc_fletcher_modifier", 0);
                if (fletcherMod > 0) {
                    // Re-read the just-baked gamble data and re-boost
                    var freshGamble = SmithGambleHandler.readGambleData(stack);
                    ItemAttributeModifiers baked = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                            ItemAttributeModifiers.EMPTY);
                    var reBuilder = ItemAttributeModifiers.builder();
                    for (var modEntry : baked.modifiers()) {
                        String id = modEntry.modifier().id().toString();
                        if (id.startsWith("mlkymc:smith_gamble_")) continue;
                        reBuilder.add(modEntry.attribute(), modEntry.modifier(), modEntry.slot());
                    }
                    var sg = SmithGambleHandler.getSlotGroup(stack);
                    for (var gEntry : freshGamble.values()) {
                        var attr = SmithGambleHandler.getVanillaAttribute(gEntry.attrName);
                        if (attr == null) continue;
                        double boosted = gEntry.value * (1.0 + fletcherMod);
                        reBuilder.add(attr,
                                new AttributeModifier(
                                        Identifier.parse("mlkymc:smith_gamble_" + gEntry.attrName),
                                        boosted, AttributeModifier.Operation.ADD_VALUE),
                                sg);
                    }
                    stack.set(DataComponents.ATTRIBUTE_MODIFIERS, reBuilder.build());
                }
            }
        }
    }
}
