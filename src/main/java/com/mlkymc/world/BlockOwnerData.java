package com.mlkymc.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores block ownership data (which player placed which block).
 * Uses a dirty flag and periodic saves instead of saving on every change.
 */
public class BlockOwnerData {

    private static final Gson GSON = new GsonBuilder().create(); // No pretty printing for speed
    private static BlockOwnerData instance;

    private final Map<String, OwnerEntry> owners = new ConcurrentHashMap<>();
    private Path savePath;
    private boolean dirty = false;
    private long lastSaveTime = 0;
    private static final long SAVE_INTERVAL_MS = 30_000; // Save at most every 30 seconds

    public record OwnerEntry(String uuid, String name) {}

    public static BlockOwnerData get(MinecraftServer server) {
        if (instance == null) {
            instance = new BlockOwnerData();
            instance.savePath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("mlkymc_block_owners.json");
            instance.load();
        }
        return instance;
    }

    public static void reset() {
        if (instance != null) {
            instance.saveNow(); // Force save on shutdown
            instance = null;
        }
    }

    public void setOwner(BlockPos pos, UUID uuid, String name) {
        owners.put(String.valueOf(pos.asLong()), new OwnerEntry(uuid.toString(), name));
        dirty = true;
    }

    public void removeOwner(BlockPos pos) {
        owners.remove(String.valueOf(pos.asLong()));
        dirty = true;
    }

    public UUID getOwnerUUID(BlockPos pos) {
        var entry = owners.get(String.valueOf(pos.asLong()));
        if (entry == null) return null;
        try { return UUID.fromString(entry.uuid); }
        catch (IllegalArgumentException e) { return null; }
    }

    public String getOwnerName(BlockPos pos) {
        var entry = owners.get(String.valueOf(pos.asLong()));
        return entry != null ? entry.name : null;
    }

    public boolean hasOwner(BlockPos pos) {
        return owners.containsKey(String.valueOf(pos.asLong()));
    }

    public OwnerEntry getOwner(BlockPos pos) {
        return owners.get(String.valueOf(pos.asLong()));
    }

    public int totalCount() {
        return owners.size();
    }

    /** Returns all entries in the ownership map. Keys are stringified BlockPos longs. */
    public java.util.Set<java.util.Map.Entry<String, OwnerEntry>> getAllEntries() {
        return owners.entrySet();
    }

    /**
     * Get all owned blocks in loaded chunks for the given level.
     * Works regardless of player proximity — any owned block in a loaded chunk is returned.
     */
    public java.util.Map<net.minecraft.core.BlockPos, OwnerEntry> getLoadedOwned(net.minecraft.server.level.ServerLevel level) {
        java.util.Map<net.minecraft.core.BlockPos, OwnerEntry> result = new java.util.HashMap<>();
        for (var entry : owners.entrySet()) {
            long posLong = Long.parseLong(entry.getKey());
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(posLong);
            if (level.isLoaded(pos)) {
                result.put(pos, entry.getValue());
            }
        }
        return result;
    }

    /**
     * Get all owned blocks near a position (within radius).
     */
    public java.util.Map<net.minecraft.core.BlockPos, OwnerEntry> getOwnersNear(net.minecraft.core.BlockPos center, int radius) {
        java.util.Map<net.minecraft.core.BlockPos, OwnerEntry> result = new java.util.HashMap<>();
        int radiusSq = radius * radius;
        for (var entry : owners.entrySet()) {
            long posLong = Long.parseLong(entry.getKey());
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(posLong);
            if (pos.distSqr(center) <= radiusSq) {
                result.put(pos, entry.getValue());
            }
        }
        return result;
    }

    /**
     * Called periodically (e.g., from level tick) to flush dirty data.
     */
    public void tickSave() {
        if (!dirty) return;
        long now = System.currentTimeMillis();
        if (now - lastSaveTime < SAVE_INTERVAL_MS) return;
        saveNow();
    }

    private void load() {
        if (savePath == null || !Files.exists(savePath)) return;
        try {
            String json = Files.readString(savePath);
            var type = new TypeToken<Map<String, OwnerEntry>>() {}.getType();
            Map<String, OwnerEntry> loaded = GSON.fromJson(json, type);
            if (loaded != null) owners.putAll(loaded);
        } catch (Exception e) {
            System.err.println("[mlkyMC] Failed to load block owner data: " + e.getMessage());
        }
    }

    private void saveNow() {
        if (savePath == null) return;
        try {
            Files.writeString(savePath, GSON.toJson(owners));
            dirty = false;
            lastSaveTime = System.currentTimeMillis();
        } catch (IOException e) {
            System.err.println("[mlkyMC] Failed to save block owner data: " + e.getMessage());
        }
    }

    // Keep public save() for backward compat (shutdown hooks, reset)
    public void save() {
        saveNow();
    }
}
