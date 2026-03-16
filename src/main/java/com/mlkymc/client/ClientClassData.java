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

    static {
        for (ProfessionType p : ProfessionType.values()) levels.put(p, 0);
    }

    public static ClassType getChosenClass() { return chosenClass; }
    public static boolean hasChosenClass() { return chosenClass != ClassType.NONE; }

    public static void setChosenClass(ClassType type) { chosenClass = type; }

    public static int getLevel(ProfessionType prof) { return levels.getOrDefault(prof, 0); }
    public static void setLevel(ProfessionType prof, int level) { levels.put(prof, level); }

    public static void reset() {
        chosenClass = ClassType.NONE;
        for (ProfessionType p : ProfessionType.values()) levels.put(p, 0);
    }
}
