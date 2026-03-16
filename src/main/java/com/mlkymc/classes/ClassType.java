package com.mlkymc.classes;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * The 5 player classes + NONE for unselected state.
 * Class selection is permanent once chosen.
 */
public enum ClassType {
    NONE("None", "No class selected", 0x888888),
    ADVENTURER("Adventurer", "Explorer and monster hunter", 0x55AA55),
    CLERIC("Cleric", "Healer and enchanter", 0xAA55AA),
    FARMHAND("Farmhand", "Farmer and animal breeder", 0xAAAA00),
    MINECRAFTER("MineCrafter", "Miner and builder", 0x5555FF),
    SMITH("Smith", "Blacksmith and smelter", 0xAA5500);

    private final String displayName;
    private final String description;
    private final int color;

    ClassType(String displayName, String description, int color) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getColor() {
        return color;
    }

    public Component getColoredName() {
        return Component.literal(displayName).withStyle(Style.EMPTY.withColor(color));
    }

    /**
     * Get the profession type that matches this class.
     * Returns null for NONE.
     */
    public ProfessionType getMatchingProfession() {
        return switch (this) {
            case ADVENTURER -> ProfessionType.ADVENTURER;
            case CLERIC -> ProfessionType.CLERIC;
            case FARMHAND -> ProfessionType.FARMHAND;
            case MINECRAFTER -> ProfessionType.MINECRAFTER;
            case SMITH -> ProfessionType.SMITH;
            case NONE -> null;
        };
    }
}
