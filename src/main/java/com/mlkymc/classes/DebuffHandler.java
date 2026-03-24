package com.mlkymc.classes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies level-based debuffs per profession.
 * - Adventurer: hostile mob drop-rate/chance -50% to 0%
 * - Cleric: EXP gain -50% to 0%
 * - Farmhand: neutral mob/fishing/farming drops -50% to 0%
 * - MineCrafter: mining/woodcutting drops -50% to 0%
 * - Smith: smelting speed -50% to 0%
 *
 * Debuffs apply to ALL players based on their profession level.
 */
public class DebuffHandler {
    private final ClassManager classManager;

    public DebuffHandler(ClassManager classManager) {
        this.classManager = classManager;
    }

    /**
     * Adventurer debuff: reduce hostile mob drops.
     * Called from MobDropListener before dropping stars.
     */
    public boolean shouldReduceHostileDrops(ServerPlayer player) {
        ClassData data = classManager.getOrCreate(player);
        double debuff = data.getDebuffPercent(ProfessionType.ADVENTURER);
        if (debuff <= 0) return false;
        return ThreadLocalRandom.current().nextDouble() < debuff;
    }

    /**
     * Cleric debuff: reduce XP pickup amount.
     * Cleric exclusive: bonus XP gain (+20/40/60/80/100% at Lv5/15/25/35/45).
     */
    @SubscribeEvent
    public void onXpPickup(PlayerXpEvent.PickupXp event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ClassData data = classManager.getOrCreate(player);
        int level = data.getLevel(ProfessionType.CLERIC);

        var orb = event.getOrb();
        int value = orb.getValue();

        // Debuff: reduce XP for everyone
        double debuff = data.getDebuffPercent(ProfessionType.CLERIC);
        if (debuff > 0) {
            value = (int) Math.ceil(value * (1.0 - debuff));
        }

        // Cleric exclusive: bonus XP gain
        if (data.getChosenClass() == ClassType.CLERIC) {
            double bonus = getClericXpBonus(level);
            if (bonus > 0) {
                value = (int) Math.ceil(value * (1.0 + bonus));
            }
        }

        orb.setValue(Math.max(1, value));
    }

    private static double getClericXpBonus(int level) {
        if (level >= 45) return 1.0;  // +100%
        if (level >= 35) return 0.8;  // +80%
        if (level >= 25) return 0.6;  // +60%
        if (level >= 15) return 0.4;  // +40%
        if (level >= 5)  return 0.2;  // +20%
        return 0.0;
    }

    /**
     * MineCrafter debuff: reduce mining/woodcutting drops.
     * Chance to clear block drops entirely.
     */
    @SubscribeEvent
    public void onBlockDrop(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;

        String blockId = event.getState().getBlock().getDescriptionId();

        // Only apply to mining-related blocks (ores, stone, logs, wood)
        boolean isMining = blockId.contains("ore") || blockId.contains("stone")
                || blockId.contains("deepslate") || blockId.contains("log")
                || blockId.contains("wood") || blockId.contains("coal")
                || blockId.contains("copper") || blockId.contains("iron")
                || blockId.contains("gold") || blockId.contains("diamond")
                || blockId.contains("emerald") || blockId.contains("lapis")
                || blockId.contains("redstone") || blockId.contains("quartz")
                || blockId.contains("netherite") || blockId.contains("ancient_debris");

        if (!isMining) return;

        ClassData data = classManager.getOrCreate(player);
        double debuff = data.getDebuffPercent(ProfessionType.MINECRAFTER);
        if (debuff <= 0) return;

        if (ThreadLocalRandom.current().nextDouble() < debuff) {
            event.getDrops().clear();
        }
    }

    /**
     * Farmhand debuff: reduce neutral mob/fishing/farming drops.
     * Called externally from relevant event handlers.
     */
    public boolean shouldReduceFarmDrops(ServerPlayer player) {
        ClassData data = classManager.getOrCreate(player);
        double debuff = data.getDebuffPercent(ProfessionType.FARMHAND);
        if (debuff <= 0) return false;
        return ThreadLocalRandom.current().nextDouble() < debuff;
    }

    /**
     * Smith debuff: smelting speed multiplier.
     * Returns cook time multiplier (1.0 = normal, 2.0 = half speed at -50%).
     */
    public double getSmeltingSpeedMultiplier(ServerPlayer player) {
        ClassData data = classManager.getOrCreate(player);
        double debuff = data.getDebuffPercent(ProfessionType.SMITH);
        if (debuff <= 0) return 1.0;
        return 1.0 / (1.0 - debuff);
    }

    /**
     * Adventurer debuff: reduce hostile mob drops.
     * Chance to clear all mob drops on kill.
     */
    @SubscribeEvent
    public void onHostileMobDrop(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        var source = event.getSource();
        if (source.getEntity() == null) return;
        if (!(source.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        double debuff = data.getDebuffPercent(ProfessionType.ADVENTURER);
        if (debuff <= 0) return;

        if (ThreadLocalRandom.current().nextDouble() < debuff) {
            event.getDrops().clear();
        }
    }

    /**
     * Farmhand debuff: reduce crop/farming block drops.
     * Applies to wheat, carrots, potatoes, beetroot, sugar cane, melon, pumpkin, cocoa, nether wart.
     */
    @SubscribeEvent
    public void onFarmBlockDrop(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;

        String blockId = event.getState().getBlock().getDescriptionId();
        boolean isFarming = blockId.contains("wheat") || blockId.contains("carrot")
                || blockId.contains("potato") || blockId.contains("beetroot")
                || blockId.contains("sugar_cane") || blockId.contains("melon")
                || blockId.contains("pumpkin") || blockId.contains("cocoa")
                || blockId.contains("nether_wart") || blockId.contains("sweet_berr");

        if (!isFarming) return;

        ClassData data = classManager.getOrCreate(player);
        double debuff = data.getDebuffPercent(ProfessionType.FARMHAND);
        if (debuff <= 0) return;

        if (ThreadLocalRandom.current().nextDouble() < debuff) {
            event.getDrops().clear();
        }
    }
}
