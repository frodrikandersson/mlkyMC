package com.mlkymc.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mlkymc.MlkyMC;
import com.mlkymc.economy.MilkyStar;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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

    // Spawned villager entities (transient, not saved)
    private final Map<String, Villager> stallVillagers = new HashMap<>();

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

    // ==== STALL LISTINGS (per-player, accessed via stall vendor) ====

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

    // ==== PLAYER STALLS ====

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
        spawnStallVillager(uuid, stall);
        return true;
    }

    public boolean removeStall(ServerPlayer player) {
        String uuid = player.getStringUUID();
        if (!data.stalls.containsKey(uuid)) return false;
        despawnVillager(stallVillagers, uuid);
        data.stalls.remove(uuid);
        save();
        return true;
    }

    public StallData getStall(String ownerUuid) {
        return data.stalls.get(ownerUuid);
    }

    public Map<String, StallData> getAllStalls() {
        return Collections.unmodifiableMap(data.stalls);
    }

    // ==== VILLAGER MANAGEMENT ====

    public void spawnAllVillagers() {
        if (server == null) return;

        // Kill any existing stall villagers from world save to prevent duplicates on restart
        for (var level : server.getAllLevels()) {
            var existing = level.getEntities(
                    net.minecraft.world.entity.EntityType.VILLAGER,
                    e -> e.getTags().contains(STALL_TAG));
            for (var entity : existing) {
                entity.discard();
            }
        }

        for (Map.Entry<String, StallData> entry : data.stalls.entrySet()) {
            spawnStallVillager(entry.getKey(), entry.getValue());
        }
    }

    public void despawnAll() {
        for (Villager v : stallVillagers.values()) {
            if (v != null && v.isAlive()) v.discard();
        }
        stallVillagers.clear();
    }

    private void spawnStallVillager(String ownerUuid, StallData stall) {
        if (server == null) {
            LOGGER.warn("[Stall] server is null, cannot spawn villager for {}", ownerUuid);
            return;
        }
        despawnVillager(stallVillagers, ownerUuid);
        ServerLevel level = getLevel(stall.dimension);
        if (level == null) {
            LOGGER.warn("[Stall] Could not find level for dimension '{}' for stall owner {}", stall.dimension, ownerUuid);
            return;
        }

        // Remove any old persisted stall villagers for this owner (from before restart)
        removeOldStallVillagers(level, ownerUuid, stall.x, stall.y, stall.z);

        String displayName = stall.stallName + " [" + stall.ownerName + "]";
        Villager villager = createVillager(level, displayName, stall.x, stall.y, stall.z, stall.yaw, ownerUuid);
        boolean added = level.addFreshEntity(villager);
        LOGGER.info("[Stall] Spawned villager for {} at {},{},{} in {} - addFreshEntity returned: {}, isAlive: {}, id: {}",
                ownerUuid, stall.x, stall.y, stall.z, stall.dimension, added, villager.isAlive(), villager.getId());
        applyHeadRotation(villager, stall.yaw);
        stallVillagers.put(ownerUuid, villager);
    }

    private void removeOldStallVillagers(ServerLevel level, String ownerUuid, double x, double y, double z) {
        String ownerTag = STALL_TAG + "_" + ownerUuid;
        // Search wider area — villagers might drift slightly
        var aabb = new net.minecraft.world.phys.AABB(x - 5, y - 5, z - 5, x + 5, y + 5, z + 5);
        var old = level.getEntitiesOfClass(Villager.class, aabb, v -> v.getTags().contains(ownerTag));
        for (Villager v : old) {
            v.discard();
        }
    }

    private static final String STALL_TAG = "mlkymc_stall";

    private Villager createVillager(ServerLevel level, String name, double x, double y, double z, float yaw, String ownerUuid) {
        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.setPos(x, y, z);
        villager.setYRot(yaw);
        villager.yBodyRot = yaw;
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        villager.setNoAi(true);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setNoGravity(true);
        villager.addTag(STALL_TAG);
        villager.addTag(STALL_TAG + "_" + ownerUuid);
        return villager;
    }

    private void applyHeadRotation(Villager villager, float yaw) {
        villager.yHeadRot = yaw;
        villager.yHeadRotO = yaw;
        villager.setYHeadRot(yaw);
        villager.yBodyRot = yaw;
        villager.yBodyRotO = yaw;
        if (villager.level() instanceof ServerLevel serverLevel) {
            var packet = new net.minecraft.network.protocol.game.ClientboundRotateHeadPacket(
                    villager, (byte)((int)(yaw * 256.0F / 360.0F)));
            for (ServerPlayer player : serverLevel.players()) {
                player.connection.send(packet);
            }
        }
    }

    private void despawnVillager(Map<String, Villager> map, String key) {
        Villager villager = map.remove(key);
        if (villager != null && villager.isAlive()) {
            villager.discard();
        }
    }

    // ==== IDENTIFICATION ====

    public String getStallOwner(Villager villager) {
        for (Map.Entry<String, Villager> entry : stallVillagers.entrySet()) {
            if (entry.getValue().equals(villager)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean isStallVillager(Villager villager) {
        return getStallOwner(villager) != null;
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
                            if (com.mlkymc.economy.MilkyStar.isMarketBook(lectern.getBook())) {
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
            if (!buyer.getInventory().add(stack)) {
                buyer.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        buyer.level(), buyer.getX(), buyer.getY(), buyer.getZ(), stack));
            }
        }

        data.pendingEarnings.merge(listing.getSellerUuid(), listing.getPrice(), Integer::sum);

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

    /**
     * Get the display name of a listing's item. Uses the custom name from NBT if available,
     * otherwise falls back to the default item name (e.g., "Milky Star" instead of "nether_star").
     */
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

    /**
     * Get the display name of an item by its registry ID. Uses the item's default name.
     */
    public String getItemDisplayName(String itemId) {
        Identifier id = Identifier.parse(itemId);
        Item item = BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(null);
        if (item != null) return new ItemStack(item).getHoverName().getString();
        return itemId.replace("minecraft:", "");
    }

    /**
     * Get a list of content description strings for container items (shulker boxes, bundles, etc.)
     * Returns empty list if the item has no container contents or NBT is missing.
     */
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

            // Check if this is a wallet - show balance instead of container contents
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

    /** Serialize an ItemStack to SNBT string for storage. */
    public static String serializeItemStack(ItemStack stack, ServerPlayer player) {
        var ops = player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var result = ItemStack.CODEC.encodeStart(ops, stack);
        return result.getOrThrow().toString();
    }

    /** Restore an ItemStack from a listing's saved NBT, falling back to basic item if no NBT. */
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
        // Fallback for old listings without NBT
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
