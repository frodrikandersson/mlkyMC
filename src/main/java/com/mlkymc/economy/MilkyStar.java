package com.mlkymc.economy;

import com.mlkymc.MlkyMC;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public class MilkyStar {

    private static final String NBT_KEY = "mlkymc_milky_star";

    public static ItemStack create(int amount) {
        ItemStack stack = new ItemStack(Items.GOLD_NUGGET, amount);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Milky Star")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFD700)).withItalic(false)));
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        net.minecraft.nbt.CompoundTag tag = customData.copyTag();
        tag.putBoolean(NBT_KEY, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static boolean isMilkyStar(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.GOLD_NUGGET)) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBooleanOr(NBT_KEY, false);
    }

    public static int count(Player player) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isMilkyStar(stack)) {
                total += stack.getCount();
            }
            // Check shulker boxes
            total += countInShulker(stack);
        }
        return total;
    }

    public static boolean remove(Player player, int amount) {
        if (count(player) < amount) return false;

        Inventory inv = player.getInventory();
        int remaining = amount;

        // Remove from top-level inventory first
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (isMilkyStar(stack)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
        }

        // Remove from shulker boxes if needed
        if (remaining > 0) {
            for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
                remaining = removeFromShulker(inv.getItem(i), remaining);
            }
        }

        return remaining == 0;
    }

    private static int countInShulker(ItemStack stack) {
        if (!isShulkerBox(stack)) return 0;
        var container = stack.get(DataComponents.CONTAINER);
        if (container == null) return 0;
        int total = 0;
        for (ItemStack inner : container.nonEmptyItems()) {
            if (isMilkyStar(inner)) {
                total += inner.getCount();
            }
        }
        return total;
    }

    private static int removeFromShulker(ItemStack shulkerStack, int remaining) {
        if (!isShulkerBox(shulkerStack) || remaining <= 0) return remaining;
        var container = shulkerStack.get(DataComponents.CONTAINER);
        if (container == null) return remaining;

        var items = new java.util.ArrayList<ItemStack>();
        for (ItemStack inner : container.nonEmptyItems()) {
            items.add(inner.copy());
        }

        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack inner = items.get(i);
            if (isMilkyStar(inner)) {
                int take = Math.min(remaining, inner.getCount());
                inner.shrink(take);
                remaining -= take;
            }
        }

        // Rebuild container
        shulkerStack.set(DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
        return remaining;
    }

    private static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = stack.getItem().toString();
        return id.contains("shulker_box");
    }
}
