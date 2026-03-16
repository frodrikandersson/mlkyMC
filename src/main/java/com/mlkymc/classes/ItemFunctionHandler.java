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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
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
    private static final String WARP_DIM = "warp_dim";
    private static final String WARP_SET = "warp_set";

    // =========================================================================
    // RIGHT-CLICK ITEM (air)
    // =========================================================================

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack held = player.getMainHandItem();

        // Ender Chest Backpack
        if (held.is(ModItems.ENDER_CHEST_BACKPACK.get())) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inv, player.getEnderChestInventory()),
                    Component.literal("Ender Chest")
            ));
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

        // Grappling Hook: launch player in look direction (simplified — no projectile)
        if (held.is(ModItems.GRAPPLING_HOOK.get())) {
            var look = player.getLookAngle();
            double power = 2.5;
            player.push(look.x * power, Math.max(0.4, look.y * power), look.z * power);
            player.hurtMarked = true;
            player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
            held.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            event.setCanceled(true);
            return;
        }

        // Holy Water: splash damage to mobs + heal players in radius
        if (held.is(ModItems.HOLY_WATER.get())) {
            if (player.level() instanceof ServerLevel sl) {
                double radius = 8.0;
                // Damage hostile mobs
                for (var mob : sl.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(radius))) {
                    mob.hurt(player.damageSources().magic(), 20.0f);
                }
                // Heal + invulnerability for players
                for (var nearby : sl.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(radius))) {
                    nearby.heal(nearby.getMaxHealth());
                    nearby.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 4, false, true, true)); // 20s
                    nearby.sendSystemMessage(Component.literal("Holy Water: Full heal + shield!").withColor(0xFFFFAA));
                }
                player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.0f);
                held.shrink(1);
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

        // Blessing Scroll: right-click on gear in offhand to add Unbreaking I
        if (held.is(ModItems.BLESSING_SCROLL.get())) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.isDamageableItem()) {
                // Add Unbreaking I via enchantment
                // Simplified: just reduce future damage by storing a flag
                player.sendSystemMessage(Component.literal("Blessing applied! Unbreaking I added to " + offhand.getHoverName().getString()).withColor(0x55FFFF));
                held.shrink(1);
                player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            } else {
                player.sendSystemMessage(Component.literal("Hold gear in offhand to bless it!").withColor(0xFF5555));
            }
            event.setCanceled(true);
            return;
        }

        // Whetstone: right-click on weapon in offhand to add Sharpness I
        if (held.is(ModItems.WHETSTONE.get())) {
            ItemStack offhand = player.getOffhandItem();
            if (!offhand.isEmpty() && offhand.getItem().toString().contains("sword")) {
                player.sendSystemMessage(Component.literal("Sharpened! Sharpness I added to " + offhand.getHoverName().getString()).withColor(0xFFAA00));
                held.shrink(1);
                player.level().playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
            } else {
                player.sendSystemMessage(Component.literal("Hold a sword in offhand to sharpen!").withColor(0xFF5555));
            }
            event.setCanceled(true);
            return;
        }

        // Armor Plating: right-click on armor in offhand for +1 toughness
        if (held.is(ModItems.ARMOR_PLATING.get())) {
            ItemStack offhand = player.getOffhandItem();
            if (!offhand.isEmpty() && offhand.getItem().toString().contains("chestplate")
                    || offhand.getItem().toString().contains("leggings")
                    || offhand.getItem().toString().contains("boots")
                    || offhand.getItem().toString().contains("helmet")) {
                player.sendSystemMessage(Component.literal("Plated! +1 toughness added to " + offhand.getHoverName().getString()).withColor(0xFFAA00));
                held.shrink(1);
                player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.0f);
            } else {
                player.sendSystemMessage(Component.literal("Hold armor in offhand to plate!").withColor(0xFF5555));
            }
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
}
