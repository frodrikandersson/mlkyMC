package com.mlkymc.altar;

import com.mlkymc.MlkyMC;
import com.mlkymc.ghost.GhostData;
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
 * Ghost donation menu — lets ghosts choose how much Spectral Energy to donate.
 * 1-row chest with preset amounts: 10, 25, 50, 100, 250, All.
 */
public class GhostDonationMenu extends AbstractContainerMenu {
    private static final int CONTAINER_SLOTS = 9;

    private static final int DONATE_10 = 1;
    private static final int DONATE_25 = 2;
    private static final int DONATE_50 = 3;
    private static final int DONATE_100 = 4;
    private static final int DONATE_250 = 5;
    private static final int DONATE_ALL = 7;

    private final SimpleContainer container;
    private final SoulAltarData altar;
    private final GhostData ghostData;

    public static void open(ServerPlayer ghost, SoulAltarData altar, SoulAltarManager altarManager) {
        var gdm = MlkyMC.getGhostDataManager();
        if (gdm == null) return;
        var ghostData = gdm.getOrCreate(ghost.getUUID());
        ghost.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new GhostDonationMenu(id, inv, ghost, altar, ghostData),
                Component.literal("Donate Spectral Energy").withColor(0x55FFFF)
        ));
    }

    public GhostDonationMenu(int containerId, Inventory playerInv, ServerPlayer ghost,
                             SoulAltarData altar, GhostData ghostData) {
        super(MenuType.GENERIC_9x1, containerId);
        this.container = new SimpleContainer(CONTAINER_SLOTS);
        this.altar = altar;
        this.ghostData = ghostData;

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(container, col, 8 + col * 18, 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 50 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 108));
        }

        refreshDisplay();
    }

    private void refreshDisplay() {
        for (int i = 0; i < CONTAINER_SLOTS; i++) {
            container.setItem(i, makeFiller());
        }

        int se = ghostData.spectralEnergy;

        container.setItem(0, makeInfo(se));
        container.setItem(DONATE_10, makeDonateButton(10, se));
        container.setItem(DONATE_25, makeDonateButton(25, se));
        container.setItem(DONATE_50, makeDonateButton(50, se));
        container.setItem(DONATE_100, makeDonateButton(100, se));
        container.setItem(DONATE_250, makeDonateButton(250, se));
        container.setItem(6, makeDisconnectButton());
        container.setItem(DONATE_ALL, makeDonateAllButton(se));
        container.setItem(8, makeCloseButton());
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (slotId < 0 || slotId >= CONTAINER_SLOTS) return;

        switch (slotId) {
            case DONATE_10 -> donate(sp, 10);
            case DONATE_25 -> donate(sp, 25);
            case DONATE_50 -> donate(sp, 50);
            case DONATE_100 -> donate(sp, 100);
            case DONATE_250 -> donate(sp, 250);
            case DONATE_ALL -> donate(sp, ghostData.spectralEnergy);
            case 6 -> disconnect(sp);
            case 8 -> sp.closeContainer();
        }
    }

    private void donate(ServerPlayer sp, int amount) {
        if (ghostData.spectralEnergy <= 0) {
            sp.sendSystemMessage(Component.literal("No Spectral Energy to donate!").withColor(0xFF5555));
            return;
        }

        int toDonate = Math.min(amount, ghostData.spectralEnergy);
        if (toDonate <= 0) return;

        var altarManager = MlkyMC.getSoulAltarManager();
        int altarGain = altarManager.ghostDonate(altar, sp.getUUID(), sp.getName().getString(), toDonate);

        ghostData.spectralEnergy = Math.max(0, ghostData.spectralEnergy - toDonate);
        var gdm = MlkyMC.getGhostDataManager();
        if (gdm != null) {
            gdm.sendSpectralSync(sp, ghostData);
            gdm.save();
        }

        sp.sendSystemMessage(Component.literal("Donated " + toDonate + " SE (+" + altarGain + " to altar)")
                .withColor(0xAA55FF));

        if (ghostData.spectralEnergy <= 0) {
            sp.sendSystemMessage(Component.literal("All Spectral Energy donated. Abilities reset until you rebuild.")
                    .withColor(0xAAAAAA));
        }

        refreshDisplay();
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private ItemStack makeInfo(int se) {
        ItemStack stack = new ItemStack(Items.SOUL_LANTERN);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Your SE: " + se + " / 1,000").withColor(0x55FFFF));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Altar: " + altar.ownerName).withColor(0xAAAAAA),
                Component.literal("Converts at 2:1 ratio").withColor(0x777777)
        )));
        return stack;
    }

    private ItemStack makeDonateButton(int amount, int available) {
        boolean canAfford = available >= amount;
        int altarGain = amount / 2;
        var item = canAfford ? Items.ENDER_EYE : Items.GRAY_DYE;
        int color = canAfford ? 0x55FF55 : 0x555555;

        ItemStack stack = new ItemStack(item, Math.min(amount, 64));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Donate " + amount + " SE (+" + altarGain + " altar)").withColor(color));
        if (!canAfford) {
            stack.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Not enough SE").withColor(0xFF5555)
            )));
        }
        return stack;
    }

    private ItemStack makeDonateAllButton(int available) {
        int altarGain = available / 2;
        var item = available > 0 ? Items.NETHER_STAR : Items.GRAY_DYE;
        int color = available > 0 ? 0xFFD700 : 0x555555;

        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Donate ALL (" + available + " SE → +" + altarGain + " altar)").withColor(color));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Warning: Resets all abilities!").withColor(0xFF5555)
        )));
        return stack;
    }

    private void disconnect(ServerPlayer sp) {
        var altarManager = MlkyMC.getSoulAltarManager();
        altar.connectedGhosts.removeIf(gc -> gc.ghostUuid.equals(sp.getUUID()));
        altarManager.save();
        sp.sendSystemMessage(Component.literal("Disconnected from " + altar.ownerName + "'s altar.").withColor(0xFFAA00));
        sp.closeContainer();
    }

    private ItemStack makeDisconnectButton() {
        ItemStack stack = new ItemStack(Items.RED_WOOL);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Disconnect from Altar").withColor(0xFF5555));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Leave " + altar.ownerName + "'s altar").withColor(0xAAAAAA),
                Component.literal("Keeps your remaining SE").withColor(0x777777)
        )));
        return stack;
    }

    private ItemStack makeCloseButton() {
        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Close").withColor(0xFF5555));
        return stack;
    }

    private ItemStack makeFiller() {
        ItemStack stack = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        return stack;
    }
}
