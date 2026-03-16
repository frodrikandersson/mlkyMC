package com.mlkymc.market;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public class SellMenu extends AbstractContainerMenu {

    private static final int ROWS = 3;
    private static final int COLS = 9;
    private static final int CONTAINER_SLOTS = ROWS * COLS; // 27

    // Slot layout:
    // Row 0: [glass] [glass] [glass] [glass] [DEPOSIT] [glass] [glass] [glass] [glass]
    // Row 1: [glass] [-100] [-10]   [-1]    [PRICE]   [+1]    [+10]   [+100]  [glass]
    // Row 2: [glass] [glass] [glass] [glass] [CONFIRM] [glass] [glass] [glass] [CANCEL]

    private static final int DEPOSIT_SLOT = 4;
    private static final int PRICE_SLOT = 13;
    private static final int CONFIRM_SLOT = 22;
    private static final int CANCEL_SLOT = 26;

    // Price adjustment button slots (row 1)
    private static final int MINUS_100 = 10;
    private static final int MINUS_10 = 11;
    private static final int MINUS_1 = 12;
    private static final int PLUS_1 = 14;
    private static final int PLUS_10 = 15;
    private static final int PLUS_100 = 16;

    private final SimpleContainer container;
    private final MarketManager manager;
    private final boolean isMarketplace;
    private int price = 1;

    public SellMenu(int containerId, Inventory playerInv, ServerPlayer seller,
                    MarketManager manager, boolean isMarketplace) {
        super(MenuType.GENERIC_9x3, containerId);
        this.container = new SimpleContainer(CONTAINER_SLOTS);
        this.manager = manager;
        this.isMarketplace = isMarketplace;

        // Container slots (3 rows)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = col + row * COLS;
                if (index == DEPOSIT_SLOT) {
                    // The deposit slot allows item placement
                    this.addSlot(new DepositSlot(container, index, 8 + col * 18, 18 + row * 18));
                } else {
                    this.addSlot(new Slot(container, index, 8 + col * 18, 18 + row * 18));
                }
            }
        }

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 67 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 125));
        }

        refreshDisplay();
    }

    private void refreshDisplay() {
        // Fill all non-special slots with glass panes
        for (int i = 0; i < CONTAINER_SLOTS; i++) {
            if (i != DEPOSIT_SLOT) {
                container.setItem(i, createPane(Items.BLACK_STAINED_GLASS_PANE, ""));
            }
        }

        // Deposit slot label (only show if empty)
        if (container.getItem(DEPOSIT_SLOT).isEmpty()) {
            container.setItem(DEPOSIT_SLOT, createPane(Items.LIGHT_GRAY_STAINED_GLASS_PANE, "Place item to sell here"));
        }

        // Price display
        ItemStack priceDisplay = new ItemStack(Items.NETHER_STAR, Math.min(price, 64));
        priceDisplay.set(DataComponents.CUSTOM_NAME,
                Component.literal("Price: " + price + " Milky Stars").withColor(0xFFD700));
        priceDisplay.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Use the buttons to adjust").withColor(0xAAAAAA)
        )));
        container.setItem(PRICE_SLOT, priceDisplay);

        // Price adjustment buttons
        container.setItem(MINUS_100, createButton(Items.RED_STAINED_GLASS_PANE, "-100", 0xFF5555));
        container.setItem(MINUS_10, createButton(Items.ORANGE_STAINED_GLASS_PANE, "-10", 0xFFAA00));
        container.setItem(MINUS_1, createButton(Items.YELLOW_STAINED_GLASS_PANE, "-1", 0xFFFF55));
        container.setItem(PLUS_1, createButton(Items.YELLOW_STAINED_GLASS_PANE, "+1", 0xFFFF55));
        container.setItem(PLUS_10, createButton(Items.LIME_STAINED_GLASS_PANE, "+10", 0x55FF55));
        container.setItem(PLUS_100, createButton(Items.GREEN_STAINED_GLASS_PANE, "+100", 0x00AA00));

        // Confirm button
        ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
        confirm.set(DataComponents.CUSTOM_NAME,
                Component.literal("Confirm Listing").withColor(0x55FF55));
        container.setItem(CONFIRM_SLOT, confirm);

        // Cancel button
        ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        cancel.set(DataComponents.CUSTOM_NAME,
                Component.literal("Cancel").withColor(0xFF5555));
        container.setItem(CANCEL_SLOT, cancel);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // Allow normal interaction with deposit slot and player inventory
        if (slotId == DEPOSIT_SLOT || slotId >= CONTAINER_SLOTS) {
            // If clicking the placeholder pane, clear it first
            if (slotId == DEPOSIT_SLOT) {
                ItemStack current = container.getItem(DEPOSIT_SLOT);
                if (isPlaceholder(current)) {
                    container.setItem(DEPOSIT_SLOT, ItemStack.EMPTY);
                }
            }
            super.clicked(slotId, button, clickType, player);
            // After interaction, update placeholder if empty
            if (container.getItem(DEPOSIT_SLOT).isEmpty()) {
                container.setItem(DEPOSIT_SLOT, createPane(Items.LIGHT_GRAY_STAINED_GLASS_PANE, "Place item to sell here"));
            }
            return;
        }

        // Block all other container slot interactions
        if (slotId < 0 || slotId >= CONTAINER_SLOTS) return;

        // Price adjustments
        switch (slotId) {
            case MINUS_100 -> adjustPrice(-100);
            case MINUS_10 -> adjustPrice(-10);
            case MINUS_1 -> adjustPrice(-1);
            case PLUS_1 -> adjustPrice(1);
            case PLUS_10 -> adjustPrice(10);
            case PLUS_100 -> adjustPrice(100);
            case CONFIRM_SLOT -> confirmListing(serverPlayer);
            case CANCEL_SLOT -> {
                returnDepositItem(serverPlayer);
                serverPlayer.closeContainer();
            }
        }
    }

    private void adjustPrice(int delta) {
        price = Math.max(1, price + delta);
        refreshDisplay();
        broadcastChanges();
    }

    private void confirmListing(ServerPlayer player) {
        ItemStack deposited = container.getItem(DEPOSIT_SLOT);
        if (deposited.isEmpty() || isPlaceholder(deposited)) {
            player.sendSystemMessage(Component.literal(
                    "Place an item in the slot first!").withColor(0xFF5555));
            return;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(deposited.getItem()).toString();
        int amount = deposited.getCount();
        String itemNbt = MarketManager.serializeItemStack(deposited, player);

        // Clear the deposit slot (item is consumed by listing)
        container.setItem(DEPOSIT_SLOT, ItemStack.EMPTY);

        if (isMarketplace) {
            manager.addMarketplaceListing(player, itemId, amount, price, itemNbt);
        } else {
            manager.addStallListing(player, itemId, amount, price, itemNbt);
        }

        player.sendSystemMessage(Component.literal(
                "Listed " + amount + "x " + itemId.replace("minecraft:", "") +
                " for " + price + " Milky Stars!").withColor(0x55FF55));

        // Reopen the listings GUI
        player.closeContainer();
        String title = isMarketplace ? "Marketplace" : getStallTitle(player);
        player.openMenu(new SimpleMenuProvider(
                (cid, inv, p) -> {
                    if (isMarketplace) {
                        return MarketMenu.marketplace(cid, inv, player, manager, 0);
                    } else {
                        return MarketMenu.stall(cid, inv, player, manager, player.getStringUUID(), 0);
                    }
                },
                Component.literal(title)
        ));
    }

    private String getStallTitle(ServerPlayer player) {
        MarketManager.StallData stall = manager.getStall(player.getStringUUID());
        return stall != null ? stall.stallName : "Your Stall";
    }

    private void returnDepositItem(ServerPlayer player) {
        ItemStack deposited = container.getItem(DEPOSIT_SLOT);
        if (!deposited.isEmpty() && !isPlaceholder(deposited)) {
            if (!player.getInventory().add(deposited)) {
                player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        player.level(), player.getX(), player.getY(), player.getZ(), deposited));
            }
            container.setItem(DEPOSIT_SLOT, ItemStack.EMPTY);
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Return the deposited item if menu is closed without confirming
        if (player instanceof ServerPlayer serverPlayer) {
            returnDepositItem(serverPlayer);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Allow shift-clicking from player inventory into deposit slot
        if (index >= CONTAINER_SLOTS) {
            Slot slot = this.slots.get(index);
            if (slot.hasItem()) {
                ItemStack current = container.getItem(DEPOSIT_SLOT);
                if (current.isEmpty() || isPlaceholder(current)) {
                    container.setItem(DEPOSIT_SLOT, slot.getItem().copy());
                    slot.set(ItemStack.EMPTY);
                    return ItemStack.EMPTY;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private boolean isPlaceholder(ItemStack stack) {
        return stack.is(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack createPane(net.minecraft.world.item.Item item, String name) {
        ItemStack stack = new ItemStack(item);
        if (!name.isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal(name).withColor(0xAAAAAA));
        }
        return stack;
    }

    private ItemStack createButton(net.minecraft.world.item.Item item, String label, int color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(label).withColor(color));
        return stack;
    }

    // Custom slot that accepts items from player
    private static class DepositSlot extends Slot {
        public DepositSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }
}
