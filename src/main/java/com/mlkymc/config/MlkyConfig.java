package com.mlkymc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mlkymc.MlkyMC;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class MlkyConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ConfigData data = new ConfigData();
    private static Path configFile;

    public static void init(Path configDir) {
        configFile = configDir.resolve("config.json");
        load();
    }

    public static void load() {
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                data = GSON.fromJson(reader, ConfigData.class);
                if (data == null) data = new ConfigData();
            } catch (IOException e) {
                MlkyMC.LOGGER.error("Failed to load config", e);
                data = new ConfigData();
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save config", e);
        }
    }

    // Ghost settings
    public static int getGhostSpeedAmplifier() { return data.ghost.speedAmplifier; }
    public static int getGhostJumpAmplifier() { return data.ghost.jumpAmplifier; }

    // Revive settings
    public static boolean getBroadcastRevive() { return data.revive.broadcastRevive; }
    public static String getReviveItem() { return data.revive.reviveItem; }

    // Economy settings
    public static double getMobDropChance() { return data.economy.mobDropChance; }
    public static int getMobDropMin() { return data.economy.mobDropMin; }
    public static int getMobDropMax() { return data.economy.mobDropMax; }

    // Dimension settings
    public static int getDimensionGoal(String dimension) {
        return dimension.equals("end") ? data.dimensions.end.goal : data.dimensions.nether.goal;
    }

    public static int getDimensionUnlockHours(String dimension) {
        return dimension.equals("end") ? data.dimensions.end.unlockHours : data.dimensions.nether.unlockHours;
    }

    // Spawner aging settings
    public static boolean isSpawnerAgingEnabled() { return data.spawnerAging.enabled; }
    public static int getSpawnerMaxSpawns() { return data.spawnerAging.maxSpawns; }

    // World settings
    public static boolean getDisableVillagerSpawning() { return data.world.disableVillagerSpawning; }

    // Twitch settings
    public static boolean isTwitchEnabled() { return data.twitch.enabled; }
    public static String getTwitchClientId() { return data.twitch.clientId; }
    public static String getTwitchAccessToken() { return data.twitch.accessToken; }
    public static String getTwitchRewardId() { return data.twitch.rewardId; }
    public static String getTwitchClientSecret() { return data.twitch.clientSecret; }
    public static int getStreamerPollInterval() { return data.twitch.streamerPollInterval; }

    public static void setTwitchAccessToken(String token) {
        data.twitch.accessToken = token;
        save();
    }

    // Config data classes
    public static class ConfigData {
        public GhostConfig ghost = new GhostConfig();
        public ReviveConfig revive = new ReviveConfig();
        public EconomyConfig economy = new EconomyConfig();
        public DimensionsConfig dimensions = new DimensionsConfig();
        public SpawnerAgingConfig spawnerAging = new SpawnerAgingConfig();
        public WorldConfig world = new WorldConfig();
        public TwitchConfig twitch = new TwitchConfig();
    }

    public static class GhostConfig {
        public int speedAmplifier = 2;
        public int jumpAmplifier = 2;
    }

    public static class ReviveConfig {
        public boolean broadcastRevive = true;
        public String reviveItem = "minecraft:nether_star";
    }

    public static class EconomyConfig {
        public double mobDropChance = 0.3;
        public int mobDropMin = 1;
        public int mobDropMax = 3;
    }

    public static class DimensionsConfig {
        public DimensionEntry nether = new DimensionEntry(500, 48);
        public DimensionEntry end = new DimensionEntry(1000, 24);
    }

    public static class DimensionEntry {
        public int goal;
        public int unlockHours;

        public DimensionEntry() { this(500, 48); }
        public DimensionEntry(int goal, int unlockHours) {
            this.goal = goal;
            this.unlockHours = unlockHours;
        }
    }

    public static class SpawnerAgingConfig {
        public boolean enabled = true;
        public int maxSpawns = 200;
    }

    public static class WorldConfig {
        public boolean disableVillagerSpawning = true;
    }

    public static class TwitchConfig {
        public boolean enabled = false;
        public String clientId = "";
        public String clientSecret = "";
        public String accessToken = "";
        public String rewardId = "";
        public int streamerPollInterval = 60;
    }
}
