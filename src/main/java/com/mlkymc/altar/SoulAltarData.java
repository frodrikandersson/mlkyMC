package com.mlkymc.altar;

import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * Data for a single Soul Altar instance.
 * Persisted to JSON via SoulAltarManager.
 */
public class SoulAltarData {
    public UUID ownerUuid;
    public String ownerName;
    public BlockPos capstonePos;
    public String dimension;
    public int storedSE;
    public int highWaterSE; // highest SE ever reached, for tier unlocks
    public int selectedAbility; // 0 = base Soul Skill
    public List<GhostConnection> connectedGhosts = new ArrayList<>();
    public List<DonorLogEntry> recentDonors = new ArrayList<>();

    public static class GhostConnection {
        public UUID ghostUuid;
        public String name;
        public int donatedTotal;
    }

    public static class DonorLogEntry {
        public String name;
        public int amount;
        public long timestamp;
    }

    public void addDonorLog(String name, int amount) {
        DonorLogEntry entry = new DonorLogEntry();
        entry.name = name;
        entry.amount = amount;
        entry.timestamp = System.currentTimeMillis();
        recentDonors.add(0, entry);
        if (recentDonors.size() > 10) {
            recentDonors = new ArrayList<>(recentDonors.subList(0, 10));
        }
    }

    // --- Serialization ---

    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("ownerUuid", ownerUuid.toString());
        data.put("ownerName", ownerName);
        data.put("capstoneX", capstonePos.getX());
        data.put("capstoneY", capstonePos.getY());
        data.put("capstoneZ", capstonePos.getZ());
        data.put("dimension", dimension);
        data.put("storedSE", storedSE);
        data.put("highWaterSE", highWaterSE);
        data.put("selectedAbility", selectedAbility);

        List<Map<String, Object>> ghostList = new ArrayList<>();
        for (GhostConnection gc : connectedGhosts) {
            Map<String, Object> gm = new HashMap<>();
            gm.put("uuid", gc.ghostUuid.toString());
            gm.put("name", gc.name);
            gm.put("donatedTotal", gc.donatedTotal);
            ghostList.add(gm);
        }
        data.put("connectedGhosts", ghostList);

        List<Map<String, Object>> donorList = new ArrayList<>();
        for (DonorLogEntry dle : recentDonors) {
            Map<String, Object> dm = new HashMap<>();
            dm.put("name", dle.name);
            dm.put("amount", dle.amount);
            dm.put("timestamp", dle.timestamp);
            donorList.add(dm);
        }
        data.put("recentDonors", donorList);

        return data;
    }

    @SuppressWarnings("unchecked")
    public static SoulAltarData deserialize(Map<String, Object> data) {
        SoulAltarData altar = new SoulAltarData();
        altar.ownerUuid = UUID.fromString((String) data.get("ownerUuid"));
        altar.ownerName = (String) data.get("ownerName");
        altar.capstonePos = new BlockPos(
                ((Number) data.get("capstoneX")).intValue(),
                ((Number) data.get("capstoneY")).intValue(),
                ((Number) data.get("capstoneZ")).intValue()
        );
        altar.dimension = (String) data.get("dimension");
        altar.storedSE = ((Number) data.get("storedSE")).intValue();
        altar.highWaterSE = data.containsKey("highWaterSE") ? ((Number) data.get("highWaterSE")).intValue() : 0;
        altar.selectedAbility = data.containsKey("selectedAbility") ? ((Number) data.get("selectedAbility")).intValue() : 0;

        List<Map<String, Object>> ghostList = (List<Map<String, Object>>) data.get("connectedGhosts");
        if (ghostList != null) {
            for (Map<String, Object> gm : ghostList) {
                GhostConnection gc = new GhostConnection();
                gc.ghostUuid = UUID.fromString((String) gm.get("uuid"));
                gc.name = (String) gm.get("name");
                gc.donatedTotal = ((Number) gm.get("donatedTotal")).intValue();
                altar.connectedGhosts.add(gc);
            }
        }

        List<Map<String, Object>> donorList = (List<Map<String, Object>>) data.get("recentDonors");
        if (donorList != null) {
            for (Map<String, Object> dm : donorList) {
                DonorLogEntry dle = new DonorLogEntry();
                dle.name = (String) dm.get("name");
                dle.amount = ((Number) dm.get("amount")).intValue();
                dle.timestamp = ((Number) dm.get("timestamp")).longValue();
                altar.recentDonors.add(dle);
            }
        }

        return altar;
    }
}
