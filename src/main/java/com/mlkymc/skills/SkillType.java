package com.mlkymc.skills;

public enum SkillType {
    COMBAT("Combat", "Increases mob drop rates and Milky Star rewards"),
    FARMING("Farming", "Improves crop yields and farming rewards"),
    WOODCUTTING("Woodcutting", "Grants extra logs and wood drops"),
    FISHING("Fishing", "Better catches and rare loot");

    private final String displayName;
    private final String description;

    SkillType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
