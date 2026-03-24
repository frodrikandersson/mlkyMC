package com.mlkymc.classes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Grants profession XP for class-related activities.
 *
 * Adventurer: first-time block travel, killing mobs (scaled by difficulty)
 * Cleric: healing/resurrection (in ActiveSkillHandler), enchanting, brewing, vanilla XP orbs
 * Farmhand: breeding/killing neutrals, fishing, harvesting fully-grown crops
 * MineCrafter: mining, woodcutting, crafting (material-based via CraftingXpRegistry)
 * Smith: smelting, anvil repair/enchant
 */
public class ClassExpHandler {
    private final ClassManager classManager;

    // Track explored block positions per player for Adventurer XP
    private final java.util.Map<UUID, Set<Long>> exploredPositions = new java.util.HashMap<>();

    // Track player-placed sugar cane, pumpkin, melon positions (dimension key + block pos)
    // These blocks should NOT give Farmhand XP when broken
    private final Set<Long> playerPlacedGrowables = new HashSet<>();

    public ClassExpHandler(ClassManager classManager) {
        this.classManager = classManager;
    }

    // =========================================================================
    // ADVENTURER: mob kills + block travel
    // =========================================================================

    @SubscribeEvent
    public void onMobKill(LivingDeathEvent event) {
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;

        Entity dead = event.getEntity();

        // Check if mob was cursed (Curse of Unrest aura) — double XP multiplier
        boolean cursed = PowerHandler.isCursedMob(dead.getId());
        int multiplier = cursed ? 2 : 1;
        if (cursed) {
            PowerHandler.removeCursedMob(dead.getId());
        }

        // Hostile mobs (including Phantoms) = Adventurer XP
        if (dead instanceof Monster || dead instanceof net.minecraft.world.entity.monster.Phantom) {
            int xp = 10;
            String mobId = dead.getType().getDescriptionId();
            if (mobId.contains("ender_dragon") || mobId.contains("wither")) {
                xp = 100;
            } else if (mobId.contains("warden") || mobId.contains("elder_guardian")) {
                xp = 50;
            } else if (mobId.contains("blaze") || mobId.contains("ghast") || mobId.contains("piglin_brute")) {
                xp = 25;
            } else if (mobId.contains("enderman") || mobId.contains("witch") || mobId.contains("ravager")
                    || mobId.contains("evoker") || mobId.contains("vindicator") || mobId.contains("guardian")) {
                xp = 15;
            }
            classManager.addXp(player, ProfessionType.ADVENTURER, xp * multiplier);
        }

        // Neutral/passive mobs = Farmhand XP
        if (dead instanceof Animal) {
            classManager.addXp(player, ProfessionType.FARMHAND, 5 * multiplier);
        }

        // Cursed mobs also give double vanilla XP orbs
        if (cursed && dead instanceof net.minecraft.world.entity.LivingEntity living) {
            int vanillaXp = living.getExperienceReward((ServerLevel) player.level(), player);
            if (vanillaXp > 0) {
                net.minecraft.world.entity.ExperienceOrb.award((ServerLevel) player.level(),
                        dead.position(), vanillaXp);
            }
        }
    }

    /**
     * Adventurer XP: first-time block travel.
     * Called from a tick handler. Grants 1 XP per new block position visited.
     */
    public void onPlayerMove(ServerPlayer player, int blockX, int blockY, int blockZ) {
        long pos = ((long) blockX & 0x3FFFFFF) << 38 | ((long) blockY & 0xFFF) << 26 | ((long) blockZ & 0x3FFFFFF);
        UUID uuid = player.getUUID();
        Set<Long> visited = exploredPositions.computeIfAbsent(uuid, k -> new HashSet<>());
        if (visited.add(pos)) {
            classManager.addXp(player, ProfessionType.ADVENTURER, 1);
        }
    }

    // =========================================================================
    // CLERIC: enchanting, brewing, vanilla XP orbs
    // (Healing/resurrection XP is handled in ActiveSkillHandler)
    // =========================================================================

    /**
     * Cleric XP: enchanting items at an enchanting table.
     * Higher enchantment level cost = more XP.
     */
    @SubscribeEvent
    public void onEnchantItem(PlayerEnchantItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        // XP scales with number of enchantments applied
        int enchantCount = event.getEnchantments().size();
        classManager.addXp(player, ProfessionType.CLERIC, 5 + enchantCount * 5);
    }

    /**
     * Cleric XP: brewing potions. XP varies by potion complexity.
     */
    @SubscribeEvent
    public void onPotionBrewed(PlayerBrewedPotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ItemStack stack = event.getStack();
        String potionId = "";
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents != null && contents.potion().isPresent()) {
            potionId = contents.potion().get().getRegisteredName();
        }

        // Tier by potion rarity
        int xp;
        if (potionId.contains("regeneration") || potionId.contains("strength")
                || potionId.contains("invisibility") || potionId.contains("turtle_master")) {
            xp = 10; // Complex potions
        } else if (potionId.contains("swiftness") || potionId.contains("healing")
                || potionId.contains("fire_resistance") || potionId.contains("night_vision")
                || potionId.contains("water_breathing") || potionId.contains("leaping")) {
            xp = 7; // Medium potions
        } else if (potionId.contains("poison") || potionId.contains("weakness")
                || potionId.contains("harming") || potionId.contains("slowness")) {
            xp = 5; // Negative/easy potions
        } else if (potionId.contains("long_") || potionId.contains("strong_")) {
            xp = 8; // Extended/amplified variants
        } else {
            xp = 3; // Mundane, thick, awkward, etc.
        }

        classManager.addXp(player, ProfessionType.CLERIC, xp);
    }

    /**
     * Cleric XP: gaining vanilla experience orbs also grants Cleric XP.
     * 1 Cleric XP per 3 vanilla XP picked up (minimum 1).
     * Excludes orbs from Bottle o' Enchanting (tagged by ThrownExperienceBottle).
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public void onXpPickup(PlayerXpEvent.PickupXp event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        var orb = event.getOrb();

        // Skip orbs from Bottle o' Enchanting
        if (orb.getTags().contains("mlkymc_bottle_xp")) return;

        int orbValue = orb.getValue();

        // Cleric in Soul Energy Mode: convert XP to Soul Energy instead
        // Spec: 1 xp level ≈ 10 SE. At low levels, 1 level ≈ 7 XP points.
        // So ratio is ~1.4 SE per XP point. Use orbValue / 5 to keep it balanced.
        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() == ClassType.CLERIC && data.isSoulEnergyMode()) {
            int seGain = Math.max(1, orbValue / 5);

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
                        // If all SE went to altar, done
                        if (seGain <= 0) {
                            orb.discard();
                            event.setCanceled(true);
                            return;
                        }
                    }
                }
            }

            int before = data.getSoulEnergy();
            // If SE is already full, let the orb give normal XP instead
            if (before >= 100) return;
            data.addSoulEnergy(seGain);
            int after = data.getSoulEnergy();
            if (after != before) {
                classManager.sendSoulSync(player);
            }
            // Remove the orb so it doesn't get picked up again
            orb.discard();
            event.setCanceled(true);
            return;
        }

        // Normal: grant Cleric XP from vanilla XP orbs
        int clericXp = Math.max(1, orbValue / 3);
        classManager.addXp(player, ProfessionType.CLERIC, clericXp);
    }

    // Track positions where experience bottles broke (to tag their orbs)
    private static final java.util.Map<String, Long> bottleBreakPositions = new java.util.HashMap<>();

    /**
     * When an experience bottle projectile hits, record its position.
     */
    @SubscribeEvent
    public void onBottleImpact(ProjectileImpactEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle)) return;

        var pos = event.getEntity().position();
        String key = (int) pos.x + "," + (int) pos.y + "," + (int) pos.z;
        bottleBreakPositions.put(key, event.getEntity().level().getGameTime());
    }

    /**
     * When XP orbs spawn, check if they're from a bottle and tag them.
     */
    @SubscribeEvent
    public void onOrbSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ExperienceOrb orb)) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        // Clean old entries
        long now = sl.getGameTime();
        bottleBreakPositions.entrySet().removeIf(e -> now - e.getValue() > 5);

        // Check if this orb spawned near a recent bottle break
        var pos = orb.position();
        String key = (int) pos.x + "," + (int) pos.y + "," + (int) pos.z;
        if (bottleBreakPositions.containsKey(key)) {
            orb.addTag("mlkymc_bottle_xp");
        }
    }

    // =========================================================================
    // FARMHAND: crops, breeding, fishing
    // =========================================================================

    /**
     * Farmhand XP: fishing.
     */
    @SubscribeEvent
    public void onFishing(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        classManager.addXp(player, ProfessionType.FARMHAND, 10);
    }

    /**
     * Farmhand XP: breeding animals.
     */
    @SubscribeEvent
    public void onAnimalBred(net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent event) {
        if (event.getCausedByPlayer() == null) return;
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player)) return;
        classManager.addXp(player, ProfessionType.FARMHAND, 8);
    }

    // =========================================================================
    // MINECRAFTER: mining, woodcutting, crafting
    // =========================================================================

    /**
     * MineCrafter XP: mining ores, stone, chopping wood.
     * Farmhand XP: harvesting fully-grown crops only.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;

        BlockState state = event.getState();
        String blockId = state.getBlock().getDescriptionId();

        // Ores give more XP
        if (blockId.contains("ore") || blockId.contains("ancient_debris")) {
            classManager.addXp(player, ProfessionType.MINECRAFTER, 5);
        } else if (blockId.contains("stone") || blockId.contains("deepslate")
                || blockId.contains("coal") || blockId.contains("copper")) {
            classManager.addXp(player, ProfessionType.MINECRAFTER, 1);
        } else if (blockId.contains("log") || blockId.contains("wood")) {
            classManager.addXp(player, ProfessionType.MINECRAFTER, 2);
        }

        // Crops = Farmhand XP (only fully grown CropBlock instances)
        if (state.getBlock() instanceof CropBlock crop) {
            if (crop.isMaxAge(state)) {
                classManager.addXp(player, ProfessionType.FARMHAND, 3);
            }
        }
        // Nether wart has its own block type, check by ID + age
        if (blockId.contains("nether_wart")) {
            classManager.addXp(player, ProfessionType.FARMHAND, 3);
        }
        // Cocoa beans
        if (blockId.contains("cocoa")) {
            classManager.addXp(player, ProfessionType.FARMHAND, 3);
        }

        // Sugar cane — count the full column above the broken block
        // When you break a sugar cane, all blocks above it also break from block updates
        // but those don't fire BlockDropsEvent, so we count them here
        if (state.getBlock() instanceof SugarCaneBlock) {
            BlockPos pos = event.getPos();
            // Count how many sugar cane blocks are above this one (they will all break)
            int aboveCount = 0;
            BlockPos check = pos.above();
            while (player.level().getBlockState(check).getBlock() instanceof SugarCaneBlock) {
                aboveCount++;
                check = check.above();
            }
            // Total blocks broken = 1 (this one) + aboveCount
            int totalBroken = 1 + aboveCount;

            // Subtract any player-placed blocks in this column from XP
            int naturalCount = 0;
            BlockPos xpCheck = pos;
            for (int i = 0; i < totalBroken; i++) {
                long key = xpCheck.asLong();
                if (playerPlacedGrowables.contains(key)) {
                    playerPlacedGrowables.remove(key);
                } else {
                    naturalCount++;
                }
                xpCheck = xpCheck.above();
            }
            if (naturalCount > 0) {
                classManager.addXp(player, ProfessionType.FARMHAND, 3 * naturalCount);
            }
        }

        // Pumpkin, melon — only give XP if NOT player-placed
        if (state.getBlock() == Blocks.PUMPKIN || state.getBlock() == Blocks.MELON) {
            BlockPos pos = event.getPos();
            long posKey = pos.asLong();
            if (!playerPlacedGrowables.contains(posKey)) {
                classManager.addXp(player, ProfessionType.FARMHAND, 3);
            } else {
                playerPlacedGrowables.remove(posKey);
            }
        }
    }

    public static final String BLOCK_OWNER_TAG = "mlkymc_owner";
    public static final String BLOCK_OWNER_NAME_TAG = "mlkymc_owner_name";

    /**
     * Check if a block is a utility block relevant to the class system.
     */
    public static boolean isOwnableBlock(BlockState state) {
        String id = state.getBlock().getDescriptionId();
        return id.contains("furnace") || id.contains("smoker") || id.contains("blast")
                || id.contains("composter") || id.contains("brewing")
                || id.contains("anvil") || id.contains("enchant")
                || id.contains("soul_forge")
                || state.getBlock() instanceof net.minecraft.world.level.block.ComposterBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.EnchantingTableBlock;
    }

    /**
     * Track when a player places sugar cane, pumpkin, or melon blocks.
     * Also tag block entities with owner UUID.
     */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockState placed = event.getPlacedBlock();
        if (placed.getBlock() instanceof SugarCaneBlock
                || placed.getBlock() == Blocks.PUMPKIN
                || placed.getBlock() == Blocks.MELON) {
            playerPlacedGrowables.add(event.getPos().asLong());
        }

        // Only tag utility blocks relevant to the class system
        if (isOwnableBlock(placed) && event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            com.mlkymc.world.BlockOwnerData.get(sl.getServer())
                    .setOwner(event.getPos(), player.getUUID(), player.getName().getString());

            // Also tag block entities (for furnace smelting debuff system)
            var be = event.getLevel().getBlockEntity(event.getPos());
            if (be != null) {
                be.getPersistentData().putString(BLOCK_OWNER_TAG, player.getUUID().toString());
                be.getPersistentData().putString(BLOCK_OWNER_NAME_TAG, player.getName().getString());
            }
        }
    }

    /**
     * Update block owner when a player right-clicks (uses) a block.
     * This ensures the last user becomes the owner, not just whoever placed it.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public void onBlockInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl)) return;

        var state = sl.getBlockState(event.getPos());

        if (isOwnableBlock(state)) {
            com.mlkymc.world.BlockOwnerData.get(sl.getServer())
                    .setOwner(event.getPos(), player.getUUID(), player.getName().getString());

            // Also update block entity tag if it has one
            var be = sl.getBlockEntity(event.getPos());
            if (be != null) {
                be.getPersistentData().putString(BLOCK_OWNER_TAG, player.getUUID().toString());
                be.getPersistentData().putString(BLOCK_OWNER_NAME_TAG, player.getName().getString());
            }
        }
    }

    /**
     * Remove block ownership when a block is broken.
     */
    @SubscribeEvent
    public void onBlockBreakOwnership(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            com.mlkymc.world.BlockOwnerData.get(sl.getServer()).removeOwner(event.getPos());
        }
    }

    /**
     * Crafting XP: awarded based on what was crafted.
     * XP amount and profession determined by CraftingXpRegistry.
     */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        var entry = CraftingXpRegistry.lookup(event.getCrafting());
        if (entry.xp() > 0) {
            classManager.addXp(player, entry.profession(), entry.xp());
        }
    }

    // =========================================================================
    // SMITH: smelting, anvil
    // =========================================================================

    /**
     * Smelting XP: routed by output type.
     * Food -> FARMHAND only. Non-food -> SMITH + MINECRAFTER if applicable.
     */
    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ItemStack result = event.getSmelting();
        String id = result.getItem().toString();
        int count = result.getCount(); // Scale XP with stack size

        boolean isFood = id.contains("cooked_") || id.contains("baked_") || id.contains("dried_");

        if (isFood) {
            // Food smelting -> FARMHAND only
            classManager.addXp(player, ProfessionType.FARMHAND, 3 * count);
        } else {
            // Non-food smelting -> SMITH
            classManager.addXp(player, ProfessionType.SMITH, 3 * count);

            // Ingots/materials from ore smelting -> also MINECRAFTER
            if (id.contains("_ingot") || id.equals("gold_nugget") || id.equals("iron_nugget")
                    || id.contains("brick") || id.equals("glass") || id.equals("stone")
                    || id.equals("smooth_stone") || id.contains("glazed_terracotta")
                    || id.equals("sponge") || id.equals("netherite_scrap")
                    || id.equals("quartz") || id.equals("popped_chorus_fruit")
                    || id.equals("charcoal")) {
                int xpPer = id.equals("netherite_scrap") ? 10 : 3;
                classManager.addXp(player, ProfessionType.MINECRAFTER, xpPer * count);
            }
        }

        // Smith Lv20 exclusive: low chance to get Milky Stars from smelting (per item)
        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() == ClassType.SMITH && data.getLevel(ProfessionType.SMITH) >= 20) {
            int starsFound = 0;
            for (int i = 0; i < count; i++) {
                if (player.level().random.nextFloat() < 0.05f) { // 5% chance per item
                    starsFound++;
                }
            }
            if (starsFound > 0) {
                var star = new ItemStack(com.mlkymc.registry.ModItems.MILKY_STAR.get(), starsFound);
                if (!player.getInventory().add(star)) {
                    player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), star);
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Found " + starsFound + " Milky Star" + (starsFound > 1 ? "s" : "") + " while smelting!").withColor(0xFFD700));
            }
        }
    }

    /**
     * Smith XP: using an anvil to repair or enchant items.
     * Fires after the player picks up the anvil result.
     */
    @SubscribeEvent
    public void onAnvilCraft(AnvilCraftEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ItemStack right = event.getRight();
        String rightId = right.getItem().toString();

        // Enchanting with books = higher XP
        if (rightId.contains("enchanted_book")) {
            classManager.addXp(player, ProfessionType.SMITH, 10);
        }
        // Combining/repairing items
        else if (!right.isEmpty()) {
            classManager.addXp(player, ProfessionType.SMITH, 5);
        }
        // Renaming only
        else {
            classManager.addXp(player, ProfessionType.SMITH, 1);
        }
    }
}
