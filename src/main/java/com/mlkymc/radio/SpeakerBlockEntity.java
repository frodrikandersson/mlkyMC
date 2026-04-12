package com.mlkymc.radio;

import com.mlkymc.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SpeakerBlockEntity extends BlockEntity {

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER_BE.get(), pos, state);
    }

    public int getFrequency() {
        return getPersistentData().getIntOr("mlkymc_radio_frequency", 1);
    }

    public void setFrequency(int freq) {
        getPersistentData().putInt("mlkymc_radio_frequency", Math.max(1, Math.min(99, freq)));
        setChanged();
    }

    public float getRange() {
        return getPersistentData().getFloatOr("mlkymc_radio_range", 16.0f);
    }

    public void setRange(float range) {
        getPersistentData().putFloat("mlkymc_radio_range", Math.max(8.0f, Math.min(64.0f, range)));
        setChanged();
    }

    // --- Music mode ---

    public boolean isMusicMode() {
        return getPersistentData().getBooleanOr("mlkymc_radio_music_mode", false);
    }

    public void setMusicMode(boolean musicMode) {
        getPersistentData().putBoolean("mlkymc_radio_music_mode", musicMode);
        setChanged();
    }

    public int getSongIndex() {
        return getPersistentData().getIntOr("mlkymc_radio_song_index", 0);
    }

    public void setSongIndex(int index) {
        getPersistentData().putInt("mlkymc_radio_song_index", index);
        setChanged();
    }

    public float getVolume() {
        return getPersistentData().getFloatOr("mlkymc_radio_volume", 0.5f);
    }

    public void setVolume(float volume) {
        getPersistentData().putFloat("mlkymc_radio_volume", Math.max(0.01f, Math.min(1.0f, volume)));
        setChanged();
    }

    // --- Lock system (identical to MicrophoneBlockEntity) ---

    public String getLockedBy() {
        return getPersistentData().getStringOr("mlkymc_radio_locked_by", "");
    }

    public String getLockedByName() {
        return getPersistentData().getStringOr("mlkymc_radio_locked_by_name", "");
    }

    public boolean isLocked() {
        return !getLockedBy().isEmpty();
    }

    public void lock(UUID playerUuid, String playerName) {
        getPersistentData().putString("mlkymc_radio_locked_by", playerUuid.toString());
        getPersistentData().putString("mlkymc_radio_locked_by_name", playerName);
        setChanged();
    }

    public void unlock() {
        getPersistentData().remove("mlkymc_radio_locked_by");
        getPersistentData().remove("mlkymc_radio_locked_by_name");
        setChanged();
    }

    public boolean canAccess(UUID playerUuid) {
        if (!isLocked()) return true;
        if (getLockedBy().equals(playerUuid.toString())) return true;
        return getAllowedPlayers().contains(playerUuid.toString());
    }

    public Set<String> getAllowedPlayers() {
        Set<String> allowed = new HashSet<>();
        var list = getPersistentData().getListOrEmpty("mlkymc_radio_allowed");
        for (int i = 0; i < list.size(); i++) {
            var tag = list.get(i);
            if (tag instanceof StringTag st) {
                allowed.add(st.value());
            }
        }
        return allowed;
    }

    public void addAllowedPlayer(UUID uuid) {
        var allowed = getAllowedPlayers();
        allowed.add(uuid.toString());
        saveAllowedPlayers(allowed);
    }

    public void removeAllowedPlayer(UUID uuid) {
        var allowed = getAllowedPlayers();
        allowed.remove(uuid.toString());
        saveAllowedPlayers(allowed);
    }

    private void saveAllowedPlayers(Set<String> allowed) {
        ListTag list = new ListTag();
        for (String uuid : allowed) {
            list.add(StringTag.valueOf(uuid));
        }
        getPersistentData().put("mlkymc_radio_allowed", list);
        setChanged();
    }
}
