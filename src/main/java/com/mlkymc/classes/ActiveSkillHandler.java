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
import net.minecraft.world.entity.animal.Animal;
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

    // Tracked animals following players (entity ID -> owner UUID)
    private final Map<Integer, UUID> followingAnimals = new HashMap<>();

    /** Call this when an animal starts following a player */
    public void trackFollowingAnimal(int entityId, UUID ownerUuid) {
        followingAnimals.put(entityId, ownerUuid);
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
    // Auto-applies when off cooldown on next shot. 15s cooldown.
    // Lv40 exclusive: bow/crossbow charge time effectively halved.
    // =========================================================================

    @SubscribeEvent
    public void onArrowLoose(net.neoforged.neoforge.event.entity.player.ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.ADVENTURER) return;

        int level = data.getLevel(ProfessionType.ADVENTURER);

        // Lv40 exclusive: charge time effectively halved (double the charge value)
        if (level >= 40) {
            event.setCharge(Math.min(event.getCharge() * 2, 20));
        }

        // Active skill: projectile speed boost (auto-applies when off cooldown)
        if (!isOnCooldown(projectileBoostCooldown, player)) {
            // Boost charge to max to ensure full-power shot with extra velocity
            event.setCharge(Math.min(event.getCharge() + 10, 20));
            projectileBoostCooldown.put(player.getUUID(), player.level().getGameTime() + 300); // 15s cooldown
            player.sendSystemMessage(Component.literal("Power Shot!").withColor(0x55FFFF));
        }
    }

    // =========================================================================
    // FARMHAND: Nature's Call + Animal Whisperer
    // =========================================================================

    /**
     * Tick handler: makes animals with the following tag walk toward their Farmhand.
     * (Supporting mechanic for Whisperer, which is now in PowerHandler)
     */
    public void tickAnimalFollowing(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % 10 != 0) return; // Every 0.5s

        // Use tracked animal IDs instead of scanning all entities
        java.util.Iterator<Map.Entry<Integer, UUID>> iter = followingAnimals.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, UUID> entry = iter.next();
            int entityId = entry.getKey();
            UUID ownerId = entry.getValue();
            var entity = serverLevel.getEntity(entityId);
            if (!(entity instanceof Animal animal)) {
                iter.remove(); // entity gone
                continue;
            }
            if (!animal.hasEffect(MobEffects.SPEED)) {
                animal.removeTag("mlkymc_following_" + ownerId);
                iter.remove();
                continue;
            }
            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
            if (owner == null || owner.level() != serverLevel) continue;
            double dist = animal.distanceTo(owner);
            if (dist > 3.0) {
                animal.getNavigation().moveTo(owner, 1.2);
            }
        }
    }

    // =========================================================================
    // MINECRAFTER: Vein Mine + Timber + Auto-Smelt
    // =========================================================================

    @SubscribeEvent
    public void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!player.isShiftKeyDown()) return;

        ClassData data = classManager.getOrCreate(player);
        boolean isMinecrafter = data.getChosenClass() == ClassType.MINECRAFTER;

        // Check held tool and determine if player has access
        var held = player.getMainHandItem();
        String heldId = held.getItem().toString();
        boolean hasReinforcedPick = held.is(com.mlkymc.registry.ModItems.REINFORCED_PICKAXE.get());
        boolean hasReinforcedAxe = held.is(com.mlkymc.registry.ModItems.REINFORCED_AXE.get());
        boolean holdingPickaxe = heldId.contains("pickaxe") || hasReinforcedPick;
        boolean holdingAxe = (heldId.contains("axe") && !heldId.contains("pickaxe")) || hasReinforcedAxe;

        // Access: MineCrafter class OR holding Reinforced Tools
        boolean canVeinMine = isMinecrafter || hasReinforcedPick;
        boolean canTimber = isMinecrafter || hasReinforcedAxe;

        if (!canVeinMine && !canTimber) return;

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
                    serverLevel.destroyBlock(pos, true, player);
                }
                if (!toBreak.isEmpty()) {
                    // Durability cost for extra blocks
                    held.hurtAndBreak(toBreak.size() - 1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    player.sendSystemMessage(Component.literal("Vein Mine: " + toBreak.size() + " blocks!").withColor(0x5555FF));
                }
            }

            // Timber: logs (requires axe)
            if (canTimber && holdingAxe
                    && (blockId.contains("log") || blockId.contains("wood"))) {
                Set<BlockPos> toBreak = findConnected(serverLevel, event.getPos(), broken.getBlock(), maxBlocks);
                for (BlockPos pos : toBreak) {
                    if (pos.equals(event.getPos())) continue;
                    serverLevel.destroyBlock(pos, true, player);
                }
                if (!toBreak.isEmpty()) {
                    held.hurtAndBreak(toBreak.size() - 1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    player.sendSystemMessage(Component.literal("Timber: " + toBreak.size() + " blocks!").withColor(0x5555FF));
                }
            }
        }
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

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ClassData data = classManager.getOrCreate(player);

        // Smith: Forge Heat — shift+RC furnace to fill fuel with lava
        if (data.getChosenClass() == ClassType.SMITH && player.isShiftKeyDown()) {
            BlockState state = player.level().getBlockState(event.getPos());
            String blockName = state.getBlock().getDescriptionId();
            if (blockName.contains("furnace") || blockName.contains("smoker") || blockName.contains("blast")) {
                if (isOnCooldown(forgeHeatCooldown, player)) {
                    player.sendSystemMessage(Component.literal("Forge Heat on cooldown! " + getCooldownRemaining(forgeHeatCooldown, player) + "s").withColor(0xFF5555));
                    return;
                }

                // Set furnace lit time directly via reflection (lava bucket = 20000 ticks)
                var be = player.level().getBlockEntity(event.getPos());
                if (!(be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace)) return;

                try {
                    var litField = net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.class
                            .getDeclaredField("litTimeRemaining");
                    litField.setAccessible(true);
                    int currentLit = litField.getInt(furnace);
                    if (currentLit > 0) {
                        player.sendSystemMessage(Component.literal("Furnace is already lit!").withColor(0xFF5555));
                        return;
                    }
                    // Set lit time to lava bucket equivalent (20000 ticks = 1000 seconds)
                    litField.setInt(furnace, 20000);

                    // Also set the lit total time for the GUI display
                    var litTotalField = net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.class
                            .getDeclaredField("litTotalTime");
                    litTotalField.setAccessible(true);
                    litTotalField.setInt(furnace, 20000);

                    // Update block state to show lit furnace
                    var litState = player.level().getBlockState(event.getPos())
                            .setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT, true);
                    player.level().setBlock(event.getPos(), litState, 3);

                    furnace.setChanged();
                } catch (Exception e) {
                    player.sendSystemMessage(Component.literal("Forge Heat failed!").withColor(0xFF5555));
                    return;
                }

                player.sendSystemMessage(Component.literal("Forge Heat: Furnace fueled with forge fire!").withColor(0xFFAA00));
                player.level().playSound(null, event.getPos(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0f, 1.0f);
                forgeHeatCooldown.put(player.getUUID(), player.level().getGameTime() + 600); // 30s
                event.setCanceled(true);
            }
        }

        // MineCrafter Auto-Smelt toggle is now via Secondary Power keybind
    }
}
