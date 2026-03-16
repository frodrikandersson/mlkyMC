package com.mlkymc.region;

import java.util.HashSet;
import java.util.Set;

public class Region {

    public String name;
    public String dimension;
    public int x1, y1, z1;
    public int x2, y2, z2;
    public int priority = 0;
    // Store as string IDs for clean Gson serialization
    public Set<String> flags = new HashSet<>();

    public Region() {}

    public Region(String name, String dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        this.dimension = dimension;
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.z2 = Math.max(z1, z2);
    }

    public boolean contains(String dim, int x, int y, int z) {
        return dimension.equals(dim)
                && x >= x1 && x <= x2
                && y >= y1 && y <= y2
                && z >= z1 && z <= z2;
    }

    public boolean hasFlag(RegionFlag flag) {
        return flags.contains(flag.getId());
    }

    public void setFlag(RegionFlag flag, boolean value) {
        if (value) {
            flags.add(flag.getId());
        } else {
            flags.remove(flag.getId());
        }
    }

    public int blockCount() {
        return (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
    }
}
