package com.mlkymc.classes;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
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
     */
    @SubscribeEvent
    public void onXpPickup(PlayerXpEvent.PickupXp event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ClassData data = classManager.getOrCreate(player);
        double debuff = data.getDebuffPercent(ProfessionType.CLERIC);
        if (debuff <= 0) return;

        // Reduce XP orb value by debuff percent
        var orb = event.getOrb();
        int original = orb.getValue();
        int reduced = (int) Math.ceil(original * (1.0 - debuff));
        orb.setValue(Math.max(1, reduced));
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
}
