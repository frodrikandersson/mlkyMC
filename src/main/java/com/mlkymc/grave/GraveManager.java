package com.mlkymc.grave;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player graves. When a player dies, their inventory is stored in a grave.
 * The grave block is a player head (skull) placed at the death location.
 * Owner can access immediately. Others can access after 10 minutes.
 */
public class GraveManager {

    // Key: BlockPos hashcode + dimension string -> GraveData
    private final Map<String, GraveData> graves = new ConcurrentHashMap<>();

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("mlkymc-graves");

    public GraveData createGrave(ServerPlayer player, ServerLevel level) {
        LOGGER.info("[Grave] Creating grave for {} at {}", player.getName().getString(), player.blockPosition());
        BlockPos deathPos = player.blockPosition();

        // Find a valid position (air block near death location)
        BlockPos gravePos = findGravePosition(level, deathPos);

        // Collect all inventory items
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }

        // Also collect armor and offhand
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack equipped = player.getItemBySlot(slot);
            if (!equipped.isEmpty()) {
                items.add(equipped.copy());
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }

        if (items.isEmpty()) {
            LOGGER.info("[Grave] No items to grave for {}", player.getName().getString());
            return null;
        }
        LOGGER.info("[Grave] Collected {} items, placing grave at {}", items.size(), gravePos);

        int xp = player.totalExperience;

        String dim = level.dimension().identifier().toString();
        GraveData grave = new GraveData(player.getUUID(), player.getName().getString(),
                gravePos, dim, level.getGameTime(), items, xp);

        String key = makeKey(dim, gravePos);
        graves.put(key, grave);

        // Place a skull block as the grave marker
        level.setBlock(gravePos, Blocks.PLAYER_HEAD.defaultBlockState(), 3);

        // Broadcast
        for (var p : level.players()) {
            p.sendSystemMessage(Component.literal(player.getName().getString() + " died! Grave at " +
                    gravePos.getX() + ", " + gravePos.getY() + ", " + gravePos.getZ()).withColor(0xAAAAAA));
        }

        return grave;
    }

    public GraveData getGrave(String dimension, BlockPos pos) {
        return graves.get(makeKey(dimension, pos));
    }

    public GraveData removeGrave(String dimension, BlockPos pos) {
        return graves.remove(makeKey(dimension, pos));
    }

    public Collection<GraveData> getAllGraves() {
        return graves.values();
    }

    /**
     * Give grave items back to a player and remove the grave.
     */
    public boolean claimGrave(ServerPlayer player, GraveData grave, ServerLevel level) {
        // Give items back
        for (ItemStack item : grave.items) {
            if (!player.getInventory().add(item.copy())) {
                // Inventory full — drop on ground
                player.drop(item.copy(), false);
            }
        }

        // Give XP back
        player.giveExperiencePoints(grave.xpPoints);

        // Remove grave block
        level.setBlock(grave.pos, Blocks.AIR.defaultBlockState(), 3);

        // Remove from tracking
        String key = makeKey(grave.dimension, grave.pos);
        graves.remove(key);

        return true;
    }

    private BlockPos findGravePosition(ServerLevel level, BlockPos deathPos) {
        // Try the death position first
        if (level.getBlockState(deathPos).isAir() || level.getBlockState(deathPos).liquid()) {
            return deathPos;
        }
        // Try above
        for (int dy = 1; dy <= 5; dy++) {
            BlockPos up = deathPos.above(dy);
            if (level.getBlockState(up).isAir()) return up;
        }
        // Fallback to death position (replace whatever is there)
        return deathPos;
    }

    private String makeKey(String dim, BlockPos pos) {
        return dim + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
