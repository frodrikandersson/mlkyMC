package com.mlkymc.classes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
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
 * MineCrafter: mining, woodcutting, crafting (ingredient-sum-based via ItemBaseValues)
 * Smith: smelting, anvil repair/enchant
 */
public class ClassExpHandler {
    private final ClassManager classManager;

    // Track Lootr chests looted per player for Adventurer XP — persisted to disk
    private java.util.Map<String, Set<String>> lootedLootrChests = new java.util.HashMap<>(); // UUID string -> set of chest keys
    private java.nio.file.Path lootrDataFile;

    // Track player-placed sugar cane, pumpkin, melon positions (dimension key + block pos)
    // These blocks should NOT give Farmhand XP when broken
    private final Set<Long> playerPlacedGrowables = new HashSet<>();

    // Brewing stand tracking: brewTime >0 to 0 AND outputs changed = brew completed.
    private record BrewState(String ingredientId, boolean wasActive, int[] outputFingerprints) {}
    private final java.util.Map<Long, BrewState> brewingStates = new java.util.HashMap<>();

    private static int stackFingerprint(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return ItemStack.hashItemAndComponents(stack);
    }

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

        // Elite mob bonus: +50% class XP per elite type on the mob.
        // Single-elite = 1.5×, dual-elite = 2×. Stacks with curse multiplier.
        if (dead.getTags().contains("mlkymc_elite")) {
            int eliteTypes = 0;
            for (String tag : dead.getTags()) {
                if (tag.startsWith("mlkymc_") && !tag.equals("mlkymc_elite")
                        && !tag.equals("mlkymc_minion") && !tag.startsWith("mlkymc_threat_scaled_")) {
                    eliteTypes++;
                }
            }
            if (eliteTypes > 0) {
                // 1.5× for single, 2× for dual — applied as multiplier on top of curse
                double eliteBonus = 1.0 + (eliteTypes * 0.5);
                multiplier = (int) Math.ceil(multiplier * eliteBonus);
            }
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
            classManager.addXp(player, ProfessionType.ADVENTURER, xp * multiplier,
                    "kill " + dead.getType().getDescriptionId().replace("entity.minecraft.", "") + (cursed ? " (cursed 2x)" : ""));

            // Adventurer exclusive: 1/100 chance of Totem of Undying from pillager-type mobs
            ClassData advKillData = classManager.getOrCreate(player);
            if (advKillData.getChosenClass() == ClassType.ADVENTURER) {
                String killMobId = dead.getType().getDescriptionId();
                if (killMobId.contains("pillager") || killMobId.contains("vindicator")
                        || killMobId.contains("evoker") || killMobId.contains("ravager")
                        || killMobId.contains("illusioner")) {
                    if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) == 0) {
                        ItemStack totem = new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
                        if (!player.getInventory().add(totem)) {
                            player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), totem);
                        }
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "A Totem of Undying materializes!").withColor(0xFF55FF));
                    }
                }
            }
        }

        // Hoglins are Animals (breedable) but hostile — route to Adventurer XP, not Farmhand.
        if (dead instanceof Animal && dead.getType().getDescriptionId().contains("hoglin")) {
            classManager.addXp(player, ProfessionType.ADVENTURER, 15 * multiplier,
                    "kill " + dead.getType().getDescriptionId().replace("entity.minecraft.", ""));
        }
        // Neutral/passive mobs = Farmhand XP (excludes hoglins handled above)
        else if (dead instanceof Animal) {
            classManager.addXp(player, ProfessionType.FARMHAND, 5 * multiplier,
                    "kill " + dead.getType().getDescriptionId().replace("entity.minecraft.", ""));
        }

        // Curse of Unrest XP doubling is now handled at pickup time (works with Clumps mod)
    }

    /**
     * Adventurer XP: looting a Lootr chest for the first time.
     * Grants 100 XP per unique Lootr chest opened.
     * Detects Lootr chests by checking if the block entity implements ILootrBlockEntity.
     */
    @SubscribeEvent
    public void onContainerOpen(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;

        // Check blocks in range — try raycast first, then scan nearby
        net.minecraft.core.BlockPos lootrPos = null;

        // Method 1: raycast
        var hitResult = player.pick(6.0, 0, false);
        if (hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit
                && hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            if (isLootrBlock(sl, blockHit.getBlockPos())) {
                lootrPos = blockHit.getBlockPos();
            }
        }

        // Method 2: scan nearby blocks if raycast missed
        if (lootrPos == null) {
            var playerPos = player.blockPosition();
            outer:
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        var checkPos = playerPos.offset(dx, dy, dz);
                        if (isLootrBlock(sl, checkPos)) {
                            lootrPos = checkPos;
                            break outer;
                        }
                    }
                }
            }
        }

        if (lootrPos == null) return;

        // Track per-player per-chest
        String chestKey = sl.dimension().identifier().toString() + ":" + lootrPos.asLong();
        String uuidStr = player.getStringUUID();
        Set<String> looted = lootedLootrChests.computeIfAbsent(uuidStr, k -> new HashSet<>());

        if (looted.add(chestKey)) {
            classManager.addXp(player, ProfessionType.ADVENTURER, 100, "Lootr chest discovery");
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "+100 Adventurer XP (Loot discovery!)").withColor(0x55FFFF), true);
            saveLootrData();

            // Adventurer exclusive: chance to find Milky Stars in Lootr chests
            ClassData advData = classManager.getOrCreate(player);
            if (advData.getChosenClass() == ClassType.ADVENTURER) {
                int advLvl = advData.getLevel(ProfessionType.ADVENTURER);
                if (advLvl >= 10) {
                    int starAmount = advLvl >= 50 ? 3 + player.level().random.nextInt(5)
                            : advLvl >= 30 ? 2 + player.level().random.nextInt(3)
                            : 1 + player.level().random.nextInt(2);
                    var star = com.mlkymc.economy.MilkyStar.create(starAmount);
                    if (!player.getInventory().add(star)) {
                        player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), star);
                    }
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Found " + starAmount + " Milky Star" + (starAmount > 1 ? "s" : "") + " in the chest!").withColor(0xFFD700));
                }
            }
        }
    }

    // --- Lootr data persistence ---

    private static final com.google.gson.Gson LOOTR_GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    public void setLootrDataFile(java.nio.file.Path worldDir) {
        this.lootrDataFile = worldDir.resolve("mlkymc_looted_chests.json");
        loadLootrData();
    }

    private void loadLootrData() {
        if (lootrDataFile == null || !java.nio.file.Files.exists(lootrDataFile)) return;
        try (var reader = java.nio.file.Files.newBufferedReader(lootrDataFile)) {
            var type = new com.google.gson.reflect.TypeToken<java.util.Map<String, Set<String>>>() {}.getType();
            java.util.Map<String, Set<String>> loaded = LOOTR_GSON.fromJson(reader, type);
            if (loaded != null) lootedLootrChests = loaded;
        } catch (Exception e) {
            com.mlkymc.MlkyMC.LOGGER.error("Failed to load looted chests data", e);
        }
    }

    private void saveLootrData() {
        if (lootrDataFile == null) return;
        try {
            java.nio.file.Files.createDirectories(lootrDataFile.getParent());
            try (var writer = java.nio.file.Files.newBufferedWriter(lootrDataFile)) {
                LOOTR_GSON.toJson(lootedLootrChests, writer);
            }
        } catch (Exception e) {
            com.mlkymc.MlkyMC.LOGGER.error("Failed to save looted chests data", e);
        }
    }

    /**
     * Check if a block is a Lootr block by checking block ID or block entity class name.
     */
    private boolean isLootrBlock(net.minecraft.server.level.ServerLevel sl, net.minecraft.core.BlockPos pos) {
        // Check block ID for "lootr"
        String blockId = sl.getBlockState(pos).getBlock().getDescriptionId();
        if (blockId.contains("lootr")) return true;

        // Check block entity class hierarchy for ILootrBlockEntity
        var be = sl.getBlockEntity(pos);
        if (be == null) return false;
        Class<?> cls = be.getClass();
        while (cls != null) {
            for (Class<?> iface : cls.getInterfaces()) {
                if (iface.getName().contains("Lootr")) return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    // =========================================================================
    // CLERIC: enchanting, brewing, vanilla XP orbs
    // (Healing/resurrection XP is handled in ActiveSkillHandler)
    // =========================================================================

    /**
     * Cleric XP + Milky Stars: enchanting items at an enchanting table.
     * XP scales with total enchantment levels. Stars are Cleric exclusive.
     */
    @SubscribeEvent
    public void onEnchantItem(PlayerEnchantItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        // XP scales with total enchantment levels applied
        int totalLevels = 0;
        for (var inst : event.getEnchantments()) {
            totalLevels += inst.level();
        }
        int enchantXp = 5 + totalLevels * 12;
        classManager.addXp(player, ProfessionType.CLERIC, enchantXp,
                "enchant (" + event.getEnchantments().size() + " enchants, " + totalLevels + " total lvls)");

        // Milky Stars: Cleric exclusive, scaled by enchant level cost
        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() == ClassType.CLERIC) {
            int stars = Math.max(1, totalLevels); // 1 star per enchant level, minimum 1
            var starStack = com.mlkymc.economy.MilkyStar.create(stars);
            if (!player.getInventory().add(starStack)) {
                player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), starStack);
            }
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "+" + stars + " Milky Stars (enchanting)").withColor(0xFFD700));
        }
    }

    /**
     * Tick-based brewing stand tracker. Scans owned brewing stands near players.
     * Snapshots the ingredient + output items. When the ingredient disappears AND
     * the outputs have changed, a brew actually completed — award the owner.
     * If the ingredient disappears but outputs are unchanged, it was manually removed.
     * Called from MlkyMC server tick.
     */
    public void tickBrewingStands(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return;
        if (sl.getGameTime() % 5 != 0) return; // Check every 5 ticks

        var ownerData = com.mlkymc.world.BlockOwnerData.get(sl.getServer());
        var loadedOwned = ownerData.getLoadedOwned(sl);

        for (var entry : loadedOwned.entrySet()) {
            net.minecraft.core.BlockPos pos = entry.getKey();
            var be = sl.getBlockEntity(pos);
            if (!(be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity brewingStand)) continue;

            long posKey = pos.asLong();

            // Read brewTime via ContainerData (index 0 = brewTime, index 1 = fuel)
            int brewTime;
            try {
                var field = net.minecraft.world.level.block.entity.BrewingStandBlockEntity.class.getDeclaredField("brewTime");
                field.setAccessible(true);
                brewTime = field.getInt(brewingStand);
            } catch (Exception e) {
                continue;
            }

            BrewState prev = brewingStates.get(posKey);
            boolean wasActive = prev != null && prev.wasActive;

            // Fingerprint current outputs
            int[] currentFp = new int[3];
            for (int i = 0; i < 3; i++) {
                currentFp[i] = stackFingerprint(brewingStand.getItem(i));
            }

            if (brewTime > 0) {
                // Brewing is active — record the ingredient and snapshot outputs
                ItemStack ingredientSlot = brewingStand.getItem(3);
                String ingId = ingredientSlot.isEmpty() ? "" : ingredientSlot.getItem().getDescriptionId();
                brewingStates.put(posKey, new BrewState(ingId, true, currentFp));
            } else {
                // brewTime is 0 — if it WAS active, check if outputs actually changed
                if (wasActive && prev.ingredientId != null && !prev.ingredientId.isEmpty()) {
                    boolean outputsChanged = false;
                    for (int i = 0; i < 3; i++) {
                        if (currentFp[i] != prev.outputFingerprints[i]) {
                            outputsChanged = true;
                            break;
                        }
                    }

                    if (outputsChanged) {
                        // Brew completed! Find the owner
                        UUID ownerUuid = ownerData.getOwnerUUID(pos);
                        if (ownerUuid != null) {
                            ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(ownerUuid);
                            if (owner != null) {
                                int xp = getBrewIngredientXp(prev.ingredientId);
                                int stars = getBrewIngredientStars(prev.ingredientId);

                                classManager.addXp(owner, ProfessionType.CLERIC, xp,
                                        "brew (" + prev.ingredientId.replace("block.minecraft.", "").replace("item.minecraft.", "") + ")");

                                ClassData data = classManager.getOrCreate(owner);
                                if (data.getChosenClass() == ClassType.CLERIC && stars > 0) {
                                    var starStack = com.mlkymc.economy.MilkyStar.create(stars);
                                    if (!owner.getInventory().add(starStack)) {
                                        owner.spawnAtLocation(sl, starStack);
                                    }
                                    owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                            "+" + stars + " Milky Stars (brewing)").withColor(0xFFD700));
                                }
                            }
                        }
                    }
                }
                // Mark as inactive with current output fingerprints
                brewingStates.put(posKey, new BrewState("", false, currentFp));
            }
        }
    }

    /**
     * Cleric XP from brewing, scaled by ingredient difficulty.
     *
     * Tier 4 (Rare):    Rare and end game ingredients, often from difficult mobs or dimensions
     * Tier 3 (Medium):  Difficult to obtain but not super rare
     * Tier 2 (Common):  Somewhat easy to obtain but not trivial
     * Tier 1 (Basic):   Easy to obtain
     */
    private static int getBrewIngredientXp(String ingredientId) {
        // Tier 4 - Rare
        if (ingredientId.contains("dragon_breath")
                || ingredientId.contains("ghast_tear")
                || ingredientId.contains("rabbit_foot")) {
            return 35;
        }
        // Tier 3 - Medium
        if (ingredientId.contains("blaze_powder") 
                || ingredientId.contains("magma_cream")
                || ingredientId.contains("golden_carrot") 
                || ingredientId.contains("pufferfish")
                || ingredientId.contains("phantom_membrane") 
                || ingredientId.contains("fermented_spider_eye")) {
            return 25;
        }
        // Tier 2 - Common
        if (ingredientId.contains("glistering_melon") 
                || ingredientId.contains("gunpowder") 
                || ingredientId.contains("glowstone")
                || ingredientId.contains("redstone")
                || ingredientId.contains("spider_eye")) {
            return 20;
        }
        // Tier 1 - Basic
        if (ingredientId.contains("sugar") 
                || ingredientId.contains("nether_wart")) {
            return 10;
        }
        return 5; // Unknown/modded
    }

    /**
     * Cleric Milky Stars gainage from brewing, scaled by ingredient difficulty.
     *
     * Tier 4 (Rare):    Rare and end game ingredients, often from difficult mobs or dimensions
     * Tier 3 (Medium):  Difficult to obtain but not super rare
     * Tier 2 (Common):  Somewhat easy to obtain but not trivial
     * Tier 1 (Basic):   Easy to obtain
     */
    private static int getBrewIngredientStars(String ingredientId) {
        // Tier 4 - Rare
        if (ingredientId.contains("dragon_breath")
                || ingredientId.contains("ghast_tear")
                || ingredientId.contains("rabbit_foot")) {
            return 4;
        }
        // Tier 3 - Medium
        if (ingredientId.contains("blaze_powder") 
                || ingredientId.contains("phantom_membrane") 
                || ingredientId.contains("magma_cream")
                || ingredientId.contains("golden_carrot") 
                || ingredientId.contains("pufferfish")
                || ingredientId.contains("fermented_spider_eye")) {
            return 3;
        }
        // Tier 2 - Common
        if (ingredientId.contains("glistering_melon") 
                || ingredientId.contains("gunpowder") 
                || ingredientId.contains("glowstone")
                || ingredientId.contains("redstone")) {
            return 2;
        }
        // Tier 1 - Basic
        return 1;
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

        // Curse of Unrest active: double all XP orb values (works with Clumps mod)
        ClassData data = classManager.getOrCreate(player);
        boolean curseActive = PowerHandler.isCurseAuraActive(player.getUUID());
        if (curseActive) {
            orbValue *= 2;
            // Also double the actual orb for vanilla XP when not in SE mode
            if (!(data.getChosenClass() == ClassType.CLERIC && data.isSoulEnergyMode())) {
                orb.setValue(orbValue);
            }
        }

        // Cleric in Soul Energy Mode: convert XP to Soul Energy instead
        // Spec: 1 xp level ≈ 10 SE. At low levels, 1 level ≈ 7 XP points.
        // So ratio is ~1.4 SE per XP point. Round up to ensure every orb gives meaningful SE.
        if (data.getChosenClass() == ClassType.CLERIC && data.isSoulEnergyMode()) {
            // Skip orbs we already processed (tagged via addTag)
            if (orb.getTags().contains("mlkymc_se_processed")) {
                event.setCanceled(true);
                return;
            }

            // Apply Cleric exclusive XP bonus to SE conversion too
            double clericBonus = 1.0;
            int clericLv = data.getLevel(ProfessionType.CLERIC);
            if (clericLv >= 45) clericBonus = 2.0;      // +100%
            else if (clericLv >= 35) clericBonus = 1.8;  // +80%
            else if (clericLv >= 25) clericBonus = 1.6;  // +60%
            else if (clericLv >= 15) clericBonus = 1.4;  // +40%
            else if (clericLv >= 5)  clericBonus = 1.2;  // +20%
            int seGain = Math.max(1, (int) Math.ceil(orbValue * 1.4 * clericBonus));

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
                            orb.addTag("mlkymc_se_processed");
                            // Discard after brief delay so pickup animation plays
                            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                                sl.getServer().execute(() -> orb.discard());
                            }
                            event.setCanceled(true);
                            classManager.sendSoulSync(player);
                            return;
                        }
                    }
                }
            }

            int before = data.getSoulEnergy();
            // If SE is already full, let the orb give normal XP instead
            if (before >= 100) return;
            data.addSoulEnergy(seGain);

            // Tag orb as processed, cancel vanilla XP, discard after brief delay
            orb.addTag("mlkymc_se_processed");
            event.setCanceled(true);
            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.getServer().execute(() -> orb.discard());
            }
            // Always sync on SE change
            classManager.sendSoulSync(player);
            return;
        }

        // Normal: grant Cleric XP from vanilla XP orbs
        int clericXp = Math.max(1, orbValue / 3);
        classManager.addXp(player, ProfessionType.CLERIC, clericXp, "xp orb pickup");
    }

    // Clumps mod removed — all XP handling is now in DebuffHandler.onXpPickup

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
        classManager.addXp(player, ProfessionType.FARMHAND, 10, "fishing");
    }

    /**
     * Farmhand XP: breeding animals.
     */
    @SubscribeEvent
    public void onAnimalBred(net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent event) {
        if (event.getCausedByPlayer() == null) return;
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player)) return;
        classManager.addXp(player, ProfessionType.FARMHAND, 8, "breeding");
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
        Item blockItem = state.getBlock().asItem();

        // Only give mining/dig XP for naturally generated blocks
        // Excludes crops, flowers, grass, etc. via profession + shovel check
        String blockDesc = state.getBlock().getDescriptionId();
        boolean isShovelBlock = blockDesc.contains("dirt") || blockDesc.contains("grass_block")
                || blockDesc.contains("sand") || blockDesc.contains("gravel")
                || blockDesc.contains("clay") || blockDesc.contains("soul_sand")
                || blockDesc.contains("soul_soil") || blockDesc.contains("mud")
                || blockDesc.contains("snow") || blockDesc.contains("mycelium")
                || blockDesc.contains("podzol") || blockDesc.contains("concrete_powder");
        ProfessionType blockProf = ItemBaseValues.getProfession(blockItem);
        boolean isMiningOrSmith = blockProf == ProfessionType.MINECRAFTER || blockProf == ProfessionType.SMITH;
        if (!ItemBaseValues.isNaturallyGenerated(blockItem) || (!isMiningOrSmith && !isShovelBlock)) {
            // Still allow crop XP below (crops are planted by players but XP is valid)
        } else {
            // Check if this block was player-placed (no XP for placed blocks)
            boolean isPlayerPlaced = false;
            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                var tracker = com.mlkymc.world.PlacedOreTracker.get(sl.getServer());
                String dim = sl.dimension().identifier().toString();
                if (tracker.isPlayerPlaced(dim, event.getPos())) {
                    isPlayerPlaced = true;
                    tracker.markBroken(dim, event.getPos());
                }
            }

            if (!isPlayerPlaced) {
                int xp = ItemBaseValues.getBaseXp(blockItem);
                if (xp > 0) {
                    // Shovel blocks give Smith XP, everything else gives MineCrafter XP
                    ProfessionType prof = isShovelBlock ? ProfessionType.SMITH : ProfessionType.MINECRAFTER;
                    classManager.addXp(player, prof, xp,
                            (isShovelBlock ? "dig " : "mine ") + blockDesc.replace("block.minecraft.", ""));
                }
            }

            // MineCrafter exclusive: 1/1000 chance of Totem of Undying from emerald ore
            ClassData mcData = classManager.getOrCreate(player);
            if (mcData.getChosenClass() == ClassType.MINECRAFTER
                    && (blockDesc.contains("emerald_ore") || blockDesc.contains("deepslate_emerald_ore"))) {
                if (java.util.concurrent.ThreadLocalRandom.current().nextInt(200) == 0) {
                    ItemStack totem = new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
                    if (!player.getInventory().add(totem)) {
                        player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), totem);
                    }
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "A Totem of Undying materializes!").withColor(0xFF55FF));
                }
            }
        }

        // Crops = Farmhand XP (only fully grown CropBlock instances)
        String blockId = state.getBlock().getDescriptionId();
        if (state.getBlock() instanceof CropBlock crop) {
            if (crop.isMaxAge(state)) {
                classManager.addXp(player, ProfessionType.FARMHAND, 3, "harvest crop");
            }
        }
        // Nether wart has its own block type, check by ID + age
        if (blockId.contains("nether_wart")) {
            classManager.addXp(player, ProfessionType.FARMHAND, 3, "harvest nether wart");
        }
        // Cocoa beans
        if (blockId.contains("cocoa")) {
            classManager.addXp(player, ProfessionType.FARMHAND, 3, "harvest cocoa");
        }

        // Sugar cane — uses PlacedOreTracker to check if player-placed (persists through restarts)
        if (state.getBlock() instanceof SugarCaneBlock) {
            BlockPos pos = event.getPos();
            // Count how many sugar cane blocks are above this one (they will all break)
            int aboveCount = 0;
            BlockPos check = pos.above();
            while (player.level().getBlockState(check).getBlock() instanceof SugarCaneBlock) {
                aboveCount++;
                check = check.above();
            }
            int totalBroken = 1 + aboveCount;

            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                var tracker = com.mlkymc.world.PlacedOreTracker.get(sl.getServer());
                String dim = sl.dimension().identifier().toString();

                int naturalCount = 0;
                BlockPos xpCheck = pos;
                for (int i = 0; i < totalBroken; i++) {
                    if (tracker.isPlayerPlaced(dim, xpCheck)) {
                        tracker.markBroken(dim, xpCheck);
                    } else {
                        naturalCount++;
                    }
                    xpCheck = xpCheck.above();
                }
                if (naturalCount > 0) {
                    classManager.addXp(player, ProfessionType.FARMHAND, 3 * naturalCount, "harvest sugar cane x" + naturalCount);
                }
            }

            // Apply Farmhand drop bonus to the connected blocks above (they break via block updates
            // and don't go through BlockDropsEvent, so the bonus never applies to them)
            if (aboveCount > 0 && player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                ClassData cdata = classManager.getOrCreate(player);
                if (cdata.getChosenClass() == ClassType.FARMHAND) {
                    int fl = cdata.getLevel(ProfessionType.FARMHAND);
                    double bonus = fl >= 45 ? 1.0 : fl >= 35 ? 0.8 : fl >= 25 ? 0.6 : fl >= 15 ? 0.4 : fl >= 5 ? 0.2 : 0.0;
                    if (bonus > 0) {
                        int bonusDrops = 0;
                        for (int i = 0; i < aboveCount; i++) {
                            if (sl.random.nextFloat() < bonus) {
                                bonusDrops++;
                            }
                        }
                        if (bonusDrops > 0) {
                            var bonusStack = new net.minecraft.world.item.ItemStack(
                                    net.minecraft.world.item.Items.SUGAR_CANE, bonusDrops);
                            var itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                                    sl, player.getX(), player.getY(), player.getZ(), bonusStack);
                            sl.addFreshEntity(itemEntity);
                        }
                    }
                }
            }
        }

        // Pumpkin, melon — only give XP if NOT player-placed
        if (state.getBlock() == Blocks.PUMPKIN || state.getBlock() == Blocks.MELON) {
            BlockPos pos = event.getPos();
            long posKey = pos.asLong();
            if (!playerPlacedGrowables.contains(posKey)) {
                classManager.addXp(player, ProfessionType.FARMHAND, 3, "harvest " + (state.getBlock() == Blocks.PUMPKIN ? "pumpkin" : "melon"));
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
                || id.contains("grill") || id.contains("stove")
                || id.contains("microwave") || id.contains("toaster")
                || id.contains("cutting_board") || id.contains("campfire")
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

        // Track player-placed ores/stone/netherrack for exploit prevention
        String placedBlockId = placed.getBlock().getDescriptionId();
        if (com.mlkymc.world.PlacedOreTracker.isTrackedBlock(placedBlockId)
                && event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
            com.mlkymc.world.PlacedOreTracker.get(sl.getServer())
                    .markPlaced(sl.dimension().identifier().toString(), event.getPos());
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
            // NOTE: placed ore cleanup is done in SpecialEffectHandler.onBlockDrop AFTER the drop check
            // Do NOT call markBroken here — it would remove the entry before the drop handler checks it
        }
    }

    /**
     * Crafting XP: awarded based on SUM of ingredient base values.
     * Profession determined by the OUTPUT item.
     * Conversion recipes (9 diamonds → diamond block) give 0 XP.
     */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        var output = event.getCrafting();
        // Skip voided crafts — CraftRestrictionHandler runs at HIGHEST priority and zeroes
        // the result stack when a class-restricted item is crafted by the wrong class.
        // No item produced = no XP.
        if (output.isEmpty() || output.getCount() == 0) return;
        // Skip conversion recipes entirely
        if (ItemBaseValues.isConversionOutput(output.getItem())) return;

        // Sum ingredient base values from the crafting grid
        var grid = event.getInventory();
        int totalXp = 0;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            var slot = grid.getItem(i);
            if (!slot.isEmpty()) {
                totalXp += ItemBaseValues.getBaseXp(slot.getItem());
            }
        }

        if (totalXp <= 0) return;

        // Profession comes from the OUTPUT item
        ProfessionType profession = ItemBaseValues.getProfession(output.getItem());
        classManager.addXp(player, profession, totalXp, "craft " + output.getItem().getDescriptionId().replace("item.mlkymc.", "").replace("item.minecraft.", "").replace("block.minecraft.", ""));

        // MineCrafter Lv30 exclusive: very small chance of getting 1 Milky Star per craft.
        // Only rolls when this craft actually awarded MineCrafter XP — skips conversions
        // (already filtered above via isConversionOutput) and skips crafts whose output
        // routes XP to a different profession (e.g. a MineCrafter crafting food = FARMHAND XP).
        ClassData craftData = classManager.getOrCreate(player);
        if (craftData.getChosenClass() == ClassType.MINECRAFTER
                && profession == ProfessionType.MINECRAFTER
                && craftData.getLevel(ProfessionType.MINECRAFTER) >= 30) {
            float starChance = craftData.getLevel(ProfessionType.MINECRAFTER) >= 50 ? 0.02f : 0.01f; // 2% at 50, 1% at 30
            if (player.level().random.nextFloat() < starChance) {
                // Auto-deposit into a Milky Star Jar if the player has one (hotbar / inventory /
                // nested in a shulker box or pouch); otherwise adds loose stars to inventory.
                com.mlkymc.economy.MilkyStar.giveOrDeposit(player, 1);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Found a Milky Star while crafting!").withColor(0xFFD700));
            }
        }
    }

    // =========================================================================
    // SMITH: smelting, anvil
    // =========================================================================

    /**
     * Smelting XP: based on INPUT item's base value.
     * Profession routed by output type: food → FARMHAND, other → SMITH.
     */
    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ItemStack result = event.getSmelting();
        int count = result.getCount();

        // Look up what was smelted to produce this output
        Item inputItem = ItemBaseValues.getSmeltingInput(result.getItem());
        int xpPerItem;
        if (inputItem != null) {
            xpPerItem = ItemBaseValues.getBaseXp(inputItem);
        } else {
            // Fallback: use output item's base value
            xpPerItem = ItemBaseValues.getBaseXp(result.getItem());
        }

        if (xpPerItem <= 0) xpPerItem = 1; // minimum 1 XP per smelt

        // Profession: food outputs → FARMHAND, everything else → SMITH
        boolean isFood = result.has(net.minecraft.core.component.DataComponents.FOOD);
        ProfessionType profession = isFood ? ProfessionType.FARMHAND : ProfessionType.SMITH;
        classManager.addXp(player, profession, xpPerItem * count,
                "smelt " + result.getItem().getDescriptionId().replace("item.minecraft.", "").replace("block.minecraft.", "") + " x" + count);

        // Smith Lv10 exclusive: chance to get Milky Stars from smelting (scales with level)
        ClassData data = classManager.getOrCreate(player);
        int smithLvl = data.getLevel(ProfessionType.SMITH);
        if (data.getChosenClass() == ClassType.SMITH && smithLvl >= 10) {
            float chance = smithLvl >= 50 ? 0.15f : smithLvl >= 30 ? 0.10f : 0.05f;
            int starsFound = 0;
            for (int i = 0; i < count; i++) {
                if (player.level().random.nextFloat() < chance) {
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

        // Smith exclusive: 1/1000 chance of Totem of Undying when smelting gold ingots
        if (data.getChosenClass() == ClassType.SMITH
                && result.getItem() == net.minecraft.world.item.Items.GOLD_INGOT) {
            if (java.util.concurrent.ThreadLocalRandom.current().nextInt(1000) == 0) {
                ItemStack totem = new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
                if (!player.getInventory().add(totem)) {
                    player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), totem);
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "A Totem of Undying materializes!").withColor(0xFF55FF));
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

        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (right.isEmpty()) {
            // Renaming only
            classManager.addXp(player, ProfessionType.SMITH, 1, "anvil rename");
            return;
        }

        // Base XP from the material tier of the item being worked on
        int materialXp = getAnvilMaterialXp(left);

        // Enchanted book: XP scales with total enchantment levels on the book
        var storedEnchants = right.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        if (storedEnchants != null && storedEnchants.size() > 0) {
            int totalLevels = 0;
            for (var entry : storedEnchants.entrySet()) {
                totalLevels += entry.getIntValue();
            }
            int enchantXp = materialXp + totalLevels * 5;
            classManager.addXp(player, ProfessionType.SMITH, enchantXp,
                    "anvil enchant (tier " + materialXp + " + " + totalLevels + " enchant lvls)");
            return;
        }

        // Combining/repairing: XP based on material tier
        classManager.addXp(player, ProfessionType.SMITH, Math.max(3, materialXp),
                "anvil combine/repair (" + left.getItem().getDescriptionId().replace("item.minecraft.", "") + ")");
    }

    /**
     * Anvil XP based on the material tier of the item being worked on.
     */
    private int getAnvilMaterialXp(ItemStack item) {
        String id = item.getItem().getDescriptionId();
        if (id.contains("netherite")) return 20;
        if (id.contains("diamond")) return 15;
        if (id.contains("iron")) return 8;
        if (id.contains("gold")) return 6;
        if (id.contains("chain")) return 7;
        if (id.contains("stone")) return 4;
        if (id.contains("leather")) return 3;
        if (id.contains("wood") || id.contains("bow") || id.contains("crossbow")
                || id.contains("fishing_rod") || id.contains("shears")
                || id.contains("shield") || id.contains("trident")
                || id.contains("mace") || id.contains("elytra")) return 10;
        return 5; // default for unknown items
    }
}
