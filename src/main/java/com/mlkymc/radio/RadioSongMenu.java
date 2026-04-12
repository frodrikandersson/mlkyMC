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
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Song selection menu for Radio blocks in music mode.
 *
 * Layout (6 rows = 54 slots):
 * Rows 0-4: song entries (disc icons with song names)
 * Row 5: [BACK]...[page controls if needed]
 */
public class RadioSongMenu extends AbstractContainerMenu {

    private static final int SLOTS = 54;
    private static final int BACK_SLOT = 45;
    private static final int PREV_PAGE_SLOT = 48;
    private static final int PAGE_DISPLAY_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;

    private final SimpleContainer container;
    private final BlockPos blockPos;
    private final ServerPlayer viewer;
    private int page = 0;

    public RadioSongMenu(int containerId, Inventory playerInv, ServerPlayer player, BlockPos blockPos) {
        super(MenuType.GENERIC_9x6, containerId);
        this.container = new SimpleContainer(SLOTS);
        this.blockPos = blockPos;
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

    private int getCurrentSongIndex() {
        var be = getBlockEntity();
        if (be instanceof SpeakerBlockEntity spk) return spk.getSongIndex();
        return -1;
    }

    private void refreshDisplay() {
        for (int i = 0; i < SLOTS; i++) {
            container.setItem(i, pane(Items.BLACK_STAINED_GLASS_PANE, ""));
        }

        List<String> playlist = RadioMusicPlayer.getPlaylist();
        int songsPerPage = 45; // rows 0-4
        int totalPages = Math.max(1, (playlist.size() + songsPerPage - 1) / songsPerPage);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int startIdx = page * songsPerPage;
        int currentIdx = getCurrentSongIndex();
        boolean isPlaying = RadioMusicPlayer.isPlaying(blockPos);

        for (int slot = 0; slot < songsPerPage && (startIdx + slot) < playlist.size(); slot++) {
            int songIdx = startIdx + slot;
            String songName = playlist.get(songIdx);
            // Strip extension for display
            String displayName = songName;
            int dotIdx = displayName.lastIndexOf('.');
            if (dotIdx > 0) displayName = displayName.substring(0, dotIdx);

            boolean isCurrent = isPlaying && songIdx == currentIdx;

            ItemStack songItem;
            if (isCurrent) {
                songItem = new ItemStack(Items.MUSIC_DISC_CAT); // highlighted
                songItem.set(DataComponents.CUSTOM_NAME,
                        Component.literal("> " + displayName).withColor(0x55FF55));
                songItem.set(DataComponents.LORE, new ItemLore(List.of(
                        Component.literal("Now Playing").withColor(0x55FF55))));
            } else {
                songItem = new ItemStack(Items.MUSIC_DISC_13);
                songItem.set(DataComponents.CUSTOM_NAME,
                        Component.literal(displayName).withColor(0xFFFFFF));
                songItem.set(DataComponents.LORE, new ItemLore(List.of(
                        Component.literal("Click to play").withColor(0xAAAAAA))));
            }
            container.setItem(slot, songItem);
        }

        // Bottom row controls
        container.setItem(BACK_SLOT, pane(Items.ARROW, "Back"));

        if (totalPages > 1) {
            if (page > 0) {
                container.setItem(PREV_PAGE_SLOT, pane(Items.RED_STAINED_GLASS_PANE, "Previous Page"));
            }
            ItemStack pageDisplay = new ItemStack(Items.PAPER, Math.min(64, page + 1));
            pageDisplay.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Page " + (page + 1) + "/" + totalPages).withColor(0xFFFF55));
            container.setItem(PAGE_DISPLAY_SLOT, pageDisplay);
            if (page < totalPages - 1) {
                container.setItem(NEXT_PAGE_SLOT, pane(Items.LIME_STAINED_GLASS_PANE, "Next Page"));
            }
        }
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
                    (cid, inv, p) -> new RadioSettingsMenu(cid, inv, sp, blockPos, true),
                    Component.literal("Radio Settings")
            ));
            return;
        }

        if (slotId == PREV_PAGE_SLOT && page > 0) {
            page--;
            refreshDisplay();
            broadcastChanges();
            return;
        }

        if (slotId == NEXT_PAGE_SLOT) {
            page++;
            refreshDisplay();
            broadcastChanges();
            return;
        }

        // Song selection (rows 0-4)
        if (slotId < 45) {
            List<String> playlist = RadioMusicPlayer.getPlaylist();
            int songIdx = page * 45 + slotId;
            if (songIdx >= playlist.size()) return;

            var be = getBlockEntity();
            if (!(be instanceof SpeakerBlockEntity spk)) return;

            spk.setSongIndex(songIdx);

            // Start playing the selected song
            var vcPlugin = RadioVoicechatPlugin.getServerApi();
            var vcApi = RadioVoicechatPlugin.getApi();
            if (vcPlugin != null && vcApi != null && sp.level() instanceof ServerLevel sl) {
                RadioMusicPlayer.stopPlaying(blockPos);
                RadioMusicPlayer.startPlaying(vcPlugin, vcApi, blockPos, sl, songIdx, spk.getVolume());
            }

            String songName = playlist.get(songIdx);
            int dotIdx = songName.lastIndexOf('.');
            String displayName = dotIdx > 0 ? songName.substring(0, dotIdx) : songName;
            sp.displayClientMessage(Component.literal("Now playing: " + displayName).withColor(0x55FFFF), true);

            refreshDisplay();
            broadcastChanges();
        }
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
