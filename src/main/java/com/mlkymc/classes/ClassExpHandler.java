package com.mlkymc.classes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Grants profession XP for class-related activities.
 *
 * Adventurer: first-time block travel (1 xp), killing mobs (10+ xp)
 * Cleric: enchanting items/books (handled elsewhere), healing/resurrecting
 * Farmhand: breeding/killing neutral mobs, fishing, harvesting crops
 * MineCrafter: mining, woodcutting, crafting (+1 xp/craft)
 * Smith: smelting, anvil repairs, anvil enchanting
 */
public class ClassExpHandler {
    private final ClassManager classManager;

    // Track explored block positions per player for Adventurer XP
    // Key: playerUUID, Value: set of visited chunk-local positions (compacted)
    // Note: This is in-memory only; resets on restart. Could be persisted later.
    private final java.util.Map<UUID, Set<Long>> exploredPositions = new java.util.HashMap<>();

    public ClassExpHandler(ClassManager classManager) {
        this.classManager = classManager;
    }

    /**
     * Adventurer XP: killing hostile mobs.
     * 10 base XP, scaling with mob difficulty.
     */
    @SubscribeEvent
    public void onMobKill(LivingDeathEvent event) {
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;

        Entity dead = event.getEntity();

        // Hostile mobs = Adventurer XP
        if (dead instanceof Monster) {
            int xp = 10;
            String mobId = dead.getType().getDescriptionId();
            if (mobId.contains("ender_dragon") || mobId.contains("wither")) {
                xp = 100;
            } else if (mobId.contains("warden") || mobId.contains("elder_guardian")) {
                xp = 50;
            } else if (mobId.contains("blaze") || mobId.contains("ghast") || mobId.contains("piglin_brute")) {
                xp = 25;
            }
            classManager.addXp(player, ProfessionType.ADVENTURER, xp);
        }

        // Neutral/passive mobs = Farmhand XP
        if (dead instanceof Animal) {
            classManager.addXp(player, ProfessionType.FARMHAND, 5);
        }
    }

    /**
     * MineCrafter XP: mining ores, stone, and chopping wood.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;

        String blockId = event.getState().getBlock().getDescriptionId();

        // Ores give more XP
        if (blockId.contains("ore") || blockId.contains("ancient_debris")) {
            classManager.addXp(player, ProfessionType.MINECRAFTER, 5);
        } else if (blockId.contains("stone") || blockId.contains("deepslate")
                || blockId.contains("coal") || blockId.contains("copper")) {
            classManager.addXp(player, ProfessionType.MINECRAFTER, 1);
        } else if (blockId.contains("log") || blockId.contains("wood")) {
            classManager.addXp(player, ProfessionType.MINECRAFTER, 2);
        }

        // Crops = Farmhand XP
        if (blockId.contains("wheat") || blockId.contains("carrot")
                || blockId.contains("potato") || blockId.contains("beetroot")
                || blockId.contains("nether_wart") || blockId.contains("melon")
                || blockId.contains("pumpkin") || blockId.contains("cocoa")
                || blockId.contains("sweet_berr")) {
            classManager.addXp(player, ProfessionType.FARMHAND, 3);
        }
    }

    /**
     * MineCrafter XP: crafting items.
     */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        classManager.addXp(player, ProfessionType.MINECRAFTER, 1);
    }

    /**
     * Smith XP: smelting items.
     */
    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        classManager.addXp(player, ProfessionType.SMITH, 3);
    }

    /**
     * Adventurer XP: first-time block travel.
     * Called from a tick handler or movement event.
     * Grants 1 XP per new block position visited.
     */
    public void onPlayerMove(ServerPlayer player, int blockX, int blockY, int blockZ) {
        // Compact position to a long for memory efficiency
        long pos = ((long) blockX & 0x3FFFFFF) << 38 | ((long) blockY & 0xFFF) << 26 | ((long) blockZ & 0x3FFFFFF);

        UUID uuid = player.getUUID();
        Set<Long> visited = exploredPositions.computeIfAbsent(uuid, k -> new HashSet<>());

        if (visited.add(pos)) {
            classManager.addXp(player, ProfessionType.ADVENTURER, 1);
        }
    }

    /**
     * Farmhand XP: fishing.
     */
    @SubscribeEvent
    public void onFishing(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        classManager.addXp(player, ProfessionType.FARMHAND, 10);
    }
}
