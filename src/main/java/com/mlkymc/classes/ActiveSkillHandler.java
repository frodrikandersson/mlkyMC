package com.mlkymc.classes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.*;

/**
 * All active skills for the 5 classes.
 * Active skills ONLY work if the player chose that specific class.
 */
public class ActiveSkillHandler {
    private final ClassManager classManager;

    // Cooldown trackers (UUID -> tick when cooldown expires)
    private final Map<UUID, Long> forgeHeatCooldown = new HashMap<>();
    private final Map<UUID, Long> projectileBoostCooldown = new HashMap<>();

    public long getQuickChargeCooldownExpiry(java.util.UUID uuid) {
        return projectileBoostCooldown.getOrDefault(uuid, 0L);
    }

    // Furnaces lit by Forge Heat — get 2x smelting speed (posKey -> expiry tick)
    private static final Map<Long, Long> forgeHeatedFurnaces = new java.util.concurrent.ConcurrentHashMap<>();

    public long getForgeHeatCooldownExpiry(UUID uuid) {
        Long cd = forgeHeatCooldown.get(uuid);
        return cd != null ? cd : 0;
    }

    /** Mark a furnace as Forge Heated for a duration (milliseconds). */
    public static void markForgeHeated(long posKey, long durationMs) {
        forgeHeatedFurnaces.put(posKey, System.currentTimeMillis() + durationMs);
    }

    /** Check if a furnace at this position has Forge Heat active (2x speed). */
    public static boolean isForgeHeated(long posKey) {
        Long expiry = forgeHeatedFurnaces.get(posKey);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    // Tracked animals following players (entity ID -> owner UUID)
    private final Map<Integer, UUID> followingAnimals = new HashMap<>();
    // Expiry time for each following animal (entity ID -> System.currentTimeMillis when it expires)
    private final Map<Integer, Long> followingExpiry = new HashMap<>();
    // Saved goals removed during follow (entity ID -> removed goals)
    private final Map<Integer, java.util.List<net.minecraft.world.entity.ai.goal.Goal>> savedGoals = new HashMap<>();

    /** Call this when an animal starts following a player (60 second duration) */
    public void trackFollowingAnimal(int entityId, UUID ownerUuid) {
        followingAnimals.put(entityId, ownerUuid);
        followingExpiry.put(entityId, System.currentTimeMillis() + 60_000); // 60 seconds
    }

    // MineCrafter Auto-Smelt is now in PowerHandler

    public ActiveSkillHandler(ClassManager classManager) {
        this.classManager = classManager;
    }

    private boolean isOnCooldown(Map<UUID, Long> cdMap, ServerPlayer player) {
        Long expiry = cdMap.get(player.getUUID());
        return expiry != null && player.level().getGameTime() < expiry;
    }

    private long getCooldownRemaining(Map<UUID, Long> cdMap, ServerPlayer player) {
        Long expiry = cdMap.get(player.getUUID());
        if (expiry == null) return 0;
        long remaining = expiry - player.level().getGameTime();
        return remaining > 0 ? remaining / 20 : 0; // convert ticks to seconds
    }

    // =========================================================================
    // RIGHT-CLICK HANDLER — routes to appropriate skill
    // =========================================================================

    // All class active skills are now routed through PowerHandler keybinds.
    // Only Forge Heat (Shift+RC furnace) and Adventurer arrow boost remain here.

    // =========================================================================
    // ADVENTURER: Bow/Crossbow Projectile Speed Boost + Lv40 Charge Reduction
    // Quick-Charge: when off cooldown, bow draws 3x faster (charge tripled).
    // Cooldown only triggers when the quick-charged arrow is actually fired.
    // 15s cooldown. Lv40 exclusive: bow charge always doubled on top.
    // =========================================================================

    // Quick-Charge draw speed is handled client-side for bows (QuickChargeHandler.java)
    // Crossbows need server-side tick acceleration since they auto-complete on timer.
    // Server tracks whether draw started with quick-charge available.

    // Players who started drawing while quick-charge was off cooldown
    private final java.util.Set<UUID> quickChargeActiveDraw = new java.util.HashSet<>();

    /**
     * Server-side tick: advance crossbow loading faster for Adventurers.
     * Bows don't need this (handled via charge boost on ArrowLooseEvent).
     */
    public void tickQuickChargeCrossbow(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        for (var player : serverLevel.players()) {
            if (!(player instanceof ServerPlayer sp)) continue;
            if (!sp.isUsingItem()) continue;
            if (!(sp.getUseItem().getItem() instanceof net.minecraft.world.item.CrossbowItem)) continue;
            if (!quickChargeActiveDraw.contains(sp.getUUID())) {
                // Not quick-charge draw — check Lv40 passive
                ClassData data = classManager.getOrCreate(sp);
                if (data.getChosenClass() != ClassType.ADVENTURER) continue;
                if (data.getLevel(ProfessionType.ADVENTURER) >= 40) {
                    // Lv40: 2x speed — skip 1 extra tick
                    if (sp.isUsingItem()) sp.updatingUsingItem();
                }
                continue;
            }
            // Quick-charge active: 3x speed — skip 2 extra ticks
            for (int i = 0; i < 2; i++) {
                if (sp.isUsingItem()) sp.updatingUsingItem();
            }
            // Lv40 stacks: skip 1 more
            ClassData data = classManager.getOrCreate(sp);
            if (data.getLevel(ProfessionType.ADVENTURER) >= 40) {
                if (sp.isUsingItem()) sp.updatingUsingItem();
            }
        }
    }

    @SubscribeEvent
    public void onStartUsingBow(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var item = event.getItem();
        if (!(item.getItem() instanceof net.minecraft.world.item.BowItem)
                && !(item.getItem() instanceof net.minecraft.world.item.CrossbowItem)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.ADVENTURER) return;

        // Snapshot: was quick-charge available when draw started?
        if (!isOnCooldown(projectileBoostCooldown, player)) {
            quickChargeActiveDraw.add(player.getUUID());
        } else {
            quickChargeActiveDraw.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onArrowLoose(net.neoforged.neoforge.event.entity.player.ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.ADVENTURER) return;

        int level = data.getLevel(ProfessionType.ADVENTURER);
        int charge = event.getCharge();

        // Lv40 exclusive: always double charge (stacks with quick-charge)
        if (level >= 40) {
            charge = charge * 2;
        }

        // Quick-Charge: only if draw STARTED while off cooldown
        if (quickChargeActiveDraw.remove(player.getUUID())) {
            charge = charge * 3;
            projectileBoostCooldown.put(player.getUUID(), player.level().getGameTime() + 300); // 15s cooldown
            player.displayClientMessage(Component.literal("Quick-Charge!").withColor(0x55FFFF), true);
            player.sendSystemMessage(Component.literal("[MLKYMC_QUICKCHARGE_CD]").withColor(0x000000));
        }

        event.setCharge(Math.min(charge, 20));
    }

    // =========================================================================
    // FARMHAND: Nature's Call + Animal Whisperer
    // =========================================================================

    /**
     * Tick handler: makes mobs with the following tag walk toward their Farmhand.
     * Supports all mob types (animals, golems, dolphins, etc.)
     * (Supporting mechanic for Whisperer, which is now in PowerHandler)
     */
    private long lastAnimalFollowTick = -1;

    public void tickAnimalFollowing(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (followingAnimals.isEmpty()) return;

        // Only process once per server tick (first dimension that calls us)
        long currentTick = serverLevel.getServer().getTickCount();
        if (lastAnimalFollowTick == currentTick) return;
        lastAnimalFollowTick = currentTick;

        var server = serverLevel.getServer();

        java.util.Iterator<Map.Entry<Integer, UUID>> iter = followingAnimals.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, UUID> entry = iter.next();
            int entityId = entry.getKey();
            UUID ownerId = entry.getValue();

            // Find the owner to determine which dimension to search
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner == null) continue;

            // Look for the mob in the owner's dimension
            ServerLevel ownerLevel = (ServerLevel) owner.level();
            var found = ownerLevel.getEntity(entityId);
            if (!(found instanceof net.minecraft.world.entity.Mob mob)) {
                iter.remove();
                continue;
            }
            // Check time-based expiry (60 seconds from activation)
            Long expiry = followingExpiry.get(entityId);
            if (expiry == null || System.currentTimeMillis() >= expiry) {
                mob.removeTag("mlkymc_following_" + ownerId);
                mob.removeTag("mlkymc_goals_cleared");
                mob.setTarget(null);
                mob.setAggressive(false);
                mob.removeEffect(MobEffects.SPEED);
                mob.removeEffect(MobEffects.GLOWING);
                // Restore saved goals
                var restored = savedGoals.remove(entityId);
                if (restored != null) {
                    for (var goal : restored) {
                        mob.goalSelector.addGoal(7, goal);
                    }
                }
                followingExpiry.remove(entityId);
                iter.remove();
                continue;
            }
            // owner already resolved above


            // Remove wandering goals on first tick (so they stop overriding our navigation)
            if (!mob.getTags().contains("mlkymc_goals_cleared")) {
                var removed = new java.util.ArrayList<net.minecraft.world.entity.ai.goal.Goal>();
                var goalsToRemove = new java.util.ArrayList<net.minecraft.world.entity.ai.goal.WrappedGoal>();
                for (var wrapped : mob.goalSelector.getAvailableGoals()) {
                    var goal = wrapped.getGoal();
                    if (goal instanceof net.minecraft.world.entity.ai.goal.RandomStrollGoal
                            || goal instanceof net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
                            || goal instanceof net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
                            || goal instanceof net.minecraft.world.entity.ai.goal.PanicGoal
                            || goal instanceof net.minecraft.world.entity.ai.goal.AvoidEntityGoal) {
                        goalsToRemove.add(wrapped);
                        removed.add(goal);
                    }
                }
                goalsToRemove.forEach(g -> mob.goalSelector.removeGoal(g.getGoal()));
                savedGoals.put(entityId, removed);
                mob.addTag("mlkymc_goals_cleared");
            }

            // Force look at owner
            mob.getLookControl().setLookAt(owner, 30.0f, 30.0f);

            // Always re-path toward owner every tick — no distance check
            mob.getNavigation().moveTo(owner, 1.2);
        }
    }

    // =========================================================================
    // MINECRAFTER: Vein Mine + Timber + Auto-Smelt
    // =========================================================================

    @SubscribeEvent
    public void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        boolean isMinecrafter = data.getChosenClass() == ClassType.MINECRAFTER;

        // Check held tool
        var held = player.getMainHandItem();
        String heldId = held.getItem().toString();
        boolean hasReinforcedPick = held.is(com.mlkymc.registry.ModItems.REINFORCED_PICKAXE.get());
        boolean hasReinforcedAxe = held.is(com.mlkymc.registry.ModItems.REINFORCED_AXE.get());
        boolean holdingPickaxe = heldId.contains("pickaxe") || hasReinforcedPick;
        boolean holdingAxe = (heldId.contains("axe") && !heldId.contains("pickaxe")) || hasReinforcedAxe;
        boolean holdingShovel = heldId.contains("shovel");

        // MineCrafter Vein Mine/Timber requires crouching
        boolean canVeinMine = player.isShiftKeyDown() && (isMinecrafter || hasReinforcedPick);
        boolean canTimber = player.isShiftKeyDown() && (isMinecrafter || hasReinforcedAxe);
        // Smith Tempered Dig: active while Tempered Mind is on (no crouch needed)
        boolean canTemperedDig = PowerHandler.isTemperedMindActive(player.getUUID()) && holdingShovel;

        if (!canVeinMine && !canTimber && !canTemperedDig) return;

        BlockState broken = event.getState();
        String blockId = broken.getBlock().getDescriptionId();

        int level = data.getLevel(ProfessionType.MINECRAFTER);
        int maxBlocks = 8 + (level / 10) * 2; // 8 at lv0, 18 at lv50
        // Exclusive: Vein Mine limit doubled at Lv50
        if (isMinecrafter && level >= 50) {
            maxBlocks *= 2;
        }
        // Reinforced tools for non-MineCrafters: fixed 8 block limit
        if (!isMinecrafter) {
            maxBlocks = 8;
        }

        if (player.level() instanceof ServerLevel serverLevel) {
            // Vein Mine: ores (requires pickaxe)
            if (canVeinMine && holdingPickaxe
                    && (blockId.contains("ore") || blockId.contains("ancient_debris"))) {
                Set<BlockPos> toBreak = findConnected(serverLevel, event.getPos(), broken.getBlock(), maxBlocks);
                for (BlockPos pos : toBreak) {
                    if (pos.equals(event.getPos())) continue;
                    breakWithTool(serverLevel, pos, player, held);
                }
                if (!toBreak.isEmpty()) {
                    held.hurtAndBreak(toBreak.size() - 1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    player.sendSystemMessage(Component.literal("Vein Mine: " + toBreak.size() + (toBreak.size() == 1 ? " block!" : " blocks!")).withColor(0x5555FF));
                }
            }

            // Timber: logs (requires axe)
            if (canTimber && holdingAxe
                    && (blockId.contains("log") || blockId.contains("wood")
                        || blockId.contains("stem") || blockId.contains("hyphae"))) {
                Set<BlockPos> toBreak = findConnected(serverLevel, event.getPos(), broken.getBlock(), maxBlocks);
                for (BlockPos pos : toBreak) {
                    if (pos.equals(event.getPos())) continue;
                    breakWithTool(serverLevel, pos, player, held);
                }
                if (!toBreak.isEmpty()) {
                    held.hurtAndBreak(toBreak.size() - 1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    player.sendSystemMessage(Component.literal("Timber: " + toBreak.size() + (toBreak.size() == 1 ? " block!" : " blocks!")).withColor(0x5555FF));
                }
            }

            // SMITH: Tempered Mind shovel vein mine (active while Tempered Mind is on)
            if (canTemperedDig && isShovelBlock(blockId)) {
                boolean isSmithExclusive = data.getChosenClass() == ClassType.SMITH
                        && data.getLevel(ProfessionType.SMITH) >= 50;
                int shovelMax = isSmithExclusive ? 16 : 8;
                Set<BlockPos> toBreak = findConnected(serverLevel, event.getPos(), broken.getBlock(), shovelMax);
                for (BlockPos pos : toBreak) {
                    if (pos.equals(event.getPos())) continue;
                    breakWithTool(serverLevel, pos, player, held);
                }
                if (!toBreak.isEmpty()) {
                    held.hurtAndBreak(toBreak.size() - 1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    player.displayClientMessage(Component.literal("Tempered Dig: " + toBreak.size() + " blocks!").withColor(0xFFAA00), true);
                }
            }
        }
    }

    /** Check if a block is efficiently broken with a shovel. */
    /**
     * Break a block and drop loot as if the player mined it with their held tool.
     * Respects silk touch, fortune, and other enchantments.
     */
    private void breakWithTool(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        var blockEntity = level.getBlockEntity(pos);
        Block.dropResources(state, level, pos, blockEntity, player, tool);
        level.destroyBlock(pos, false); // false = don't drop default loot (we already dropped with tool)
    }

    private boolean isShovelBlock(String blockId) {
        return blockId.contains("dirt") || blockId.contains("grass_block") || blockId.contains("sand")
                || blockId.contains("gravel") || blockId.contains("clay") || blockId.contains("soul_sand")
                || blockId.contains("soul_soil") || blockId.contains("mud") || blockId.contains("snow")
                || blockId.contains("mycelium") || blockId.contains("podzol") || blockId.contains("rooted_dirt")
                || blockId.contains("coarse_dirt") || blockId.contains("farmland") || blockId.contains("dirt_path")
                || blockId.contains("concrete_powder") || blockId.contains("red_sand");
    }

    /** Check if a block is a furnace-type block (vanilla + modded). */
    private boolean isFurnaceBlock(BlockState state) {
        String id = state.getBlock().getDescriptionId();
        return id.contains("furnace") || id.contains("smoker") || id.contains("blast")
                || id.contains("generator") || id.contains("grill")
                || id.contains("microwave") || id.contains("toaster")
                || id.contains("stove") || id.contains("oven")
                || ItemBaseValues.isCookingBlock(state.getBlock());
    }

    /**
     * Apply Forge Heat to a single furnace: light it + mark for 2x speed.
     * Returns true if successfully applied.
     */
    private boolean applyForgeHeat(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be == null) return false;

        // Vanilla furnaces (furnace, smoker, blast furnace)
        if (be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace) {
            try {
                var litField = net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.class
                        .getDeclaredField("litTimeRemaining");
                litField.setAccessible(true);
                int currentLit = litField.getInt(furnace);
                if (currentLit > 0) return false; // already lit

                litField.setInt(furnace, 20000);

                var litTotalField = net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.class
                        .getDeclaredField("litTotalTime");
                litTotalField.setAccessible(true);
                litTotalField.setInt(furnace, 20000);

                var blockState = level.getBlockState(pos);
                try {
                    var litState = blockState.setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT, true);
                    level.setBlock(pos, litState, 3);
                } catch (Exception ignored) {}

                furnace.setChanged();
                forgeHeatedFurnaces.put(pos.asLong(), System.currentTimeMillis() + 20000L * 50);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        // Modded blocks: try setting energy + totalEnergy fields directly for proper visuals
        try {
            var energyField = be.getClass().getDeclaredField("energy");
            var totalEnergyField = be.getClass().getDeclaredField("totalEnergy");
            energyField.setAccessible(true);
            totalEnergyField.setAccessible(true);

            int currentEnergy = energyField.getInt(be);
            if (currentEnergy > 0) return false; // already has fuel

            energyField.setInt(be, 20000);
            totalEnergyField.setInt(be, 20000);

            // Also try to enable the generator
            try {
                var enabledField = be.getClass().getDeclaredField("enabled");
                enabledField.setAccessible(true);
                enabledField.setBoolean(be, true);
            } catch (Exception ignored) {}

            // Try toggling power on
            try {
                var toggleMethod = be.getClass().getMethod("setNodePowered", boolean.class);
                toggleMethod.invoke(be, true);
            } catch (Exception ignored) {}

            be.setChanged();
            forgeHeatedFurnaces.put(pos.asLong(), System.currentTimeMillis() + 20000L * 50);
            return true;
        } catch (Exception ignored) {}

        // Fallback: try addEnergy(int) method
        try {
            var addEnergyMethod = be.getClass().getMethod("addEnergy", int.class);
            addEnergyMethod.invoke(be, 20000);
            be.setChanged();
            forgeHeatedFurnaces.put(pos.asLong(), System.currentTimeMillis() + 20000L * 50);
            return true;
        } catch (Exception ignored) {}

        // Try setFuel/setBurnTime methods
        try {
            var m = be.getClass().getMethod("setBurnTime", int.class);
            m.invoke(be, 20000);
            be.setChanged();
            forgeHeatedFurnaces.put(pos.asLong(), System.currentTimeMillis() + 20000L * 50);
            return true;
        } catch (Exception ignored) {}

        return false;
    }

    private Set<BlockPos> findConnected(ServerLevel level, BlockPos start, Block targetBlock, int max) {
        Set<BlockPos> found = new LinkedHashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start);
        found.add(start);

        while (!queue.isEmpty() && found.size() < max) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!found.contains(neighbor) && level.getBlockState(neighbor).is(targetBlock)) {
                    found.add(neighbor);
                    queue.add(neighbor);
                }
            }
            // Also check diagonals for trees
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos diag = current.offset(dx, dy, dz);
                        if (!found.contains(diag) && level.getBlockState(diag).is(targetBlock)) {
                            found.add(diag);
                            queue.add(diag);
                        }
                    }
                }
            }
        }
        return found;
    }

    // Auto-Smelt is now handled by PowerHandler (keybind-based toggle with coal consumption)

    // Smith Emergency Repair + Tempered Body moved to PowerHandler (Tempered Mind/Body)

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ClassData data = classManager.getOrCreate(player);

        // Smith: Forge Heat — shift+RC furnace to fill fuel with lava + 2x smelt speed
        if (data.getChosenClass() == ClassType.SMITH && player.isShiftKeyDown()) {
            BlockState state = player.level().getBlockState(event.getPos());
            if (isFurnaceBlock(state)) {
                if (isOnCooldown(forgeHeatCooldown, player)) {
                    player.sendSystemMessage(Component.literal("Forge Heat on cooldown! " + getCooldownRemaining(forgeHeatCooldown, player) + "s").withColor(0xFF5555));
                    return;
                }

                int smithLevel = data.getLevel(ProfessionType.SMITH);
                boolean isSmithExclusive = data.hasExclusiveSkills(ProfessionType.SMITH);

                if (player.level() instanceof ServerLevel sl) {
                    int count = 0;

                    // Lv30 exclusive: AoE — light all furnaces in 5 block radius
                    if (isSmithExclusive && smithLevel >= 30) {
                        BlockPos center = event.getPos();
                        for (int dx = -5; dx <= 5; dx++) {
                            for (int dy = -3; dy <= 3; dy++) {
                                for (int dz = -5; dz <= 5; dz++) {
                                    BlockPos check = center.offset(dx, dy, dz);
                                    if (isFurnaceBlock(sl.getBlockState(check))) {
                                        if (applyForgeHeat(sl, check)) count++;
                                    }
                                }
                            }
                        }
                    } else {
                        // Single target
                        if (applyForgeHeat(sl, event.getPos())) count = 1;
                    }

                    if (count == 0) {
                        player.sendSystemMessage(Component.literal("No inactive furnaces found!").withColor(0xFF5555));
                        return;
                    }

                    String msg = count > 1
                            ? "Forge Heat: " + count + " furnaces fueled + 2x speed!"
                            : "Forge Heat: Furnace fueled + 2x smelt speed!";
                    player.displayClientMessage(Component.literal(msg).withColor(0xFFAA00), true);
                    sl.playSound(null, event.getPos(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0f, 1.0f);
                    forgeHeatCooldown.put(player.getUUID(), player.level().getGameTime() + 600); // 30s
                }
                event.setCanceled(true);
            }
        }

        // MineCrafter Auto-Smelt toggle is now via Secondary Power keybind
    }
}
