package com.mlkymc.classes;

/**
 * The 5 professions that all players can level.
 * Each profession maps 1:1 to a ClassType.
 * All players level all professions, but only your chosen class
 * grants active skills and special effects for that profession.
 */
public enum ProfessionType {
    ADVENTURER("Adventurer", 50),
    CLERIC("Cleric", 50),
    FARMHAND("Farmhand", 50),
    MINECRAFTER("MineCrafter", 50),
    SMITH("Smith", 50);

    private final String displayName;
    private final int maxLevel;

    ProfessionType(String displayName, int maxLevel) {
        this.displayName = displayName;
        this.maxLevel = maxLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    /**
     * XP required to reach the next level from the given level.
     * Scales quadratically so higher levels take more effort.
     */
    public static int xpForLevel(int level) {
        if (level < 0) return 0;
        if (level >= 50) return 0; // Max level
        // Lv 0-10:  100 to 500
        // Lv 11-20: 500 to 1000
        // Lv 21-30: 1000 to 1750
        // Lv 31-50: 2000 to 4000
        if (level <= 10) {
            return 100 + (level * 40); // 100, 140, 180, ..., 500
        } else if (level <= 20) {
            return 500 + ((level - 10) * 50); // 550, 600, ..., 1000
        } else if (level <= 30) {
            return 1000 + ((level - 20) * 75); // 1075, 1150, ..., 1750
        } else {
            return 2000 + ((level - 30) * 100); // 2100, 2200, ..., 4000
        }
    }

    /**
     * Get the debuff percentage for a given level.
     * Starts at -50% (level 0), reduces by 10% at levels 5, 15, 25, 35, 45.
     * Returns value between 0.0 (no debuff) and 0.5 (max debuff).
     */
    public static double getDebuffPercent(int level) {
        if (level >= 45) return 0.0;
        if (level >= 35) return 0.10;
        if (level >= 25) return 0.20;
        if (level >= 15) return 0.30;
        if (level >= 5) return 0.40;
        return 0.50;
    }

    /**
     * Get the ClassType that matches this profession.
     */
    public ClassType getMatchingClass() {
        return switch (this) {
            case ADVENTURER -> ClassType.ADVENTURER;
            case CLERIC -> ClassType.CLERIC;
            case FARMHAND -> ClassType.FARMHAND;
            case MINECRAFTER -> ClassType.MINECRAFTER;
            case SMITH -> ClassType.SMITH;
        };
    }
}
