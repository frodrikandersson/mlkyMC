package com.mlkymc.altar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mlkymc.MlkyMC;
import com.mlkymc.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages Soul Altar multiblock structures.
 * Handles validation, data persistence, SE storage, ghost connections, ability tiers.
 * Follows the MarketManager pattern for JSON persistence.
 */
public class SoulAltarManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataFile;
    private final List<SoulAltarData> altars = new ArrayList<>();

    // Tier thresholds
    public static final int TIER_1_THRESHOLD = 25_000;
    public static final int TIER_2_THRESHOLD = 50_000;
    public static final int TIER_3_THRESHOLD = 75_000;
    public static final int TIER_4_THRESHOLD = 100_000;
    public static final int MAX_ALTAR_SE = 100_000;
    public static final int MAX_GHOSTS = 3;

    public SoulAltarManager(Path configDir) {
        this.dataFile = configDir.resolve("soul_altars.json");
        load();
    }



    // --- Multiblock Validation ---

    /**
     * Validates the 3x3x2 Soul Altar multiblock structure.
     * Capstone is at the center of the top layer (y+1).
     * Bottom layer (y=0): 8 Soulstone Brick at edges, Conduit Core at center.
     * Top layer (y=1): 4 Soul Pillar at corners, Capstone at center, 4 air.
     *
     * @param level The server level
     * @param capstonePos Position of the Soul Altar Capstone block (top center)
     * @return true if structure is valid
     */
    public boolean validateMultiblock(ServerLevel level, BlockPos capstonePos) {
        BlockPos bottomCenter = capstonePos.below();

        // Bottom layer: center must be Conduit Core
        if (!isBlock(level, bottomCenter, ModBlocks.CONDUIT_CORE.get())) return false;

        // Bottom layer: 8 edges must be Soulstone Brick
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue; // skip center (Conduit Core)
                BlockPos edgePos = bottomCenter.offset(dx, 0, dz);
                if (!isBlock(level, edgePos, ModBlocks.SOULSTONE_BRICK.get())) return false;
            }
        }

        // Top layer: 4 corners must be Soul Pillar
        int[][] corners = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        for (int[] c : corners) {
            BlockPos cornerPos = capstonePos.offset(c[0], 0, c[1]);
            if (!isBlock(level, cornerPos, ModBlocks.SOUL_PILLAR.get())) return false;
        }

        // Top layer: 4 edges (non-corner, non-center) must be air
        int[][] edges = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] e : edges) {
            BlockPos edgePos = capstonePos.offset(e[0], 0, e[1]);
            if (!level.getBlockState(edgePos).isAir()) return false;
        }

        return true;
    }

    private boolean isBlock(ServerLevel level, BlockPos pos, Block expected) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() == expected;
    }

    // --- Altar CRUD ---

    public SoulAltarData createAltar(ServerPlayer player, BlockPos capstonePos, String dimension) {
        // Check if altar already exists at this position
        SoulAltarData existing = getAltar(dimension, capstonePos);
        if (existing != null) return existing;

        SoulAltarData altar = new SoulAltarData();
        altar.ownerUuid = player.getUUID();
        altar.ownerName = player.getName().getString();
        altar.capstonePos = capstonePos;
        altar.dimension = dimension;
        altar.storedSE = 0;
        altar.connectedGhosts = new ArrayList<>();
        altar.recentDonors = new ArrayList<>();
        altar.highWaterSE = 0;
        altar.selectedAbility = 0;

        altars.add(altar);
        save();
        return altar;
    }

    public SoulAltarData getAltar(String dimension, BlockPos pos) {
        for (SoulAltarData altar : altars) {
            if (altar.dimension.equals(dimension) && altar.capstonePos.equals(pos)) {
                return altar;
            }
        }
        return null;
    }

    public SoulAltarData getAltarByOwner(UUID ownerUuid) {
        for (SoulAltarData altar : altars) {
            if (altar.ownerUuid.equals(ownerUuid)) {
                return altar;
            }
        }
        return null;
    }

    public void removeAltar(SoulAltarData altar) {
        altars.remove(altar);
        save();
    }

    // --- SE Operations ---

    public int depositMilkyStars(SoulAltarData altar, String donorName, int count) {
        int spaceLeft = MAX_ALTAR_SE - altar.storedSE;
        if (spaceLeft <= 0) return 0;
        // Only consume as many stars as needed to fill to cap
        int starsNeeded = (int) Math.ceil(spaceLeft / 30.0);
        int starsUsed = Math.min(count, starsNeeded);
        int actualGain = Math.min(starsUsed * 30, spaceLeft);
        altar.storedSE += actualGain;
        if (altar.storedSE > altar.highWaterSE) {
            altar.highWaterSE = altar.storedSE;
        }
        altar.addDonorLog(donorName, actualGain);
        save();
        return actualGain;
    }

    public int channelPersonalSE(SoulAltarData altar, ServerPlayer player, int amount) {
        var classData = MlkyMC.getClassManager().getOrCreate(player);
        int spaceLeft = MAX_ALTAR_SE - altar.storedSE;
        int transfer = Math.min(Math.min(amount, classData.getSoulEnergy()), spaceLeft);
        if (transfer <= 0) return 0;

        classData.addSoulEnergy(-transfer);
        altar.storedSE += transfer;
        if (altar.storedSE > altar.highWaterSE) {
            altar.highWaterSE = altar.storedSE;
        }
        MlkyMC.getClassManager().sendSoulSync(player);
        altar.addDonorLog(player.getName().getString() + " (channeled)", transfer);
        save();
        return transfer;
    }

    public int ghostDonate(SoulAltarData altar, UUID ghostUuid, String ghostName, int spectralEnergy) {
        // 2:1 ratio: ghost loses all SE, altar gains half
        int seGain = spectralEnergy / 2;
        int spaceLeft = MAX_ALTAR_SE - altar.storedSE;
        int actualGain = Math.min(seGain, spaceLeft);
        altar.storedSE += actualGain;
        if (altar.storedSE > altar.highWaterSE) {
            altar.highWaterSE = altar.storedSE;
        }

        // Track donation on ghost connection
        for (SoulAltarData.GhostConnection gc : altar.connectedGhosts) {
            if (gc.ghostUuid.equals(ghostUuid)) {
                gc.donatedTotal += actualGain;
                break;
            }
        }

        altar.addDonorLog(ghostName + " (ghost)", actualGain);
        save();
        return actualGain;
    }

    public boolean spendAltarSE(SoulAltarData altar, int cost) {
        if (altar.storedSE < cost) return false;
        altar.storedSE -= cost;
        save();
        return true;
    }

    // --- Ghost Connection ---

    public boolean connectGhost(SoulAltarData altar, UUID ghostUuid, String name) {
        if (altar.connectedGhosts.size() >= MAX_GHOSTS) return false;
        for (SoulAltarData.GhostConnection gc : altar.connectedGhosts) {
            if (gc.ghostUuid.equals(ghostUuid)) return false; // already connected
        }
        SoulAltarData.GhostConnection gc = new SoulAltarData.GhostConnection();
        gc.ghostUuid = ghostUuid;
        gc.name = name;
        gc.donatedTotal = 0;
        altar.connectedGhosts.add(gc);
        save();
        return true;
    }

    public void disconnectGhost(SoulAltarData altar, UUID ghostUuid) {
        altar.connectedGhosts.removeIf(gc -> gc.ghostUuid.equals(ghostUuid));
        save();
    }

    public void disconnectGhostFromAll(UUID ghostUuid) {
        boolean changed = false;
        for (SoulAltarData altar : altars) {
            if (altar.connectedGhosts.removeIf(gc -> gc.ghostUuid.equals(ghostUuid))) {
                changed = true;
            }
        }
        if (changed) save();
    }

    // --- Tier Computation ---

    public int computeTier(SoulAltarData altar) {
        int hwm = altar.highWaterSE;
        if (hwm >= TIER_4_THRESHOLD) return 4;
        if (hwm >= TIER_3_THRESHOLD) return 3;
        if (hwm >= TIER_2_THRESHOLD) return 2;
        if (hwm >= TIER_1_THRESHOLD) return 1;
        return 0;
    }

    // --- Persistence ---

    public void save() {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (SoulAltarData altar : altars) {
            raw.add(altar.serialize());
        }
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(raw, writer);
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save soul altar data", e);
        }
    }

    public void load() {
        if (!Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> raw = GSON.fromJson(reader, type);
            if (raw == null) return;
            altars.clear();
            for (Map<String, Object> entry : raw) {
                try {
                    altars.add(SoulAltarData.deserialize(entry));
                } catch (Exception e) {
                    MlkyMC.LOGGER.warn("Failed to load soul altar entry: {}", e.getMessage());
                }
            }
            MlkyMC.LOGGER.info("Loaded {} soul altars", altars.size());
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to load soul altar data", e);
        }
    }

    public List<SoulAltarData> getAllAltars() {
        return Collections.unmodifiableList(altars);
    }
}
