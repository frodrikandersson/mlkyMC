package com.mlkymc.skills;

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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkillManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_LEVEL = 100;
    private final Path dataFile;
    private Map<UUID, Map<SkillType, Integer>> playerXp = new HashMap<>();

    public SkillManager(Path configDir) {
        this.dataFile = configDir.resolve("skills.json");
        load();
    }

    public int getXp(UUID playerId, SkillType skill) {
        return playerXp.getOrDefault(playerId, Map.of()).getOrDefault(skill, 0);
    }

    public int getLevel(UUID playerId, SkillType skill) {
        int xp = getXp(playerId, skill);
        int level = 0;
        while (level < MAX_LEVEL && xp >= getTotalXpForLevel(level + 1)) {
            level++;
        }
        return level;
    }

    public int getTotalXpForLevel(int level) {
        int total = 0;
        for (int i = 1; i <= level; i++) {
            total += i * 100;
        }
        return total;
    }

    public int getXpForNextLevel(int level) {
        return (level + 1) * 100;
    }

    public void addXp(ServerPlayer player, SkillType skill, int amount) {
        UUID id = player.getUUID();
        int oldLevel = getLevel(id, skill);

        playerXp.computeIfAbsent(id, k -> new EnumMap<>(SkillType.class));
        playerXp.get(id).merge(skill, amount, Integer::sum);

        int newLevel = getLevel(id, skill);
        if (newLevel > oldLevel) {
            player.sendSystemMessage(Component.literal(
                    skill.getDisplayName() + " leveled up to " + newLevel + "!").withColor(0x55FF55));
        }
    }

    public void load() {
        if (Files.exists(dataFile)) {
            try (Reader reader = Files.newBufferedReader(dataFile)) {
                Type type = new TypeToken<Map<UUID, Map<SkillType, Integer>>>() {}.getType();
                Map<UUID, Map<SkillType, Integer>> loaded = GSON.fromJson(reader, type);
                if (loaded != null) playerXp = loaded;
            } catch (IOException e) {
                MlkyMC.LOGGER.error("Failed to load skills.json", e);
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer writer = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(playerXp, writer);
            }
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save skills.json", e);
        }
    }
}
