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
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketMenu extends AbstractContainerMenu {

    private static final int ROWS = 6;
    private static final int COLS = 9;
    private static final int CONTAINER_SLOTS = ROWS * COLS; // 54
    private static final int ITEMS_PER_PAGE = 45; // rows 1-5, row 6 = navigation

    private final SimpleContainer container;
    private final Map<Integer, MarketListing> slotToListing = new HashMap<>();
    private final Map<Integer, String> slotToCategory = new HashMap<>(); // slot -> itemId for category drill-in
    private final MarketManager manager;
    private final ServerPlayer viewer;
    private final boolean isMarketplace; // true = marketplace, false = player stall
    private final boolean fromComputer;  // true = opened via MrCrayfish Computer
    private final String stallOwnerUuid;  // only used when isMarketplace == false
    private String categoryFilter; // null = category view, non-null = detail view for this itemId
    private int page;

    /** Open the central marketplace (lectern Market Book). */
    public static MarketMenu marketplace(int containerId, Inventory playerInv,
                                         ServerPlayer viewer, MarketManager manager, int page) {
        return new MarketMenu(containerId, playerInv, viewer, manager, true, false, null, page);
    }

    /** Open the marketplace via MrCrayfish Computer — requires mailbox for purchases. */
    public static MarketMenu computerMarketplace(int containerId, Inventory playerInv,
                                                  ServerPlayer viewer, MarketManager manager, int page) {
        return new MarketMenu(containerId, playerInv, viewer, manager, true, true, null, page);
    }

    /** Open a player stall. */
    public static MarketMenu stall(int containerId, Inventory playerInv,
                                   ServerPlayer viewer, MarketManager manager,
                                   String stallOwnerUuid, int page) {
        return new MarketMenu(containerId, playerInv, viewer, manager, false, false, stallOwnerUuid, page);
    }

    private MarketMenu(int containerId, Inventory playerInv, ServerPlayer viewer,
                       MarketManager manager, boolean isMarketplace, boolean fromComputer, String stallOwnerUuid, int page) {
        super(MenuType.GENERIC_9x6, containerId);
        this.container = new SimpleContainer(CONTAINER_SLOTS);
        this.manager = manager;
        this.viewer = viewer;
        this.isMarketplace = isMarketplace;
        this.fromComputer = fromComputer;
        this.stallOwnerUuid = stallOwnerUuid;
        this.page = page;

        // Add container slots (6 rows)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                this.addSlot(new Slot(container, col + row * COLS, 8 + col * 18, 18 + row * 18));
            }
        }

        // Add player inventory slots (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 103 + row * 18));
            }
        }

        // Add player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 161));
        }

        refreshListings();
    }

    private List<MarketListing> getListings() {
        if (isMarketplace) {
            return manager.getMarketplaceListings();
        } else {
            return manager.getStallListings(stallOwnerUuid);
        }
    }

    private void refreshListings() {
        // Clear
        for (int i = 0; i < CONTAINER_SLOTS; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
        slotToListing.clear();
        slotToCategory.clear();

        if (categoryFilter == null) {
            refreshCategoryView();
        } else {
            refreshDetailView();
        }
    }

    private void refreshCategoryView() {
        List<MarketListing> listings = getListings();

        // Group listings by item ID, preserving insertion order
        Map<String, List<MarketListing>> grouped = new java.util.LinkedHashMap<>();
        for (MarketListing listing : listings) {
            grouped.computeIfAbsent(listing.getItemId(), k -> new ArrayList<>()).add(listing);
        }

        // Build a flat list of category entries
        List<Map.Entry<String, List<MarketListing>>> categories = new ArrayList<>(grouped.entrySet());

        int startIndex = page * ITEMS_PER_PAGE;
        int slot = 0;
        for (int i = startIndex; i < categories.size() && slot < ITEMS_PER_PAGE; i++, slot++) {
            var entry = categories.get(i);
            List<MarketListing> group = entry.getValue();

            if (group.size() == 1) {
                // Single listing - show directly (purchasable/cancellable)
                container.setItem(slot, createListingDisplay(group.get(0)));
                slotToListing.put(slot, group.get(0));
            } else {
                // Multiple listings - show category icon
                container.setItem(slot, createCategoryDisplay(entry.getKey(), group));
                slotToCategory.put(slot, entry.getKey());
            }
        }

        // Navigation row
        refreshNavigation(categories.size());
    }

    private void refreshDetailView() {
        List<MarketListing> listings = getListings();

        // Filter to only listings matching the category
        List<MarketListing> filtered = new ArrayList<>();
        for (MarketListing listing : listings) {
            if (listing.getItemId().equals(categoryFilter)) {
                filtered.add(listing);
            }
        }

        // If category is now empty, go back to category view
        if (filtered.isEmpty()) {
            categoryFilter = null;
            page = 0;
            refreshCategoryView();
            return;
        }

        int startIndex = page * ITEMS_PER_PAGE;
        int slot = 0;
        for (int i = startIndex; i < filtered.size() && slot < ITEMS_PER_PAGE; i++, slot++) {
            MarketListing listing = filtered.get(i);
            container.setItem(slot, createListingDisplay(listing));
            slotToListing.put(slot, listing);
        }

        // Navigation row
        refreshNavigation(filtered.size());

        // Back button (slot 46)
        ItemStack backBtn = new ItemStack(Items.SPECTRAL_ARROW);
        backBtn.set(DataComponents.CUSTOM_NAME,
                Component.literal("Back to Categories").withColor(0xFF5555));
        container.setItem(46, backBtn);
    }

    private void refreshNavigation(int totalItems) {
        // Navigation row (slots 45-53)
        for (int i = 45; i < CONTAINER_SLOTS; i++) {
            container.setItem(i, createPane(Items.BLACK_STAINED_GLASS_PANE, ""));
        }

        // Previous page
        if (page > 0) {
            container.setItem(45, createPane(Items.ARROW, "Previous Page"));
        }

        // Info
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) ITEMS_PER_PAGE));
        String label = categoryFilter != null
                ? categoryFilter.replace("minecraft:", "") + " - Page " + (page + 1) + "/" + totalPages
                : "Page " + (page + 1) + "/" + totalPages + " (" + totalItems + " items)";
        container.setItem(49, createPane(Items.PAPER, label));

        // Next page
        if ((page + 1) * ITEMS_PER_PAGE < totalItems) {
            container.setItem(53, createPane(Items.ARROW, "Next Page"));
        }

        // Sell button - show for marketplace users or stall owners
        boolean canSell = isMarketplace ||
                (stallOwnerUuid != null && stallOwnerUuid.equals(viewer.getStringUUID()));
        if (canSell) {
            ItemStack sellBtn = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            sellBtn.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Sell Item").withColor(0x55FF55));
            var sellLore = new java.util.ArrayList<Component>();
            sellLore.add(Component.literal("Click to list an item for sale").withColor(0xAAAAAA));
            if (fromComputer) {
                sellLore.add(Component.literal("Listing fee: 1 Milky Star").withColor(0xFFAA00));
            }
            sellBtn.set(DataComponents.LORE, new ItemLore(sellLore));
            container.setItem(47, sellBtn);
        }

        // Collect earnings button
        int pending = manager.getPendingEarnings(viewer.getStringUUID());
        if (pending > 0) {
            ItemStack collectBtn = new ItemStack(Items.GOLD_INGOT);
            collectBtn.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Collect Earnings").withColor(0xFFD700));
            collectBtn.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal(pending + " Milky Stars available!").withColor(0x55FF55),
                    Component.literal("Click to collect").withColor(0xFFFF55)
            )));
            container.setItem(51, collectBtn);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Block all normal container interactions
        if (slotId < 0 || slotId >= CONTAINER_SLOTS) return;

        // Navigation: previous page
        if (slotId == 45 && page > 0) {
            page--;
            refreshListings();
            broadcastChanges();
            return;
        }

        // Back button (detail view only)
        if (slotId == 46 && categoryFilter != null) {
            categoryFilter = null;
            page = 0;
            refreshListings();
            broadcastChanges();
            return;
        }

        // Sell button
        if (slotId == 47 && player instanceof ServerPlayer serverPlayer) {
            boolean canSell = isMarketplace ||
                    (stallOwnerUuid != null && stallOwnerUuid.equals(serverPlayer.getStringUUID()));
            if (canSell) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (cid, inv, p) -> new SellMenu(cid, inv, serverPlayer, manager, isMarketplace, fromComputer),
                        Component.literal(isMarketplace ? "Sell on Marketplace" : "Sell in Stall")
                ));
                return;
            }
        }

        // Collect earnings
        if (slotId == 51 && player instanceof ServerPlayer serverPlayer) {
            int collected = manager.collectEarnings(serverPlayer);
            if (collected > 0) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "Collected " + collected + " Milky Stars!").withColor(0x55FF55));
                refreshListings();
                broadcastChanges();
            }
            return;
        }

        // Navigation: next page
        if (slotId == 53) {
            page++;
            refreshListings();
            broadcastChanges();
            return;
        }

        // Click category: drill into detail view
        String category = slotToCategory.get(slotId);
        if (category != null) {
            categoryFilter = category;
            page = 0;
            refreshListings();
            broadcastChanges();
            return;
        }

        // Click listing: cancel if own, purchase if not
        MarketListing listing = slotToListing.get(slotId);
        if (listing != null && player instanceof ServerPlayer serverPlayer) {
            boolean isOwn = listing.getSellerUuid().equals(serverPlayer.getStringUUID());

            if (isOwn) {
                // Cancel own listing
                boolean cancelled;
                if (isMarketplace) {
                    cancelled = manager.cancelMarketplaceListing(serverPlayer, listing.getId());
                } else {
                    cancelled = manager.cancelStallListing(serverPlayer, listing.getId());
                }
                if (cancelled) {
                    serverPlayer.sendSystemMessage(Component.literal(
                            "Listing cancelled. " + listing.getAmount() + "x " +
                            manager.getItemDisplayName(listing) + " returned.")
                            .withColor(0x55FF55));
                    refreshListings();
                    broadcastChanges();
                }
            } else {
                // Purchase — Computer-specific gates
                if (fromComputer) {
                    // Require a named mailbox for delivery
                    if (com.mlkymc.compat.FurnitureCompat.isAvailable()
                            && !com.mlkymc.compat.FurnitureCompat.hasNamedMailbox(
                            serverPlayer.level().getServer(), serverPlayer)) {
                        serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "You need a Mailbox with your name to buy via Computer! " +
                                "Place a Mailbox and rename it to your player name.")
                                .withColor(0xFF5555));
                        return;
                    }
                    // Transaction fee: 1 Milky Star per Computer purchase
                    if (com.mlkymc.economy.MilkyStar.count(serverPlayer) < 1) {
                        serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "Computer transaction fee: 1 Milky Star (you don't have enough!)")
                                .withColor(0xFF5555));
                        return;
                    }
                    com.mlkymc.economy.MilkyStar.remove(serverPlayer, 1);
                    serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Computer transaction fee: -1 Milky Star").withColor(0xFFAA00));
                }
                boolean success;
                if (isMarketplace) {
                    success = manager.purchaseMarketplaceListing(serverPlayer, listing.getId());
                } else {
                    success = manager.purchaseStallListing(serverPlayer, stallOwnerUuid, listing.getId());
                }
                if (success) {
                    refreshListings();
                    broadcastChanges();
                }
            }
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

        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("[LISTING] ").withColor(0x55FFFF)
                        .append(Component.literal(itemName + " (" + group.size() + " listings)")
                                .withColor(0x55FFFF)));

        List<Component> lore = new ArrayList<>();

        // Price range
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

    private ItemStack createListingDisplay(MarketListing listing) {
        // Restore full item from stored NBT (preserves damage, enchantments, custom data)
        ItemStack stack = manager.restoreItemStack(listing);
        if (stack == null || stack.isEmpty()) {
            Identifier itemId = Identifier.parse(listing.getItemId());
            Item item = BuiltInRegistries.ITEM.get(itemId).map(Holder.Reference::value).orElse(null);
            if (item == null) return new ItemStack(Items.BARRIER);
            stack = new ItemStack(item, listing.getAmount());
        }
        boolean isOwn = listing.getSellerUuid().equals(viewer.getStringUUID());
        String displayName = manager.getItemDisplayName(listing);

        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(listing.getAmount() + "x " +
                                displayName +
                                " - " + listing.getPrice() + " Stars")
                                .withColor(isOwn ? 0xFF5555 : 0xFFD700));

        // Show durability if item is damaged
        List<Component> lore = new ArrayList<>();
        if (stack.isDamageableItem() && stack.getDamageValue() > 0) {
            int maxDur = stack.getMaxDamage();
            int remaining = maxDur - stack.getDamageValue();
            int percent = (int) ((remaining / (float) maxDur) * 100);
            int color = percent > 50 ? 0x55FF55 : percent > 25 ? 0xFFAA00 : 0xFF5555;
            lore.add(Component.literal("Durability: " + remaining + "/" + maxDur + " (" + percent + "%)").withColor(color));
        }
        if (isOwn) {
            lore.add(Component.literal("Your listing").withColor(0xAAAAAA));
            lore.add(Component.literal("Price: " + listing.getPrice() + " Milky Stars").withColor(0x55FF55));
            lore.add(Component.literal("Click to cancel and get item back!").withColor(0xFF5555));
        } else {
            lore.add(Component.literal("Seller: " + listing.getSellerName()).withColor(0xAAAAAA));
            lore.add(Component.literal("Price: " + listing.getPrice() + " Milky Stars").withColor(0x55FF55));
            if (fromComputer) {
                lore.add(Component.literal("+ 1 Milky Star transaction fee").withColor(0xFFAA00));
                lore.add(Component.literal("(delivered to your Mailbox)").withColor(0xAAAAAA));
            }
            lore.add(Component.literal("Click to purchase!").withColor(0xFFFF55));
        }

        List<String> contents = manager.getContainerContents(listing);
        if (!contents.isEmpty()) {
            lore.add(Component.literal(""));
            lore.add(Component.literal("Contains:").withColor(0x55FFFF));
            for (String line : contents) {
                lore.add(Component.literal("  " + line).withColor(0xAAAAAA));
            }
        }

        stack.set(DataComponents.LORE, new ItemLore(lore));

        return stack;
    }

    private ItemStack createPane(Item paneItem, String name) {
        ItemStack stack = new ItemStack(paneItem);
        if (!name.isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal(name).withColor(0xFFFFFF));
        }
        return stack;
    }
}
