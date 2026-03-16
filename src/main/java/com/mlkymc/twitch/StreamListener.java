package com.mlkymc.twitch;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class StreamListener {

    private final StreamerManager streamerManager;

    public StreamListener(StreamerManager streamerManager) {
        this.streamerManager = streamerManager;
    }

    @SubscribeEvent
    public void onNameFormat(PlayerEvent.NameFormat event) {
        String mcName = event.getEntity().getName().getString();
        Component prefix = getPrefix(mcName);
        if (prefix != null) {
            event.setDisplayname(((MutableComponent) prefix).append(event.getDisplayname()));
        }
    }

    @SubscribeEvent
    public void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        String mcName = event.getEntity().getName().getString();
        Component prefix = getPrefix(mcName);
        if (prefix != null) {
            Component current = event.getDisplayName();
            if (current == null) {
                current = event.getEntity().getName();
            }
            event.setDisplayName(((MutableComponent) prefix).append(current));
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String mcName = player.getName().getString();
            if (streamerManager.isRegistered(mcName)) {
                streamerManager.updatePlayerTeam(player);
            }
        }
    }

    private Component getPrefix(String mcName) {
        if (!streamerManager.isRegistered(mcName)) return null;

        boolean isLive = streamerManager.getLiveStreams().stream()
                .anyMatch(info -> info.mcName().equalsIgnoreCase(mcName));

        if (isLive) {
            return Component.literal("\u25CF ").withColor(0x55FF55); // Green circle
        } else {
            return Component.literal("\u25CF ").withColor(0xFF5555); // Red circle
        }
    }
}
