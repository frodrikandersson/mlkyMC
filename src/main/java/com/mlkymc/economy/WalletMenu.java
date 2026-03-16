package com.mlkymc.economy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.mlkymc.registry.ModItems;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public class WalletMenu extends AbstractContainerMenu {

    private static final int ROWS = 3;
    private static final int COLS = 9;
    private static final int CONTAINER_SLOTS = ROWS * COLS;

    // Deposit button slots
    private static final int DEPOSIT_1 = 10;
    private static final int DEPOSIT_10 = 11;
    private static final int DEPOSIT_64 = 12;
    private static final int DEPOSIT_ALL = 14;

    // Withdraw button slots
    private static final int WITHDRAW_1 = 19;
    private static final int WITHDRAW_10 = 20;
    private static final int WITHDRAW_64 = 21;
    private static final int WITHDRAW_ALL = 23;

    private final SimpleContainer container;
    private final ServerPlayer viewer;
    private final int walletSlot; // inventory slot index where the wallet lives

    public WalletMenu(int containerId, Inventory playerInv, ServerPlayer viewer, int walletSlot) {
        super(MenuType.GENERIC_9x3, containerId);
        this.container = new SimpleContainer(CONTAINER_SLOTS);
        this.viewer = viewer;
        this.walletSlot = walletSlot;

        // Container slots (3 rows)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                this.addSlot(new Slot(container, col + row * COLS, 8 + col * 18, 18 + row * 18));
            }
        }

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        refreshDisplay();
    }

    private ItemStack getWalletItem() {
        ItemStack stack = viewer.getInventory().getItem(walletSlot);
        if (MilkyStar.isWallet(stack)) return stack;
        return ItemStack.EMPTY;
    }

    private int getBalance() {
        return MilkyStar.getWalletBalance(getWalletItem());
    }

    private void setBalance(int balance) {
        MilkyStar.setJarBalanceAndUpdateVisual(viewer, walletSlot, balance);
    }

    private void refreshDisplay() {
        int walletBal = getBalance();
        int physicalStars = MilkyStar.countLoose(viewer);

        // Count stars in other wallets (including wallets inside shulker boxes)
        int otherWalletStars = 0;
        var inv = viewer.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (i == walletSlot) continue;
            ItemStack stack = inv.getItem(i);
            if (MilkyStar.isWallet(stack)) {
                otherWalletStars += MilkyStar.getWalletBalance(stack);
            }
            otherWalletStars += countWalletsInShulker(stack);
        }

        int totalStars = walletBal + physicalStars + otherWalletStars;

        for (int i = 0; i < CONTAINER_SLOTS; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }

        // Row 1: Info
        fillPane(0, 8, Items.BLACK_STAINED_GLASS_PANE, "");

        // Balance display (slot 4)
        ItemStack balanceItem = new ItemStack(ModItems.MILKY_STAR.get());
        balanceItem.set(DataComponents.CUSTOM_NAME,
                Component.literal("Jar: " + walletBal + " Milky Stars").withColor(0xFFD700));
        balanceItem.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Owner: " + MilkyStar.getWalletOwner(getWalletItem())).withColor(0xAAAAAA),
                Component.literal("In inventory: " + physicalStars + " Stars").withColor(0xAAAAAA),
                Component.literal("Other wallets: " + otherWalletStars + " Stars").withColor(0xAAAAAA),
                Component.literal("Total: " + totalStars + " Stars").withColor(0x55FF55)
        )));
        container.setItem(4, balanceItem);

        // Row 2: Deposit buttons
        fillPane(9, 17, Items.BLACK_STAINED_GLASS_PANE, "");

        container.setItem(DEPOSIT_1, makeButton(ModItems.MILKY_STAR.get(), 1,
                "Deposit 1 Star", 0x55FF55));
        container.setItem(DEPOSIT_10, makeButton(ModItems.MILKY_STAR.get(), 10,
                "Deposit 10 Stars", 0x55FF55));
        container.setItem(DEPOSIT_64, makeButton(ModItems.MILKY_STAR.get(), 64,
                "Deposit 64 Stars", 0x55FF55));
        container.setItem(DEPOSIT_ALL, makeButtonSingle(Items.HOPPER,
                "Deposit All (" + physicalStars + ")", 0x55FF55));

        // Row 3: Withdraw buttons
        fillPane(18, 26, Items.BLACK_STAINED_GLASS_PANE, "");

        container.setItem(WITHDRAW_1, makeButton(ModItems.MILKY_STAR.get(), 1,
                "Withdraw 1 Star", 0xFFAA00));
        container.setItem(WITHDRAW_10, makeButton(ModItems.MILKY_STAR.get(), 10,
                "Withdraw 10 Stars", 0xFFAA00));
        container.setItem(WITHDRAW_64, makeButton(ModItems.MILKY_STAR.get(), 64,
                "Withdraw 64 Stars", 0xFFAA00));
        container.setItem(WITHDRAW_ALL, makeButtonSingle(Items.CHEST,
                "Withdraw All (" + walletBal + ")", 0xFFAA00));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (slotId < 0 || slotId >= CONTAINER_SLOTS) return;

        switch (slotId) {
            case DEPOSIT_1 -> doDeposit(sp, 1);
            case DEPOSIT_10 -> doDeposit(sp, 10);
            case DEPOSIT_64 -> doDeposit(sp, 64);
            case DEPOSIT_ALL -> doDeposit(sp, MilkyStar.countLoose(sp));
            case WITHDRAW_1 -> doWithdraw(sp, 1);
            case WITHDRAW_10 -> doWithdraw(sp, 10);
            case WITHDRAW_64 -> doWithdraw(sp, 64);
            case WITHDRAW_ALL -> doWithdraw(sp, getBalance());
        }
    }

    private void doDeposit(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        int available = MilkyStar.countLoose(player);
        int toDeposit = Math.min(amount, available);
        if (toDeposit <= 0) {
            player.sendSystemMessage(Component.literal(
                    "No Milky Stars to deposit.").withColor(0xFF5555));
            return;
        }

        if (MilkyStar.removeLoose(player, toDeposit)) {
            setBalance(getBalance() + toDeposit);
            player.sendSystemMessage(Component.literal(
                    "Deposited " + toDeposit + " Milky Stars.").withColor(0x55FF55));
        }
        refreshDisplay();
        broadcastChanges();
    }

    private void doWithdraw(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        int available = getBalance();
        int toWithdraw = Math.min(amount, available);
        if (toWithdraw <= 0) {
            player.sendSystemMessage(Component.literal(
                    "Jar is empty.").withColor(0xFF5555));
            return;
        }

        // Calculate how many stars can actually fit in inventory
        // Count empty slots and partial stacks that can accept milky stars
        var inv = player.getInventory();
        int canFit = 0;
        for (int i = 0; i < 36; i++) { // main inventory only (0-35)
            if (i == walletSlot) continue; // skip the jar slot
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) {
                canFit += 64;
            } else if (MilkyStar.isMilkyStar(slot)) {
                canFit += 64 - slot.getCount();
            }
        }

        if (canFit <= 0) {
            player.sendSystemMessage(Component.literal(
                    "Inventory is full!").withColor(0xFF5555));
            return;
        }

        // Only withdraw what fits
        int actualWithdraw = Math.min(toWithdraw, canFit);
        setBalance(available - actualWithdraw);

        // Add stars to inventory
        int remaining = actualWithdraw;
        for (int i = 0; i < 36 && remaining > 0; i++) {
            if (i == walletSlot) continue;
            ItemStack slot = inv.getItem(i);
            if (MilkyStar.isMilkyStar(slot)) {
                int space = 64 - slot.getCount();
                if (space > 0) {
                    int add = Math.min(remaining, space);
                    slot.grow(add);
                    remaining -= add;
                }
            }
        }
        for (int i = 0; i < 36 && remaining > 0; i++) {
            if (i == walletSlot) continue;
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) {
                int add = Math.min(remaining, 64);
                inv.setItem(i, MilkyStar.create(add));
                remaining -= add;
            }
        }

        if (actualWithdraw < toWithdraw) {
            player.sendSystemMessage(Component.literal(
                    "Withdrew " + actualWithdraw + " Milky Stars (inventory full, " + (toWithdraw - actualWithdraw) + " remain in jar).").withColor(0xFFAA00));
        } else {
            player.sendSystemMessage(Component.literal(
                    "Withdrew " + actualWithdraw + " Milky Stars.").withColor(0xFFAA00));
        }
        refreshDisplay();
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift-clicking Milky Stars from inventory deposits them
        if (index >= CONTAINER_SLOTS && player instanceof ServerPlayer sp) {
            Slot slot = this.slots.get(index);
            if (slot != null && slot.hasItem() && MilkyStar.isMilkyStar(slot.getItem())) {
                int count = slot.getItem().getCount();
                slot.set(ItemStack.EMPTY);
                setBalance(getBalance() + count);
                sp.sendSystemMessage(Component.literal(
                        "Deposited " + count + " Milky Stars.").withColor(0x55FF55));
                refreshDisplay();
                broadcastChanges();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return !getWalletItem().isEmpty();
    }

    // ---- Helpers ----

    private static int countWalletsInShulker(ItemStack shulkerStack) {
        if (shulkerStack.isEmpty()) return 0;
        String id = shulkerStack.getItem().toString();
        if (!id.contains("shulker_box")) return 0;
        var container = shulkerStack.get(DataComponents.CONTAINER);
        if (container == null) return 0;
        int total = 0;
        for (ItemStack inner : container.nonEmptyItems()) {
            if (MilkyStar.isWallet(inner)) {
                total += MilkyStar.getWalletBalance(inner);
            }
        }
        return total;
    }

    private void fillPane(int from, int to, net.minecraft.world.item.Item pane, String name) {
        for (int i = from; i <= to; i++) {
            ItemStack stack = new ItemStack(pane);
            if (!name.isEmpty()) {
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withColor(0xFFFFFF));
            }
            container.setItem(i, stack);
        }
    }

    private ItemStack makeButton(net.minecraft.world.item.Item item, int count, String name, int color) {
        ItemStack stack = new ItemStack(item, Math.min(count, 64));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withColor(color));
        return stack;
    }

    private ItemStack makeButtonSingle(net.minecraft.world.item.Item item, String name, int color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withColor(color));
        return stack;
    }
}
