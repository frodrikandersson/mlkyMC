package com.mlkymc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Renders a Spectral Energy bar for ghost players.
 * Shows current SE, accumulated total, and available abilities.
 * Synced via [MLKYMC_SPECTRAL:amount:accumulated] chat messages.
 */
public class SpectralEnergyHud {

    private static int spectralEnergy = -1; // -1 = not a ghost
    private static String nearestClericCoords = null; // "X, Y, Z" or null
    private static int nearestClericDist = 0;

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public void onChatReceived(ClientChatReceivedEvent event) {
        String msg = event.getMessage().getString();

        // On death, immediately mark as ghost so Soul Energy HUD hides
        if (msg.contains("[MLKYMC_DEATH:")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && msg.contains(mc.player.getName().getString())) {
                spectralEnergy = 0;
                ClientClassData.setSoulEnergyMode(false);
            }
        }

        // Check REVIVED first, even if event is cancelled by another handler
        if (msg.contains("[MLKYMC_REVIVED:")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String playerName = mc.player.getName().getString();
                if (msg.contains(playerName)) {
                    spectralEnergy = -1;
                }
            }
        }

        if (event.isCanceled()) return;

        if (msg.startsWith("[MLKYMC_SPECTRAL:") && msg.endsWith("]")) {
            String value = msg.substring(17, msg.length() - 1);
            String[] parts = value.split(":");
            if (parts.length >= 2) {
                try {
                    spectralEnergy = Integer.parseInt(parts[0]);
                    // parts[1] is totalAccumulated — unused on client
                } catch (NumberFormatException ignored) {}
            }
            event.setCanceled(true);
        }
        // REVIVED check moved to top of method (runs before cancel check)

        if (msg.startsWith("[MLKYMC_CLERIC_POS:") && msg.endsWith("]")) {
            String content = msg.substring(19, msg.length() - 1);
            if (content.equals("NONE")) {
                nearestClericCoords = null;
                nearestClericDist = 0;
            } else {
                // Format: X/Y/Z:dist
                String[] parts2 = content.split(":");
                if (parts2.length >= 2) {
                    String[] xyz = parts2[0].split("/");
                    if (xyz.length == 3) {
                        nearestClericCoords = xyz[0] + ", " + xyz[1] + ", " + xyz[2];
                        try { nearestClericDist = Integer.parseInt(parts2[1]); } catch (NumberFormatException ignored) {}
                    }
                }
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (net.minecraft.client.Minecraft.getInstance().options.hideGui) return;
        if (spectralEnergy < 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Render at XP bar position
        int barWidth = 182;
        int barHeight = 5;
        int barX = (screenW - barWidth) / 2;
        int barY = screenH - 32 + 3;

        // Background (dark cyan)
        g.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF001A1A);

        // SE fill - teal gradient
        int fillWidth = (int) (barWidth * (spectralEnergy / 1000.0));
        if (fillWidth > 0) {
            float ratio = spectralEnergy / 1000.0f;
            int r = (int) (30 + ratio * 50);
            int gr = (int) (180 + ratio * 75);
            int b = (int) (180 + ratio * 75);
            int fillColor = 0xFF000000 | (r << 16) | (gr << 8) | b;
            g.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);
        }

        // Border
        g.fill(barX - 1, barY, barX, barY + barHeight, 0xFF003333);
        g.fill(barX + barWidth, barY, barX + barWidth + 1, barY + barHeight, 0xFF003333);
        g.fill(barX - 1, barY - 1, barX + barWidth + 1, barY, 0xFF003333);
        g.fill(barX - 1, barY + barHeight, barX + barWidth + 1, barY + barHeight + 1, 0xFF003333);

        // Text centered on top of the bar
        String text = spectralEnergy + "/1000";
        int textW = mc.font.width(text);
        int textY = barY + (barHeight - 8) / 2; // 8 = font height, center vertically
        g.drawString(mc.font, text, (screenW - textW) / 2, textY, 0xFFFFFFFF, true);

        // Nearest Cleric coordinates (purple text above hearts/food bar)
        if (nearestClericCoords != null) {
            String clericText = "Nearest Cleric: " + nearestClericCoords + " (" + nearestClericDist + "m)";
            int clericW = mc.font.width(clericText);
            g.drawString(mc.font, clericText, (screenW - clericW) / 2, screenH - 52, 0xFFBB77FF, true);
        }
    }

    public static boolean isGhost() {
        return spectralEnergy >= 0;
    }

    public static void reset() {
        spectralEnergy = -1;
        nearestClericCoords = null;
        nearestClericDist = 0;
    }
}
