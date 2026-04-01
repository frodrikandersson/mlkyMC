package com.mlkymc.altar;

import com.mlkymc.MlkyMC;
import com.mlkymc.economy.MilkyStar;
import com.mlkymc.registry.ModItems;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * Chest-based GUI for the Soul Altar.
 * 6 rows (54 slots):
 * Row 0: SE display + altar info
 * Row 1: Deposit buttons (Milky Stars, channel personal SE)
 * Row 2: Tier info
 * Row 3: Ability selection
 * Row 4: Connected ghosts + disconnect
 * Row 5: Recent donor log
 */
public class SoulAltarMenu extends AbstractContainerMenu {

    private static final int ROWS = 6;
    private static final int COLS = 9;
    private static final int CONTAINER_SLOTS = ROWS * COLS;

    // Button slots
    private static final int DEPOSIT_1_STAR = 10;
    private static final int DEPOSIT_10_STARS = 11;
    private static final int DEPOSIT_ALL_STARS = 12;
    private static final int CHANNEL_10_SE = 14;
    private static final int CHANNEL_ALL_SE = 16;

    // Ability slots (row 3)
    private static final int ABILITY_BASE = 27;  // slot 27 = base Soul Skill
    private static final int ABILITY_1A = 28;
    private static final int ABILITY_1B = 29;
    private static final int ABILITY_2A = 30;
    private static final int ABILITY_2B = 31;
    private static final int ABILITY_3A = 32;
    private static final int ABILITY_3B = 33;
    private static final int ABILITY_4 = 34;

    // Ghost slots (row 4)
    private static final int GHOST_1 = 37;
    private static final int GHOST_1_DISCONNECT = 38;
    private static final int GHOST_2 = 40;
    private static final int GHOST_2_DISCONNECT = 41;
    private static final int GHOST_3 = 43;
    private static final int GHOST_3_DISCONNECT = 44;

    private final SimpleContainer container;
    private final ServerPlayer viewer;
    private final SoulAltarData altar;
    private final boolean isOwner;

    public SoulAltarMenu(int containerId, Inventory playerInv, ServerPlayer viewer, SoulAltarData altar) {
        super(MenuType.GENERIC_9x6, containerId);
        this.container = new SimpleContainer(CONTAINER_SLOTS);
        this.viewer = viewer;
        this.altar = altar;
        this.isOwner = altar.ownerUuid.equals(viewer.getUUID());

        // Container slots (6 rows)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                this.addSlot(new Slot(container, col + row * COLS, 8 + col * 18, 18 + row * 18));
            }
        }

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }

        refreshDisplay();
    }

    private void refreshDisplay() {
        for (int i = 0; i < CONTAINER_SLOTS; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }

        var altarManager = MlkyMC.getSoulAltarManager();
        int tier = altarManager.computeTier(altar);
        int se = altar.storedSE;

        // Row 0: Info header
        fillPane(0, 8, Items.PURPLE_STAINED_GLASS_PANE, "");

        ItemStack seDisplay = new ItemStack(Items.AMETHYST_SHARD);
        seDisplay.set(DataComponents.CUSTOM_NAME,
                Component.literal("Altar SE: " + formatNumber(se) + " / 100,000").withColor(0xAA55FF));
        seDisplay.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Tier: " + tier + " | Owner: " + altar.ownerName).withColor(0xAAAAAA),
                Component.literal("Highest SE: " + formatNumber(altar.highWaterSE)).withColor(0x888888)
        )));
        container.setItem(4, seDisplay);

        // Row 1: Deposit/Channel buttons
        fillPane(9, 17, Items.BLACK_STAINED_GLASS_PANE, "");

        int looseStars = MilkyStar.count(viewer);
        container.setItem(DEPOSIT_1_STAR, makeButton(ModItems.MILKY_STAR.get(), 1,
                "Deposit 1 Star (+30 SE)", 0x55FF55));
        container.setItem(DEPOSIT_10_STARS, makeButton(ModItems.MILKY_STAR.get(), 10,
                "Deposit 10 Stars (+300 SE)", 0x55FF55));
        container.setItem(DEPOSIT_ALL_STARS, makeButton(Items.HOPPER, 1,
                "Deposit All (" + looseStars + " Stars)", 0x55FF55));

        var classData = MlkyMC.getClassManager().getOrCreate(viewer);
        int personalSE = classData.getSoulEnergy();
        if (isOwner) {
            container.setItem(CHANNEL_10_SE, makeButton(Items.EXPERIENCE_BOTTLE, 1,
                    "Channel 10 SE (" + personalSE + " available)", 0xAA55FF));
            container.setItem(CHANNEL_ALL_SE, makeButton(Items.BEACON, 1,
                    "Channel All SE (" + personalSE + ")", 0xAA55FF));
        }

        // Row 2: Tier info
        fillPane(18, 26, Items.GRAY_STAINED_GLASS_PANE, "");
        String[] tierNames = {"No Tier", "Tier 1 (25k)", "Tier 2 (50k)", "Tier 3 (75k)", "Tier 4 (100k)"};
        for (int t = 0; t <= 4; t++) {
            var item = t <= tier ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE;
            container.setItem(20 + t, makeButton(item, 1, tierNames[t], t <= tier ? 0x55FF55 : 0xFF5555));
        }

        // Row 3: Ability selection (only for owner)
        fillPane(27, 35, Items.BLACK_STAINED_GLASS_PANE, "");
        if (isOwner) {
            int selected = altar.selectedAbility;
            // Base Soul Skill (always available)
            container.setItem(ABILITY_BASE, makeAbilityButton("Heal", "40 SE | 3 hearts, 8 blocks",
                    selected == 0, true));
            // Tier 1
            container.setItem(ABILITY_1A, makeAbilityButton("Enhanced Heal", "80 SE | 5 hearts, 14 blocks",
                    selected == 1, tier >= 1));
            container.setItem(ABILITY_1B, makeAbilityButton("Spirit Ward", "120 SE | Reduce spawns, 3 min",
                    selected == 2, tier >= 1));
            // Tier 2
            container.setItem(ABILITY_2A, makeAbilityButton("Soul Mend", "60 SE | HoT 10s",
                    selected == 3, tier >= 2));
            container.setItem(ABILITY_2B, makeAbilityButton("Death Sense", "50 SE | Ghost vision 15s",
                    selected == 4, tier >= 2));
            // Tier 3
            container.setItem(ABILITY_3A, makeAbilityButton("Aura of Protection", "200 SE | Res I + Abs I, 30s",
                    selected == 5, tier >= 3));
            container.setItem(ABILITY_3B, makeAbilityButton("Curse of Unrest", "80 SE | 60s aura, double XP",
                    selected == 6, tier >= 3));
            // Tier 4
            container.setItem(ABILITY_4, makeAbilityButton("Resurrection", "Full altar wipe + Totem",
                    selected == 7, tier >= 4));
        }

        // Row 4: Connected ghosts
        fillPane(36, 44, Items.BLACK_STAINED_GLASS_PANE, "");
        var ghosts = altar.connectedGhosts;
        int[][] ghostSlots = {{GHOST_1, GHOST_1_DISCONNECT}, {GHOST_2, GHOST_2_DISCONNECT}, {GHOST_3, GHOST_3_DISCONNECT}};
        for (int i = 0; i < 3; i++) {
            if (i < ghosts.size()) {
                var gc = ghosts.get(i);
                ItemStack ghostItem = new ItemStack(Items.PLAYER_HEAD);
                // Set the ghost player's skin — resolves lazily on client
                ghostItem.set(DataComponents.PROFILE,
                        net.minecraft.world.item.component.ResolvableProfile.createUnresolved(gc.name));
                ghostItem.set(DataComponents.CUSTOM_NAME,
                        Component.literal(gc.name).withColor(0x55FFFF));
                ghostItem.set(DataComponents.LORE, new ItemLore(List.of(
                        Component.literal("Donated: " + gc.donatedTotal + " SE").withColor(0xAAAAAA)
                )));
                // Hide "When on Head" and "Dynamic" tooltips
                var hiddenSet = new it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet<net.minecraft.core.component.DataComponentType<?>>();
                hiddenSet.add(DataComponents.ATTRIBUTE_MODIFIERS);
                hiddenSet.add(DataComponents.PROFILE);
                ghostItem.set(DataComponents.TOOLTIP_DISPLAY,
                        new net.minecraft.world.item.component.TooltipDisplay(false, hiddenSet));
                container.setItem(ghostSlots[i][0], ghostItem);
                if (isOwner) {
                    container.setItem(ghostSlots[i][1], makeButton(Items.BARRIER, 1,
                            "Disconnect " + gc.name, 0xFF5555));
                }
            } else {
                container.setItem(ghostSlots[i][0], makeButton(Items.GRAY_STAINED_GLASS_PANE, 1,
                        "Empty Ghost Slot", 0x888888));
            }
        }

        // Row 5: Recent donors
        fillPane(45, 53, Items.BLACK_STAINED_GLASS_PANE, "");
        var donors = altar.recentDonors;
        for (int i = 0; i < Math.min(5, donors.size()); i++) {
            var donor = donors.get(i);
            ItemStack donorItem = new ItemStack(Items.PAPER);
            donorItem.set(DataComponents.CUSTOM_NAME,
                    Component.literal(donor.name + ": +" + donor.amount + " SE").withColor(0xFFD700));
            container.setItem(47 + i, donorItem);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (slotId < 0 || slotId >= CONTAINER_SLOTS) return;

        var altarManager = MlkyMC.getSoulAltarManager();

        switch (slotId) {
            case DEPOSIT_1_STAR -> doDepositStars(sp, 1);
            case DEPOSIT_10_STARS -> doDepositStars(sp, 10);
            case DEPOSIT_ALL_STARS -> doDepositStars(sp, MilkyStar.count(sp));
            case CHANNEL_10_SE -> { if (isOwner) doChannelSE(sp, 10); }
            case CHANNEL_ALL_SE -> { if (isOwner) doChannelSE(sp, MlkyMC.getClassManager().getOrCreate(sp).getSoulEnergy()); }
            case GHOST_1_DISCONNECT -> { if (isOwner && altar.connectedGhosts.size() > 0) doDisconnectGhost(0); }
            case GHOST_2_DISCONNECT -> { if (isOwner && altar.connectedGhosts.size() > 1) doDisconnectGhost(1); }
            case GHOST_3_DISCONNECT -> { if (isOwner && altar.connectedGhosts.size() > 2) doDisconnectGhost(2); }
            default -> {
                // Ability selection (row 3, slots 27-34)
                if (isOwner && slotId >= ABILITY_BASE && slotId <= ABILITY_4) {
                    int abilityIndex = slotId - ABILITY_BASE;
                    int tier = altarManager.computeTier(altar);
                    // Check tier requirement
                    int requiredTier = switch (abilityIndex) {
                        case 0 -> 0;
                        case 1, 2 -> 1;
                        case 3, 4 -> 2;
                        case 5, 6 -> 3;
                        case 7 -> 4;
                        default -> 99;
                    };
                    if (tier >= requiredTier) {
                        altar.selectedAbility = abilityIndex;
                        MlkyMC.getClassManager().getOrCreate(sp).setSelectedAltarAbility(abilityIndex);
                        MlkyMC.getClassManager().save();
                        altarManager.save();
                        sp.sendSystemMessage(Component.literal("Selected ability: " + getAbilityName(abilityIndex))
                                .withColor(0xAA55FF));
                    } else {
                        sp.sendSystemMessage(Component.literal("Need Tier " + requiredTier + " to unlock!")
                                .withColor(0xFF5555));
                    }
                }
            }
        }
    }

    private void doDepositStars(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        int available = MilkyStar.count(player);
        int spaceLeft = SoulAltarManager.MAX_ALTAR_SE - altar.storedSE;
        if (spaceLeft <= 0) {
            player.sendSystemMessage(Component.literal("Altar is full! (100,000 SE)").withColor(0xFF5555));
            return;
        }
        int starsForSpace = (int) Math.ceil(spaceLeft / 30.0);
        int toDeposit = Math.min(Math.min(amount, available), starsForSpace);
        if (toDeposit <= 0) {
            player.sendSystemMessage(Component.literal("No Milky Stars to deposit.").withColor(0xFF5555));
            return;
        }
        MilkyStar.remove(player, toDeposit);
        int gained = MlkyMC.getSoulAltarManager().depositMilkyStars(altar, player.getName().getString(), toDeposit);
        player.sendSystemMessage(Component.literal("Deposited " + toDeposit + " Stars (+" + gained + " SE)")
                .withColor(0x55FF55));
        refreshDisplay();
        broadcastChanges();
    }

    private void doChannelSE(ServerPlayer player, int amount) {
        int transferred = MlkyMC.getSoulAltarManager().channelPersonalSE(altar, player, amount);
        if (transferred > 0) {
            player.sendSystemMessage(Component.literal("Channeled " + transferred + " SE to altar")
                    .withColor(0xAA55FF));
        } else {
            player.sendSystemMessage(Component.literal("No SE to channel or altar is full.").withColor(0xFF5555));
        }
        refreshDisplay();
        broadcastChanges();
    }

    private void doDisconnectGhost(int index) {
        if (index < altar.connectedGhosts.size()) {
            var gc = altar.connectedGhosts.get(index);
            MlkyMC.getSoulAltarManager().disconnectGhost(altar, gc.ghostUuid);
            viewer.sendSystemMessage(Component.literal("Disconnected " + gc.name).withColor(0xFFAA00));
            refreshDisplay();
            broadcastChanges();
        }
    }

    private String getAbilityName(int index) {
        return switch (index) {
            case 0 -> "Heal";
            case 1 -> "Enhanced Heal";
            case 2 -> "Spirit Ward";
            case 3 -> "Soul Mend";
            case 4 -> "Death Sense";
            case 5 -> "Aura of Protection";
            case 6 -> "Curse of Unrest";
            case 7 -> "Resurrection";
            default -> "Unknown";
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // --- Helpers ---

    private void fillPane(int from, int to, net.minecraft.world.item.Item item, String name) {
        for (int i = from; i <= to; i++) {
            ItemStack pane = new ItemStack(item);
            pane.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            container.setItem(i, pane);
        }
    }

    private ItemStack makeButton(net.minecraft.world.item.Item item, int count, String name, int color) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withColor(color));
        return stack;
    }

    private ItemStack makeAbilityButton(String name, String desc, boolean selected, boolean unlocked) {
        var item = unlocked ? (selected ? Items.LIME_STAINED_GLASS_PANE : Items.LIGHT_BLUE_STAINED_GLASS_PANE)
                : Items.RED_STAINED_GLASS_PANE;
        int color = unlocked ? (selected ? 0x55FF55 : 0x55FFFF) : 0xFF5555;
        ItemStack stack = new ItemStack(item);
        String prefix = selected ? "[SELECTED] " : unlocked ? "" : "[LOCKED] ";
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(prefix + name).withColor(color));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(desc).withColor(0xAAAAAA)
        )));
        return stack;
    }

    private String formatNumber(int n) {
        if (n >= 1000) return String.format("%,d", n);
        return String.valueOf(n);
    }
}
