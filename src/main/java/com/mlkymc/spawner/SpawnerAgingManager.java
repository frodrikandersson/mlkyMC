package com.mlkymc.spawner;

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
import java.util.HashMap;
import java.util.Map;

public class SpawnerAgingManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Integer>>() {}.getType();

    private final Path dataFile;
    // Key: "dimension,x,y,z" -> spawn count
    private Map<String, Integer> spawnCounts = new HashMap<>();

    public SpawnerAgingManager(Path configDir) {
        this.dataFile = configDir.resolve("spawner_aging.json");
        load();
    }

    public void load() {
        if (Files.exists(dataFile)) {
            try (Reader reader = Files.newBufferedReader(dataFile)) {
                Map<String, Integer> loaded = GSON.fromJson(reader, DATA_TYPE);
                if (loaded != null) spawnCounts = loaded;
            } catch (IOException e) {
                MlkyMC.LOGGER.error("Failed to load spawner_aging.json", e);
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer writer = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(spawnCounts, writer);
            }
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save spawner_aging.json", e);
        }
    }

    public static String makeKey(String dimension, int x, int y, int z) {
        return dimension + "," + x + "," + y + "," + z;
    }

    public int getSpawnCount(String key) {
        return spawnCounts.getOrDefault(key, 0);
    }

    public int incrementAndGet(String key) {
        int count = spawnCounts.getOrDefault(key, 0) + 1;
        spawnCounts.put(key, count);
        return count;
    }

    public void remove(String key) {
        spawnCounts.remove(key);
    }
}
