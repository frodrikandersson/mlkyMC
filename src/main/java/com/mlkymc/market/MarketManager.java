package com.mlkymc.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mlkymc.MlkyMC;
import com.mlkymc.economy.MilkyStar;
import com.mlkymc.registry.ModBlocks;
import com.mlkymc.shop.ShopBlock;
import com.mlkymc.shop.ShopBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarketManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("mlkymc");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private Path dataFile;
    private MarketData data = new MarketData();
    private MinecraftServer server;

    public MarketManager(Path configDir) {
        this.dataFile = configDir.resolve("market.json");
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    // ==== MARKETPLACE LISTINGS (accessed via Market Book on lectern) ====

    public MarketListing addMarketplaceListing(ServerPlayer player, String itemId, int amount, int price, String itemNbt) {
        MarketListing listing = new MarketListing(
                player.getStringUUID(), player.getName().getString(), itemId, amount, price, itemNbt);
        data.marketplaceListings.add(listing);
        save();
        return listing;
    }

    public List<MarketListing> getMarketplaceListings() {
        return Collections.unmodifiableList(data.marketplaceListings);
    }

    public List<MarketListing> getMarketplaceListingsBySeller(String uuid) {
        List<MarketListing> result = new ArrayList<>();
        for (MarketListing l : data.marketplaceListings) {
            if (l.getSellerUuid().equals(uuid)) result.add(l);
        }
        return result;
    }

    public boolean cancelMarketplaceListing(ServerPlayer player, String listingId) {
        MarketListing listing = findInList(data.marketplaceListings, listingId);
        if (listing == null || !listing.getSellerUuid().equals(player.getStringUUID())) return false;
        giveItemBack(player, listing);
        data.marketplaceListings.remove(listing);
        save();
        return true;
    }

    public boolean purchaseMarketplaceListing(ServerPlayer buyer, String listingId) {
        MarketListing listing = findInList(data.marketplaceListings, listingId);
        if (listing == null) return false;
        if (!processPurchase(buyer, listing)) return false;
        data.marketplaceListings.remove(listing);
        save();
        return true;
    }

    // ==== STALL LISTINGS (per-player, accessed via stall block) ====

    public MarketListing addStallListing(ServerPlayer player, String itemId, int amount, int price, String itemNbt) {
        String uuid = player.getStringUUID();
        MarketListing listing = new MarketListing(
                uuid, player.getName().getString(), itemId, amount, price, itemNbt);
        data.stallListings.computeIfAbsent(uuid, k -> new ArrayList<>()).add(listing);
        save();
        return listing;
    }

    public List<MarketListing> getStallListings(String ownerUuid) {
        return Collections.unmodifiableList(
                data.stallListings.getOrDefault(ownerUuid, Collections.emptyList()));
    }

    public List<MarketListing> getAllStallListings() {
        List<MarketListing> all = new ArrayList<>();
        for (List<MarketListing> listings : data.stallListings.values()) {
            all.addAll(listings);
        }
        return Collections.unmodifiableList(all);
    }

    public String getStallNameForSeller(String sellerUuid) {
        StallData stall = data.stalls.get(sellerUuid);
        return stall != null ? stall.stallName : null;
    }

    public boolean cancelStallListing(ServerPlayer player, String listingId) {
        String uuid = player.getStringUUID();
        List<MarketListing> listings = data.stallListings.get(uuid);
        if (listings == null) return false;
        MarketListing listing = findInList(listings, listingId);
        if (listing == null) return false;
        giveItemBack(player, listing);
        listings.remove(listing);
        save();
        return true;
    }

    public boolean purchaseStallListing(ServerPlayer buyer, String stallOwnerUuid, String listingId) {
        List<MarketListing> listings = data.stallListings.get(stallOwnerUuid);
        if (listings == null) return false;
        MarketListing listing = findInList(listings, listingId);
        if (listing == null) return false;
        if (!processPurchase(buyer, listing)) return false;
        listings.remove(listing);
        save();
        return true;
    }

    // ==== SHARED: Earnings ====

    public int collectEarnings(ServerPlayer player) {
        String uuid = player.getStringUUID();
        int earnings = data.pendingEarnings.getOrDefault(uuid, 0);
        if (earnings <= 0) return 0;

        for (ItemStack star : MilkyStar.createAll(earnings)) {
            if (!player.getInventory().add(star)) {
                player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        player.level(), player.getX(), player.getY(), player.getZ(), star));
            }
        }

        data.pendingEarnings.put(uuid, 0);
        save();
        return earnings;
    }

    public int getPendingEarnings(String uuid) {
        return data.pendingEarnings.getOrDefault(uuid, 0);
    }

    // ==== PLAYER STALLS (block-based) ====

    public boolean createStall(ServerPlayer player, String stallName) {
        String uuid = player.getStringUUID();
        if (data.stalls.containsKey(uuid)) return false;

        StallData stall = new StallData();
        stall.ownerName = player.getName().getString();
        stall.stallName = stallName;
        stall.dimension = player.level().dimension().identifier().toString();
        stall.x = player.getX();
        stall.y = player.getY();
        stall.z = player.getZ();
        stall.yaw = player.getYRot();
        data.stalls.put(uuid, stall);
        save();
        placeStallBlock(uuid, stall);
        return true;
    }

    /**
     * Register a stall from an already-placed block (called by ShopBlock.setPlacedBy).
     * Does NOT place a block — the block is already in the world.
     */
    public void registerStall(String ownerUuid, StallData stall) {
        data.stalls.put(ownerUuid, stall);
        save();
    }

    public boolean removeStall(ServerPlayer player) {
        String uuid = player.getStringUUID();
        StallData stall = data.stalls.get(uuid);
        if (stall == null) return false;
        removeStallBlock(stall);
        data.stalls.remove(uuid);
        save();
        return true;
    }

    /**
     * Remove stall data only (called when the block is already being broken).
     * Does NOT remove the block — it's already gone.
     */
    public void removeStallData(String ownerUuid) {
        data.stalls.remove(ownerUuid);
        save();
    }

    public StallData getStall(String ownerUuid) {
        return data.stalls.get(ownerUuid);
    }

    public Map<String, StallData> getAllStalls() {
        return Collections.unmodifiableMap(data.stalls);
    }

    /**
     * Find the stall owner UUID by looking up the block entity at a position.
     */
    public String getStallOwnerAtPos(BlockPos pos, ServerLevel level) {
        var be = level.getBlockEntity(pos);
        if (be instanceof ShopBlockEntity shopBE) {
            String shopId = shopBE.getShopId();
            if (!shopId.isEmpty() && shopId.startsWith("stall_")) {
                return shopId.substring(6); // "stall_<uuid>" -> "<uuid>"
            }
        }
        return null;
    }

    // ==== BLOCK MANAGEMENT ====

    private void placeStallBlock(String ownerUuid, StallData stall) {
        if (server == null) return;
        ServerLevel level = getLevel(stall.dimension);
        if (level == null) return;

        BlockPos pos = BlockPos.containing(stall.x, stall.y, stall.z);
        var facing = net.minecraft.core.Direction.fromYRot(stall.yaw);

        var state = ModBlocks.STALL_DEED.get().defaultBlockState()
                .setValue(ShopBlock.FACING, facing)
                .setValue(ShopBlock.HALF, DoubleBlockHalf.LOWER);
        level.setBlock(pos, state, 3);
        level.setBlock(pos.above(), state.setValue(ShopBlock.HALF, DoubleBlockHalf.UPPER), 3);

        var be = level.getBlockEntity(pos);
        if (be instanceof ShopBlockEntity shopBE) {
            shopBE.setShopId("stall_" + ownerUuid);
        }
    }

    private void removeStallBlock(StallData stall) {
        if (server == null) return;
        ServerLevel level = getLevel(stall.dimension);
        if (level == null) return;

        BlockPos pos = BlockPos.containing(stall.x, stall.y, stall.z);
        if (level.getBlockState(pos).is(ModBlocks.STALL_DEED.get())) {
            level.removeBlock(pos.above(), false);
            level.removeBlock(pos, false);
        }
    }

    /**
     * On server start: ensure all stall blocks exist with correct data.
     * Blocks persist in chunks — only place if missing (e.g. first migration from villagers).
     */
    public void placeAllStallBlocks() {
        if (server == null) return;
        for (Map.Entry<String, StallData> entry : data.stalls.entrySet()) {
            String uuid = entry.getKey();
            StallData stall = entry.getValue();
            ServerLevel level = getLevel(stall.dimension);
            if (level == null) continue;

            BlockPos pos = BlockPos.containing(stall.x, stall.y, stall.z);
            if (!level.getBlockState(pos).is(ModBlocks.STALL_DEED.get())) {
                placeStallBlock(uuid, stall);
            } else {
                var be = level.getBlockEntity(pos);
                if (be instanceof ShopBlockEntity shopBE) {
                    String expectedId = "stall_" + uuid;
                    if (!expectedId.equals(shopBE.getShopId())) {
                        shopBE.setShopId(expectedId);
                    }
                }
            }
        }
    }

    // ==== PROXIMITY CHECKS ====

    private static final double INTERACT_RANGE = 6.0;

    public boolean isNearMarketLectern(ServerPlayer player) {
        var level = player.level();
        var playerPos = player.blockPosition();
        int range = (int) INTERACT_RANGE;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    var pos = playerPos.offset(dx, dy, dz);
                    if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LECTERN)) {
                        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity lectern) {
                            if (MilkyStar.isMarketBook(lectern.getBook())) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean isNearOwnStall(ServerPlayer player) {
        StallData stall = data.stalls.get(player.getStringUUID());
        if (stall == null) return false;
        if (!player.level().dimension().identifier().toString().equals(stall.dimension)) return false;
        double distSq = player.distanceToSqr(stall.x, stall.y, stall.z);
        return distSq <= INTERACT_RANGE * INTERACT_RANGE;
    }

    // ==== HELPERS ====

    private MarketListing findInList(List<MarketListing> listings, String id) {
        for (MarketListing l : listings) {
            if (l.getId().equals(id)) return l;
        }
        return null;
    }

    private void giveItemBack(ServerPlayer player, MarketListing listing) {
        ItemStack stack = restoreItemStack(listing);
        if (!stack.isEmpty()) {
            if (!player.getInventory().add(stack)) {
                player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        player.level(), player.getX(), player.getY(), player.getZ(), stack));
            }
        }
    }

    private boolean processPurchase(ServerPlayer buyer, MarketListing listing) {
        if (listing.getSellerUuid().equals(buyer.getStringUUID())) {
            buyer.sendSystemMessage(Component.literal("You can't buy your own listing.").withColor(0xFF5555));
            return false;
        }

        if (MilkyStar.count(buyer) < listing.getPrice()) {
            buyer.sendSystemMessage(Component.literal("You need " + listing.getPrice() + " Milky Stars.").withColor(0xFF5555));
            return false;
        }

        if (!MilkyStar.remove(buyer, listing.getPrice())) return false;

        ItemStack stack = restoreItemStack(listing);
        if (!stack.isEmpty()) {
            // Try mailbox delivery first (Refurbished Furniture integration).
            // Falls back to inventory/drop if the mod isn't installed or the buyer
            // has no mailbox.
            boolean mailed = false;
            if (com.mlkymc.compat.FurnitureCompat.isAvailable() && server != null) {
                mailed = com.mlkymc.compat.FurnitureCompat.sendToMailbox(
                        server, buyer.getUUID(), stack);
            }
            if (!mailed) {
                if (!buyer.getInventory().add(stack)) {
                    buyer.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                            buyer.level(), buyer.getX(), buyer.getY(), buyer.getZ(), stack));
                }
            }
        }

        // Route seller earnings to their mailbox as Milky Stars if possible.
        // Only fall back to pendingEarnings if mailbox delivery fails — otherwise
        // the seller gets paid twice (once from mailbox, once from collectEarnings).
        boolean earningsMailed = false;
        if (com.mlkymc.compat.FurnitureCompat.isAvailable() && server != null) {
            try {
                UUID sellerUuid = UUID.fromString(listing.getSellerUuid());
                earningsMailed = true;
                for (ItemStack star : com.mlkymc.economy.MilkyStar.createAll(listing.getPrice())) {
                    if (!com.mlkymc.compat.FurnitureCompat.sendToMailbox(server, sellerUuid, star)) {
                        earningsMailed = false;
                        break;
                    }
                }
            } catch (Exception e) {
                earningsMailed = false;
            }
        }
        if (!earningsMailed) {
            data.pendingEarnings.merge(listing.getSellerUuid(), listing.getPrice(), Integer::sum);
        }

        String displayName = getItemDisplayName(listing);
        if (server != null) {
            ServerPlayer seller = server.getPlayerList().getPlayer(UUID.fromString(listing.getSellerUuid()));
            if (seller != null) {
                seller.sendSystemMessage(Component.literal(
                        buyer.getName().getString() + " bought " + listing.getAmount() + "x " +
                        displayName + " for " + listing.getPrice() +
                        " Milky Stars!").withColor(0x55FF55));
            }
        }

        buyer.sendSystemMessage(Component.literal(
                "Purchased " + listing.getAmount() + "x " +
                displayName + " for " +
                listing.getPrice() + " Milky Stars.").withColor(0x55FF55));
        return true;
    }

    public String getItemDisplayName(MarketListing listing) {
        if (listing.getItemNbt() != null && !listing.getItemNbt().isEmpty() && server != null) {
            try {
                net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseCompoundFully(listing.getItemNbt());
                var ops = server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                var result = ItemStack.CODEC.parse(ops, tag);
                return result.getOrThrow().getHoverName().getString();
            } catch (Exception e) {
                // Fall through to registry name
            }
        }
        Identifier id = Identifier.parse(listing.getItemId());
        Item item = BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(null);
        if (item != null) return new ItemStack(item).getHoverName().getString();
        return listing.getItemId().replace("minecraft:", "");
    }

    public String getItemDisplayName(String itemId) {
        Identifier id = Identifier.parse(itemId);
        Item item = BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(null);
        if (item != null) return new ItemStack(item).getHoverName().getString();
        return itemId.replace("minecraft:", "");
    }

    public List<String> getContainerContents(MarketListing listing) {
        List<String> contents = new ArrayList<>();
        if (listing.getItemNbt() == null || listing.getItemNbt().isEmpty() || server == null) {
            return contents;
        }
        try {
            net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseCompoundFully(listing.getItemNbt());
            var ops = server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var result = ItemStack.CODEC.parse(ops, tag);
            ItemStack stack = result.getOrThrow();

            if (MilkyStar.isJar(stack)) {
                int balance = MilkyStar.getJarBalance(stack);
                contents.add(balance + " Milky Stars");
                return contents;
            }

            var container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
            if (container != null) {
                for (ItemStack inner : container.nonEmptyItems()) {
                    contents.add(inner.getCount() + "x " + inner.getHoverName().getString());
                }
            }

            var bundleContents = stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
            if (bundleContents != null) {
                for (ItemStack inner : bundleContents.items()) {
                    contents.add(inner.getCount() + "x " + inner.getHoverName().getString());
                }
            }
        } catch (Exception e) {
            MlkyMC.LOGGER.error("Failed to read container contents for listing " + listing.getId(), e);
        }
        return contents;
    }

    public static String serializeItemStack(ItemStack stack, ServerPlayer player) {
        var ops = player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var result = ItemStack.CODEC.encodeStart(ops, stack);
        return result.getOrThrow().toString();
    }

    public ItemStack restoreItemStack(MarketListing listing) {
        if (listing.getItemNbt() != null && !listing.getItemNbt().isEmpty() && server != null) {
            try {
                net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseCompoundFully(listing.getItemNbt());
                var ops = server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                var result = ItemStack.CODEC.parse(ops, tag);
                return result.getOrThrow();
            } catch (Exception e) {
                MlkyMC.LOGGER.error("Failed to parse item NBT for listing " + listing.getId(), e);
            }
        }
        Identifier id = Identifier.parse(listing.getItemId());
        Item item = BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(null);
        if (item != null) return new ItemStack(item, listing.getAmount());
        return ItemStack.EMPTY;
    }

    private ServerLevel getLevel(String dimension) {
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    // ==== PERSISTENCE ====

    public void load() {
        if (Files.exists(dataFile)) {
            try (Reader reader = Files.newBufferedReader(dataFile)) {
                MarketData loaded = GSON.fromJson(reader, MarketData.class);
                if (loaded != null) data = loaded;
            } catch (IOException e) {
                MlkyMC.LOGGER.error("Failed to load market.json", e);
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer writer = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save market.json", e);
        }
    }

    public void reload(Path dir) {
        this.dataFile = dir.resolve("market.json");
        load();
    }

    // ==== DATA CLASSES ====

    private static class MarketData {
        List<MarketListing> marketplaceListings = new ArrayList<>();
        Map<String, List<MarketListing>> stallListings = new LinkedHashMap<>();
        Map<String, StallData> stalls = new LinkedHashMap<>();
        Map<String, Integer> pendingEarnings = new HashMap<>();
    }

    public static class StallData {
        public String ownerName;
        public String stallName;
        public String dimension;
        public double x, y, z;
        public float yaw;
    }
}
