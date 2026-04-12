package com.mlkymc.economy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class WalletListener {

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        var hand = event.getHand();
        var heldItem = player.getItemInHand(hand);
        if (!MilkyStar.isJar(heldItem)) return;

        event.setCanceled(true);
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        // Find the exact slot of the held jar
        int foundSlot = -1;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == heldItem) {
                foundSlot = i;
                break;
            }
        }
        if (foundSlot == -1) return;
        final int jarSlot = foundSlot;
        String owner = MilkyStar.getJarOwner(heldItem);
        String title = owner.isEmpty() ? "Milky Star Jar" : "Milky Star Jar (" + owner + ")";

        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> new WalletMenu(containerId, playerInv, player, jarSlot),
                Component.literal(title)
        ));
    }

    /**
     * Auto-deposit picked up Milky Stars into a jar.
     * Search order: hotbar -> inventory -> shulker boxes/pouches.
     * If no jar found, the stars go to inventory normally.
     */
    @SubscribeEvent
    public void onMilkyStarPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        var itemEntity = event.getItemEntity();
        ItemStack pickupStack = itemEntity.getItem();
        if (!MilkyStar.isMilkyStar(pickupStack)) return;

        // Respect vanilla pickup delay so players can intentionally drop stars
        if (itemEntity.hasPickUpDelay()) return;

        // Cauldron transmutation guard: if the player is standing in a water cauldron
        // (and NOT sneaking to retrieve), skip the jar siphon entirely — the Milky Stars
        // must remain in the cauldron so TransmutationHandler can match recipes that
        // require them. Sneaking bypasses this so players can still crouch-retrieve
        // stars they mis-dropped into a cauldron.
        if (!player.isShiftKeyDown()) {
            var playerBlock = player.level().getBlockState(player.blockPosition());
            if (playerBlock.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) {
                var iePos = itemEntity.blockPosition();
                var playerPos = player.blockPosition();
                if (iePos.equals(playerPos) || iePos.distSqr(playerPos) <= 2) {
                    event.setCanPickup(net.minecraft.util.TriState.FALSE);
                    return;
                }
            }
        }

        int amount = pickupStack.getCount();
        var inv = player.getInventory();

        // Search hotbar first (slots 0-8)
        int jarSlot = findJarInRange(inv, 0, 9);
        // Then rest of inventory (slots 9-35)
        if (jarSlot == -1) jarSlot = findJarInRange(inv, 9, 36);

        if (jarSlot != -1) {
            int balance = MilkyStar.getJarBalance(inv.getItem(jarSlot));
            MilkyStar.setJarBalanceAndUpdateVisual(player, jarSlot, balance + amount);
            pickupStack.setCount(0);
            event.getItemEntity().discard();
            event.setCanPickup(net.minecraft.util.TriState.FALSE);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.2f,
                    ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7f + 1.0f) * 2.0f);
            player.sendSystemMessage(Component.literal("+" + amount + " Milky Stars deposited into jar")
                    .withColor(0xFFD700), true); // action bar
            return;
        }

        // Search shulker boxes and pouches for jars
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack container = inv.getItem(i);
            if (container.isEmpty()) continue;
            if (!isContainer(container)) continue;

            var contents = container.get(DataComponents.CONTAINER);
            if (contents == null) continue;

            var items = new java.util.ArrayList<ItemStack>();
            for (ItemStack inner : contents.nonEmptyItems()) items.add(inner.copy());

            for (int j = 0; j < items.size(); j++) {
                if (MilkyStar.isJar(items.get(j))) {
                    int balance = MilkyStar.getJarBalance(items.get(j));
                    MilkyStar.setJarBalance(items.get(j), balance + amount);
                    container.set(DataComponents.CONTAINER,
                            net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
                    pickupStack.setCount(0);
                    event.getItemEntity().discard();
                    event.setCanPickup(net.minecraft.util.TriState.FALSE);
                    player.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.2f,
                            ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7f + 1.0f) * 2.0f);
                    player.sendSystemMessage(Component.literal("+" + amount + " Milky Stars deposited into jar")
                            .withColor(0xFFD700), true);
                    return;
                }
            }
        }

        // No jar found — let vanilla pickup handle it normally
    }

    private static int findJarInRange(net.minecraft.world.entity.player.Inventory inv, int from, int to) {
        for (int i = from; i < to; i++) {
            if (MilkyStar.isJar(inv.getItem(i))) return i;
        }
        return -1;
    }

    private static boolean isContainer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getItem().toString();
        return name.contains("shulker_box") || name.contains("ender_pouch") || name.contains("pouch");
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // mlkymc items (Milky Stars, etc.) are valid crafting ingredients for mlkymc recipes

        // Stamp the crafter's name onto the jar
        if (MilkyStar.isJar(event.getCrafting())) {
            String playerName = player.getName().getString();
            net.minecraft.world.item.component.CustomData customData =
                    event.getCrafting().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.EMPTY);
            net.minecraft.nbt.CompoundTag tag = customData.copyTag();
            tag.putString("mlkymc_wallet_owner", playerName);
            event.getCrafting().set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
        }
    }
}
