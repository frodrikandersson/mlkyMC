package com.mlkymc.radio;

import com.mlkymc.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MicrophoneBlockEntity extends BlockEntity {

    public MicrophoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MICROPHONE_BE.get(), pos, state);
    }

    public int getFrequency() {
        return getPersistentData().getIntOr("mlkymc_radio_frequency", 1);
    }

    public void setFrequency(int freq) {
        getPersistentData().putInt("mlkymc_radio_frequency", Math.max(1, Math.min(99, freq)));
        setChanged();
    }

    public boolean isActive() {
        return getPersistentData().getBooleanOr("mlkymc_radio_active", false);
    }

    public void setActive(boolean active) {
        getPersistentData().putBoolean("mlkymc_radio_active", active);
        setChanged();
    }

    // --- Lock system ---

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
