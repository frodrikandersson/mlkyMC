package com.mlkymc.grave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player graves. When a player dies, their inventory is stored in a grave.
 * The grave block is a player head (skull) placed at the death location.
 * Owner can access immediately. Others after 60 seconds. Graves expire after 60 minutes.
 */
public class GraveManager {

    private static final long GRAVE_EXPIRY_TICKS = 72000; // 60 minutes
    private final Map<String, GraveData> graves = new ConcurrentHashMap<>();
    private MinecraftServer server;
    private boolean dirty = false;

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("mlkymc-graves");

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public GraveData createGrave(ServerPlayer player, ServerLevel level) {
        LOGGER.info("[Grave] Creating grave for {} at {}", player.getName().getString(), player.blockPosition());
        BlockPos deathPos = player.blockPosition();
        BlockPos gravePos = findGravePosition(level, deathPos);

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack equipped = player.getItemBySlot(slot);
            if (!equipped.isEmpty()) {
                items.add(equipped.copy());
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }

        // Always create a grave, even with no items — it serves as the death marker
        // Remove any existing grave for this player first
        GraveData oldGrave = getGraveByOwner(player.getUUID());
        if (oldGrave != null) {
            // Remove old grave block from world
            var oldLevel = level.getServer().getLevel(
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.Identifier.parse(oldGrave.dimension)));
            if (oldLevel != null) {
                oldLevel.setBlock(oldGrave.pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            removeGrave(oldGrave.dimension, oldGrave.pos);
            LOGGER.info("[Grave] Removed old grave for {} at {}", player.getName().getString(), oldGrave.pos);
        }
        LOGGER.info("[Grave] Collected {} items, placing grave at {}", items.size(), gravePos);

        int xp = player.totalExperience;
        String dim = level.dimension().identifier().toString();
        GraveData grave = new GraveData(player.getUUID(), player.getName().getString(),
                gravePos, dim, level.getGameTime(), items, xp);

        String key = makeKey(dim, gravePos);
        graves.put(key, grave);
        dirty = true;

        // Place a player head with the dead player's skin
        level.setBlock(gravePos, Blocks.PLAYER_HEAD.defaultBlockState(), 3);
        var blockEntity = level.getBlockEntity(gravePos);
        if (blockEntity instanceof net.minecraft.world.level.block.entity.SkullBlockEntity skull) {
            try {
                var ownerField = net.minecraft.world.level.block.entity.SkullBlockEntity.class.getDeclaredField("owner");
                ownerField.setAccessible(true);
                ownerField.set(skull, net.minecraft.world.item.component.ResolvableProfile.createResolved(player.getGameProfile()));
                skull.setChanged();
            } catch (Exception e) {
                LOGGER.warn("[Grave] Failed to set skull owner skin", e);
            }
        }

        save();
        return grave;
    }

    /**
     * Tick handler — call every 20 ticks (1 second).
     * Removes expired graves (60 minutes) and their blocks.
     */
    public void tick(MinecraftServer server) {
        if (graves.isEmpty()) return;

        var iterator = graves.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            GraveData grave = entry.getValue();

            // Find the level for this grave's dimension
            ServerLevel level = null;
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().identifier().toString().equals(grave.dimension)) {
                    level = sl;
                    break;
                }
            }
            if (level == null) continue;

            long elapsed = level.getGameTime() - grave.deathTime;
            if (elapsed >= GRAVE_EXPIRY_TICKS) {
                // Remove grave block
                level.setBlock(grave.pos, Blocks.AIR.defaultBlockState(), 3);
                iterator.remove();
                dirty = true;
                LOGGER.info("[Grave] Expired grave for {} at {}", grave.ownerName, grave.pos);

                // Notify owner if online
                ServerPlayer owner = server.getPlayerList().getPlayer(grave.ownerUUID);
                if (owner != null) {
                    owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Your grave has expired! Items lost.").withColor(0xFF5555));
                }
            }
        }

        if (dirty) save();
    }

    public GraveData getGrave(String dimension, BlockPos pos) {
        return graves.get(makeKey(dimension, pos));
    }

    public GraveData removeGrave(String dimension, BlockPos pos) {
        dirty = true;
        return graves.remove(makeKey(dimension, pos));
    }

    public Collection<GraveData> getAllGraves() {
        return graves.values();
    }

    public GraveData getGraveByOwner(java.util.UUID ownerUUID) {
        GraveData newest = null;
        for (GraveData g : graves.values()) {
            if (g.ownerUUID.equals(ownerUUID)) {
                if (newest == null || g.deathTime > newest.deathTime) {
                    newest = g;
                }
            }
        }
        return newest;
    }

    public boolean claimGrave(ServerPlayer player, GraveData grave, ServerLevel level) {
        for (ItemStack item : grave.items) {
            if (!player.getInventory().add(item.copy())) {
                player.drop(item.copy(), false);
            }
        }
        player.giveExperiencePoints(grave.xpPoints);
        level.setBlock(grave.pos, Blocks.AIR.defaultBlockState(), 3);
        String key = makeKey(grave.dimension, grave.pos);
        graves.remove(key);
        dirty = true;
        save();
        return true;
    }

    // --- Persistence ---

    public void save() {
        if (server == null) return;
        dirty = false;
        Path savePath = getSavePath();
        try {
            HolderLookup.Provider registries = server.registryAccess();
            CompoundTag root = new CompoundTag();
            ListTag graveList = new ListTag();

            for (var entry : graves.entrySet()) {
                GraveData grave = entry.getValue();
                CompoundTag graveTag = new CompoundTag();
                graveTag.putString("key", entry.getKey());
                graveTag.putString("owner", grave.ownerUUID.toString());
                graveTag.putString("ownerName", grave.ownerName);
                graveTag.putInt("x", grave.pos.getX());
                graveTag.putInt("y", grave.pos.getY());
                graveTag.putInt("z", grave.pos.getZ());
                graveTag.putString("dim", grave.dimension);
                graveTag.putLong("deathTime", grave.deathTime);
                graveTag.putInt("xp", grave.xpPoints);

                // Serialize items
                ListTag itemList = new ListTag();
                for (ItemStack stack : grave.items) {
                    Tag itemTag = ItemStack.OPTIONAL_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                            .getOrThrow();
                    itemList.add(itemTag);
                }
                graveTag.put("items", itemList);
                graveList.add(graveTag);
            }
            root.put("graves", graveList);

            // Write to file
            try (var out = new DataOutputStream(new FileOutputStream(savePath.toFile()))) {
                net.minecraft.nbt.NbtIo.writeCompressed(root, out);
            }
        } catch (Exception e) {
            LOGGER.error("[Grave] Failed to save graves", e);
        }
    }

    public void load() {
        if (server == null) return;
        Path savePath = getSavePath();
        if (!Files.exists(savePath)) return;

        try {
            HolderLookup.Provider registries = server.registryAccess();
            CompoundTag root;
            try (var in = new DataInputStream(new FileInputStream(savePath.toFile()))) {
                root = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }

            ListTag graveList = root.getListOrEmpty("graves");
            for (int i = 0; i < graveList.size(); i++) {
                CompoundTag graveTag = graveList.getCompound(i).orElseThrow();
                String key = graveTag.getStringOr("key", "");
                String ownerStr = graveTag.getStringOr("owner", "");
                if (ownerStr.isEmpty() || key.isEmpty()) continue;
                UUID owner;
                try {
                    owner = UUID.fromString(ownerStr);
                } catch (IllegalArgumentException e) { continue; }

                String ownerName = graveTag.getStringOr("ownerName", "Unknown");
                BlockPos pos = new BlockPos(
                        graveTag.getIntOr("x", 0),
                        graveTag.getIntOr("y", 0),
                        graveTag.getIntOr("z", 0));
                String dim = graveTag.getStringOr("dim", "minecraft:overworld");
                long deathTime = graveTag.getLongOr("deathTime", 0);
                int xp = graveTag.getIntOr("xp", 0);

                // Deserialize items
                List<ItemStack> items = new ArrayList<>();
                ListTag itemList = graveTag.getListOrEmpty("items");
                for (int j = 0; j < itemList.size(); j++) {
                    Tag itemTag = itemList.get(j);
                    ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(
                            registries.createSerializationContext(NbtOps.INSTANCE), itemTag)
                            .result().orElse(ItemStack.EMPTY);
                    if (!stack.isEmpty()) {
                        items.add(stack);
                    }
                }

                GraveData grave = new GraveData(owner, ownerName, pos, dim, deathTime, items, xp);
                graves.put(key, grave);
            }
            LOGGER.info("[Grave] Loaded {} graves from disk", graves.size());
        } catch (Exception e) {
            LOGGER.error("[Grave] Failed to load graves", e);
        }
    }

    private Path getSavePath() {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("mlkymc_graves.dat");
    }

    private BlockPos findGravePosition(ServerLevel level, BlockPos deathPos) {
        if (level.getBlockState(deathPos).isAir() || !level.getFluidState(deathPos).isEmpty()) {
            return deathPos;
        }
        for (int dy = 1; dy <= 5; dy++) {
            BlockPos up = deathPos.above(dy);
            if (level.getBlockState(up).isAir()) return up;
        }
        return deathPos;
    }

    private String makeKey(String dim, BlockPos pos) {
        return dim + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
