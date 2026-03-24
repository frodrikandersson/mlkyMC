package com.mlkymc.revive;

import com.mlkymc.config.MlkyConfig;
import com.mlkymc.ghost.GhostManager;
import com.mlkymc.storage.JsonStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ReviveManager {

    private final GhostManager ghostManager;
    private final JsonStorage storage;

    public ReviveManager(GhostManager ghostManager, JsonStorage storage) {
        this.ghostManager = ghostManager;
        this.storage = storage;
    }

    public void revive(ServerPlayer ghost) {
        JsonStorage.LocationData statue = storage.getStatueLocation();
        if (statue != null) {
            teleportToStatue(ghost, statue);
        }

        ghostManager.revivePlayer(ghost);

        if (MlkyConfig.getBroadcastRevive()) {
            ghost.level().getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal(ghost.getName().getString() + " has been revived!").withColor(0x55FF55), false);
        }
    }

    public void reviveRandom() {
        List<ServerPlayer> ghosts = ghostManager.getOnlineGhosts();
        if (ghosts.isEmpty()) return;
        ServerPlayer ghost = ghosts.get(ThreadLocalRandom.current().nextInt(ghosts.size()));
        revive(ghost);
    }

    public void setStatueLocation(ServerPlayer player) {
        String dimension = player.level().dimension().identifier().toString();
        storage.setStatueLocation(new JsonStorage.LocationData(
                dimension,
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        ));
    }

    public JsonStorage.LocationData getStatueLocation() {
        return storage.getStatueLocation();
    }

    private void teleportToStatue(ServerPlayer player, JsonStorage.LocationData loc) {
        MinecraftServer server = player.level().getServer();
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                Identifier.parse(loc.dimension));
        ServerLevel level = server.getLevel(dimKey);
        if (level != null) {
            player.teleportTo(level, loc.x, loc.y, loc.z, java.util.Set.of(), loc.yaw, loc.pitch, false);
        }
    }
}
