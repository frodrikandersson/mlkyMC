package com.mlkymc.world;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks player-placed ore blocks persistently.
 * Prevents silk touch → place → mine exploit for bonus drops.
 * Stored as packed BlockPos longs with dimension prefix.
 */
public class PlacedOreTracker {

    private static final Gson GSON = new Gson();
    private static PlacedOreTracker INSTANCE;

    private final Path saveFile;
    private final Set<String> placedOres = new HashSet<>(); // "dim:x,y,z"
    private boolean dirty = false;

    private PlacedOreTracker(Path saveFile) {
        this.saveFile = saveFile;
        load();
    }

    public static PlacedOreTracker get(MinecraftServer server) {
        if (INSTANCE == null) {
            Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            INSTANCE = new PlacedOreTracker(worldDir.resolve("mlkymc_placed_ores.json"));
        }
        return INSTANCE;
    }

    public static void invalidate() {
        if (INSTANCE != null && INSTANCE.dirty) {
            INSTANCE.save();
        }
        INSTANCE = null;
    }

    private static String key(String dimension, BlockPos pos) {
        return dimension + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public void markPlaced(String dimension, BlockPos pos) {
        if (placedOres.add(key(dimension, pos))) {
            dirty = true;
        }
    }

    public void markBroken(String dimension, BlockPos pos) {
        if (placedOres.remove(key(dimension, pos))) {
            dirty = true;
        }
    }

    public boolean isPlayerPlaced(String dimension, BlockPos pos) {
        return placedOres.contains(key(dimension, pos));
    }

    /**
     * Check if a block ID represents an ore block.
     */
    public static boolean isOre(String blockId) {
        return blockId.contains("ore") || blockId.contains("ancient_debris");
    }

    /**
     * Check if a block should be tracked as player-placed to prevent the
     * "place then mine for XP / debuff farming" exploit. Must stay in sync with the
     * isMining substring list in DebuffHandler.onBlockDrop — any block that can roll
     * the MineCrafter debuff on break must also be trackable on place.
     */
    public static boolean isTrackedBlock(String blockId) {
        return blockId.contains("ore")
                || blockId.contains("ancient_debris")
                || blockId.contains("deepslate")
                || blockId.contains("stone")
                || blockId.contains("log") || blockId.contains("wood")
                || blockId.contains("stem") || blockId.contains("hyphae")
                || blockId.contains("granite") || blockId.contains("diorite")
                || blockId.contains("andesite") || blockId.contains("tuff")
                || blockId.contains("calcite") || blockId.contains("dripstone")
                || blockId.contains("basalt") || blockId.contains("blackstone")
                || blockId.contains("netherrack") || blockId.contains("end_stone")
                || blockId.contains("obsidian") || blockId.contains("sandstone")
                || blockId.contains("terracotta")
                || blockId.contains("sugar_cane")
                || blockId.contains("carved_pumpkin");
    }

    /**
     * Check if a block ID represents a natural stone block (not ores, not crafted variants).
     */
    public static boolean isStone(String blockId) {
        return blockId.equals("block.minecraft.stone")
                || blockId.equals("block.minecraft.deepslate")
                || blockId.equals("block.minecraft.granite")
                || blockId.equals("block.minecraft.diorite")
                || blockId.equals("block.minecraft.andesite")
                || blockId.equals("block.minecraft.tuff")
                || blockId.equals("block.minecraft.calcite");
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
            dirty = false;
        }
    }

    private void load() {
        if (!Files.exists(saveFile)) return;
        try (Reader reader = Files.newBufferedReader(saveFile)) {
            Type type = new TypeToken<Set<String>>() {}.getType();
            Set<String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                placedOres.addAll(loaded);
            }
        } catch (IOException e) {
            com.mlkymc.MlkyMC.LOGGER.warn("Failed to load placed ore data", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(saveFile.getParent());
            try (Writer writer = Files.newBufferedWriter(saveFile)) {
                GSON.toJson(placedOres, writer);
            }
        } catch (IOException e) {
            com.mlkymc.MlkyMC.LOGGER.warn("Failed to save placed ore data", e);
        }
    }
}
