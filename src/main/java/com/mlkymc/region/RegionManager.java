package com.mlkymc.region;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mlkymc.MlkyMC;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class RegionManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Region>>() {}.getType();

    private Path dataFile;
    private Map<String, Region> regions = new HashMap<>();

    // Per-player selection state (pos1/pos2 for wand)
    private final Map<UUID, int[]> pos1 = new HashMap<>();
    private final Map<UUID, int[]> pos2 = new HashMap<>();
    private final Map<UUID, String> posDim = new HashMap<>();

    public RegionManager(Path configDir) {
        this.dataFile = configDir.resolve("regions.json");
    }

    public Region getRegion(String name) {
        return regions.get(name.toLowerCase());
    }

    public Region createRegion(String name, String dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
        Region region = new Region(name.toLowerCase(), dimension, x1, y1, z1, x2, y2, z2);
        regions.put(region.name, region);
        save();
        return region;
    }

    public boolean deleteRegion(String name) {
        Region removed = regions.remove(name.toLowerCase());
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public Collection<Region> getAllRegions() {
        return regions.values();
    }

    /**
     * Check if a flag is active at the given position (any region).
     */
    public boolean isFlagActiveAt(RegionFlag flag, String dimension, int x, int y, int z) {
        for (Region region : regions.values()) {
            if (region.contains(dimension, x, y, z) && region.hasFlag(flag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all regions containing a position, sorted by priority (highest first).
     */
    public List<Region> getRegionsAt(String dimension, int x, int y, int z) {
        List<Region> result = new ArrayList<>();
        for (Region region : regions.values()) {
            if (region.contains(dimension, x, y, z)) {
                result.add(region);
            }
        }
        result.sort(Comparator.comparingInt((Region r) -> r.priority).reversed());
        return result;
    }

    // Selection wand helpers
    public void setPos1(UUID player, String dimension, int x, int y, int z) {
        pos1.put(player, new int[]{x, y, z});
        posDim.put(player, dimension);
    }

    public void setPos2(UUID player, String dimension, int x, int y, int z) {
        pos2.put(player, new int[]{x, y, z});
        posDim.put(player, dimension);
    }

    public int[] getPos1(UUID player) { return pos1.get(player); }
    public int[] getPos2(UUID player) { return pos2.get(player); }
    public String getPosDim(UUID player) { return posDim.get(player); }

    public void clearSelection(UUID player) {
        pos1.remove(player);
        pos2.remove(player);
        posDim.remove(player);
    }

    public void load() {
        if (Files.exists(dataFile)) {
            try (Reader reader = Files.newBufferedReader(dataFile)) {
                Map<String, Region> loaded = GSON.fromJson(reader, DATA_TYPE);
                if (loaded != null) regions = loaded;
            } catch (IOException e) {
                MlkyMC.LOGGER.error("Failed to load regions.json", e);
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer writer = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(regions, writer);
            }
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save regions.json", e);
        }
    }

    public void reload(Path dir) {
        this.dataFile = dir.resolve("regions.json");
        load();
        loadSpawn(dir);
    }

    // --- Exact spawn point ---
    private double spawnX, spawnY, spawnZ;
    private float spawnYaw, spawnPitch;
    private boolean hasExactSpawn = false;
    private Path spawnFile;

    public void setExactSpawn(double x, double y, double z, float yaw, float pitch) {
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.spawnYaw = yaw;
        this.spawnPitch = pitch;
        this.hasExactSpawn = true;
        saveSpawn();
    }

    public boolean hasExactSpawn() { return hasExactSpawn; }
    public double getSpawnX() { return spawnX; }
    public double getSpawnY() { return spawnY; }
    public double getSpawnZ() { return spawnZ; }
    public float getSpawnYaw() { return spawnYaw; }
    public float getSpawnPitch() { return spawnPitch; }

    private void loadSpawn(Path dir) {
        spawnFile = dir.resolve("spawn.json");
        if (Files.exists(spawnFile)) {
            try (Reader reader = Files.newBufferedReader(spawnFile)) {
                var data = new Gson().fromJson(reader, com.google.gson.JsonObject.class);
                if (data != null && data.has("x")) {
                    spawnX = data.get("x").getAsDouble();
                    spawnY = data.get("y").getAsDouble();
                    spawnZ = data.get("z").getAsDouble();
                    spawnYaw = data.has("yaw") ? data.get("yaw").getAsFloat() : 0;
                    spawnPitch = data.has("pitch") ? data.get("pitch").getAsFloat() : 0;
                    hasExactSpawn = true;
                }
            } catch (Exception e) {
                MlkyMC.LOGGER.warn("Failed to load spawn.json", e);
            }
        }
    }

    private void saveSpawn() {
        if (spawnFile == null) return;
        try {
            Files.createDirectories(spawnFile.getParent());
            var obj = new com.google.gson.JsonObject();
            obj.addProperty("x", spawnX);
            obj.addProperty("y", spawnY);
            obj.addProperty("z", spawnZ);
            obj.addProperty("yaw", spawnYaw);
            obj.addProperty("pitch", spawnPitch);
            try (Writer writer = Files.newBufferedWriter(spawnFile)) {
                GSON.toJson(obj, writer);
            }
        } catch (Exception e) {
            MlkyMC.LOGGER.warn("Failed to save spawn.json", e);
        }
    }
}
