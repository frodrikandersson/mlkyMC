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
        classManager.addXp(player, ProfessionType.CLERIC, clericXp, "xp orb pickup");

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
                || blockId.contains("door") || blockId.contains("trapdoor")
                || blockId.contains("redstone_block") || blockId.contains("lapis_block");
        if (isCrafted) return;

        // Exempt functional blocks (right-click opens a GUI / has a utility). Most of these
        // don't match the isMining whitelist below anyway, but any whose id contains a
        // mining substring (stonecutter/grindstone/lodestone all contain "stone") would
        // otherwise trip the debuff and nuke the drop when the player breaks one.
        boolean isFunctional = blockId.equals("block.minecraft.stonecutter")
                || blockId.equals("block.minecraft.grindstone")
                || blockId.equals("block.minecraft.lodestone")
                || blockId.equals("block.minecraft.smooth_stone");
        if (isFunctional) return;

        boolean isMining = blockId.contains("ore")
                || blockId.contains("ancient_debris")
                || blockId.contains("deepslate")
                || blockId.contains("stone")
                || blockId.contains("log") || blockId.contains("wood")
                || blockId.contains("stem") || blockId.contains("hyphae")
                || blockId.contains("granite") || blockId.contains("diorite")
                || blockId.contains("andesite") || blockId.contains("tuff")
                || blockId.contains("calcite") || blockId.contains("dripstone")
                || blockId.contains("basalt") || blockId.contains("blackstone")
                || blockId.contains("netherrack") || blockId.contains("end_stone")
                || blockId.contains("obsidian") || blockId.contains("sandstone")
                || blockId.contains("terracotta");

        if (!isMining) return;

        // Skip the debuff entirely for player-placed blocks (place-and-mine exploit prevention).
        // Also free the tracker entry so the slot is reclaimed when the block is broken.
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            var tracker = com.mlkymc.world.PlacedOreTracker.get(sl.getServer());
            String dim = sl.dimension().identifier().toString();
            if (tracker.isPlayerPlaced(dim, event.getPos())) {
                tracker.markBroken(dim, event.getPos());
                return;
            }
        }

        ClassData data = classManager.getOrCreate(player);
        double debuff = data.getDebuffPercent(ProfessionType.MINECRAFTER);
        if (debuff <= 0) return;

        // Only remove bonus drops (ores dropping raw materials, etc.)
        // Never remove drops for blocks that drop themselves (dirt, logs, sand, etc.)
        // This prevents players from losing placed blocks
        if (ThreadLocalRandom.current().nextDouble() < debuff) {
            var blockItem = event.getState().getBlock().asItem();
            boolean affectedDrops = false;

            // Check if player has silk touch — silk touch makes ores drop themselves
            var tool = player.getMainHandItem();
            var enchReg = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            var silkHolder = enchReg.get(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH).orElse(null);
            boolean hasSilkTouch = silkHolder != null
                    && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(silkHolder, tool) > 0;

            if (hasSilkTouch) {
                // Silk touch: reduce stack count of all drops by half (min 0)
                for (var itemEntity : event.getDrops()) {
                    int count = itemEntity.getItem().getCount();
                    if (count > 0) {
                        int reduced = Math.max(1, count / 2);
                        if (reduced < count) {
                            itemEntity.getItem().setCount(reduced);
                            affectedDrops = true;
                        }
                    }
                }
                event.getDrops().removeIf(e -> e.getItem().isEmpty() || e.getItem().getCount() <= 0);
            } else {
                // Check if the block drops itself (logs, sand, etc.) vs bonus drops (ores)
                boolean isSelfDrop = event.getDrops().stream()
                        .anyMatch(e -> e.getItem().getItem() == blockItem);
                boolean hasNonSelfDrops = event.getDrops().stream()
                        .anyMatch(e -> e.getItem().getItem() != blockItem);

                if (hasNonSelfDrops) {
                    // Ore-type: remove bonus drops, keep self-drops
                    affectedDrops = event.getDrops().removeIf(itemEntity -> {
                        var dropItem = itemEntity.getItem().getItem();
                        return dropItem != blockItem;
                    });
                } else if (isSelfDrop) {
                    // Self-dropping block (logs, sand, etc.): clear all drops
                    event.getDrops().clear();
                    affectedDrops = true;
                }
            }

            if (affectedDrops) {
                playDebuffSound(player);
                // Compensate: 10x class XP when debuff triggers
                int baseXp = ItemBaseValues.getBaseXp(blockItem);
                if (baseXp > 0) {
                    classManager.addXp(player, ProfessionType.MINECRAFTER, baseXp * 9,
                            "debuff bonus (lost drops)");
                }
            }
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
            // Reduce extra drops but always keep at least 1 of the primary drop
            var drops = event.getDrops();
            if (drops.size() > 1) {
                var first = drops.iterator().next();
                drops.clear();
                drops.add(first);
            }
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

        // Skip debuff for player-placed tracked blocks (e.g. carved pumpkin placed
        // for decoration/iron golems, sugar cane replanted). Clean up the tracker entry
        // so the slot is freed when broken.
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            var tracker = com.mlkymc.world.PlacedOreTracker.get(sl.getServer());
            String dim = sl.dimension().identifier().toString();
            if (tracker.isPlayerPlaced(dim, event.getPos())) {
                tracker.markBroken(dim, event.getPos());
                return;
            }
        }

        ClassData data = classManager.getOrCreate(player);
        double debuff = data.getDebuffPercent(ProfessionType.FARMHAND);
        if (debuff <= 0) return;

        if (ThreadLocalRandom.current().nextDouble() < debuff) {
            event.getDrops().clear();
            playDebuffSound(player);

            // Compensate: 10x class XP when debuff triggers
            var blockItem = event.getState().getBlock().asItem();
            int baseXp = ItemBaseValues.getBaseXp(blockItem);
            if (baseXp > 0) {
                classManager.addXp(player, ProfessionType.FARMHAND, baseXp * 9,
                        "debuff bonus (lost drops)");
            }
        }
    }

    private static void playDebuffSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 0.5f);
    }
}
