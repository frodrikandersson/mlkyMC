package com.mlkymc.grave;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stores data for a player's grave.
 */
public class GraveData {
    public final UUID ownerUUID;
    public final String ownerName;
    public final BlockPos pos;
    public final String dimension;
    public final long deathTime; // game tick when player died
    public final List<ItemStack> items;
    public final int xpPoints;

    public GraveData(UUID ownerUUID, String ownerName, BlockPos pos, String dimension, long deathTime, List<ItemStack> items, int xpPoints) {
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.pos = pos;
        this.dimension = dimension;
        this.deathTime = deathTime;
        this.items = new ArrayList<>(items);
        this.xpPoints = xpPoints;
    }

    /**
     * Can another player (not the owner) access this grave?
     * Only after 10 minutes (12000 ticks).
     */
    public boolean canOtherPlayerAccess(long currentTime) {
        return currentTime - deathTime >= 1200; // 60 seconds
    }

    /**
     * Can a Cleric resurrect from this grave cheaply? (within 60 seconds = 1200 ticks)
     */
    public boolean isWithinResurrectionWindow(long currentTime) {
        return currentTime - deathTime <= 1200;
    }
}
