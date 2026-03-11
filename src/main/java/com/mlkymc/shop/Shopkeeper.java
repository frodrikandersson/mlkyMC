package com.mlkymc.shop;

import java.util.ArrayList;
import java.util.List;

public class Shopkeeper {

    private String id;
    private String name;
    private String profession;
    private String dimension;
    private double x, y, z;
    private float yaw;
    private List<TradeData> trades = new ArrayList<>();

    public Shopkeeper() {}

    public Shopkeeper(String id, String name, String profession, String dimension, double x, double y, double z, float yaw) {
        this.id = id;
        this.name = name;
        this.profession = profession;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getProfession() { return profession; }
    public String getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public List<TradeData> getTrades() { return trades; }

    public void addTrade(TradeData trade) { trades.add(trade); }
    public void removeTrade(int index) { if (index >= 0 && index < trades.size()) trades.remove(index); }

    public static class TradeData {
        public String item;
        public int amount;
        public int price;
        public boolean selling;

        public TradeData() {}

        public TradeData(String item, int amount, int price, boolean selling) {
            this.item = item;
            this.amount = amount;
            this.price = price;
            this.selling = selling;
        }
    }
}
