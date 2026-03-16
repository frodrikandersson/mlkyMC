package com.mlkymc.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

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

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Prevent mlkymc custom items from being used as crafting ingredients
        var craftingInv = event.getInventory();
        boolean blocked = false;
        for (int i = 0; i < craftingInv.getContainerSize(); i++) {
            var item = craftingInv.getItem(i);
            if (MilkyStar.isMilkyStar(item) || MilkyStar.isJar(item)
                    || MilkyStar.isStallDeed(item)
                    || MilkyStar.isMarketCatalog(item) || MilkyStar.isMarketBook(item)) {
                blocked = true;
                break;
            }
        }

        if (blocked) {
            List<ItemStack> savedIngredients = new ArrayList<>();
            for (int i = 0; i < craftingInv.getContainerSize(); i++) {
                savedIngredients.add(craftingInv.getItem(i).copy());
            }

            event.getCrafting().setCount(0);

            player.level().getServer().execute(() -> {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                for (ItemStack ingredient : savedIngredients) {
                    if (!ingredient.isEmpty()) {
                        if (!player.getInventory().add(ingredient)) {
                            player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                                    player.level(), player.getX(), player.getY(), player.getZ(), ingredient));
                        }
                    }
                }
                player.containerMenu.broadcastChanges();
                player.inventoryMenu.broadcastChanges();
            });

            player.sendSystemMessage(Component.literal(
                    "Custom mlkymc items cannot be used in crafting recipes.").withColor(0xFF5555));
            return;
        }

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
