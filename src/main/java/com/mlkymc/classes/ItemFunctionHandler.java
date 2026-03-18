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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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

        // Grappling Hook: handled by GrapplingHookItem.use() — do NOT intercept here

        // Blessing Scroll: apply Unbreaking I to offhand gear
        if (held.is(ModItems.BLESSING_SCROLL.get())) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.isDamageableItem()) {
                applyEnchantment(player, offhand, Enchantments.UNBREAKING, 1);
                player.sendSystemMessage(Component.literal("Blessing applied! Unbreaking I added to " + offhand.getHoverName().getString()).withColor(0x55FFFF));
                held.shrink(1);
                player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            } else {
                player.sendSystemMessage(Component.literal("Hold damageable gear in offhand!").withColor(0xFF5555));
            }
            event.setCanceled(true);
            return;
        }

        // Whetstone: apply Sharpness I to offhand weapon
        if (held.is(ModItems.WHETSTONE.get())) {
            ItemStack offhand = player.getOffhandItem();
            if (!offhand.isEmpty() && offhand.isDamageableItem()) {
                applyEnchantment(player, offhand, Enchantments.SHARPNESS, 1);
                player.sendSystemMessage(Component.literal("Sharpened! Sharpness I added to " + offhand.getHoverName().getString()).withColor(0xFFAA00));
                held.shrink(1);
                player.level().playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
            } else {
                player.sendSystemMessage(Component.literal("Hold a weapon in offhand!").withColor(0xFF5555));
            }
            event.setCanceled(true);
            return;
        }

        // Holy Water: throw like splash potion
        if (held.is(ModItems.HOLY_WATER.get())) {
            if (player.level() instanceof ServerLevel sl) {
                // Throw a snowball entity that triggers holy water on impact
                net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball projectile =
                        new net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball(sl, player, held.copy());
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.5f, 1.0f);
                projectile.addTag("mlkymc_holy_water");
                sl.addFreshEntity(projectile);

                held.shrink(1);
                sl.playSound(null, player.blockPosition(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.5f, 0.4f);
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

    private void applyEnchantment(ServerPlayer player, ItemStack target, ResourceKey<Enchantment> enchantKey, int levelToAdd) {
        var registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var holder = registry.get(enchantKey);
        if (holder.isEmpty()) return;

        Holder<Enchantment> enchant = holder.get();
        int currentLevel = target.getEnchantmentLevel(enchant);
        int newLevel = Math.min(currentLevel + levelToAdd, enchant.value().getMaxLevel());
        target.enchant(enchant, newLevel);
    }

    // =========================================================================
    // HOLY WATER IMPACT — triggered when the thrown snowball with tag lands
    // =========================================================================

    @SubscribeEvent
    public void onProjectileImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!event.getEntity().getTags().contains("mlkymc_holy_water")) return;
        if (!(event.getEntity().level() instanceof ServerLevel sl)) return;

        var impactPos = event.getEntity().position();
        double radius = 8.0;

        // Damage undead mobs only
        for (var mob : sl.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                event.getEntity().getBoundingBox().inflate(radius))) {
            if (mob.isInvertedHealAndHarm()) {
                // Undead mobs (zombies, skeletons, wither, phantoms, etc.)
                mob.hurt(sl.damageSources().magic(), 40.0f);
            }
        }

        // Heal + invulnerability for all nearby players
        for (var nearby : sl.getEntitiesOfClass(ServerPlayer.class,
                event.getEntity().getBoundingBox().inflate(radius))) {
            nearby.heal(nearby.getMaxHealth());
            nearby.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.ABSORPTION, 400, 4, false, true, true));
            nearby.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Holy Water: Full heal + shield!").withColor(0xFFFFAA));
        }

        // Particles + sound at impact
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                impactPos.x, impactPos.y, impactPos.z, 30, 2.0, 1.0, 2.0, 0.1);
        sl.playSound(null, net.minecraft.core.BlockPos.containing(impactPos),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.5f);

        // Remove the projectile
        event.getEntity().discard();
    }
}
