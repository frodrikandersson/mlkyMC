package com.mlkymc.market;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CatalogMenu extends AbstractContainerMenu {

    private static final int ROWS = 6;
    private static final int COLS = 9;
    private static final int CONTAINER_SLOTS = ROWS * COLS;
    private static final int ITEMS_PER_PAGE = 45;

    private final SimpleContainer container;
    private final MarketManager manager;
    private final Map<Integer, String> slotToCategory = new java.util.HashMap<>();
    private boolean showingMarketplace = true; // true = marketplace tab, false = stalls tab
    private String categoryFilter; // null = category view, non-null = detail view
    private int page;

    public CatalogMenu(int containerId, Inventory playerInv, MarketManager manager) {
        super(MenuType.GENERIC_9x6, containerId);
        this.container = new SimpleContainer(CONTAINER_SLOTS);
        this.manager = manager;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                this.addSlot(new Slot(container, col + row * COLS, 8 + col * 18, 18 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 103 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 161));
        }

        refresh();
    }

    private List<MarketListing> getListings() {
        return showingMarketplace ? manager.getMarketplaceListings() : manager.getAllStallListings();
    }

    private void refresh() {
        for (int i = 0; i < CONTAINER_SLOTS; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
        slotToCategory.clear();

        if (categoryFilter == null) {
            refreshCategoryView();
        } else {
            refreshDetailView();
        }
    }

    private void refreshCategoryView() {
        List<MarketListing> listings = getListings();

        // Group listings by item ID
        Map<String, List<MarketListing>> grouped = new java.util.LinkedHashMap<>();
        for (MarketListing listing : listings) {
            grouped.computeIfAbsent(listing.getItemId(), k -> new ArrayList<>()).add(listing);
        }

        List<Map.Entry<String, List<MarketListing>>> categories = new ArrayList<>(grouped.entrySet());

        int startIndex = page * ITEMS_PER_PAGE;
        int slot = 0;
        for (int i = startIndex; i < categories.size() && slot < ITEMS_PER_PAGE; i++, slot++) {
            var entry = categories.get(i);
            List<MarketListing> group = entry.getValue();

            if (group.size() == 1) {
                container.setItem(slot, createDisplay(group.get(0)));
            } else {
                container.setItem(slot, createCategoryDisplay(entry.getKey(), group));
                slotToCategory.put(slot, entry.getKey());
            }
        }

        refreshNavigation(categories.size());
    }

    private void refreshDetailView() {
        List<MarketListing> listings = getListings();

        List<MarketListing> filtered = new ArrayList<>();
        for (MarketListing listing : listings) {
            if (listing.getItemId().equals(categoryFilter)) {
                filtered.add(listing);
            }
        }

        if (filtered.isEmpty()) {
            categoryFilter = null;
            page = 0;
            refreshCategoryView();
            return;
        }

        int startIndex = page * ITEMS_PER_PAGE;
        int slot = 0;
        for (int i = startIndex; i < filtered.size() && slot < ITEMS_PER_PAGE; i++, slot++) {
            container.setItem(slot, createDisplay(filtered.get(i)));
        }

        refreshNavigation(filtered.size());

        // Back button
        ItemStack backBtn = new ItemStack(Items.SPECTRAL_ARROW);
        backBtn.set(DataComponents.CUSTOM_NAME,
                Component.literal("Back to Categories").withColor(0xFF5555));
        container.setItem(46, backBtn);
    }

    private void refreshNavigation(int totalItems) {
        // Navigation row (slots 45-53)
        for (int i = 45; i < CONTAINER_SLOTS; i++) {
            container.setItem(i, pane(Items.BLACK_STAINED_GLASS_PANE, ""));
        }

        if (page > 0) {
            container.setItem(45, pane(Items.ARROW, "Previous Page"));
        }

        // Marketplace tab
        ItemStack mpTab = new ItemStack(showingMarketplace ? Items.WRITTEN_BOOK : Items.BOOK);
        mpTab.set(DataComponents.CUSTOM_NAME,
                Component.literal("Marketplace").withColor(showingMarketplace ? 0xFFAA00 : 0xAAAAAA));
        if (showingMarketplace) {
            mpTab.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Currently viewing").withColor(0x55FF55)
            )));
        }
        container.setItem(47, mpTab);

        // Page info
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE));
        String label = categoryFilter != null
                ? categoryFilter.replace("minecraft:", "") + " - Page " + (page + 1) + "/" + totalPages
                : "Page " + (page + 1) + "/" + totalPages + " (" + totalItems + " items)";
        container.setItem(49, pane(Items.PAPER, label));

        // Stalls tab
        ItemStack stallTab = new ItemStack(showingMarketplace ? Items.OAK_SIGN : Items.OAK_HANGING_SIGN);
        stallTab.set(DataComponents.CUSTOM_NAME,
                Component.literal("Player Stalls").withColor(showingMarketplace ? 0xAAAAAA : 0xFFAA00));
        if (!showingMarketplace) {
            stallTab.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Currently viewing").withColor(0x55FF55)
            )));
        }
        container.setItem(51, stallTab);

        if ((page + 1) * ITEMS_PER_PAGE < totalItems) {
            container.setItem(53, pane(Items.ARROW, "Next Page"));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= CONTAINER_SLOTS) return;

        // Previous page
        if (slotId == 45 && page > 0) {
            page--;
            refresh();
            broadcastChanges();
            return;
        }

        // Back button (detail view only)
        if (slotId == 46 && categoryFilter != null) {
            categoryFilter = null;
            page = 0;
            refresh();
            broadcastChanges();
            return;
        }

        // Marketplace tab
        if (slotId == 47 && !showingMarketplace) {
            showingMarketplace = true;
            categoryFilter = null;
            page = 0;
            refresh();
            broadcastChanges();
            return;
        }

        // Stalls tab
        if (slotId == 51 && showingMarketplace) {
            showingMarketplace = false;
            categoryFilter = null;
            page = 0;
            refresh();
            broadcastChanges();
            return;
        }

        // Next page
        if (slotId == 53) {
            page++;
            refresh();
            broadcastChanges();
            return;
        }

        // Click category: drill into detail view
        String category = slotToCategory.get(slotId);
        if (category != null) {
            categoryFilter = category;
            page = 0;
            refresh();
            broadcastChanges();
            return;
        }

        // All other clicks: show "view only" message
        if (slotId < ITEMS_PER_PAGE && player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal(
                    "This is a catalog - visit the marketplace or stall to buy.").withColor(0xFFFF55));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private ItemStack createCategoryDisplay(String itemId, List<MarketListing> group) {
        Identifier id = Identifier.parse(itemId);
        Item item = BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(null);
        if (item == null) return new ItemStack(Items.BARRIER);

        ItemStack stack = new ItemStack(item);
        String itemName = manager.getItemDisplayName(itemId);

        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(itemName + " (" + group.size() + " listings)")
                        .withColor(0x55FFFF));

        List<Component> lore = new ArrayList<>();

        int minPrice = Integer.MAX_VALUE;
        int maxPrice = Integer.MIN_VALUE;
        int totalQty = 0;
        for (MarketListing listing : group) {
            minPrice = Math.min(minPrice, listing.getPrice());
            maxPrice = Math.max(maxPrice, listing.getPrice());
            totalQty += listing.getAmount();
        }
        if (minPrice == maxPrice) {
            lore.add(Component.literal("Price: " + minPrice + " Milky Stars").withColor(0x55FF55));
        } else {
            lore.add(Component.literal("Price: " + minPrice + " - " + maxPrice + " Milky Stars").withColor(0x55FF55));
        }
        lore.add(Component.literal("Total available: " + totalQty).withColor(0xAAAAAA));
        lore.add(Component.literal(""));
        lore.add(Component.literal("Click to browse listings").withColor(0xFFFF55));

        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private ItemStack createDisplay(MarketListing listing) {
        Identifier itemId = Identifier.parse(listing.getItemId());
        Item item = BuiltInRegistries.ITEM.get(itemId).map(Holder.Reference::value).orElse(null);
        if (item == null) return new ItemStack(Items.BARRIER);

        ItemStack stack = new ItemStack(item, listing.getAmount());

        String displayName = manager.getItemDisplayName(listing);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(listing.getAmount() + "x " +
                        displayName +
                        " - " + listing.getPrice() + " Milky Stars")
                        .withColor(0xFFD700));

        // Build lore based on whether this is marketplace or stall listing
        List<Component> lore = new ArrayList<>();
        if (showingMarketplace) {
            lore.add(Component.literal("Seller: " + listing.getSellerName()).withColor(0xAAAAAA));
            lore.add(Component.literal("Price: " + listing.getPrice() + " Milky Stars").withColor(0x55FF55));
        } else {
            String stallName = manager.getStallNameForSeller(listing.getSellerUuid());
            String stallLine = stallName != null ? stallName : listing.getSellerName() + "'s Stall";
            lore.add(Component.literal("Stall: " + stallLine).withColor(0x55FF55));
            lore.add(Component.literal("Vendor: " + listing.getSellerName()).withColor(0xAAAAAA));
            lore.add(Component.literal("Price: " + listing.getPrice() + " Milky Stars").withColor(0x55FF55));
        }

        List<String> contents = manager.getContainerContents(listing);
        if (!contents.isEmpty()) {
            lore.add(Component.literal(""));
            lore.add(Component.literal("Contains:").withColor(0x55FFFF));
            for (String line : contents) {
                lore.add(Component.literal("  " + line).withColor(0xAAAAAA));
            }
        }

        lore.add(Component.literal(""));
        if (showingMarketplace) {
            lore.add(Component.literal("Visit the marketplace lectern to purchase").withColor(0xFFFF55));
        } else {
            lore.add(Component.literal("Visit this player's stall to purchase").withColor(0xFFFF55));
        }

        stack.set(DataComponents.LORE, new ItemLore(lore));

        return stack;
    }

    private ItemStack pane(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        if (!name.isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withColor(0xFFFFFF));
        }
        return stack;
    }
}
