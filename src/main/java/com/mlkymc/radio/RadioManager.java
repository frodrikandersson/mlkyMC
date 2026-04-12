package com.mlkymc.radio;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mlkymc.MlkyMC;
import net.minecraft.core.BlockPos;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-side singleton that tracks all Microphone and Speaker block positions
 * and their frequencies. Used by RadioVoicechatPlugin to route audio from mics
 * to speakers on the same frequency. Persisted to mlkymc_radio.json.
 */
public class RadioManager {

    private static RadioManager INSTANCE;

    private final List<RadioEntry> mics = new CopyOnWriteArrayList<>();
    private final List<RadioEntry> speakers = new CopyOnWriteArrayList<>();
    private Path saveFile;
    private boolean dirty = false;

    public record RadioEntry(int x, int y, int z, String dimension, int frequency, float range) {
        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }

        public static RadioEntry fromMic(BlockPos pos, String dim, int freq) {
            return new RadioEntry(pos.getX(), pos.getY(), pos.getZ(), dim, freq, 0);
        }

        public static RadioEntry fromSpeaker(BlockPos pos, String dim, int freq, float range) {
            return new RadioEntry(pos.getX(), pos.getY(), pos.getZ(), dim, freq, range);
        }

        public boolean matchesPos(BlockPos pos, String dim) {
            return x == pos.getX() && y == pos.getY() && z == pos.getZ() && dimension.equals(dim);
        }
    }

    public static RadioManager getInstance() {
        return INSTANCE;
    }

    // --- Mic registration ---

    public void registerMic(BlockPos pos, String dim, int frequency) {
        unregisterMic(pos, dim); // remove stale entry if any
        mics.add(RadioEntry.fromMic(pos, dim, frequency));
        dirty = true;
        save();
    }

    public void unregisterMic(BlockPos pos, String dim) {
        if (mics.removeIf(e -> e.matchesPos(pos, dim))) {
            dirty = true;
            save();
        }
    }

    public List<RadioEntry> getActiveMics() {
        return mics;
    }

    // --- Speaker registration ---

    public void registerSpeaker(BlockPos pos, String dim, int frequency, float range) {
        unregisterSpeaker(pos, dim); // remove stale entry if any
        speakers.add(RadioEntry.fromSpeaker(pos, dim, frequency, range));
        dirty = true;
        save();
    }

    public void unregisterSpeaker(BlockPos pos, String dim) {
        if (speakers.removeIf(e -> e.matchesPos(pos, dim))) {
            dirty = true;
            save();
        }
    }

    public List<RadioEntry> getSpeakersOnFrequency(int frequency) {
        var result = new ArrayList<RadioEntry>();
        for (var s : speakers) {
            if (s.frequency() == frequency) result.add(s);
        }
        return result;
    }

    // --- Persistence ---

    public void reload(Path worldDataDir) {
        INSTANCE = this;
        saveFile = worldDataDir.resolve("mlkymc_radio.json");
        mics.clear();
        speakers.clear();

        if (Files.exists(saveFile)) {
            try (Reader reader = Files.newBufferedReader(saveFile)) {
                var data = new Gson().fromJson(reader, SaveData.class);
                if (data != null) {
                    if (data.mics != null) mics.addAll(data.mics);
                    if (data.speakers != null) speakers.addAll(data.speakers);
                }
            } catch (Exception e) {
                MlkyMC.LOGGER.warn("[Radio] Failed to load radio data", e);
            }
        }
        MlkyMC.LOGGER.info("[Radio] Loaded {} mics, {} speakers", mics.size(), speakers.size());
    }

    public void save() {
        if (saveFile == null) return;
        try {
            Files.createDirectories(saveFile.getParent());
            var data = new SaveData();
            data.mics = new ArrayList<>(mics);
            data.speakers = new ArrayList<>(speakers);
            try (Writer writer = Files.newBufferedWriter(saveFile)) {
                new Gson().toJson(data, writer);
            }
            dirty = false;
        } catch (Exception e) {
            MlkyMC.LOGGER.warn("[Radio] Failed to save radio data", e);
        }
    }

    private static class SaveData {
        List<RadioEntry> mics;
        List<RadioEntry> speakers;
    }
}
