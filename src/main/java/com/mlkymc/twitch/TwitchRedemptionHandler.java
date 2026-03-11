package com.mlkymc.twitch;

import com.mlkymc.MlkyMC;
import com.mlkymc.ghost.GhostManager;
import com.mlkymc.revive.ReviveManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class TwitchRedemptionHandler {

    private final GhostManager ghostManager;
    private final ReviveManager reviveManager;
    private MinecraftServer server;

    public TwitchRedemptionHandler(GhostManager ghostManager, ReviveManager reviveManager) {
        this.ghostManager = ghostManager;
        this.reviveManager = reviveManager;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void handleRedemption(String redeemerName) {
        if (server == null) return;

        server.execute(() -> {
            List<ServerPlayer> ghosts = ghostManager.getOnlineGhosts();
            if (ghosts.isEmpty()) {
                MlkyMC.LOGGER.info("Twitch redemption by {} but no ghosts online.", redeemerName);
                return;
            }

            reviveManager.reviveRandom();

            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("A Twitch viewer (" + redeemerName + ") revived a ghost!").withColor(0xFF55FF), false);
        });
    }
}
