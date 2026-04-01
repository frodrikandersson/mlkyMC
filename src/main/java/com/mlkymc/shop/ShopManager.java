package com.mlkymc.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mlkymc.MlkyMC;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ShopManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataFile;
    private Map<String, Shopkeeper> shopkeepers = new LinkedHashMap<>();
    private final Map<String, Villager> spawnedVillagers = new HashMap<>();
    private MinecraftServer server;

    public ShopManager(Path configDir) {
        this.dataFile = configDir.resolve("shops.json");
        load(); // Shops are global config, not per-world
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public Shopkeeper getShopkeeper(String id) {
        return shopkeepers.get(id);
    }

    public Map<String, Shopkeeper> getAllShopkeepers() {
        return Collections.unmodifiableMap(shopkeepers);
    }

    public void createShopkeeper(String id, String name, String profession, ServerLevel level, double x, double y, double z, float yaw) {
        Shopkeeper shop = new Shopkeeper(id, name, profession, level.dimension().identifier().toString(), x, y, z, yaw);
        shopkeepers.put(id, shop);
        save();
        spawnVillager(shop);
    }

    public void removeShopkeeper(String id) {
        despawnVillager(id);
        shopkeepers.remove(id);
        save();
    }

    public void spawnAllVillagers() {
        if (server == null) return;
        for (Shopkeeper shop : shopkeepers.values()) {
            spawnVillager(shop);
        }
    }

    private void spawnVillager(Shopkeeper shop) {
        if (server == null) return;
        despawnVillager(shop.getId());

        ServerLevel level = getLevel(shop.getDimension());
        if (level == null) return;

        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.setPos(shop.getX(), shop.getY(), shop.getZ());
        villager.setYRot(shop.getYaw());
        villager.setCustomName(Component.literal(shop.getName()));
        villager.setCustomNameVisible(true);
        villager.setNoAi(true);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setNoGravity(true);

        level.addFreshEntity(villager);
        spawnedVillagers.put(shop.getId(), villager);
    }

    public void despawnVillager(String id) {
        Villager villager = spawnedVillagers.remove(id);
        if (villager != null && villager.isAlive()) {
            villager.discard();
        }
    }

    public void respawnVillager(String id) {
        Shopkeeper shop = shopkeepers.get(id);
        if (shop != null) spawnVillager(shop);
    }

    public void despawnAll() {
        for (Villager v : spawnedVillagers.values()) {
            if (v != null && v.isAlive()) v.discard();
        }
        spawnedVillagers.clear();
    }

    public boolean isShopkeeperVillager(Villager villager) {
        return spawnedVillagers.containsValue(villager);
    }

    public Shopkeeper getShopkeeperByVillager(Villager villager) {
        for (Map.Entry<String, Villager> entry : spawnedVillagers.entrySet()) {
            if (entry.getValue().equals(villager)) {
                return shopkeepers.get(entry.getKey());
            }
        }
        return null;
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

    public void load() {
        if (Files.exists(dataFile)) {
            try (Reader reader = Files.newBufferedReader(dataFile)) {
                Type type = new TypeToken<LinkedHashMap<String, Shopkeeper>>() {}.getType();
                Map<String, Shopkeeper> loaded = GSON.fromJson(reader, type);
                if (loaded != null) shopkeepers = loaded;
            } catch (IOException e) {
                MlkyMC.LOGGER.error("Failed to load shops.json", e);
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer writer = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(shopkeepers, writer);
            }
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save shops.json", e);
        }
    }
}
