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
        // Base 100 XP for level 1, scaling up
        // Level 1: 100, Level 10: 1000, Level 25: 2500, Level 50: 5000
        return 100 + (level * 100);
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
