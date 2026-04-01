package com.mlkymc.classes;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores all class system data for a single player.
 * - Chosen class (permanent once set)
 * - 5 profession levels and XP values
 * - Cooldown timers for active skills
 */
public class ClassData {
    private final UUID playerUuid;
    private ClassType chosenClass;
    private final EnumMap<ProfessionType, Integer> levels;
    private final EnumMap<ProfessionType, Integer> xp;

    // Soul Energy system (Cleric)
    private int soulEnergy;           // 0-100 personal SE bar
    private boolean soulEnergyMode;   // true = SE mode, false = XP mode
    private int selectedAltarAbility; // 0 = base Soul Skill, 1+ = altar tier abilities
    private boolean tomeUnlocked;     // true when SE first reaches 100

    public ClassData(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.chosenClass = ClassType.NONE;
        this.levels = new EnumMap<>(ProfessionType.class);
        this.xp = new EnumMap<>(ProfessionType.class);
        this.soulEnergy = 0;
        this.soulEnergyMode = false;
        this.selectedAltarAbility = 0;
        this.tomeUnlocked = false;

        for (ProfessionType prof : ProfessionType.values()) {
            levels.put(prof, 0);
            xp.put(prof, 0);
        }
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public ClassType getChosenClass() {
        return chosenClass;
    }

    public boolean hasChosenClass() {
        return chosenClass != ClassType.NONE;
    }

    /**
     * Set the player's class. Can only be done once (permanent).
     * Returns true if successful, false if already chosen.
     */
    public boolean setChosenClass(ClassType classType) {
        if (classType == ClassType.NONE) return false;
        if (hasChosenClass()) return false;
        this.chosenClass = classType;
        return true;
    }

    public int getLevel(ProfessionType profession) {
        return levels.getOrDefault(profession, 0);
    }

    /**
     * Halve all profession levels and reset XP within each level to 0.
     * Used when an alive player sacrifices levels to resurrect a ghost at the statue.
     */
    public void halveLevels() {
        for (ProfessionType prof : ProfessionType.values()) {
            int lv = levels.getOrDefault(prof, 0);
            if (lv > 0) {
                levels.put(prof, lv / 2);
                xp.put(prof, 0);
            }
        }
    }

    public int getXp(ProfessionType profession) {
        return xp.getOrDefault(profession, 0);
    }

    public int getXpForNextLevel(ProfessionType profession) {
        return ProfessionType.xpForLevel(getLevel(profession));
    }

    /**
     * Add XP to a profession. Handles leveling up automatically.
     * If this is the player's chosen class, XP is doubled (2x).
     * Returns the number of levels gained.
     */
    public int addXp(ProfessionType profession, int amount) {
        if (amount <= 0) return 0;

        int currentLevel = getLevel(profession);
        if (currentLevel >= profession.getMaxLevel()) return 0;

        // 2x XP for chosen class profession
        if (chosenClass != ClassType.NONE && chosenClass.getMatchingProfession() == profession) {
            amount *= 2;
        }

        int currentXp = getXp(profession) + amount;
        int levelsGained = 0;

        while (currentLevel < profession.getMaxLevel()) {
            int required = ProfessionType.xpForLevel(currentLevel);
            if (currentXp >= required) {
                currentXp -= required;
                currentLevel++;
                levelsGained++;
            } else {
                break;
            }
        }

        // Cap at max level
        if (currentLevel >= profession.getMaxLevel()) {
            currentLevel = profession.getMaxLevel();
            currentXp = 0;
        }

        levels.put(profession, currentLevel);
        xp.put(profession, currentXp);

        return levelsGained;
    }

    /**
     * Get the debuff multiplier for a profession.
     * Returns 0.0 to 0.5 (the percentage to reduce).
     * If the player chose this class, the debuff is always 0 (zeroed at level 0).
     */
    public double getDebuffPercent(ProfessionType profession) {
        if (hasChosenClass() && chosenClass.getMatchingProfession() == profession) {
            return 0.0; // Choosing a class removes that class's debuff entirely
        }
        return ProfessionType.getDebuffPercent(getLevel(profession));
    }

    /**
     * Check if the player has their chosen class's exclusive skills for a profession.
     * True only if the profession matches the player's chosen class.
     */
    public boolean hasExclusiveSkills(ProfessionType profession) {
        if (!hasChosenClass()) return false;
        return chosenClass.getMatchingProfession() == profession;
    }

    // --- Soul Energy ---

    public int getSoulEnergy() { return soulEnergy; }

    public void setSoulEnergy(int amount) {
        this.soulEnergy = Math.max(0, Math.min(100, amount));
    }

    public int addSoulEnergy(int amount) {
        int before = soulEnergy;
        soulEnergy = Math.max(0, Math.min(100, soulEnergy + amount));
        return soulEnergy - before;
    }

    public boolean isSoulEnergyMode() { return soulEnergyMode; }

    public void setSoulEnergyMode(boolean mode) { this.soulEnergyMode = mode; }

    public int getSelectedAltarAbility() { return selectedAltarAbility; }

    public void setSelectedAltarAbility(int ability) { this.selectedAltarAbility = ability; }

    public boolean isTomeUnlocked() { return tomeUnlocked; }

    public void setTomeUnlocked(boolean unlocked) { this.tomeUnlocked = unlocked; }

    // --- Serialization helpers ---

    public Map<String, Object> serialize() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("uuid", playerUuid.toString());
        data.put("class", chosenClass.name());

        Map<String, Object> profData = new java.util.HashMap<>();
        for (ProfessionType prof : ProfessionType.values()) {
            Map<String, Integer> profEntry = new java.util.HashMap<>();
            profEntry.put("level", getLevel(prof));
            profEntry.put("xp", getXp(prof));
            profData.put(prof.name(), profEntry);
        }
        data.put("professions", profData);

        // Soul Energy fields
        data.put("soulEnergy", soulEnergy);
        data.put("soulEnergyMode", soulEnergyMode);
        data.put("selectedAltarAbility", selectedAltarAbility);
        data.put("tomeUnlocked", tomeUnlocked);

        return data;
    }

    @SuppressWarnings("unchecked")
    public static ClassData deserialize(Map<String, Object> data) {
        UUID uuid = UUID.fromString((String) data.get("uuid"));
        ClassData cd = new ClassData(uuid);

        String className = (String) data.get("class");
        try {
            cd.chosenClass = ClassType.valueOf(className);
        } catch (IllegalArgumentException e) {
            cd.chosenClass = ClassType.NONE;
        }

        Map<String, Object> profData = (Map<String, Object>) data.get("professions");
        if (profData != null) {
            for (ProfessionType prof : ProfessionType.values()) {
                Map<String, Object> profEntry = (Map<String, Object>) profData.get(prof.name());
                if (profEntry != null) {
                    cd.levels.put(prof, ((Number) profEntry.get("level")).intValue());
                    cd.xp.put(prof, ((Number) profEntry.get("xp")).intValue());
                }
            }
        }

        // Soul Energy fields (backwards compatible — defaults to 0/false if missing)
        if (data.containsKey("soulEnergy")) {
            cd.soulEnergy = ((Number) data.get("soulEnergy")).intValue();
        }
        if (data.containsKey("soulEnergyMode")) {
            cd.soulEnergyMode = (Boolean) data.get("soulEnergyMode");
        }
        if (data.containsKey("selectedAltarAbility")) {
            cd.selectedAltarAbility = ((Number) data.get("selectedAltarAbility")).intValue();
        }
        if (data.containsKey("tomeUnlocked")) {
            cd.tomeUnlocked = (Boolean) data.get("tomeUnlocked");
        }

        return cd;
    }
}
