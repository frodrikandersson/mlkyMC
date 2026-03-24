package com.mlkymc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Shows a compact countdown above the hotbar when a player dies.
 * Indicates the 60s Cleric free resurrection window.
 */
public class ResurrectionCountdownHud {

    private static long countdownEndTimeMs = -1; // Wall-clock time when countdown expires
    private static final int COUNTDOWN_SECONDS = 60;
    private static String deadPlayerName = "";

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String msg = event.getMessage().getString();
        if (msg.startsWith("[MLKYMC_DEATH:") && msg.endsWith("]")) {
            deadPlayerName = msg.substring(14, msg.length() - 1);
            countdownEndTimeMs = System.currentTimeMillis() + COUNTDOWN_SECONDS * 1000L;
            event.setCanceled(true);
        }
        // Cancel countdown when ghost is revived
        if (msg.startsWith("[MLKYMC_REVIVED:") && msg.endsWith("]")) {
            String revivedName = msg.substring(16, msg.length() - 1);
            if (revivedName.equals(deadPlayerName)) {
                countdownEndTimeMs = -1;
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (countdownEndTimeMs < 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (ModKeybinds.isPrimaryHeld()) return;

        long remainingMs = countdownEndTimeMs - System.currentTimeMillis();
        if (remainingMs <= 0) {
            countdownEndTimeMs = -1;
            return;
        }

        float remaining = (float) remainingMs / (COUNTDOWN_SECONDS * 1000f);
        int secondsLeft = (int) (remainingMs / 1000);

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Compact bar above hotbar (100px wide, centered)
        int barWidth = 100;
        int barHeight = 3;
        int barX = (screenW - barWidth) / 2;
        int barY = screenH - 48;

        // Background
        g.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0x80000000);

        // Fill bar
        int fillWidth = (int) (barWidth * remaining);
        int color = remaining > 0.3f ? 0xFFFFD700 : 0xFFFF3333;
        g.fill(barX, barY, barX + fillWidth, barY + barHeight, color);

        // Text above bar: "PlayerName 45s" (compact)
        String text = deadPlayerName + " " + secondsLeft + "s";
        int textWidth = mc.font.width(text);
        g.drawString(mc.font, text, (screenW - textWidth) / 2, barY - 10, 0xFFD700, true);

        // Small label below bar
        String label = "Free resurrect";
        int labelWidth = mc.font.width(label);
        g.drawString(mc.font, label, (screenW - labelWidth) / 2, barY + barHeight + 2, 0xAAAAAA, true);
    }
}
