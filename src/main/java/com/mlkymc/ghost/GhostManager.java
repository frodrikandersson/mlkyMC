package com.mlkymc.ghost;

import com.mlkymc.config.MlkyConfig;
import com.mlkymc.storage.JsonStorage;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.lang.reflect.Method;
import java.util.*;

public class GhostManager {

    private final JsonStorage storage;
    private MinecraftServer server;

    public GhostManager(JsonStorage storage) {
        this.storage = storage;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public boolean isGhost(UUID playerId) {
        return storage.getGhosts().contains(playerId);
    }

    public void makeGhost(ServerPlayer player) {
        UUID id = player.getUUID();
        storage.getGhosts().add(id);
        storage.save();
        dropAllItems(player);
        applyGhostEffects(player);
    }

    @SuppressWarnings("unchecked")
    private void dropAllItems(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        // Drop main inventory, armor, and offhand
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                player.spawnAtLocation(level, stack.copy());
            }
        }
        player.getInventory().clearContent();

        // Drop Backpacked backpacks via reflection (optional dependency)
        try {
            Class<?> helperClass = Class.forName("com.mrcrayfish.backpacked.BackpackHelper");
            Method removeAll = helperClass.getMethod("removeAllBackpacks", net.minecraft.world.entity.player.Player.class);
            NonNullList<ItemStack> backpacks = (NonNullList<ItemStack>) removeAll.invoke(null, player);
            for (ItemStack stack : backpacks) {
                if (!stack.isEmpty()) {
                    player.spawnAtLocation(level, stack.copy());
                }
            }
        } catch (ClassNotFoundException ignored) {
            // Backpacked not installed, skip
        } catch (Exception e) {
            // Log but don't crash
            System.err.println("[mlkyMC] Failed to drop backpack: " + e.getMessage());
        }
    }

    public void revivePlayer(ServerPlayer player) {
        UUID id = player.getUUID();
        storage.getGhosts().remove(id);
        storage.save();
        removeGhostEffects(player);

        // Clear ghost data (SE, mimic state, haunt zones, etc.)
        var gdm = com.mlkymc.MlkyMC.getGhostDataManager();
        if (gdm != null) {
            // End any active mimic or haunt zone
            var data = gdm.get(id);
            if (data != null) {
                if (data.isMimicking()) {
                    gdm.endMimic(player, data, false);
                }
                if (data.hasHauntZone()) {
                    gdm.toggleHauntZone(player, data);
                }
            }
            gdm.remove(id);
            gdm.save();
        }

        // Clear pending mimic selection
        var ph = com.mlkymc.classes.PowerHandler.getInstance();
        if (ph != null) {
            ph.clearGhostState(id);
        }

        // Disconnect from any Soul Altar
        var altarManager = com.mlkymc.MlkyMC.getSoulAltarManager();
        if (altarManager != null) {
            altarManager.disconnectGhostFromAll(id);
        }

        // Broadcast revive to cancel resurrection countdown and reset ghost HUDs on all clients
        if (server != null) {
            String cancelSync = "[MLKYMC_REVIVED:" + player.getName().getString() + "]";
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                online.sendSystemMessage(Component.literal(cancelSync).withColor(0x000000));
            }
        }
    }

    public void applyGhostEffects(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);

        int speedAmp = MlkyConfig.getGhostSpeedAmplifier();
        int jumpAmp = MlkyConfig.getGhostJumpAmplifier();

        player.addEffect(new MobEffectInstance(MobEffects.SPEED, Integer.MAX_VALUE, speedAmp, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, Integer.MAX_VALUE, jumpAmp, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, Integer.MAX_VALUE, 0, false, false));
        // No Glowing — ghosts should be invisible to living players

        // Death messages handled by GhostListener to avoid duplicates
    }

    public void removeGhostEffects(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
    }

    public List<ServerPlayer> getOnlineGhosts() {
        if (server == null) return List.of();
        List<ServerPlayer> ghosts = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isGhost(player.getUUID())) {
                ghosts.add(player);
            }
        }
        return ghosts;
    }

    public void cleanup() {
        storage.save();
    }
}
