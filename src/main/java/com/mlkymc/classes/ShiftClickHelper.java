package com.mlkymc.classes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reusable helper for merging freshly crafted items after shift-click.
 * Merges tagged slots into existing identical stacks (non-full), then with each other.
 */
public class ShiftClickHelper {

    /**
     * Schedule a 2-tick delayed task that finds untagged items matching the predicate,
     * applies the tagger, then merges the results into existing stacks.
     *
     * @param player      The player who crafted
     * @param targetItem  The item type to look for
     * @param isUntagged  Predicate that returns true if the item needs tagging (hasn't been tagged yet)
     * @param tagger      Consumer that applies tags/lore/NBT to the item
     */
    public static void scheduleTagAndMerge(ServerPlayer player, Item targetItem,
                                            Predicate<ItemStack> isUntagged,
                                            java.util.function.Consumer<ItemStack> tagger) {
        player.level().getServer().execute(() -> {
            player.level().getServer().execute(() -> {
                var taggedSlots = new ArrayList<Integer>();

                // Tag all matching untagged items in inventory
                for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
                    ItemStack slot = player.getInventory().getItem(s);
                    if (slot.isEmpty() || slot.getItem() != targetItem) continue;
                    if (!isUntagged.test(slot)) continue;
                    tagger.accept(slot);
                    taggedSlots.add(s);
                }

                // Check cursor
                ItemStack carried = player.containerMenu.getCarried();
                if (!carried.isEmpty() && carried.getItem() == targetItem && isUntagged.test(carried)) {
                    tagger.accept(carried);
                }

                // Merge freshly tagged slots into existing identical stacks, then with each other
                mergeTaggedSlots(player, taggedSlots);
            });
        });
    }

    /**
     * Merge freshly tagged slots into existing identical stacks (that aren't full),
     * then merge remaining tagged slots with each other.
     */
    public static void mergeTaggedSlots(ServerPlayer player, List<Integer> taggedSlots) {
        if (taggedSlots.isEmpty()) return;
        var inv = player.getInventory();
        var taggedSet = new HashSet<>(taggedSlots);

        // First: merge each tagged slot into existing non-tagged, non-full identical stacks
        for (int idx : taggedSlots) {
            ItemStack newStack = inv.getItem(idx);
            if (newStack.isEmpty()) continue;

            for (int s = 0; s < inv.getContainerSize(); s++) {
                if (taggedSet.contains(s)) continue;
                ItemStack existing = inv.getItem(s);
                if (existing.isEmpty()) continue;
                if (existing.getCount() >= existing.getMaxStackSize()) continue;
                if (!ItemStack.isSameItemSameComponents(newStack, existing)) continue;

                int space = existing.getMaxStackSize() - existing.getCount();
                int transfer = Math.min(space, newStack.getCount());
                if (transfer > 0) {
                    existing.grow(transfer);
                    newStack.shrink(transfer);
                    if (newStack.isEmpty()) {
                        inv.setItem(idx, ItemStack.EMPTY);
                        break;
                    }
                }
            }
        }

        // Second: merge remaining tagged slots with each other
        for (int i = 0; i < taggedSlots.size(); i++) {
            ItemStack stack = inv.getItem(taggedSlots.get(i));
            if (stack.isEmpty()) continue;
            if (stack.getCount() >= stack.getMaxStackSize()) continue;

            for (int j = i + 1; j < taggedSlots.size(); j++) {
                ItemStack other = inv.getItem(taggedSlots.get(j));
                if (other.isEmpty()) continue;
                if (!ItemStack.isSameItemSameComponents(stack, other)) continue;

                int space = stack.getMaxStackSize() - stack.getCount();
                int transfer = Math.min(space, other.getCount());
                if (transfer > 0) {
                    stack.grow(transfer);
                    other.shrink(transfer);
                    if (other.isEmpty()) inv.setItem(taggedSlots.get(j), ItemStack.EMPTY);
                }
                if (stack.getCount() >= stack.getMaxStackSize()) break;
            }
        }
    }
}
