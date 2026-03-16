package com.mlkymc.classes;

import com.mlkymc.economy.MilkyStar;
import com.mlkymc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * All active skills for the 5 classes.
 * Active skills ONLY work if the player chose that specific class.
 */
public class ActiveSkillHandler {
    private final ClassManager classManager;

    // Cooldown trackers (UUID -> tick when cooldown expires)
    private final Map<UUID, Long> healingPulseCooldown = new HashMap<>();
    private final Map<UUID, Long> resurrectionCooldown = new HashMap<>();
    private final Map<UUID, Long> naturesCallCooldown = new HashMap<>();
    private final Map<UUID, Long> forgeHeatCooldown = new HashMap<>();
    private final Map<UUID, Long> emergencyRepairCooldown = new HashMap<>();
    private final Map<UUID, Long> temperedBodyCooldown = new HashMap<>();
    private final Map<UUID, Long> weaponDashCooldown = new HashMap<>();

    // MineCrafter Auto-Smelt toggle per player
    private final Set<UUID> autoSmeltEnabled = new HashSet<>();

    // Death location tracking for Cleric resurrection
    private final Map<UUID, BlockPos> deathLocations = new HashMap<>();
    private final Map<UUID, Long> deathTimestamps = new HashMap<>();

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

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ClassData data = classManager.getOrCreate(player);
        if (!data.hasChosenClass()) return;

        ItemStack held = player.getMainHandItem();

        switch (data.getChosenClass()) {
            case ADVENTURER -> { if (handleAdventurerRC(player, data, held)) event.setCanceled(true); }
            case CLERIC -> { if (handleClericRC(player, data, held)) event.setCanceled(true); }
            case FARMHAND -> { if (handleFarmhandRC(player, data, held)) event.setCanceled(true); }
            case SMITH -> { if (handleSmithRC(player, data, held)) event.setCanceled(true); }
            default -> {}
        }
    }

    // =========================================================================
    // ADVENTURER: Weapon Dash
    // =========================================================================

    private boolean handleAdventurerRC(ServerPlayer player, ClassData data, ItemStack held) {
        // Weapon Dash: right-click with a sword to dash forward
        if (!held.isEmpty() && held.getItem().toString().contains("sword")) {
            if (isOnCooldown(weaponDashCooldown, player)) {
                player.sendSystemMessage(Component.literal("Weapon Dash on cooldown! " + getCooldownRemaining(weaponDashCooldown, player) + "s").withColor(0xFF5555));
                return true;
            }

            Vec3 look = player.getLookAngle();
            double dashPower = 2.0;
            player.push(look.x * dashPower, 0.3, look.z * dashPower);
            player.hurtMarked = true;

            player.level().playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5f, 1.5f);
            weaponDashCooldown.put(player.getUUID(), player.level().getGameTime() + 60);
            return true;
        }
        return false;
    }

    // =========================================================================
    // CLERIC: Healing Pulse + Resurrection
    // =========================================================================

    private boolean handleClericRC(ServerPlayer player, ClassData data, ItemStack held) {
        if (held.is(ModItems.MILKY_STAR.get())) {
            if (isOnCooldown(healingPulseCooldown, player)) {
                player.sendSystemMessage(Component.literal("Healing Pulse on cooldown! " + getCooldownRemaining(healingPulseCooldown, player) + "s").withColor(0xFF5555));
                return true;
            }

            int level = data.getLevel(ProfessionType.CLERIC);
            double radius = 8.0 + (level >= 10 ? 4.0 : 0);
            float healAmount = 4.0f + level * 0.2f;

            held.shrink(1);
            player.giveExperienceLevels(-1);

            var nearbyPlayers = player.level().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(radius));
            for (var nearby : nearbyPlayers) {
                nearby.heal(healAmount);
                nearby.sendSystemMessage(Component.literal("Healed by " + player.getName().getString() + "!").withColor(0x55FF55));
            }

            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
            int cooldownTicks = Math.max(100, 400 - level * 4);
            healingPulseCooldown.put(player.getUUID(), player.level().getGameTime() + cooldownTicks);
            return true;
        }
        return false;
    }

    // Track deaths for resurrection
    @SubscribeEvent
    public void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer dead)) return;
        deathLocations.put(dead.getUUID(), dead.blockPosition());
        deathTimestamps.put(dead.getUUID(), dead.level().getGameTime());
    }

    // =========================================================================
    // FARMHAND: Nature's Call + Animal Whisperer
    // =========================================================================

    private boolean handleFarmhandRC(ServerPlayer player, ClassData data, ItemStack held) {
        if (held.is(ModItems.GROWTH_FERTILIZER.get())) {
            if (isOnCooldown(naturesCallCooldown, player)) {
                player.sendSystemMessage(Component.literal("Nature's Call on cooldown! " + getCooldownRemaining(naturesCallCooldown, player) + "s").withColor(0xFF5555));
                return true;
            }

            int level = data.getLevel(ProfessionType.FARMHAND);
            int radius = 3 + level / 10;
            int grown = 0;

            if (player.level() instanceof ServerLevel serverLevel) {
                BlockPos center = player.blockPosition().below();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos pos = center.offset(dx, dy, dz);
                            BlockState state = serverLevel.getBlockState(pos);
                            if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
                                serverLevel.setBlock(pos, crop.getStateForAge(Math.min(crop.getMaxAge(), crop.getAge(state) + 2)), 3);
                                grown++;
                            }
                        }
                    }
                }
            }

            held.shrink(1);
            player.sendSystemMessage(Component.literal("Nature's Call: Grew " + grown + " crops!").withColor(0x55FF55));
            player.level().playSound(null, player.blockPosition(), SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            int cooldownTicks = Math.max(100, 600 - level * 8);
            naturesCallCooldown.put(player.getUUID(), player.level().getGameTime() + cooldownTicks);
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.FARMHAND) return;

        // Animal Whisperer: shift+right-click animal for stats
        if (player.isShiftKeyDown() && event.getTarget() instanceof Animal animal) {
            int age = animal.getAge();
            String ageStr = animal.isBaby() ? "Baby (" + (Math.abs(age) / 20) + "s to adult)" : (age > 0 ? "Breed CD: " + (age / 20) + "s" : "Ready to breed");
            String health = String.format("HP: %.1f/%.1f", animal.getHealth(), animal.getMaxHealth());
            player.sendSystemMessage(Component.literal(animal.getType().getDescription().getString() + " - " + ageStr + " | " + health).withColor(0xAAAA00));

            // Lv20+: Charm hostile mobs
            int farmLevel = data.getLevel(ProfessionType.FARMHAND);
            if (farmLevel >= 20 && event.getTarget() instanceof Monster mob) {
                mob.setTarget(null);
                mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 1200, 0)); // 1min mark
                player.sendSystemMessage(Component.literal("Charmed " + mob.getType().getDescription().getString() + " for 1 minute!").withColor(0x55FF55));
            }

            event.setCanceled(true);
        }
    }

    // =========================================================================
    // MINECRAFTER: Vein Mine + Timber + Auto-Smelt
    // =========================================================================

    @SubscribeEvent
    public void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.MINECRAFTER) return;
        if (!player.isShiftKeyDown()) return;

        BlockState broken = event.getState();
        String blockId = broken.getBlock().getDescriptionId();

        int level = data.getLevel(ProfessionType.MINECRAFTER);
        int maxBlocks = 8 + (level / 10) * 2; // 8 at lv0, 18 at lv50
        // Exclusive: Vein Mine limit doubled at Lv50
        if (data.hasExclusiveSkills(ProfessionType.MINECRAFTER) && level >= 50) {
            maxBlocks *= 2;
        }

        if (player.level() instanceof ServerLevel serverLevel) {
            // Vein Mine: ores
            if (blockId.contains("ore") || blockId.contains("ancient_debris")) {
                Set<BlockPos> toBreak = findConnected(serverLevel, event.getPos(), broken.getBlock(), maxBlocks);
                for (BlockPos pos : toBreak) {
                    if (pos.equals(event.getPos())) continue; // Skip the original block
                    serverLevel.destroyBlock(pos, true, player);
                }
                if (!toBreak.isEmpty()) {
                    player.sendSystemMessage(Component.literal("Vein Mine: " + toBreak.size() + " blocks!").withColor(0x5555FF));
                }
            }

            // Timber: logs
            if (blockId.contains("log") || blockId.contains("wood")) {
                Set<BlockPos> toBreak = findConnected(serverLevel, event.getPos(), broken.getBlock(), maxBlocks);
                for (BlockPos pos : toBreak) {
                    if (pos.equals(event.getPos())) continue;
                    serverLevel.destroyBlock(pos, true, player);
                }
                if (!toBreak.isEmpty()) {
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

    // Auto-Smelt: convert ore drops to smelted form
    @SubscribeEvent
    public void onOreDrop(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.MINECRAFTER) return;
        if (!autoSmeltEnabled.contains(player.getUUID())) return;

        String blockId = event.getState().getBlock().getDescriptionId();
        if (!blockId.contains("ore")) return;

        // Replace drops with smelted versions
        for (var drop : event.getDrops()) {
            ItemStack item = drop.getItem();
            ItemStack smelted = getSmeltedResult(item);
            if (smelted != null) {
                drop.setItem(smelted);
            }
        }
    }

    private ItemStack getSmeltedResult(ItemStack input) {
        // Common ore -> ingot/material mappings
        var item = input.getItem();
        if (item == Items.RAW_IRON) return new ItemStack(Items.IRON_INGOT, input.getCount());
        if (item == Items.RAW_GOLD) return new ItemStack(Items.GOLD_INGOT, input.getCount());
        if (item == Items.RAW_COPPER) return new ItemStack(Items.COPPER_INGOT, input.getCount());
        if (item == Items.COBBLESTONE) return new ItemStack(Items.STONE, input.getCount());
        return null; // No smelted form (diamonds, emeralds, etc. don't smelt)
    }

    // Auto-Smelt toggle command (triggered by chat or keybind)
    public void toggleAutoSmelt(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (autoSmeltEnabled.contains(uuid)) {
            autoSmeltEnabled.remove(uuid);
            player.sendSystemMessage(Component.literal("Auto-Smelt: OFF").withColor(0xFF5555));
        } else {
            autoSmeltEnabled.add(uuid);
            player.sendSystemMessage(Component.literal("Auto-Smelt: ON").withColor(0x55FF55));
        }
    }

    // =========================================================================
    // SMITH: Forge Heat + Emergency Repair + Tempered Body
    // =========================================================================

    private boolean handleSmithRC(ServerPlayer player, ClassData data, ItemStack held) {
        // Emergency Repair: right-click with iron ingot
        if (held.is(Items.IRON_INGOT)) {
            if (isOnCooldown(emergencyRepairCooldown, player)) {
                player.sendSystemMessage(Component.literal("Emergency Repair on cooldown! " + getCooldownRemaining(emergencyRepairCooldown, player) + "s").withColor(0xFF5555));
                return true;
            }

            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                ItemStack equip = player.getItemBySlot(slot);
                if (equip.isDamageableItem() && equip.getDamageValue() > 0) {
                    int repairAmount = equip.getMaxDamage() / 4;
                    equip.setDamageValue(Math.max(0, equip.getDamageValue() - repairAmount));
                    held.shrink(1);
                    player.sendSystemMessage(Component.literal("Repaired " + equip.getHoverName().getString() + " by 25%!").withColor(0xFFAA00));
                    player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.2f);
                    emergencyRepairCooldown.put(player.getUUID(), player.level().getGameTime() + 200);
                    return true;
                }
            }
            player.sendSystemMessage(Component.literal("No damaged equipment to repair!").withColor(0xAAAAAA));
            return true;
        }

        // Tempered Body: right-click with empty hand while on fire
        if (held.isEmpty() && player.isOnFire()) {
            if (isOnCooldown(temperedBodyCooldown, player)) {
                player.sendSystemMessage(Component.literal("Tempered Body on cooldown! " + getCooldownRemaining(temperedBodyCooldown, player) + "s").withColor(0xFF5555));
                return true;
            }

            player.extinguishFire();
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
            player.sendSystemMessage(Component.literal("Tempered Body: Fire extinguished!").withColor(0xFFAA00));
            player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 1.0f);
            temperedBodyCooldown.put(player.getUUID(), player.level().getGameTime() + 200);
            return true;
        }
        return false;
    }

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

                // Place lava bucket worth of fuel (200 ticks burn time equivalent)
                player.sendSystemMessage(Component.literal("Forge Heat: Furnace fueled with forge fire!").withColor(0xFFAA00));
                player.level().playSound(null, event.getPos(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0f, 1.0f);
                forgeHeatCooldown.put(player.getUUID(), player.level().getGameTime() + 600); // 30s
                event.setCanceled(true);
            }
        }

        // MineCrafter: Auto-Smelt toggle — shift+RC crafting table
        if (data.getChosenClass() == ClassType.MINECRAFTER && player.isShiftKeyDown()) {
            BlockState state = player.level().getBlockState(event.getPos());
            if (state.is(Blocks.CRAFTING_TABLE)) {
                toggleAutoSmelt(player);
                event.setCanceled(true);
            }
        }
    }
}
