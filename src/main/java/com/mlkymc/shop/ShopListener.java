package com.mlkymc.shop;

import com.mlkymc.economy.MilkyStar;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.*;

public class ShopListener {

    private final ShopManager shopManager;
    private final Map<UUID, Map<Integer, Integer>> playerSlotMap = new HashMap<>();

    public ShopListener(ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Shopkeeper shop = shopManager.getShopkeeperByVillager(villager);
        if (shop == null) return;

        event.setCanceled(true);
        openShopGui(player, shop);
    }

    private void openShopGui(ServerPlayer player, Shopkeeper shop) {
        List<Shopkeeper.TradeData> trades = shop.getTrades();
        if (trades.isEmpty()) {
            player.sendSystemMessage(Component.literal("This shop has no trades.").withColor(0xFFFF55));
            return;
        }

        // Separate trades into buy and sell
        List<Integer> buyIndices = new ArrayList<>();
        List<Integer> sellIndices = new ArrayList<>();
        for (int i = 0; i < trades.size(); i++) {
            if (trades.get(i).selling) {
                sellIndices.add(i);
            } else {
                buyIndices.add(i);
            }
        }

        int totalSlots = calculateSlots(buyIndices.size(), sellIndices.size());
        int rows = totalSlots / 9;

        Map<Integer, Integer> slotMap = new HashMap<>();
        playerSlotMap.put(player.getUUID(), slotMap);

        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> ChestMenu.threeRows(containerId, playerInv),
                Component.literal(shop.getName())
        ));

        // Build GUI contents after menu opens
        player.level().getServer().execute(() -> {
            if (player.containerMenu == player.inventoryMenu) return;

            int slot = 0;

            // Buy section header
            if (!buyIndices.isEmpty()) {
                for (int col = 0; col < 9; col++) {
                    setSlot(player, slot++, createPane(Items.LIME_STAINED_GLASS_PANE, "--- BUY ---"));
                }
                for (int idx : buyIndices) {
                    Shopkeeper.TradeData trade = trades.get(idx);
                    slotMap.put(slot, idx);
                    setSlot(player, slot++, createTradeItem(trade, true));
                }
                // Fill remaining in row
                while (slot % 9 != 0 && slot < totalSlots) {
                    setSlot(player, slot++, createPane(Items.GRAY_STAINED_GLASS_PANE, ""));
                }
            }

            // Sell section header
            if (!sellIndices.isEmpty()) {
                for (int col = 0; col < 9 && slot < totalSlots; col++) {
                    setSlot(player, slot++, createPane(Items.ORANGE_STAINED_GLASS_PANE, "--- SELL ---"));
                }
                for (int idx : sellIndices) {
                    if (slot >= totalSlots) break;
                    Shopkeeper.TradeData trade = trades.get(idx);
                    slotMap.put(slot, idx);
                    setSlot(player, slot++, createTradeItem(trade, false));
                }
                while (slot % 9 != 0 && slot < totalSlots) {
                    setSlot(player, slot++, createPane(Items.GRAY_STAINED_GLASS_PANE, ""));
                }
            }

            // Fill remaining
            while (slot < totalSlots) {
                setSlot(player, slot++, createPane(Items.GRAY_STAINED_GLASS_PANE, ""));
            }
        });
    }

    private int calculateSlots(int buyCount, int sellCount) {
        int rows = 0;
        if (buyCount > 0) rows += 1 + (int) Math.ceil(buyCount / 9.0);
        if (sellCount > 0) rows += 1 + (int) Math.ceil(sellCount / 9.0);
        return Math.min(Math.max(rows, 3), 6) * 9;
    }

    private void setSlot(ServerPlayer player, int slot, ItemStack stack) {
        if (slot < player.containerMenu.slots.size()) {
            player.containerMenu.getSlot(slot).set(stack);
        }
    }

    private ItemStack createPane(Item pane, String name) {
        ItemStack stack = new ItemStack(pane);
        if (!name.isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal(name).withColor(0xFFFFFF));
        }
        return stack;
    }

    private ItemStack createTradeItem(Shopkeeper.TradeData trade, boolean buying) {
        Identifier loc = Identifier.parse(trade.item);
        Item item = BuiltInRegistries.ITEM.get(loc).map(net.minecraft.core.Holder.Reference::value).orElse(null);
        if (item == null) return new ItemStack(Items.BARRIER);

        ItemStack stack = new ItemStack(item, trade.amount);
        String action = buying ? "Click to BUY" : "Click to SELL";
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(action + " x" + trade.amount + " for " + trade.price + " Milky Stars")
                        .withColor(buying ? 0x55FF55 : 0xFFAA00));
        return stack;
    }
}
