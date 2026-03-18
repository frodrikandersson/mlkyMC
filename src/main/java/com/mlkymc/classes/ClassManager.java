package com.mlkymc.classes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mlkymc.MlkyMC;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central manager for the class system.
 * Handles data persistence, class selection, XP/leveling, and lookups.
 */
public class ClassManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataFile;
    private final Map<UUID, ClassData> playerData = new HashMap<>();
    private final Map<UUID, Long> lastSyncTick = new HashMap<>();

    public ClassManager(Path configDir) {
        this.dataFile = configDir.resolve("class_data.json");
        load();
    }

    // --- Data Access ---

    public ClassData getOrCreate(UUID uuid) {
        return playerData.computeIfAbsent(uuid, ClassData::new);
    }

    public ClassData getOrCreate(ServerPlayer player) {
        return getOrCreate(player.getUUID());
    }

    // --- Class Selection ---

    /**
     * Attempt to set a player's class. Permanent once chosen.
     * Returns true if successful.
     */
    public boolean selectClass(ServerPlayer player, ClassType classType) {
        ClassData data = getOrCreate(player);
        if (data.hasChosenClass()) {
            player.sendSystemMessage(Component.literal("You have already chosen the ")
                    .append(data.getChosenClass().getColoredName())
                    .append(Component.literal(" class. This choice is permanent.")));
            return false;
        }
        if (classType == ClassType.NONE) {
            player.sendSystemMessage(Component.literal("Invalid class selection."));
            return false;
        }

        boolean success = data.setChosenClass(classType);
        if (success) {
            // Sync to client
            player.sendSystemMessage(Component.literal("[MLKYMC_SYNC:" + classType.name() + "]").withColor(0x000000));
            player.sendSystemMessage(Component.literal("You have chosen the ")
                    .append(classType.getColoredName())
                    .append(Component.literal(" class! This choice is permanent.")));
            player.sendSystemMessage(Component.literal("Press [K] to open your Skill Tree and view your class progression.")
                    .withStyle(style -> style.withColor(0x55FFFF)));
            save();
        }
        return success;
    }

    // --- XP & Leveling ---

    /**
     * Add XP to a player's profession. Handles 2x bonus for chosen class.
     * Notifies the player on level up.
     */
    public void addXp(ServerPlayer player, ProfessionType profession, int amount) {
        ClassData data = getOrCreate(player);
        int oldLevel = data.getLevel(profession);
        int levelsGained = data.addXp(profession, amount);

        // Sync XP to client (throttled to avoid spam — max once per second)
        long now = player.level().getGameTime();
        Long lastSync = lastSyncTick.get(player.getUUID());
        if (lastSync == null || now - lastSync >= 20 || levelsGained > 0) {
            sendLevelSync(player);
            lastSyncTick.put(player.getUUID(), now);
        }

        if (levelsGained > 0) {
            int newLevel = data.getLevel(profession);
            player.sendSystemMessage(Component.literal(profession.getDisplayName() + " leveled up! Level " + newLevel)
                    .withStyle(style -> style.withColor(profession.getMatchingClass().getColor())));

            // Check for debuff milestone notifications
            double oldDebuff = ProfessionType.getDebuffPercent(oldLevel);
            double newDebuff = ProfessionType.getDebuffPercent(newLevel);
            if (newDebuff < oldDebuff) {
                if (newDebuff == 0.0) {
                    player.sendSystemMessage(Component.literal(profession.getDisplayName() + " debuff fully removed!")
                            .withStyle(style -> style.withColor(0x55FF55)));
                } else {
                    int pct = (int) (newDebuff * 100);
                    player.sendSystemMessage(Component.literal(profession.getDisplayName() + " debuff reduced to -" + pct + "%")
                            .withStyle(style -> style.withColor(0xFFFF55)));
                }
            }

            save();
        }
    }

    /**
     * Send level data to client for GUI display.
     */
    public void sendLevelSync(ServerPlayer player) {
        ClassData data = getOrCreate(player);
        String syncTag = "[MLKYMC_SYNC:" + data.getChosenClass().name()
                + ":" + data.getLevel(ProfessionType.ADVENTURER)
                + ":" + data.getLevel(ProfessionType.CLERIC)
                + ":" + data.getLevel(ProfessionType.FARMHAND)
                + ":" + data.getLevel(ProfessionType.MINECRAFTER)
                + ":" + data.getLevel(ProfessionType.SMITH)
                + ":" + data.getXp(ProfessionType.ADVENTURER)
                + ":" + data.getXp(ProfessionType.CLERIC)
                + ":" + data.getXp(ProfessionType.FARMHAND)
                + ":" + data.getXp(ProfessionType.MINECRAFTER)
                + ":" + data.getXp(ProfessionType.SMITH)
                + "]";
        player.sendSystemMessage(Component.literal(syncTag).withColor(0x000000));
    }

    public int getLevel(UUID uuid, ProfessionType profession) {
        return getOrCreate(uuid).getLevel(profession);
    }

    public ClassType getChosenClass(UUID uuid) {
        return getOrCreate(uuid).getChosenClass();
    }

    /**
     * Admin-only: reset a player's class data entirely.
     * They will need to choose a class again.
     */
    public void resetPlayer(UUID uuid) {
        playerData.put(uuid, new ClassData(uuid));
        save();
    }

    // --- Persistence ---

    @SuppressWarnings("unchecked")
    public void load() {
        if (!Files.exists(dataFile)) return;

        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
            Map<String, Map<String, Object>> raw = GSON.fromJson(reader, type);
            if (raw == null) return;

            playerData.clear();
            for (Map.Entry<String, Map<String, Object>> entry : raw.entrySet()) {
                try {
                    ClassData data = ClassData.deserialize(entry.getValue());
                    playerData.put(data.getPlayerUuid(), data);
                } catch (Exception e) {
                    MlkyMC.LOGGER.warn("Failed to load class data for {}: {}", entry.getKey(), e.getMessage());
                }
            }
            MlkyMC.LOGGER.info("Loaded class data for {} players", playerData.size());
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to load class data", e);
        }
    }

    public void save() {
        Map<String, Map<String, Object>> raw = new HashMap<>();
        for (Map.Entry<UUID, ClassData> entry : playerData.entrySet()) {
            raw.put(entry.getKey().toString(), entry.getValue().serialize());
        }

        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(raw, writer);
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save class data", e);
        }
    }
}
