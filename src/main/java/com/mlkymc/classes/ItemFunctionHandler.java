package com.mlkymc.classes;

import com.mlkymc.registry.ModBlocks;
import com.mlkymc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Item-specific functionality for all mlkyMC items.
 */
public class ItemFunctionHandler {

    // Tracks players who just linked a warp stone (prevent instant teleport)
    private final java.util.Set<UUID> justLinked = new java.util.HashSet<>();

    // Warp Anchor locations stored per warp stone (via NBT)
    private static final String WARP_X = "warp_x";
    private static final String WARP_Y = "warp_y";
    private static final String WARP_Z = "warp_z";
    private static final String WARP_SET = "warp_set";

    // =========================================================================
    // RIGHT-CLICK ITEM (air)
    // =========================================================================

    // Track which players have already seen the bottle hint (cleared when they stop holding it)
    private final java.util.Set<java.util.UUID> bottleHintShown = new java.util.HashSet<>();
    private final java.util.Set<java.util.UUID> potionHintShown = new java.util.HashSet<>();

    @SubscribeEvent
    public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().getGameTime() % 10 != 0) return;

        java.util.UUID uuid = player.getUUID();
        var held = player.getMainHandItem();

        // Glass bottle hint for Clerics
        if (held.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
            if (!bottleHintShown.contains(uuid)) {
                var cm = com.mlkymc.MlkyMC.getClassManager();
                if (cm != null && cm.getOrCreate(player).getChosenClass() == ClassType.CLERIC) {
                    int cost = getBottleCost(cm.getOrCreate(player));
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "Crouch + RMB to bottle your experience (costs " + cost + " XP)")
                            .withColor(0xAA55FF), true);
                    bottleHintShown.add(uuid);
                }
            }
            potionHintShown.remove(uuid);
        }
        // Potion hint for Cleric Lv20+ concoction brewing
        else if (held.is(net.minecraft.world.item.Items.POTION)
                || held.is(net.minecraft.world.item.Items.SPLASH_POTION)
                || held.is(net.minecraft.world.item.Items.LINGERING_POTION)) {
            if (!potionHintShown.contains(uuid)) {
                var cm = com.mlkymc.MlkyMC.getClassManager();
                if (cm != null) {
                    var data = cm.getOrCreate(player);
                    if (data.getChosenClass() == ClassType.CLERIC
                            && data.getLevel(ProfessionType.CLERIC) >= 20) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "Crouch + RMB on a Brewing Stand to brew a Concoction")
                                .withColor(0xAA55FF), true);
                        potionHintShown.add(uuid);
                    }
                }
            }
            bottleHintShown.remove(uuid);
        } else {
            bottleHintShown.remove(uuid);
            potionHintShown.remove(uuid);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack held = player.getMainHandItem();

        // Concoction is now handled by ConcoctionItem directly

        // Cleric: Crouch + RMB with glass bottle → convert to Bottle o' Enchanting
        // Base cost: 20 XP points. Scales with Cleric XP bonus (+20/40/60/80/100%)
        if (player.isShiftKeyDown() && held.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
            var cm = com.mlkymc.MlkyMC.getClassManager();
            if (cm != null) {
                var data = cm.getOrCreate(player);
                if (data.getChosenClass() == ClassType.CLERIC) {
                    int cost = getBottleCost(data);

                    // Check player has enough total XP points (levels + progress bar)
                    int totalXp = getTotalXpPoints(player);
                    if (totalXp < cost) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "Need " + cost + " XP points! (Have: " + totalXp + ")")
                                .withColor(0xFF5555));
                        event.setCanceled(true);
                        return;
                    }

                    // Consume XP points and 1 glass bottle
                    player.giveExperiencePoints(-cost);
                    held.shrink(1);

                    // Give 1 Bottle o' Enchanting
                    var expBottle = new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, 1);
                    if (!player.getInventory().add(expBottle)) {
                        player.spawnAtLocation(
                                (net.minecraft.server.level.ServerLevel) player.level(), expBottle);
                    }

                    // Sound + message
                    if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        sl.playSound(null, player.blockPosition(),
                                net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
                                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f);
                    }
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "Bottled experience! (-" + cost + " XP)").withColor(0xAA55FF), true);

                    event.setCanceled(true);
                    return;
                }
            }
        }

        // Tome of the Soul Warden — open altar guide GUI on client
        if (held.is(ModItems.TOME_OF_THE_SOUL_WARDEN.get())) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[MLKYMC_OPEN_ALTAR_GUIDE]").withColor(0x000000));
            event.setCanceled(true);
            return;
        }

        // Armor Plating: also handle from RightClickItem (when looking at air)
        if (held.is(ModItems.ARMOR_PLATING.get())) {
            applyArmorPlating(player, held);
            event.setCanceled(true);
            return;
        }

        // Ender Pouch
        if (held.is(ModItems.ENDER_POUCH.get())) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inv, player.getEnderChestInventory()),
                    Component.literal("Ender Chest")
            ));
            event.setCanceled(true);
            return;
        }

        // Growth Fertilizer: AoE bone meal in 5-block radius
        if (held.is(ModItems.GROWTH_FERTILIZER.get())) {
            if (player.level() instanceof ServerLevel sl) {
                BlockPos center = player.blockPosition();
                int grown = 0;
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        for (int dy = -3; dy <= 3; dy++) {
                            BlockPos pos = center.offset(dx, dy, dz);
                            var state = sl.getBlockState(pos);
                            if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop) {
                                if (!crop.isMaxAge(state)) {
                                    // Apply bone meal effect (grow by 1-3 stages)
                                    net.minecraft.world.level.block.BonemealableBlock bm = crop;
                                    if (bm.isValidBonemealTarget(sl, pos, state)) {
                                        bm.performBonemeal(sl, sl.random, pos, state);
                                        sl.levelEvent(1505, pos, 0); // Bone meal particles
                                        grown++;
                                    }
                                }
                            } else if (state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock bm) {
                                if (bm.isValidBonemealTarget(sl, pos, state)) {
                                    bm.performBonemeal(sl, sl.random, pos, state);
                                    sl.levelEvent(1505, pos, 0);
                                    grown++;
                                }
                            }
                        }
                    }
                }
                held.shrink(1);
                if (grown > 0) {
                    player.sendSystemMessage(Component.literal("Growth Fertilizer: " + grown + " plants boosted!").withColor(0x55FF55));
                } else {
                    player.sendSystemMessage(Component.literal("No growable plants nearby.").withColor(0xAAAAAA));
                }
                sl.playSound(null, center, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            event.setCanceled(true);
            return;
        }

        // Warp Stone: teleport to linked anchor
        if (held.is(ModItems.WARP_STONE.get())) {
            // Skip if just linked this tick
            if (justLinked.remove(player.getUUID())) {
                event.setCanceled(true);
                return;
            }

            CustomData customData = held.get(DataComponents.CUSTOM_DATA);
            if (customData == null || !customData.copyTag().getBooleanOr(WARP_SET, false)) {
                player.sendSystemMessage(Component.literal("Warp Stone not linked! Right-click a Warp Anchor first.").withColor(0xFF5555));
                event.setCanceled(true);
                return;
            }

            CompoundTag tag = customData.copyTag();
            int x = tag.getIntOr(WARP_X, 0);
            int y = tag.getIntOr(WARP_Y, 0);
            int z = tag.getIntOr(WARP_Z, 0);

            player.teleportTo(x + 0.5, y + 1, z + 0.5);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
            player.sendSystemMessage(Component.literal("Warped!").withColor(0x55FFFF));
            held.shrink(1); // Single use
            event.setCanceled(true);
            return;
        }

        // Grappling Hook: handled by GrapplingHookItem.use() — do NOT intercept here

        // Blessing Scroll: open enchantment selection GUI
        if (held.is(ModItems.BLESSING_SCROLL.get())) {
            openBlessingScrollGui(player);
            event.setCanceled(true);
            return;
        }

        // Whetstone: repair offhand item by +10% durability
        if (held.is(ModItems.WHETSTONE.get())) {
            ItemStack offhand = player.getOffhandItem();
            if (!offhand.isEmpty() && offhand.isDamageableItem() && offhand.getDamageValue() > 0) {
                int maxDur = offhand.getMaxDamage();
                int repairAmount = Math.max(1, (int) (maxDur * 0.10));
                offhand.setDamageValue(Math.max(0, offhand.getDamageValue() - repairAmount));
                player.sendSystemMessage(Component.literal("Repaired " + offhand.getHoverName().getString() + " by 10%!").withColor(0xFFAA00));
                held.shrink(1);
                player.level().playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
            } else if (offhand.isEmpty() || !offhand.isDamageableItem()) {
                player.sendSystemMessage(Component.literal("Hold a damageable item in offhand!").withColor(0xFF5555));
            } else {
                player.sendSystemMessage(Component.literal("Item is already at full durability!").withColor(0xAAAAAA));
            }
            event.setCanceled(true);
            return;
        }

    }

    // =========================================================================
    // RIGHT-CLICK BLOCK
    // =========================================================================

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack held = player.getMainHandItem();
        BlockPos clickedPos = event.getPos();

        // Warp Anchor: right-click with Warp Stone to link it
        if (held.is(ModItems.WARP_STONE.get())) {
            BlockState state = player.level().getBlockState(clickedPos);
            if (state.is(ModBlocks.WARP_ANCHOR.get())) {
                CompoundTag tag = new CompoundTag();
                tag.putBoolean(WARP_SET, true);
                tag.putInt(WARP_X, clickedPos.getX());
                tag.putInt(WARP_Y, clickedPos.getY());
                tag.putInt(WARP_Z, clickedPos.getZ());
                held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                player.sendSystemMessage(Component.literal("Warp Stone linked to anchor at " + clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ() + "!").withColor(0x55FFFF));
                player.level().playSound(null, clickedPos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.BLOCKS, 1.0f, 1.0f);
                justLinked.add(player.getUUID()); // Prevent instant teleport
                event.setCanceled(true);
                return;
            }
        }

        // Builder's Wand: extend placed block in facing direction
        if (held.is(ModItems.BUILDERS_WAND.get())) {
            Direction facing = event.getFace();
            if (facing == null) return;

            BlockState targetState = player.level().getBlockState(clickedPos);
            if (targetState.isAir()) return;

            int placed = 0;
            for (int i = 1; i <= 8; i++) {
                BlockPos placePos = clickedPos.relative(facing, i);
                if (!player.level().getBlockState(placePos).isAir()) break;

                var targetItem = targetState.getBlock().asItem();
                int slot = findItemSlot(player, targetItem);
                if (slot == -1) break;

                player.level().setBlock(placePos, targetState, 3);
                player.getInventory().getItem(slot).shrink(1);
                placed++;
            }

            if (placed > 0) {
                held.hurtAndBreak(placed, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                player.level().playSound(null, clickedPos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            event.setCanceled(true);
            return;
        }

        // Blessing Scroll + Whetstone: moved to onRightClickItem

        // Armor Plating: right-click to apply +1 armor toughness to offhand armor piece
        if (held.is(ModItems.ARMOR_PLATING.get())) {
            applyArmorPlating(player, held);
            event.setCanceled(true);
            return;
        }
    }

    // =========================================================================
    // RIGHT-CLICK ENTITY (Animal Feed)
    // =========================================================================

    @SubscribeEvent
    public void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack held = player.getMainHandItem();

        // Animal Feed: right-click animal to remove breeding cooldown
        if (held.is(ModItems.ANIMAL_FEED.get()) && event.getTarget() instanceof Animal animal) {
            if (animal.getAge() > 0) {
                animal.setAge(0); // Reset breeding cooldown
                held.shrink(1);
                player.sendSystemMessage(Component.literal("Breeding cooldown removed!").withColor(0x55FF55));
                player.level().playSound(null, animal.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.NEUTRAL, 1.0f, 1.0f);
            } else if (animal.isBaby()) {
                animal.setAge(0); // Instantly grow up
                held.shrink(1);
                player.sendSystemMessage(Component.literal("Animal grew up instantly!").withColor(0x55FF55));
            } else {
                player.sendSystemMessage(Component.literal("Animal is already ready to breed!").withColor(0xAAAAAA));
            }
            event.setCanceled(true);
        }
    }

    private int findItemSlot(ServerPlayer player, net.minecraft.world.item.Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) return i;
        }
        return -1;
    }

    // =========================================================================
    // BLESSING SCROLL: Enchantment selection GUI
    // Opens a chest menu with enchanted books. Click one to convert the scroll
    // into that enchanted book, consuming player XP levels.
    // =========================================================================

    private record BlessingEntry(ResourceKey<Enchantment> enchant, int level, int xpCost) {}

    private static final java.util.List<BlessingEntry> BLESSING_ENTRIES = java.util.List.of(
            // Protection enchants
            new BlessingEntry(Enchantments.PROTECTION, 1, 5),
            new BlessingEntry(Enchantments.PROTECTION, 2, 10),
            new BlessingEntry(Enchantments.PROTECTION, 3, 18),
            new BlessingEntry(Enchantments.PROTECTION, 4, 28),
            new BlessingEntry(Enchantments.FIRE_PROTECTION, 1, 5),
            new BlessingEntry(Enchantments.FIRE_PROTECTION, 2, 10),
            new BlessingEntry(Enchantments.FIRE_PROTECTION, 3, 18),
            new BlessingEntry(Enchantments.FIRE_PROTECTION, 4, 28),
            new BlessingEntry(Enchantments.BLAST_PROTECTION, 1, 5),
            new BlessingEntry(Enchantments.BLAST_PROTECTION, 2, 10),
            new BlessingEntry(Enchantments.BLAST_PROTECTION, 3, 18),
            new BlessingEntry(Enchantments.BLAST_PROTECTION, 4, 28),
            new BlessingEntry(Enchantments.PROJECTILE_PROTECTION, 1, 5),
            new BlessingEntry(Enchantments.PROJECTILE_PROTECTION, 2, 10),
            new BlessingEntry(Enchantments.PROJECTILE_PROTECTION, 3, 18),
            new BlessingEntry(Enchantments.PROJECTILE_PROTECTION, 4, 28),
            // Weapon enchants
            new BlessingEntry(Enchantments.SHARPNESS, 1, 5),
            new BlessingEntry(Enchantments.SHARPNESS, 2, 10),
            new BlessingEntry(Enchantments.SHARPNESS, 3, 18),
            new BlessingEntry(Enchantments.SHARPNESS, 4, 28),
            new BlessingEntry(Enchantments.SHARPNESS, 5, 40),
            new BlessingEntry(Enchantments.SMITE, 1, 3),
            new BlessingEntry(Enchantments.SMITE, 2, 7),
            new BlessingEntry(Enchantments.SMITE, 3, 12),
            new BlessingEntry(Enchantments.SMITE, 4, 20),
            new BlessingEntry(Enchantments.SMITE, 5, 30),
            new BlessingEntry(Enchantments.FIRE_ASPECT, 1, 8),
            new BlessingEntry(Enchantments.FIRE_ASPECT, 2, 18),
            new BlessingEntry(Enchantments.LOOTING, 1, 8),
            new BlessingEntry(Enchantments.LOOTING, 2, 18),
            new BlessingEntry(Enchantments.LOOTING, 3, 30),
            new BlessingEntry(Enchantments.KNOCKBACK, 1, 5),
            new BlessingEntry(Enchantments.KNOCKBACK, 2, 12),
            new BlessingEntry(Enchantments.SWEEPING_EDGE, 1, 5),
            new BlessingEntry(Enchantments.SWEEPING_EDGE, 2, 10),
            new BlessingEntry(Enchantments.SWEEPING_EDGE, 3, 18),
            // Tool enchants
            new BlessingEntry(Enchantments.EFFICIENCY, 1, 3),
            new BlessingEntry(Enchantments.EFFICIENCY, 2, 7),
            new BlessingEntry(Enchantments.EFFICIENCY, 3, 12),
            new BlessingEntry(Enchantments.EFFICIENCY, 4, 20),
            new BlessingEntry(Enchantments.EFFICIENCY, 5, 30),
            new BlessingEntry(Enchantments.FORTUNE, 1, 8),
            new BlessingEntry(Enchantments.FORTUNE, 2, 18),
            new BlessingEntry(Enchantments.FORTUNE, 3, 30),
            new BlessingEntry(Enchantments.SILK_TOUCH, 1, 20),
            new BlessingEntry(Enchantments.UNBREAKING, 1, 5),
            new BlessingEntry(Enchantments.UNBREAKING, 2, 12),
            new BlessingEntry(Enchantments.UNBREAKING, 3, 22),
            // Bow enchants
            new BlessingEntry(Enchantments.POWER, 1, 5),
            new BlessingEntry(Enchantments.POWER, 2, 10),
            new BlessingEntry(Enchantments.POWER, 3, 15),
            new BlessingEntry(Enchantments.POWER, 4, 25),
            new BlessingEntry(Enchantments.POWER, 5, 35),
            new BlessingEntry(Enchantments.PUNCH, 1, 5),
            new BlessingEntry(Enchantments.PUNCH, 2, 12),
            new BlessingEntry(Enchantments.FLAME, 1, 10),
            new BlessingEntry(Enchantments.INFINITY, 1, 25),
            // Armor enchants
            new BlessingEntry(Enchantments.THORNS, 1, 8),
            new BlessingEntry(Enchantments.THORNS, 2, 16),
            new BlessingEntry(Enchantments.THORNS, 3, 25),
            new BlessingEntry(Enchantments.FEATHER_FALLING, 1, 5),
            new BlessingEntry(Enchantments.FEATHER_FALLING, 2, 10),
            new BlessingEntry(Enchantments.FEATHER_FALLING, 3, 16),
            new BlessingEntry(Enchantments.FEATHER_FALLING, 4, 22),
            new BlessingEntry(Enchantments.RESPIRATION, 1, 8),
            new BlessingEntry(Enchantments.RESPIRATION, 2, 15),
            new BlessingEntry(Enchantments.RESPIRATION, 3, 22),
            new BlessingEntry(Enchantments.AQUA_AFFINITY, 1, 10),
            new BlessingEntry(Enchantments.DEPTH_STRIDER, 1, 8),
            new BlessingEntry(Enchantments.DEPTH_STRIDER, 2, 15),
            new BlessingEntry(Enchantments.DEPTH_STRIDER, 3, 22),
            // Utility
            new BlessingEntry(Enchantments.MENDING, 1, 35),

            // mlkyMC custom enchantments
            new BlessingEntry(ResourceKey.create(Registries.ENCHANTMENT,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("mlkymc", "wind_burst")), 1, 10),
            new BlessingEntry(ResourceKey.create(Registries.ENCHANTMENT,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("mlkymc", "wind_burst")), 2, 13),
            new BlessingEntry(ResourceKey.create(Registries.ENCHANTMENT,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("mlkymc", "wind_burst")), 3, 15),
            new BlessingEntry(ResourceKey.create(Registries.ENCHANTMENT,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("mlkymc", "adaptive")), 1, 30)
    );

    private void applyArmorPlating(ServerPlayer player, ItemStack held) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold armor in offhand to plate!").withColor(0xFF5555));
            return;
        }

        String itemId = offhand.getItem().toString();
        boolean isArmor = itemId.contains("chestplate") || itemId.contains("leggings")
                || itemId.contains("boots") || itemId.contains("helmet");
        if (!isArmor) {
            player.sendSystemMessage(Component.literal("Hold armor in offhand to plate!").withColor(0xFF5555));
            return;
        }

        var customData = offhand.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().getBooleanOr("mlkymc_plated", false)) {
            player.sendSystemMessage(Component.literal("This armor is already plated!").withColor(0xFF5555));
            return;
        }

        var modifiers = offhand.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        var newModifiers = modifiers.withModifierAdded(
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        net.minecraft.resources.Identifier.parse("mlkymc:armor_plating"),
                        1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ARMOR);
        offhand.set(DataComponents.ATTRIBUTE_MODIFIERS, newModifiers);

        net.minecraft.nbt.CompoundTag tag = customData != null ? customData.copyTag() : new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("mlkymc_plated", true);
        offhand.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));

        player.sendSystemMessage(Component.literal("Plated! +1 armor toughness added to " + offhand.getHoverName().getString()).withColor(0xFFAA00));
        held.shrink(1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    private void openBlessingScrollGui(ServerPlayer player) {
        var registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        // Find which mainhand slot holds the scroll
        int scrollSlot = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.BLESSING_SCROLL.get())) {
                scrollSlot = i;
                break;
            }
        }
        if (scrollSlot == -1) return;
        final int finalScrollSlot = scrollSlot;

        // Build entries that have valid holders
        var validEntries = new java.util.ArrayList<BlessingEntry>();
        for (var entry : BLESSING_ENTRIES) {
            if (registry.get(entry.enchant()).isPresent()) {
                validEntries.add(entry);
            }
        }

        final int ITEMS_PER_PAGE = 45; // 5 rows of 9
        final int TOTAL_PAGES = Math.max(1, (int) Math.ceil(validEntries.size() / (double) ITEMS_PER_PAGE));
        final int[] currentPage = {0};
        var container = new net.minecraft.world.SimpleContainer(54); // always 6 rows

        // Populate a page
        Runnable[] fillPage = new Runnable[1];
        fillPage[0] = () -> {
            // Clear all slots
            for (int i = 0; i < 54; i++) container.setItem(i, ItemStack.EMPTY);

            int start = currentPage[0] * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, validEntries.size());
            int currentLvl = player.experienceLevel;

            for (int i = start; i < end; i++) {
                var entry = validEntries.get(i);
                var holder = registry.get(entry.enchant()).orElse(null);
                if (holder == null) continue;
                boolean canAfford = currentLvl >= entry.xpCost();

                ItemStack book;
                if (canAfford) {
                    book = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
                    book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                            createStoredEnchantment(holder, entry.level()));
                } else {
                    book = new ItemStack(net.minecraft.world.item.Items.BOOK);
                }

                String enchName = holder.value().description().getString();
                int color = canAfford ? 0x55FFFF : 0x555555;
                book.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal(enchName + " " + toRoman(entry.level()))
                                .withColor(color));
                String costText = canAfford ? "Cost: " + entry.xpCost() + " levels"
                        : "Need " + entry.xpCost() + " levels (too expensive)";
                int costColor = canAfford ? 0x55FF55 : 0xFF5555;
                book.set(net.minecraft.core.component.DataComponents.LORE,
                        new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                                net.minecraft.network.chat.Component.literal(costText).withColor(costColor))));
                container.setItem(i - start, book);
            }

            // Navigation row (row 6, slots 45-53)
            // Filler
            var filler = new ItemStack(net.minecraft.world.item.Items.BLACK_STAINED_GLASS_PANE);
            filler.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal(""));
            for (int i = 45; i < 54; i++) container.setItem(i, filler);

            // Previous page arrow (slot 45)
            if (currentPage[0] > 0) {
                var prev = new ItemStack(net.minecraft.world.item.Items.ARROW);
                prev.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal("<< Previous").withColor(0x55FF55));
                container.setItem(45, prev);
            }

            // Page indicator (slot 49)
            var pageInfo = new ItemStack(net.minecraft.world.item.Items.PAPER, currentPage[0] + 1);
            pageInfo.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal("Page " + (currentPage[0] + 1) + " / " + TOTAL_PAGES)
                            .withColor(0xFFFFFF));
            container.setItem(49, pageInfo);

            // Next page arrow (slot 53)
            if (currentPage[0] < TOTAL_PAGES - 1) {
                var next = new ItemStack(net.minecraft.world.item.Items.ARROW);
                next.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal("Next >>").withColor(0x55FF55));
                container.setItem(53, next);
            }
        };
        fillPage[0].run();

        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (containerId, playerInv, p) -> new net.minecraft.world.inventory.ChestMenu(
                        net.minecraft.world.inventory.MenuType.GENERIC_9x6, containerId, playerInv, container, 6) {

                    @Override
                    public void clicked(int slotId, int button,
                                        net.minecraft.world.inventory.ClickType clickType,
                                        net.minecraft.world.entity.player.Player clicker) {
                        if (!(clicker instanceof ServerPlayer sp)) return;

                        // Navigation: previous page
                        if (slotId == 45 && currentPage[0] > 0) {
                            currentPage[0]--;
                            fillPage[0].run();
                            broadcastChanges();
                            return;
                        }
                        // Navigation: next page
                        if (slotId == 53 && currentPage[0] < TOTAL_PAGES - 1) {
                            currentPage[0]++;
                            fillPage[0].run();
                            broadcastChanges();
                            return;
                        }
                        // Navigation row: ignore clicks
                        if (slotId >= 45) return;

                        // Enchantment selection
                        int entryIndex = currentPage[0] * ITEMS_PER_PAGE + slotId;
                        if (entryIndex < 0 || entryIndex >= validEntries.size()) return;

                        var entry = validEntries.get(entryIndex);
                        if (sp.experienceLevel < entry.xpCost()) {
                            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "Not enough XP levels!").withColor(0xFF5555));
                            return;
                        }

                        var scrollStack = sp.getInventory().getItem(finalScrollSlot);
                        if (!scrollStack.is(ModItems.BLESSING_SCROLL.get())) {
                            sp.closeContainer();
                            return;
                        }

                        scrollStack.shrink(1);
                        sp.giveExperienceLevels(-entry.xpCost());

                        var enchRegistry = sp.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                        var holder = enchRegistry.get(entry.enchant());
                        if (holder.isEmpty()) return;

                        ItemStack enchBook = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
                        enchBook.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                                createStoredEnchantment(holder.get(), entry.level()));

                        if (!sp.getInventory().add(enchBook)) {
                            sp.drop(enchBook, false);
                        }

                        String name = holder.get().value().description().getString();
                        sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "Created " + name + " " + toRoman(entry.level()) + " book! (-" + entry.xpCost() + " levels)")
                                .withColor(0x55FFFF));
                        sp.level().playSound(null, sp.blockPosition(),
                                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
                        sp.closeContainer();
                    }

                    @Override
                    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
                        return ItemStack.EMPTY;
                    }
                },
                net.minecraft.network.chat.Component.literal("Choose Enchantment")
        ));
    }

    private static net.minecraft.world.item.enchantment.ItemEnchantments createStoredEnchantment(
            Holder<Enchantment> holder, int level) {
        var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        mutable.set(holder, level);
        return mutable.toImmutable();
    }

    private static String toRoman(int num) {
        return switch (num) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(num);
        };
    }

    // =========================================================================
    // (Holy Water removed — replaced by Concoction system)

    /**
     * Calculate the XP point cost for bottling experience.
     * Base: 20 XP points. Scales with Cleric XP bonus.
     * Lv0: 20, Lv5: 24, Lv15: 28, Lv25: 32, Lv35: 36, Lv45: 40
     */
    private static int getBottleCost(ClassData data) {
        int clericLevel = data.getLevel(ProfessionType.CLERIC);
        double bonus;
        if (clericLevel >= 45) bonus = 1.0;
        else if (clericLevel >= 35) bonus = 0.8;
        else if (clericLevel >= 25) bonus = 0.6;
        else if (clericLevel >= 15) bonus = 0.4;
        else if (clericLevel >= 5) bonus = 0.2;
        else bonus = 0.0;

        return (int) (20 * (1.0 + bonus));
    }

    /**
     * Calculate total XP points from a player's level + progress bar.
     * Uses the vanilla Minecraft XP formula.
     */
    private static int getTotalXpPoints(ServerPlayer player) {
        int level = player.experienceLevel;
        float progress = player.experienceProgress;

        int total;
        if (level <= 16) {
            total = (int) (level * level + 6 * level);
        } else if (level <= 31) {
            total = (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            total = (int) (4.5 * level * level - 162.5 * level + 2220);
        }

        // Add progress within current level
        total += Math.round(progress * player.getXpNeededForNextLevel());
        return total;
    }
}
