package com.mlkymc.region;

public enum RegionFlag {
    NO_BREAK("no-break", "Prevent block breaking"),
    NO_PLACE("no-place", "Prevent block placing"),
    NO_PVP("no-pvp", "Prevent player vs player damage"),
    NO_MOB_SPAWN("no-mob-spawn", "Prevent hostile mob spawning"),
    NO_EXPLOSIONS("no-explosions", "Prevent explosion damage"),
    NO_HUNGER("no-hunger", "Prevent hunger drain"),
    NO_INTERACT("no-interact", "Prevent container/door interaction"),
    NO_FALL_DAMAGE("no-fall-damage", "Prevent fall damage"),
    NO_FIRE_SPREAD("no-fire-spread", "Prevent fire spread"),
    NO_ITEM_DROP("no-item-drop", "Prevent item dropping"),
    NO_ITEM_PICKUP("no-item-pickup", "Prevent item pickup");

    private final String id;
    private final String description;

    RegionFlag(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() { return id; }
    public String getDescription() { return description; }

    public static RegionFlag fromId(String id) {
        for (RegionFlag flag : values()) {
            if (flag.id.equalsIgnoreCase(id)) return flag;
        }
        return null;
    }
}
