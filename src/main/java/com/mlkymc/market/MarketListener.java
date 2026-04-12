package com.mlkymc.market;

import com.mlkymc.economy.MilkyStar;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class MarketListener {

    private final MarketManager marketManager;

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

    // ---- Market Catalog (right-click item) ----

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Market Catalog - read-only browsing of all listings
        if (MilkyStar.isMarketCatalog(player.getItemInHand(event.getHand()))) {
            event.setCanceled(true);
            if (event.getHand() != InteractionHand.MAIN_HAND) return;
            player.openMenu(new SimpleMenuProvider(
                    (containerId, playerInv, p) -> new CatalogMenu(containerId, playerInv, marketManager),
                    Component.literal("Market Catalog")
            ));
        }
    }

    // ---- Stall block interaction is now handled by ShopBlock.useWithoutItem() ----
    // ---- Villager damage protection removed — stalls are blocks now ----

    public MarketManager getMarketManager() {
        return marketManager;
    }
}
