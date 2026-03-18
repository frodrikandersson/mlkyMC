package com.mlkymc.twitch;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class StreamListener {

    private final StreamerManager streamerManager;

    public StreamListener(StreamerManager streamerManager) {
        this.streamerManager = streamerManager;
    }

    // Prefix is handled by scoreboard team (StreamerManager.updatePlayerTeam)
    // No need for NameFormat/TabListNameFormat — the team prefix covers chat, tab, and nameplate

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String mcName = player.getName().getString();
            if (streamerManager.isRegistered(mcName)) {
                streamerManager.updatePlayerTeam(player);
            }
        }
    }
}
