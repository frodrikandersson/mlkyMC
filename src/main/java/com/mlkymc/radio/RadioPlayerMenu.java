package com.mlkymc.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/**
 * Player management menu for radio blocks.
 *
 * Two modes:
 * - REMOVE mode (default): shows allowed player heads, click to remove
 * - ADD mode: shows all online players (not already allowed), click to add
 *
 * Layout (6 rows = 54 slots):
 * Row 0-4: player heads (allowed or online depending on mode)
 * Row 5: [MODE TOGGLE]...[BACK]
 */
public class RadioPlayerMenu extends AbstractContainerMenu {

    private static final int SLOTS = 54;
    private static final int MODE_SLOT = 45;
    private static final int BACK_SLOT = 53;

    private final SimpleContainer container;
    private final BlockPos blockPos;
    private final boolean isSpeaker;
    private final ServerPlayer viewer;
    private final Map<Integer, UUID> slotToPlayer = new HashMap<>();
    private boolean addMode = false; // false = show allowed (remove), true = show online (add)

    public RadioPlayerMenu(int containerId, Inventory playerInv, ServerPlayer player,
                            BlockPos blockPos, boolean isSpeaker) {
        super(MenuType.GENERIC_9x6, containerId);
        this.container = new SimpleContainer(SLOTS);
        this.blockPos = blockPos;
        this.isSpeaker = isSpeaker;
        this.viewer = player;

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 120 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 178));
        }

        refreshDisplay();
    }

    private BlockEntity getBlockEntity() {
        if (viewer.level() instanceof ServerLevel sl) {
            return sl.getBlockEntity(blockPos);
        }
        return null;
    }

    private Set<String> getAllowedPlayers() {
        var be = getBlockEntity();
        if (be instanceof MicrophoneBlockEntity mic) return mic.getAllowedPlayers();
        if (be instanceof SpeakerBlockEntity spk) return spk.getAllowedPlayers();
        return Set.of();
    }

    private void refreshDisplay() {
        slotToPlayer.clear();

        for (int i = 0; i < SLOTS; i++) {
            container.setItem(i, pane(Items.BLACK_STAINED_GLASS_PANE, ""));
        }

        if (addMode) {
            // Show all online players NOT already in the allowed list
            var allowed = getAllowedPlayers();
            int slot = 0;
            var server = viewer.level().getServer();
            if (server != null) {
                for (var sp : server.getPlayerList().getPlayers()) {
                    if (slot >= 45) break;
                    if (sp.getUUID().equals(viewer.getUUID())) continue;
                    if (allowed.contains(sp.getStringUUID())) continue;

                    ItemStack head = createPlayerHead(sp.getUUID(), sp.getName().getString(),
                            "Click to add", 0x55FF55);
                    container.setItem(slot, head);
                    slotToPlayer.put(slot, sp.getUUID());
                    slot++;
                }
            }

            // Mode toggle
            container.setItem(MODE_SLOT, pane(Items.LIME_STAINED_GLASS_PANE, "Mode: ADD (click to switch)"));
        } else {
            // Show allowed players
            var allowed = getAllowedPlayers();
            int slot = 0;
            var server = viewer.level().getServer();
            for (String uuidStr : allowed) {
                if (slot >= 45) break;
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String name = uuidStr.substring(0, 8); // fallback
                    if (server != null) {
                        // Try to find player by UUID (online or offline name from player list)
                        var onlinePlayer = server.getPlayerList().getPlayer(uuid);
                        if (onlinePlayer != null) {
                            name = onlinePlayer.getName().getString();
                        }
                    }

                    ItemStack head = createPlayerHead(uuid, name,
                            "Click to remove", 0xFF5555);
                    container.setItem(slot, head);
                    slotToPlayer.put(slot, uuid);
                    slot++;
                } catch (IllegalArgumentException ignored) {}
            }

            container.setItem(MODE_SLOT, pane(Items.ORANGE_STAINED_GLASS_PANE, "Mode: REMOVE (click to switch)"));
        }

        container.setItem(BACK_SLOT, pane(Items.ARROW, "Back"));
    }

    private ItemStack createPlayerHead(UUID uuid, String name, String action, int actionColor) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.CUSTOM_NAME, Component.literal(name).withColor(0xFFFFFF));
        head.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(action).withColor(actionColor))));
        try {
            var gameProfile = new com.mojang.authlib.GameProfile(uuid, name);
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(gameProfile));
        } catch (Exception ignored) {}
        return head;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (slotId < 0 || slotId >= SLOTS) {
            if (slotId >= SLOTS) super.clicked(slotId, button, clickType, player);
            return;
        }

        if (slotId == BACK_SLOT) {
            sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (cid, inv, p) -> new RadioSettingsMenu(cid, inv, sp, blockPos, isSpeaker),
                    Component.literal(isSpeaker ? "Speaker Settings" : "Microphone Settings")
            ));
            return;
        }

        if (slotId == MODE_SLOT) {
            addMode = !addMode;
            refreshDisplay();
            broadcastChanges();
            return;
        }

        UUID targetUuid = slotToPlayer.get(slotId);
        if (targetUuid == null) return;

        var be = getBlockEntity();
        if (addMode) {
            // Add player
            if (be instanceof MicrophoneBlockEntity mic) mic.addAllowedPlayer(targetUuid);
            else if (be instanceof SpeakerBlockEntity spk) spk.addAllowedPlayer(targetUuid);
            sp.sendSystemMessage(Component.literal("Player added to access list!").withColor(0x55FF55));
        } else {
            // Remove player
            if (be instanceof MicrophoneBlockEntity mic) mic.removeAllowedPlayer(targetUuid);
            else if (be instanceof SpeakerBlockEntity spk) spk.removeAllowedPlayer(targetUuid);
            sp.sendSystemMessage(Component.literal("Player removed from access list.").withColor(0xFFAA00));
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
        return player.blockPosition().distSqr(blockPos) < 64;
    }

    private static ItemStack pane(net.minecraft.world.item.Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withColor(0xFFFFFF));
        return stack;
    }
}
