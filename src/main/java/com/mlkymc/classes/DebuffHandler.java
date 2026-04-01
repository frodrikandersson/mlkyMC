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
        if (player.isCreative()) return;

        var orb = event.getOrb();
        if (orb.getTags().contains("mlkymc_processed")) return;

        ClassData data = classManager.getOrCreate(player);
        int value = orb.getValue();

        // Curse of Unrest: double XP
        if (PowerHandler.isCurseAuraActive(player.getUUID())) {
            value *= 2;
        }

        // Debuff: reduce XP for everyone based on Cleric level
        double debuff = data.getDebuffPercent(ProfessionType.CLERIC);
        if (debuff > 0) {
            value = (int) Math.ceil(value * (1.0 - debuff));
        }

        // Cleric exclusive: bonus XP gain
        if (data.getChosenClass() == ClassType.CLERIC) {
            double bonus = getClericXpBonus(data.getLevel(ProfessionType.CLERIC));
            if (bonus > 0) {
                value = (int) Math.ceil(value * (1.0 + bonus));
            }

            // Soul Energy mode: convert to SE instead of giving XP
            if (data.isSoulEnergyMode()) {
                int seGain = Math.max(1, (int) Math.ceil(value * 1.4));

                // Adaptive enchantment: redirect SE to altar first
                if (PowerHandler.hasAdaptiveHelmet(player)) {
                    var altarManager = com.mlkymc.MlkyMC.getSoulAltarManager();
                    if (altarManager != null) {
                        var altar = altarManager.getAltarByOwner(player.getUUID());
                        if (altar != null && altar.storedSE < com.mlkymc.altar.SoulAltarManager.MAX_ALTAR_SE) {
                            int altarSpace = com.mlkymc.altar.SoulAltarManager.MAX_ALTAR_SE - altar.storedSE;
                            int toAltar = Math.min(seGain, altarSpace);
                            altar.storedSE += toAltar;
                            altar.highWaterSE = Math.max(altar.highWaterSE, altar.storedSE);
                            altarManager.save();
                            seGain -= toAltar;
                            if (seGain <= 0) {
                                orb.addTag("mlkymc_processed");
                                event.setCanceled(true);
                                orb.discard();
                                classManager.sendSoulSync(player);
                                return;
                            }
                        }
                    }
                }

                int before = data.getSoulEnergy();
                if (before < 100) {
                    data.addSoulEnergy(seGain);
                    classManager.sendSoulSync(player);
                    orb.addTag("mlkymc_processed");
                    event.setCanceled(true);
                    orb.discard();
                    return;
                }
                // SE full — fall through and give regular XP
            }
        }

        // Grant Cleric class XP from vanilla XP
        int clericXp = Math.max(1, value / 3);
        classManager.addXp(player, ProfessionType.CLERIC, clericXp);

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

        // Only apply to naturally occurring underground/terrain blocks
        // Excludes crafted building materials (planks, bricks, slabs, stairs, walls)
        boolean isCrafted = blockId.contains("brick") || blockId.contains("slab")
                || blockId.contains("stair") || blockId.contains("wall")
                || blockId.contains("button") || blockId.contains("pressure")
                || blockId.contains("plank") || blockId.contains("fence")
                || blockId.contains("door") || blockId.contains("trapdoor");
        if (isCrafted) return;

        boolean isMining = blockId.contains("ore")
                || blockId.contains("ancient_debris")
                || blockId.contains("deepslate")
                || blockId.contains("stone")
                || blockId.contains("log") || blockId.contains("wood")
                || blockId.contains("granite") || blockId.contains("diorite")
                || blockId.contains("andesite") || blockId.contains("tuff")
                || blockId.contains("calcite") || blockId.contains("dripstone")
                || blockId.contains("basalt") || blockId.contains("blackstone")
                || blockId.contains("netherrack") || blockId.contains("end_stone")
                || blockId.contains("obsidian")
                || blockId.contains("gravel") || blockId.contains("clay")
                || blockId.contains("sand") || blockId.contains("sandstone")
                || blockId.contains("terracotta") || blockId.contains("dirt")
                || blockId.contains("mud");

        if (!isMining) return;

        // Placed ore cleanup is handled in SpecialEffectHandler (runs at HIGHEST priority)

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
