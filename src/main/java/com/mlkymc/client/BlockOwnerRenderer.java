package com.mlkymc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/**
 * Displays block owner name as action bar text when looking at an owned block.
 */
public class BlockOwnerRenderer {

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String msg = event.getMessage().getString();
        if (!msg.contains("[MLKYMC_BLOCK_OWNERS:")) return;

        int start = msg.indexOf("[MLKYMC_BLOCK_OWNERS:") + 21;
        int end = msg.indexOf("]", start);
        if (end < 0) {
            event.setCanceled(true);
            return;
        }

        String data = msg.substring(start, end);
        event.setCanceled(true);

        if (data.equals("CLEAR") || data.isEmpty()) {
            // Send empty action bar message to clear instantly
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.empty(), true);
            }
            return;
        }

        String[] parts = data.split(",", 5);
        if (parts.length >= 5) {
            String ownerName = parts[4];
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("Owner: " + ownerName).withColor(0xAADDFF),
                        true); // true = action bar
            }
        }
    }
}
