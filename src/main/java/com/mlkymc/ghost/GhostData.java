package com.mlkymc.ghost;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-ghost persistent data for the Spectral Energy system.
 * Tracks SE, accumulated total (for ability unlocks), mimic state, haunt zone.
 */
public class GhostData {
    public UUID ghostUuid;
    public int spectralEnergy;         // 0-1000
    public int totalAccumulatedSE;     // lifetime total, for ability unlock gates
    public long ghostSinceMs;          // System.currentTimeMillis when became ghost

    // Mimic state
    public String activeMimic;        // null, "BAT", "CREEPER", "ENDERMAN"
    public long mimicExpiryMs;        // System.currentTimeMillis when mimic ends
    public long mimicCooldownExpiryMs; // 60s cooldown after any form ends
    public float mimicStartHP;        // HP when mimic started, for damage break check
    public transient net.minecraft.world.entity.Mob mimicEntity; // the visible mob following the ghost
    public transient boolean hadFlightBefore; // track if player had flight before bat mimic
    public transient boolean mimicTookDamage; // set true if ghost takes ANY damage during mimic

    // Haunt zone
    public BlockPos hauntZonePos;     // null if no active zone
    public String hauntZoneDimension;
    public long hauntZoneStartMs;

    // Passive gain tracking
    public long lastGainTickMs;
    public transient int gainTickCounter; // not saved, resets on restart

    public GhostData(UUID uuid) {
        this.ghostUuid = uuid;
        this.spectralEnergy = 0;
        this.totalAccumulatedSE = 0;
        this.ghostSinceMs = System.currentTimeMillis();
        this.activeMimic = null;
        this.mimicExpiryMs = 0;
        this.hauntZonePos = null;
        this.lastGainTickMs = System.currentTimeMillis();
    }

    public void addSpectralEnergy(int amount) {
        int before = spectralEnergy;
        spectralEnergy = Math.max(0, Math.min(1000, spectralEnergy + amount));
        if (amount > 0) {
            totalAccumulatedSE += (spectralEnergy - before);
        }
    }

    public boolean canMimic() { return true; }
    public boolean canHaunt() { return true; }
    public boolean isMimicking() {
        return activeMimic != null && System.currentTimeMillis() < mimicExpiryMs;
    }

    public boolean isMimicOnCooldown() {
        return System.currentTimeMillis() < mimicCooldownExpiryMs;
    }

    public int getMimicCooldownSeconds() {
        long remaining = mimicCooldownExpiryMs - System.currentTimeMillis();
        return remaining > 0 ? (int) (remaining / 1000) : 0;
    }

    public boolean hasHauntZone() {
        return hauntZonePos != null;
    }

    // --- Serialization ---

    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("uuid", ghostUuid.toString());
        data.put("spectralEnergy", spectralEnergy);
        data.put("totalAccumulatedSE", totalAccumulatedSE);
        data.put("ghostSinceMs", ghostSinceMs);
        data.put("lastGainTickMs", lastGainTickMs);

        if (activeMimic != null) {
            data.put("activeMimic", activeMimic);
            data.put("mimicExpiryMs", mimicExpiryMs);
        }
        data.put("mimicCooldownExpiryMs", mimicCooldownExpiryMs);
        if (hauntZonePos != null) {
            data.put("hauntX", hauntZonePos.getX());
            data.put("hauntY", hauntZonePos.getY());
            data.put("hauntZ", hauntZonePos.getZ());
            data.put("hauntDim", hauntZoneDimension);
            data.put("hauntStartMs", hauntZoneStartMs);
        }
        return data;
    }

    public static GhostData deserialize(Map<String, Object> data) {
        UUID uuid = UUID.fromString((String) data.get("uuid"));
        GhostData gd = new GhostData(uuid);
        gd.spectralEnergy = ((Number) data.get("spectralEnergy")).intValue();
        gd.totalAccumulatedSE = ((Number) data.get("totalAccumulatedSE")).intValue();
        gd.ghostSinceMs = ((Number) data.get("ghostSinceMs")).longValue();
        if (data.containsKey("lastGainTickMs")) {
            gd.lastGainTickMs = ((Number) data.get("lastGainTickMs")).longValue();
        }
        if (data.containsKey("activeMimic")) {
            gd.activeMimic = (String) data.get("activeMimic");
            gd.mimicExpiryMs = ((Number) data.get("mimicExpiryMs")).longValue();
        }
        if (data.containsKey("mimicCooldownExpiryMs")) {
            gd.mimicCooldownExpiryMs = ((Number) data.get("mimicCooldownExpiryMs")).longValue();
        }
        if (data.containsKey("hauntX")) {
            gd.hauntZonePos = new BlockPos(
                    ((Number) data.get("hauntX")).intValue(),
                    ((Number) data.get("hauntY")).intValue(),
                    ((Number) data.get("hauntZ")).intValue());
            gd.hauntZoneDimension = (String) data.get("hauntDim");
            gd.hauntZoneStartMs = ((Number) data.get("hauntStartMs")).longValue();
        }
        return gd;
    }
}
