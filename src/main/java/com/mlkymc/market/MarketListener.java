package com.mlkymc.market;

import com.mlkymc.economy.MilkyStar;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MarketListener {

    private final MarketManager marketManager;
    private final Set<UUID> pickupCooldown = new HashSet<>();

    public MarketListener(MarketManager marketManager) {
        this.marketManager = marketManager;
    }

    // ---- Lectern with Market Book -> open marketplace GUI ----

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        var level = event.getLevel();
        var pos = event.getPos();

        // Check if the block is a lectern
        if (!level.getBlockState(pos).is(Blocks.LECTERN)) return;

        // Check if the lectern has a Market Book
        if (level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
            ItemStack book = lectern.getBook();
            if (MilkyStar.isMarketBook(book)) {
                event.setCanceled(true);
                player.openMenu(new SimpleMenuProvider(
                        (containerId, playerInv, p) -> MarketMenu.marketplace(
                                containerId, playerInv, player, marketManager, 0),
                        Component.literal("Marketplace")
                ));
            }
        }
    }

    // ---- Stall villager interaction ----

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        // Check if it's a player stall
        String stallOwner = marketManager.getStallOwner(villager);
        if (stallOwner == null) return;

        event.setCanceled(true);

        // Owner sneaking = pick up stall
        if (stallOwner.equals(player.getStringUUID()) && player.isShiftKeyDown()) {
            // Check stall has no listings
            if (!marketManager.getStallListings(stallOwner).isEmpty()) {
                player.sendSystemMessage(Component.literal(
                        "Remove all listings first before picking up your stall.").withColor(0xFF5555));
                return;
            }
            // Check player has inventory space
            if (player.getInventory().getFreeSlot() == -1) {
                player.sendSystemMessage(Component.literal(
                        "You need at least one free inventory slot to pick up your stall.").withColor(0xFF5555));
                return;
            }
            // Pick up: remove stall, give deed back
            marketManager.removeStall(player);
            player.getInventory().add(MilkyStar.createStallDeed());
            player.sendSystemMessage(Component.literal(
                    "Stall picked up! Deed returned to your inventory.").withColor(0x55FF55));
            pickupCooldown.add(player.getUUID());
            return;
        }

        MarketManager.StallData stall = marketManager.getStall(stallOwner);
        String title = stall != null ? stall.stallName : "Player Stall";
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> MarketMenu.stall(
                        containerId, playerInv, player, marketManager, stallOwner, 0),
                Component.literal(title)
        ));
    }

    // ---- Stall Deed placement & pickup cooldown ----

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Check pickup cooldown to prevent re-placing a just-picked-up stall
        if (pickupCooldown.remove(player.getUUID())) {
            event.setCanceled(true);
            return;
        }

        // Market Catalog - read-only browsing of all listings
        if (MilkyStar.isMarketCatalog(player.getItemInHand(event.getHand()))) {
            event.setCanceled(true);
            if (event.getHand() != InteractionHand.MAIN_HAND) return;
            player.openMenu(new SimpleMenuProvider(
                    (containerId, playerInv, p) -> new CatalogMenu(containerId, playerInv, marketManager),
                    Component.literal("Market Catalog")
            ));
            return;
        }

        // Stall Deed - place a stall at player's location
        if (MilkyStar.isStallDeed(player.getItemInHand(event.getHand()))) {
            event.setCanceled(true);
            if (event.getHand() != InteractionHand.MAIN_HAND) return;

            String uuid = player.getStringUUID();

            if (marketManager.getStall(uuid) != null) {
                player.sendSystemMessage(Component.literal(
                        "You already have a stall! Pick it up first before placing a new one.").withColor(0xFF5555));
                return;
            }

            String stallName = player.getName().getString() + "'s Stall";
            if (marketManager.createStall(player, stallName)) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.sendSystemMessage(Component.literal(
                        "Stall placed! Other players can right-click it to browse your items.").withColor(0x55FF55));
            }
        }
    }

    // ---- Protect stall villagers from damage ----

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (marketManager.isStallVillager(villager)) {
            event.setNewDamage(0);
        }
    }
}
