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

    private Path dataFile;
    // Key: "dimension,x,y,z" -> spawn count
    private Map<String, Integer> spawnCounts = new HashMap<>();

    public SpawnerAgingManager(Path configDir) {
        this.dataFile = configDir.resolve("spawner_aging.json");
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

    public void reload(Path dir) {
        this.dataFile = dir.resolve("spawner_aging.json");
        load();
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

    public Map<String, Integer> getSpawnCounts() {
        return spawnCounts;
    }

    /**
     * Check all tracked spawners on server start. If any have reached their limit,
     * deplete them (replace with empty spawner). This handles the case where a spawner
     * reached its limit but the server restarted before the block was replaced.
     */
    public void checkExpiredSpawners(net.minecraft.server.MinecraftServer server) {
        if (spawnCounts.isEmpty()) return;

        var toRemove = new java.util.ArrayList<String>();

        for (var entry : spawnCounts.entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue();

            // Parse key: "dimension,x,y,z"
            String[] parts = key.split(",", 4);
            if (parts.length < 4) continue;

            String dimension = parts[0];
            int x, y, z;
            try {
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) { continue; }

            // Find the mob type from the spawner block entity to get the limit
            var dimKey = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.Identifier.parse(dimension));
            var level = server.getLevel(dimKey);
            if (level == null) continue;

            var pos = new net.minecraft.core.BlockPos(x, y, z);

            // Check if the chunk is loaded — don't force-load chunks
            if (!level.isLoaded(pos)) continue;

            var state = level.getBlockState(pos);
            if (!state.is(net.minecraft.world.level.block.Blocks.SPAWNER)) {
                // Block is no longer a spawner — clean up
                toRemove.add(key);
                continue;
            }

            // Get mob type and check limit
            int maxSpawns = com.mlkymc.config.MlkyConfig.getSpawnerDefaultMaxSpawns();
            var be = level.getBlockEntity(pos);
            if (be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawnerBE) {
                try {
                    var spawner = spawnerBE.getSpawner();
                    var entityField = spawner.getClass().getDeclaredField("nextSpawnData");
                    entityField.setAccessible(true);
                    var spawnData = entityField.get(spawner);
                    if (spawnData instanceof net.minecraft.world.level.SpawnData sd) {
                        var entityTag = sd.entityToSpawn();
                        if (entityTag.contains("id")) {
                            String mobId = entityTag.getStringOr("id", "");
                            String mobName = mobId.contains(":") ? mobId.split(":")[1] : mobId;
                            var mobLimits = com.mlkymc.config.MlkyConfig.getSpawnerMobLimits();
                            if (mobLimits != null) {
                                maxSpawns = mobLimits.getLimit(mobName, maxSpawns);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (count >= maxSpawns) {
                // Deplete: replace with empty spawner
                level.removeBlockEntity(pos);
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.SPAWNER.defaultBlockState(), 3);
                toRemove.add(key);

                MlkyMC.LOGGER.info("Depleted spawner at {},{},{} in {} on startup (count {} >= limit {})",
                        x, y, z, dimension, count, maxSpawns);
            }
        }

        for (String key : toRemove) {
            spawnCounts.remove(key);
        }
        if (!toRemove.isEmpty()) save();
    }
}
