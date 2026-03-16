package com.mlkymc.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mlkymc.MlkyMC;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class WalletManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataFile;
    private Map<String, Integer> balances = new HashMap<>();

    public WalletManager(Path configDir) {
        this.dataFile = configDir.resolve("wallets.json");
        load();
    }

    public int getBalance(String uuid) {
        return balances.getOrDefault(uuid, 0);
    }

    public void deposit(String uuid, int amount) {
        balances.merge(uuid, amount, Integer::sum);
        save();
    }

    public boolean withdraw(String uuid, int amount) {
        int current = getBalance(uuid);
        if (current < amount) return false;
        balances.put(uuid, current - amount);
        save();
        return true;
    }

    /**
     * Deposit physical Milky Stars from player inventory into wallet.
     * Returns the number actually deposited.
     */
    public int depositFromInventory(ServerPlayer player, int requestedAmount) {
        int available = MilkyStar.count(player);
        int toDeposit = Math.min(requestedAmount, available);
        if (toDeposit <= 0) return 0;

        if (MilkyStar.remove(player, toDeposit)) {
            deposit(player.getStringUUID(), toDeposit);
            return toDeposit;
        }
        return 0;
    }

    /**
     * Withdraw stars from wallet and give as physical Milky Star items.
     * Returns the number actually withdrawn.
     */
    public int withdrawToInventory(ServerPlayer player, int requestedAmount) {
        String uuid = player.getStringUUID();
        int available = getBalance(uuid);
        int toWithdraw = Math.min(requestedAmount, available);
        if (toWithdraw <= 0) return 0;

        // Give physical stars
        for (var stack : MilkyStar.createAll(toWithdraw)) {
            if (!player.getInventory().add(stack)) {
                // Drop on ground if inventory full
                player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        player.level(), player.getX(), player.getY(), player.getZ(), stack));
            }
        }

        balances.put(uuid, available - toWithdraw);
        save();
        return toWithdraw;
    }

    /**
     * Total stars a player has: physical inventory + wallet balance.
     */
    public int totalStars(ServerPlayer player) {
        return MilkyStar.count(player) + getBalance(player.getStringUUID());
    }

    /**
     * Spend stars from inventory first, then wallet. Used by market/shops/dimensions.
     * Returns true if the full amount was spent.
     */
    public boolean spend(ServerPlayer player, int amount) {
        if (totalStars(player) < amount) return false;

        // Take from inventory first
        int inInventory = MilkyStar.count(player);
        int fromInventory = Math.min(amount, inInventory);
        if (fromInventory > 0) {
            MilkyStar.remove(player, fromInventory);
        }

        // Take remainder from wallet
        int fromWallet = amount - fromInventory;
        if (fromWallet > 0) {
            String uuid = player.getStringUUID();
            balances.put(uuid, getBalance(uuid) - fromWallet);
            save();
        }

        return true;
    }

    // ---- Persistence ----

    public void load() {
        if (Files.exists(dataFile)) {
            try (Reader reader = Files.newBufferedReader(dataFile)) {
                @SuppressWarnings("unchecked")
                Map<String, Double> loaded = GSON.fromJson(reader, Map.class);
                if (loaded != null) {
                    balances = new HashMap<>();
                    // Gson deserializes integers as doubles in untyped maps
                    for (var entry : loaded.entrySet()) {
                        balances.put(entry.getKey(), entry.getValue().intValue());
                    }
                }
            } catch (IOException e) {
                MlkyMC.LOGGER.error("Failed to load wallets.json", e);
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            try (Writer writer = Files.newBufferedWriter(dataFile)) {
                GSON.toJson(balances, writer);
            }
        } catch (IOException e) {
            MlkyMC.LOGGER.error("Failed to save wallets.json", e);
        }
    }
}
