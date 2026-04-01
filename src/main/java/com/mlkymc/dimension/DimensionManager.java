package com.mlkymc.dimension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mlkymc.MlkyMC;
import com.mlkymc.config.MlkyConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DimensionManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private Path dataFile;
    private Map<String, DimensionData> dimensions = new HashMap<>();

    public DimensionManager(Path configDir) {
        this.dataFile = configDir.resolve("dimensions.json");
    }

    public int getProgress(String dimension) {
        checkExpired(dimension);
        return dimensions.computeIfAbsent(dimension, k -> new DimensionData()).contributed;
    }

    public int getGoal(String dimension) {
        return MlkyConfig.getDimensionGoal(dimension);
    }

    public int getUnlockHours(String dimension) {
        return MlkyConfig.getDimensionUnlockHours(dimension);
    }

    public int getTotalContributed(String dimension) {
        return dimensions.computeIfAbsent(dimension, k -> new DimensionData()).totalContributed;
    }

    public boolean isUnlocked(String dimension) {
        checkExpired(dimension);
        DimensionData data = dimensions.get(dimension);
        if (data == null || data.unlockTimestamp == 0) return false;
        long elapsed = System.currentTimeMillis() - data.unlockTimestamp;
        long duration = getUnlockHours(dimension) * 3600000L;
        return elapsed < duration;
    }

    /**
     * If the unlock period has expired, reset contributed back to 0
     * so the community needs to contribute again for the next unlock.
     */
    private void checkExpired(String dimension) {
        DimensionData data = dimensions.get(dimension);
        if (data == null || data.unlockTimestamp == 0) return;
        long elapsed = System.currentTimeMillis() - data.unlockTimestamp;
        long duration = getUnlockHours(dimension) * 3600000L;
        if (elapsed >= duration) {
            data.contributed = 0;
            data.unlockTimestamp = 0;
            save();
        }
    }

    public long getRemainingMillis(String dimension) {
        DimensionData data = dimensions.get(dimension);
        if (data == null || data.unlockTimestamp == 0) return 0;
        long elapsed = System.currentTimeMillis() - data.unlockTimestamp;
        long duration = getUnlockHours(dimension) * 3600000L;
        return Math.max(0, duration - elapsed);
    }

    public void contribute(String dimension, int amount) {
        DimensionData data = dimensions.computeIfAbsent(dimension, k -> new DimensionData());
        data.contributed += amount;
        data.totalContributed += amount;
        if (data.contributed >= getGoal(dimension) && data.unlockTimestamp == 0) {
            data.unlockTimestamp = System.currentTimeMillis();
        }
        save();
    }

    public void forceUnlock(String dimension) {
        DimensionData data = dimensions.computeIfAbsent(dimension, k -> new DimensionData());
        data.unlockTimestamp = System.currentTimeMillis();
        data.contributed = getGoal(dimension);
        save();
    }

    public void forceLock(String dimension) {
        DimensionData data = dimensions.computeIfAbsent(dimension, k -> new DimensionData());
        data.unlockTimestamp = 0;
        save();
    }

    public void reset(String dimension) {
        dimensions.put(dimension, new DimensionData());
        save();
    }

    public void load() {
        if (Files.exists(dataFile)) {
            try (Reader reader = Files.newBufferedReader(dataFile)) {
                var type = new com.google.gson.reflect.TypeToken<Map<String, DimensionData>>() {}.getType();
                Map<String, DimensionData> loaded = GSON.fromJson(reader, type);
                if (loaded != null) dimensions = loaded;
            } catch (IOException e) {
                MlkyMC.LOGGER.error("Failed to load dimensions.json", e);
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer writer = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(dimensions, writer);
            }
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save dimensions.json", e);
        }
    }

    public void reload(Path dir) {
        this.dataFile = dir.resolve("dimensions.json");
        load();
    }

    public static class DimensionData {
        public int contributed = 0;
        public int totalContributed = 0;
        public long unlockTimestamp = 0;
    }
}
