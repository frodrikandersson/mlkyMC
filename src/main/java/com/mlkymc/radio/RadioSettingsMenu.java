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
 * Settings menu for Microphone and Speaker blocks.
 *
 * Layout (3 rows = 27 slots):
 * Row 0: [glass] [-10 freq] [-1 freq] [glass] [FREQ display] [glass] [+1 freq] [+10 freq] [glass]
 * Row 1: [glass] [-5 range] [glass]   [glass] [RANGE display] [glass] [glass]   [+5 range] [glass]  (speaker only)
 * Row 2: [glass] [glass]    [glass]   [glass] [LOCK toggle]   [glass] [glass]   [glass]    [CLOSE]
 */
public class RadioSettingsMenu extends AbstractContainerMenu {

    private static final int SLOTS = 27;

    // Row 0 — Frequency
    private static final int FREQ_M10 = 1;
    private static final int FREQ_M1 = 2;
    private static final int FREQ_DISPLAY = 4;
    private static final int FREQ_P1 = 6;
    private static final int FREQ_P10 = 7;

    // Row 1 — Range (speaker only) + Volume
    private static final int RANGE_M5 = 10;
    private static final int VOL_DOWN = 11;
    private static final int RANGE_DISPLAY = 12;
    private static final int VOL_DISPLAY = 13;
    private static final int RANGE_P5 = 14;
    private static final int VOL_UP = 15;

    // Row 2 — Players + Songs + Mode + Lock + Close
    private static final int PLAYERS_SLOT = 18;
    private static final int SONGS_SLOT = 19;
    private static final int MODE_SLOT = 20;
    private static final int LOCK_SLOT = 22;
    private static final int CLOSE_SLOT = 26;

    private final SimpleContainer container;
    private final BlockPos blockPos;
    private final boolean isSpeaker;
    private final ServerPlayer viewer;

    public RadioSettingsMenu(int containerId, Inventory playerInv, ServerPlayer player,
                              BlockPos blockPos, boolean isSpeaker) {
        super(MenuType.GENERIC_9x3, containerId);
        this.container = new SimpleContainer(SLOTS);
        this.blockPos = blockPos;
        this.isSpeaker = isSpeaker;
        this.viewer = player;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 67 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 125));
        }

        refreshDisplay();
    }

    private BlockEntity getBlockEntity() {
        if (viewer.level() instanceof ServerLevel sl) {
            return sl.getBlockEntity(blockPos);
        }
        return null;
    }

    private int getFrequency() {
        var be = getBlockEntity();
        if (be instanceof MicrophoneBlockEntity mic) return mic.getFrequency();
        if (be instanceof SpeakerBlockEntity spk) return spk.getFrequency();
        return 1;
    }

    private float getRange() {
        var be = getBlockEntity();
        if (be instanceof SpeakerBlockEntity spk) return spk.getRange();
        return 16;
    }

    private float getVolume() {
        var be = getBlockEntity();
        if (be instanceof SpeakerBlockEntity spk) return spk.getVolume();
        return 0.5f;
    }

    private boolean isLocked() {
        var be = getBlockEntity();
        if (be instanceof MicrophoneBlockEntity mic) return mic.isLocked();
        if (be instanceof SpeakerBlockEntity spk) return spk.isLocked();
        return false;
    }

    private int getAllowedCount() {
        var be = getBlockEntity();
        if (be instanceof MicrophoneBlockEntity mic) return mic.getAllowedPlayers().size();
        if (be instanceof SpeakerBlockEntity spk) return spk.getAllowedPlayers().size();
        return 0;
    }

    private String getLockedByName() {
        var be = getBlockEntity();
        if (be instanceof MicrophoneBlockEntity mic) return mic.getLockedByName();
        if (be instanceof SpeakerBlockEntity spk) return spk.getLockedByName();
        return "";
    }

    private void refreshDisplay() {
        // Fill all with glass
        for (int i = 0; i < SLOTS; i++) {
            container.setItem(i, pane(Items.BLACK_STAINED_GLASS_PANE, ""));
        }

        int freq = getFrequency();

        // Frequency controls
        container.setItem(FREQ_M10, pane(Items.RED_STAINED_GLASS_PANE, "-10"));
        container.setItem(FREQ_M1, pane(Items.ORANGE_STAINED_GLASS_PANE, "-1"));
        ItemStack freqDisplay = new ItemStack(Items.NOTE_BLOCK, Math.max(1, Math.min(64, freq)));
        freqDisplay.set(DataComponents.CUSTOM_NAME,
                Component.literal("Frequency: " + freq).withColor(0x55FFFF));
        container.setItem(FREQ_DISPLAY, freqDisplay);
        container.setItem(FREQ_P1, pane(Items.LIME_STAINED_GLASS_PANE, "+1"));
        container.setItem(FREQ_P10, pane(Items.GREEN_STAINED_GLASS_PANE, "+10"));

        // Range + Volume controls (speaker/radio only)
        if (isSpeaker) {
            int range = (int) getRange();
            container.setItem(RANGE_M5, pane(Items.RED_STAINED_GLASS_PANE, "-5 Range"));
            ItemStack rangeDisplay = new ItemStack(Items.SCULK_SENSOR, Math.max(1, Math.min(64, range)));
            rangeDisplay.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Voice Range: " + range + " blocks").withColor(0xFFAA00));
            rangeDisplay.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("How far voice audio carries").withColor(0xAAAAAA),
                    Component.literal("Does not affect music").withColor(0x777777))));
            container.setItem(RANGE_DISPLAY, rangeDisplay);
            container.setItem(RANGE_P5, pane(Items.LIME_STAINED_GLASS_PANE, "+5 Range"));

            // Volume controls
            int volPercent = (int) (getVolume() * 100);
            container.setItem(VOL_DOWN, pane(Items.RED_STAINED_GLASS_PANE, "- Volume"));
            ItemStack volDisplay = new ItemStack(Items.JUKEBOX, Math.max(1, Math.min(64, volPercent / 10)));
            volDisplay.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Volume: " + volPercent + "%").withColor(0xFFFF55));
            container.setItem(VOL_DISPLAY, volDisplay);
            container.setItem(VOL_UP, pane(Items.LIME_STAINED_GLASS_PANE, "+ Volume"));
        }

        // Mode toggle (Radio blocks only — not shown on Speaker blocks)
        boolean isRadioBlock = viewer.level() instanceof ServerLevel sl2
                && sl2.getBlockState(blockPos).getBlock() instanceof RadioBlock;
        if (isSpeaker && isRadioBlock) {
            boolean musicMode = false;
            var be2 = getBlockEntity();
            if (be2 instanceof SpeakerBlockEntity spk) musicMode = spk.isMusicMode();

            // Only show mode toggle if there are music files available
            if (RadioMusicPlayer.hasMusic() || musicMode) {
                ItemStack modeBtn;
                if (musicMode) {
                    modeBtn = pane(Items.JUKEBOX, "Mode: MUSIC");
                    modeBtn.set(DataComponents.LORE, new ItemLore(List.of(
                            Component.literal("Click to switch to Speaker mode").withColor(0xAAAAAA),
                            Component.literal("RMB radio to skip song").withColor(0x55FFFF))));
                } else {
                    modeBtn = pane(Items.NOTE_BLOCK, "Mode: SPEAKER");
                    modeBtn.set(DataComponents.LORE, new ItemLore(List.of(
                            Component.literal("Click to switch to Music mode").withColor(0xAAAAAA))));
                }
                container.setItem(MODE_SLOT, modeBtn);
            }
        }

        // Song list button (radio blocks in music mode only)
        if (isSpeaker && isRadioBlock && RadioMusicPlayer.hasMusic()) {
            String currentSong = RadioMusicPlayer.getCurrentSong(blockPos);
            ItemStack songsBtn = new ItemStack(Items.JUKEBOX);
            if (currentSong != null) {
                int dotIdx = currentSong.lastIndexOf('.');
                String displayName = dotIdx > 0 ? currentSong.substring(0, dotIdx) : currentSong;
                songsBtn.set(DataComponents.CUSTOM_NAME,
                        Component.literal("Song List").withColor(0x55FFFF));
                songsBtn.set(DataComponents.LORE, new ItemLore(List.of(
                        Component.literal("Now: " + displayName).withColor(0x55FF55),
                        Component.literal("Click to browse songs").withColor(0xAAAAAA))));
            } else {
                songsBtn.set(DataComponents.CUSTOM_NAME,
                        Component.literal("Song List").withColor(0x55FFFF));
                songsBtn.set(DataComponents.LORE, new ItemLore(List.of(
                        Component.literal("Click to browse songs").withColor(0xAAAAAA))));
            }
            container.setItem(SONGS_SLOT, songsBtn);
        }

        // Manage Players button
        ItemStack playersBtn = new ItemStack(Items.PLAYER_HEAD);
        int allowedCount = getAllowedCount();
        playersBtn.set(DataComponents.CUSTOM_NAME,
                Component.literal("Manage Players (" + allowedCount + ")").withColor(0x55FFFF));
        playersBtn.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Click to add/remove players").withColor(0xAAAAAA))));
        container.setItem(PLAYERS_SLOT, playersBtn);

        // Lock toggle
        boolean locked = isLocked();
        ItemStack lockBtn;
        if (locked) {
            lockBtn = pane(Items.RED_STAINED_GLASS_PANE, "LOCKED by " + getLockedByName());
            lockBtn.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Click to unlock").withColor(0xAAAAAA))));
        } else {
            lockBtn = pane(Items.LIME_STAINED_GLASS_PANE, "Unlocked");
            lockBtn.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Click to lock").withColor(0xAAAAAA))));
        }
        container.setItem(LOCK_SLOT, lockBtn);

        // Close
        container.setItem(CLOSE_SLOT, pane(Items.BARRIER, "Close"));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (slotId < 0 || slotId >= SLOTS) {
            // Player inventory interaction — allow normal behavior
            if (slotId >= SLOTS) super.clicked(slotId, button, clickType, player);
            return;
        }

        var be = getBlockEntity();
        String dim = sp.level().dimension().identifier().toString();

        switch (slotId) {
            case FREQ_M10 -> adjustFrequency(be, dim, -10);
            case FREQ_M1 -> adjustFrequency(be, dim, -1);
            case FREQ_P1 -> adjustFrequency(be, dim, 1);
            case FREQ_P10 -> adjustFrequency(be, dim, 10);
            case RANGE_M5 -> { if (isSpeaker) adjustRange(be, dim, -5); }
            case RANGE_P5 -> { if (isSpeaker) adjustRange(be, dim, 5); }
            case VOL_DOWN -> { if (isSpeaker) adjustVolume(be, -0.01f); }
            case VOL_UP -> { if (isSpeaker) adjustVolume(be, 0.01f); }
            case PLAYERS_SLOT -> openPlayerManager(sp);
            case SONGS_SLOT -> openSongList(sp);
            case MODE_SLOT -> { if (isSpeaker) toggleMusicMode(be, sp); }
            case LOCK_SLOT -> toggleLock(be, sp);
            case CLOSE_SLOT -> sp.closeContainer();
        }
    }

    private void adjustFrequency(BlockEntity be, String dim, int delta) {
        if (be instanceof MicrophoneBlockEntity mic) {
            int oldFreq = mic.getFrequency();
            mic.setFrequency(oldFreq + delta);
            // Update RadioManager
            var rm = RadioManager.getInstance();
            if (rm != null && mic.isActive()) {
                rm.unregisterMic(blockPos, dim);
                rm.registerMic(blockPos, dim, mic.getFrequency());
            }
        } else if (be instanceof SpeakerBlockEntity spk) {
            int oldFreq = spk.getFrequency();
            spk.setFrequency(oldFreq + delta);
            var rm = RadioManager.getInstance();
            if (rm != null) {
                rm.unregisterSpeaker(blockPos, dim);
                rm.registerSpeaker(blockPos, dim, spk.getFrequency(), spk.getRange());
            }
        }
        refreshDisplay();
        broadcastChanges();
    }

    private void adjustRange(BlockEntity be, String dim, float delta) {
        if (be instanceof SpeakerBlockEntity spk) {
            spk.setRange(spk.getRange() + delta);
            var rm = RadioManager.getInstance();
            if (rm != null) {
                rm.unregisterSpeaker(blockPos, dim);
                rm.registerSpeaker(blockPos, dim, spk.getFrequency(), spk.getRange());
            }
        }
        refreshDisplay();
        broadcastChanges();
    }

    private void toggleMusicMode(BlockEntity be, ServerPlayer sp) {
        if (!(be instanceof SpeakerBlockEntity spk)) return;
        boolean newMode = !spk.isMusicMode();
        spk.setMusicMode(newMode);

        String dim = sp.level().dimension().identifier().toString();
        var rm = RadioManager.getInstance();

        if (newMode && RadioMusicPlayer.hasMusic()) {
            // Unregister from speaker frequency so mic audio doesn't route here
            if (rm != null) rm.unregisterSpeaker(blockPos, dim);

            var vcPlugin = RadioVoicechatPlugin.getServerApi();
            var vcApi = RadioVoicechatPlugin.getApi();
            if (vcPlugin != null && vcApi != null && sp.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                RadioMusicPlayer.startPlaying(vcPlugin, vcApi, blockPos, sl, spk.getSongIndex(), spk.getVolume());
            }
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Radio: Music Mode ON").withColor(0x55FF55), true);
        } else {
            RadioMusicPlayer.stopPlaying(blockPos);

            // Re-register on the speaker frequency so mic audio routes here again
            if (rm != null) rm.registerSpeaker(blockPos, dim, spk.getFrequency(), spk.getRange());

            sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Radio: Speaker Mode").withColor(0xFFAA00), true);
        }

        refreshDisplay();
        broadcastChanges();
    }

    private void adjustVolume(BlockEntity be, float delta) {
        if (be instanceof SpeakerBlockEntity spk) {
            spk.setVolume(spk.getVolume() + delta);
            // Update live playback volume without restarting the song
            if (spk.isMusicMode() && RadioMusicPlayer.isPlaying(blockPos)) {
                RadioMusicPlayer.setVolume(blockPos, spk.getVolume());
            }
        }
        refreshDisplay();
        broadcastChanges();
    }

    private void toggleLock(BlockEntity be, ServerPlayer player) {
        if (be instanceof MicrophoneBlockEntity mic) {
            if (mic.isLocked()) {
                if (mic.canAccess(player.getUUID())) {
                    mic.unlock();
                } else {
                    player.sendSystemMessage(Component.literal(
                            "Locked by " + mic.getLockedByName() + "!").withColor(0xFF5555));
                    return;
                }
            } else {
                mic.lock(player.getUUID(), player.getName().getString());
            }
        } else if (be instanceof SpeakerBlockEntity spk) {
            if (spk.isLocked()) {
                if (spk.canAccess(player.getUUID())) {
                    spk.unlock();
                } else {
                    player.sendSystemMessage(Component.literal(
                            "Locked by " + spk.getLockedByName() + "!").withColor(0xFF5555));
                    return;
                }
            } else {
                spk.lock(player.getUUID(), player.getName().getString());
            }
        }
        refreshDisplay();
        broadcastChanges();
    }

    private void openPlayerManager(ServerPlayer sp) {
        sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (cid, inv, p) -> new RadioPlayerMenu(cid, inv, sp, blockPos, isSpeaker),
                Component.literal("Manage Allowed Players")
        ));
    }

    private void openSongList(ServerPlayer sp) {
        sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (cid, inv, p) -> new RadioSongMenu(cid, inv, sp, blockPos),
                Component.literal("Song List")
        ));
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
