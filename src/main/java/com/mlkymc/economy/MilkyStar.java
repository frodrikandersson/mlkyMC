package com.mlkymc.economy;

import com.mlkymc.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Utility class for creating and managing Milky Star currency items.
 * Now uses properly registered ModItems instead of NBT-tagged vanilla items.
 */
public class MilkyStar {

    // Legacy NBT keys (kept for backward compatibility with existing wallets/items)
    private static final String NBT_KEY = "mlkymc_milky_star";
    private static final String WALLET_KEY = "mlkymc_wallet";
    private static final String WALLET_BALANCE_KEY = "mlkymc_wallet_balance";
    private static final String WALLET_OWNER_KEY = "mlkymc_wallet_owner";
    private static final String STALL_DEED_KEY = "mlkymc_stall_deed";
    private static final String DIMENSION_COMPASS_KEY = "mlkymc_dimension_compass";
    private static final String MARKET_CATALOG_KEY = "mlkymc_market_catalog";
    private static final String MARKET_BOOK_KEY = "mlkymc_market_book";

    // --- Milky Star ---

    public static ItemStack create(int amount) {
        return new ItemStack(ModItems.MILKY_STAR.get(), Math.min(amount, 64));
    }

    public static java.util.List<ItemStack> createAll(int amount) {
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        while (amount > 0) {
            int batch = Math.min(amount, 64);
            stacks.add(create(batch));
            amount -= batch;
        }
        return stacks;
    }

    public static boolean isMilkyStar(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // Check new registered item
        if (stack.is(ModItems.MILKY_STAR.get())) return true;
        // Legacy: NBT-tagged nether star
        if (stack.is(Items.NETHER_STAR)) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) return customData.copyTag().getBooleanOr(NBT_KEY, false);
        }
        return false;
    }

    public static int count(Player player) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isMilkyStar(stack)) {
                total += stack.getCount();
            } else if (isJar(stack)) {
                total += getJarBalance(stack);
            }
            total += countInShulker(stack);
        }
        return total;
    }

    /**
     * Count only loose Milky Star items in inventory (NOT jar balances).
     * Use this for deposit operations.
     */
    public static int countLoose(Player player) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isMilkyStar(stack)) {
                total += stack.getCount();
            }
            total += countInShulker(stack);
        }
        return total;
    }

    /**
     * Remove only loose Milky Star items from inventory (NOT from jars).
     * Use this for deposit operations.
     */
    public static boolean removeLoose(Player player, int amount) {
        if (countLoose(player) < amount) return false;

        Inventory inv = player.getInventory();
        int remaining = amount;

        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (isMilkyStar(stack)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
        }

        if (remaining > 0) {
            for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
                remaining = removeFromShulker(inv.getItem(i), remaining);
            }
        }

        return remaining == 0;
    }

    public static boolean remove(Player player, int amount) {
        if (count(player) < amount) return false;

        Inventory inv = player.getInventory();
        int remaining = amount;

        // 1. Remove loose Milky Stars first
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (isMilkyStar(stack)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
        }

        // 2. Remove from shulker boxes
        if (remaining > 0) {
            for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
                remaining = removeFromShulker(inv.getItem(i), remaining);
            }
        }

        // 3. Remove from jars last
        if (remaining > 0) {
            for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = inv.getItem(i);
                if (isJar(stack)) {
                    int balance = getJarBalance(stack);
                    int take = Math.min(remaining, balance);
                    if (take > 0) {
                        setJarBalanceAndUpdateVisual(player, i, balance - take);
                        remaining -= take;
                    }
                }
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
            if (isMilkyStar(inner)) total += inner.getCount();
        }
        return total;
    }

    private static int removeFromShulker(ItemStack shulkerStack, int remaining) {
        if (!isShulkerBox(shulkerStack) || remaining <= 0) return remaining;
        var container = shulkerStack.get(DataComponents.CONTAINER);
        if (container == null) return remaining;

        var items = new java.util.ArrayList<ItemStack>();
        for (ItemStack inner : container.nonEmptyItems()) items.add(inner.copy());

        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack inner = items.get(i);
            if (isMilkyStar(inner)) {
                int take = Math.min(remaining, inner.getCount());
                inner.shrink(take);
                remaining -= take;
            }
        }

        shulkerStack.set(DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
        return remaining;
    }

    private static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem().toString().contains("shulker_box");
    }

    // ---- Milky Star Jar (replaces Wallet) ----

    public static ItemStack createJar(String playerName) {
        ItemStack stack = new ItemStack(ModItems.MILKY_STAR_JAR_EMPTY.get());
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean(WALLET_KEY, true);
        tag.putInt(WALLET_BALANCE_KEY, 0);
        tag.putString(WALLET_OWNER_KEY, playerName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        updateJarLore(stack, 0);
        return stack;
    }

    public static boolean isJar(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // All 3 jar variants
        if (stack.is(ModItems.MILKY_STAR_JAR_EMPTY.get())) return true;
        if (stack.is(ModItems.MILKY_STAR_JAR_HALF.get())) return true;
        if (stack.is(ModItems.MILKY_STAR_JAR_FULL.get())) return true;
        // Legacy wallet (white bundle)
        if (stack.is(Items.WHITE_BUNDLE)) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) return customData.copyTag().getBooleanOr(WALLET_KEY, false);
        }
        return false;
    }

    /**
     * Get the correct jar item for a given balance.
     * empty = 0, half = 1-999, full = 1000+
     */
    private static Item getJarItemForBalance(int balance) {
        if (balance >= 1000) return ModItems.MILKY_STAR_JAR_FULL.get();
        if (balance >= 1) return ModItems.MILKY_STAR_JAR_HALF.get();
        return ModItems.MILKY_STAR_JAR_EMPTY.get();
    }

    /**
     * Set jar balance AND swap the item visual in the player's inventory.
     * Call this instead of setJarBalance when you have access to the player.
     */
    public static void setJarBalanceAndUpdateVisual(Player player, int slot, int balance) {
        Inventory inv = player.getInventory();
        ItemStack oldStack = inv.getItem(slot);
        if (!isJar(oldStack)) return;

        // Update balance in NBT
        CustomData customData = oldStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        net.minecraft.nbt.CompoundTag tag = customData.copyTag();
        tag.putInt(WALLET_BALANCE_KEY, balance);

        // Check if we need to swap item type
        Item correctItem = getJarItemForBalance(balance);
        if (oldStack.is(correctItem)) {
            // Same visual, just update NBT
            oldStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            updateJarLore(oldStack, balance);
        } else {
            // Different visual — create new stack, copy NBT
            ItemStack newStack = new ItemStack(correctItem, 1);
            newStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            updateJarLore(newStack, balance);
            inv.setItem(slot, newStack);
        }
    }

    /** @deprecated Use isJar instead */
    public static boolean isWallet(ItemStack stack) {
        return isJar(stack);
    }

    public static int getJarBalance(ItemStack stack) {
        if (!isJar(stack)) return 0;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return 0;
        return customData.copyTag().getIntOr(WALLET_BALANCE_KEY, 0);
    }

    /** @deprecated Use getJarBalance instead */
    public static int getWalletBalance(ItemStack stack) {
        return getJarBalance(stack);
    }

    public static void setJarBalance(ItemStack stack, int balance) {
        if (!isJar(stack)) return;
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        net.minecraft.nbt.CompoundTag tag = customData.copyTag();
        tag.putInt(WALLET_BALANCE_KEY, balance);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        updateJarLore(stack, balance);
    }

    /** @deprecated Use setJarBalance instead */
    public static void setWalletBalance(ItemStack stack, int balance) {
        setJarBalance(stack, balance);
    }

    private static void updateJarLore(ItemStack stack, int balance) {
        stack.set(DataComponents.LORE, new ItemLore(java.util.List.of(
                Component.literal(balance + " Milky Stars").withColor(0xFFD700)
        )));
    }

    public static String getJarOwner(ItemStack stack) {
        if (!isJar(stack)) return "";
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return "";
        return customData.copyTag().getStringOr(WALLET_OWNER_KEY, "");
    }

    /** @deprecated Use getJarOwner instead */
    public static String getWalletOwner(ItemStack stack) {
        return getJarOwner(stack);
    }

    public static int findJarSlot(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isJar(inv.getItem(i))) return i;
        }
        return -1;
    }

    /** @deprecated Use findJarSlot instead */
    public static int findWalletSlot(Player player) {
        return findJarSlot(player);
    }

    // ---- Stall Deed ----

    public static ItemStack createStallDeed() {
        return new ItemStack(ModItems.STALL_DEED.get());
    }

    public static boolean isStallDeed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // New registered item
        if (stack.is(ModItems.STALL_DEED.get())) return true;
        // Legacy NBT paper
        if (stack.is(Items.PAPER)) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) return customData.copyTag().getBooleanOr(STALL_DEED_KEY, false);
        }
        return false;
    }

    // ---- Dimension Compass ----

    public static ItemStack createDimensionCompass() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Dimension Compass")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAA00FF)).withItalic(false)));
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean(DIMENSION_COMPASS_KEY, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static boolean isDimensionCompass(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.COMPASS)) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBooleanOr(DIMENSION_COMPASS_KEY, false);
    }

    // ---- Market Catalog ----

    public static ItemStack createMarketCatalog() {
        return new ItemStack(ModItems.MARKET_CATALOG.get());
    }

    public static boolean isMarketCatalog(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ModItems.MARKET_CATALOG.get())) return true;
        // Legacy NBT book
        if (stack.is(Items.BOOK)) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) return customData.copyTag().getBooleanOr(MARKET_CATALOG_KEY, false);
        }
        return false;
    }

    // ---- Market Book ----

    public static ItemStack createMarketBook() {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Market Book")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)).withItalic(false)));
        net.minecraft.server.network.Filterable<String> title =
                net.minecraft.server.network.Filterable.passThrough("Market Book");
        net.minecraft.server.network.Filterable<Component> page =
                net.minecraft.server.network.Filterable.passThrough(
                        Component.literal("Place this book on a lectern to create a marketplace access point."));
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new net.minecraft.world.item.component.WrittenBookContent(
                        title, "mlkyMC", 0, java.util.List.of(page), true));
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean(MARKET_BOOK_KEY, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static boolean isMarketBook(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBooleanOr(MARKET_BOOK_KEY, false);
    }
}
