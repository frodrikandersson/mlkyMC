package com.mlkymc.market;

import java.util.UUID;

public class MarketListing {

    private String id;
    private String sellerUuid;
    private String sellerName;
    private String itemId;
    private int amount;
    private int price;
    private long listedAt;
    private String itemNbt; // Serialized ItemStack (preserves shulker contents, enchantments, etc.)

    public MarketListing() {}

    public MarketListing(String sellerUuid, String sellerName, String itemId, int amount, int price, String itemNbt) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.itemId = itemId;
        this.amount = amount;
        this.price = price;
        this.listedAt = System.currentTimeMillis();
        this.itemNbt = itemNbt;
    }

    public String getId() { return id; }
    public String getSellerUuid() { return sellerUuid; }
    public String getSellerName() { return sellerName; }
    public String getItemId() { return itemId; }
    public int getAmount() { return amount; }
    public int getPrice() { return price; }
    public long getListedAt() { return listedAt; }
    public String getItemNbt() { return itemNbt; }
}
