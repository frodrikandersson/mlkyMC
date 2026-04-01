package com.mlkymc.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mlkymc.MlkyMC;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class JsonStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private Path dataFile;
    private StorageData data = new StorageData();

    public JsonStorage(Path configDir) {
        this.dataFile = configDir.resolve("data.json");
    }

    public void load() {
        if (Files.exists(dataFile)) {
            try (Reader reader = Files.newBufferedReader(dataFile)) {
                data = GSON.fromJson(reader, StorageData.class);
                if (data == null) data = new StorageData();
            } catch (IOException e) {
                MlkyMC.LOGGER.error("Failed to load data.json", e);
                data = new StorageData();
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer writer = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save data.json", e);
        }
    }

    public Set<UUID> getGhosts() {
        return data.ghosts;
    }

    public LocationData getStatueLocation() {
        return data.statueLocation;
    }

    public void setStatueLocation(LocationData location) {
        data.statueLocation = location;
        save();
    }

    public static class StorageData {
        public Set<UUID> ghosts = new HashSet<>();
        public LocationData statueLocation;
    }

    public static class LocationData {
        public String dimension;
        public double x, y, z;
        public float yaw, pitch;

        public LocationData() {}

        public LocationData(String dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    public void reload(Path dir) {
        this.dataFile = dir.resolve("data.json");
        load();
    }
}
