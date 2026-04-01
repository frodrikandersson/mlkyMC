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
    private Path dataFile;
    private final Map<UUID, ClassData> playerData = new HashMap<>();
    private final Map<UUID, Long> lastSyncTick = new HashMap<>();

    public ClassManager(Path configDir) {
        this.dataFile = configDir.resolve("class_data.json");
        // Don't load here — reload() on server start will load from world folder
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
                    .append(Component.literal(" class. Visit the Star Statue in town to reset.")));
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
                    .append(Component.literal(" class!")));
            player.sendSystemMessage(Component.literal("You can reset your levels and class at the Star Statue in town.")
                    .withStyle(style -> style.withColor(0xFFAA00)));
            player.sendSystemMessage(Component.literal("Press [K] to open your Skill Tree and view your class progression.")
                    .withStyle(style -> style.withColor(0x55FFFF)));

            // Give Tome of the Soul Warden to Clerics
            if (classType == ClassType.CLERIC) {
                giveSoulWardenTome(player, false);
            }

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
            boolean isChosenProfession = data.getChosenClass() == profession.getMatchingClass();
            double oldDebuff = ProfessionType.getDebuffPercent(oldLevel);
            double newDebuff = ProfessionType.getDebuffPercent(newLevel);
            if (newDebuff < oldDebuff) {
                if (isChosenProfession) {
                    // Show exclusive bonus instead of debuff for chosen class
                    if (newDebuff == 0.0) {
                        player.sendSystemMessage(Component.literal(profession.getDisplayName() + " debuff fully removed for all classes!")
                                .withStyle(style -> style.withColor(0x55FF55)));
                    } else {
                        // The exclusive bonus is typically debuff * 0.5 (e.g., -40% debuff → +20% exclusive)
                        player.sendSystemMessage(Component.literal(profession.getDisplayName() + " exclusive bonus increased!")
                                .withStyle(style -> style.withColor(0x55FF55)));
                    }
                } else {
                    if (newDebuff == 0.0) {
                        player.sendSystemMessage(Component.literal(profession.getDisplayName() + " debuff fully removed!")
                                .withStyle(style -> style.withColor(0x55FF55)));
                    } else {
                        int pct = (int) (newDebuff * 100);
                        player.sendSystemMessage(Component.literal(profession.getDisplayName() + " debuff reduced to -" + pct + "%")
                                .withStyle(style -> style.withColor(0xFFFF55)));
                    }
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

        // Also sync soul energy for Clerics
        if (data.getChosenClass() == ClassType.CLERIC) {
            sendSoulSync(player);
        }
    }

    // --- Tome of the Soul Warden ---

    public void giveSoulWardenTome(ServerPlayer player, boolean fullVersion) {
        var pages = new java.util.ArrayList<net.minecraft.server.network.Filterable<Component>>();

        // Page 1: Always present
        pages.add(net.minecraft.server.network.Filterable.passThrough(Component.literal(
                "SOUL ENERGY SYSTEM\n\n" +
                "As a Cleric, you harness Soul Energy (SE).\n\n" +
                "Toggle SE Mode with your Secondary Power key.\n\n" +
                "In SE Mode:\n" +
                "- XP orbs become SE\n" +
                "- Right-click Milky Star = +20 SE\n" +
                "- Max personal SE: 100"
        )));

        if (fullVersion) {
            // Page 2: Ghost cooperation
            pages.add(net.minecraft.server.network.Filterable.passThrough(Component.literal(
                    "GHOST COOPERATION\n\n" +
                    "Ghosts gain Spectral Energy over time.\n\n" +
                    "They can donate SE to your Soul Altar at a 2:1 ratio.\n\n" +
                    "Connected ghosts help you unlock powerful altar abilities.\n\n" +
                    "Max 3 ghosts per altar."
            )));

            // Page 3: Soul Altar
            pages.add(net.minecraft.server.network.Filterable.passThrough(Component.literal(
                    "SOUL ALTAR\n\n" +
                    "Build a 3x3x2 structure:\n\n" +
                    "Bottom: 8 Soulstone Brick edges, 1 Conduit Core center\n\n" +
                    "Top: 4 Soul Pillars at corners, Capstone center\n\n" +
                    "Right-click Capstone to activate.\n" +
                    "Max altar SE: 100,000"
            )));

            // Page 4: Recipes
            pages.add(net.minecraft.server.network.Filterable.passThrough(Component.literal(
                    "RECIPES\n\n" +
                    "Soulstone Brick (4):\n4 Blackstone + 4 Soul Sand + Soul Soil\n\n" +
                    "Soul Pillar (2):\n2 Soulstone Brick + Soul Sand\n\n" +
                    "Conduit Core:\n4 Crying Obsidian + 4 Soulstone Brick + Amethyst Block"
            )));

            // Page 5: More recipes
            pages.add(net.minecraft.server.network.Filterable.passThrough(Component.literal(
                    "MORE RECIPES\n\n" +
                    "Soul Altar Capstone:\n2 Crying Obsidian + Amethyst Block + 5 Soulstone Brick + Conduit Core\n\n" +
                    "Ability tiers unlock at:\n" +
                    "25k, 50k, 75k, 100k SE"
            )));
        }

        var bookContent = new net.minecraft.world.item.component.WrittenBookContent(
                net.minecraft.server.network.Filterable.passThrough("Soul Warden"),
                "The Cleric Order", 0, pages, true
        );

        var tome = new net.minecraft.world.item.ItemStack(
                com.mlkymc.registry.ModItems.TOME_OF_THE_SOUL_WARDEN.get());
        tome.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT, bookContent);

        // Remove any existing Tome of the Soul Warden before adding the new one
        var tomeItem = com.mlkymc.registry.ModItems.TOME_OF_THE_SOUL_WARDEN.get();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var existing = player.getInventory().getItem(i);
            if (!existing.isEmpty() && existing.getItem() == tomeItem) {
                player.getInventory().setItem(i, tome);
                return; // replaced in-place
            }
        }
        // No existing tome found, add new
        player.getInventory().add(tome);
    }

    /**
     * Called when SE first reaches 100 — upgrades the tome with full content.
     */
    public void checkTomeUnlock(ServerPlayer player) {
        ClassData data = getOrCreate(player);
        if (data.getChosenClass() != ClassType.CLERIC) return;
        if (data.isTomeUnlocked()) return;
        if (data.getSoulEnergy() < 100) return;

        data.setTomeUnlocked(true);
        giveSoulWardenTome(player, true);
        player.sendSystemMessage(Component.literal("Your Tome of the Soul Warden has been updated with new pages!")
                .withColor(0xAA55FF));
        save();
    }

    // --- Soul Energy ---

    public void sendSoulSync(ServerPlayer player) {
        ClassData data = getOrCreate(player);
        // Include altar SE if this Cleric owns an altar
        int altarSE = 0;
        var altarManager = com.mlkymc.MlkyMC.getSoulAltarManager();
        if (altarManager != null) {
            var altar = altarManager.getAltarByOwner(player.getUUID());
            if (altar != null) altarSE = altar.storedSE;
        }
        String syncTag = "[MLKYMC_SOUL:" + data.getSoulEnergy() + ":" + (data.isSoulEnergyMode() ? 1 : 0) + ":" + altarSE + "]";
        player.sendSystemMessage(Component.literal(syncTag).withColor(0x000000));
    }

    public void addSoulEnergy(ServerPlayer player, int amount) {
        ClassData data = getOrCreate(player);
        data.addSoulEnergy(amount);
        sendSoulSync(player);
        save();
        if (amount > 0) checkTomeUnlock(player);
    }

    public void toggleSoulMode(ServerPlayer player) {
        ClassData data = getOrCreate(player);
        boolean newMode = !data.isSoulEnergyMode();
        data.setSoulEnergyMode(newMode);
        sendSoulSync(player);
        save();
        player.displayClientMessage(Component.literal("Soul Energy Mode: " + (newMode ? "ON" : "OFF"))
                .withColor(newMode ? 0xAA55FF : 0xAAAAAA), true);
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

    public void reload(Path dir) {
        this.dataFile = dir.resolve("class_data.json");
        load();
    }
}
