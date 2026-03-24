package com.mlkymc.client;

import com.mlkymc.classes.ClassType;
import com.mlkymc.classes.ProfessionType;

import java.util.EnumMap;

/**
 * Client-side cache of the player's class data.
 * Synced from server via chat messages.
 */
public class ClientClassData {
    private static ClassType chosenClass = ClassType.NONE;
    private static final EnumMap<ProfessionType, Integer> levels = new EnumMap<>(ProfessionType.class);
    private static final EnumMap<ProfessionType, Integer> xpValues = new EnumMap<>(ProfessionType.class);
    private static int soulEnergy = 0;
    private static boolean soulEnergyMode = false;
    private static int altarSE = 0;

    static {
        for (ProfessionType p : ProfessionType.values()) {
            levels.put(p, 0);
            xpValues.put(p, 0);
        }
    }

    public static ClassType getChosenClass() { return chosenClass; }
    public static boolean hasChosenClass() { return chosenClass != ClassType.NONE; }

    public static void setChosenClass(ClassType type) { chosenClass = type; }

    public static int getLevel(ProfessionType prof) { return levels.getOrDefault(prof, 0); }
    public static void setLevel(ProfessionType prof, int level) { levels.put(prof, level); }

    public static int getXp(ProfessionType prof) { return xpValues.getOrDefault(prof, 0); }
    public static void setXp(ProfessionType prof, int xp) { xpValues.put(prof, xp); }

    public static int getSoulEnergy() { return soulEnergy; }
    public static void setSoulEnergy(int se) { soulEnergy = se; }
    public static boolean isSoulEnergyMode() { return soulEnergyMode; }
    public static void setSoulEnergyMode(boolean mode) { soulEnergyMode = mode; }
    public static int getAltarSE() { return altarSE; }
    public static void setAltarSE(int se) { altarSE = se; }

    /** XP needed for next level. Matches server formula. */
    public static int getXpForNextLevel(int currentLevel) {
        if (currentLevel >= 50) return 0;
        if (currentLevel <= 10) return 100 + (currentLevel * 40);
        if (currentLevel <= 20) return 500 + ((currentLevel - 10) * 50);
        if (currentLevel <= 30) return 1000 + ((currentLevel - 20) * 75);
        return 2000 + ((currentLevel - 30) * 100);
    }

    public static void reset() {
        chosenClass = ClassType.NONE;
        for (ProfessionType p : ProfessionType.values()) {
            levels.put(p, 0);
            xpValues.put(p, 0);
        }
        soulEnergy = 0;
        soulEnergyMode = false;
        altarSE = 0;
    }
}
