package com.mlkymc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cleric-only HUD: shows active graves within the 5-min free resurrection window.
 * Stacked bars above the skill icon list in the bottom-left.
 * Each entry shows player name, coordinates, dimension color, and a timer bar.
 */
public class GraveTrackerHud {

    private static final int BAR_WIDTH = 80;
    private static final int BAR_HEIGHT = 4;
    private static final int ENTRY_HEIGHT = 18; // space per grave entry
    private static final int MAX_TIMER = 300; // 5 minutes in seconds

    private static final List<GraveEntry> activeGraves = new ArrayList<>();
    private static final Map<String, Long> syncTimes = new java.util.LinkedHashMap<>();

    private record GraveEntry(String name, int x, int y, int z, int remainingSec, String dimension) {}

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String msg = event.getMessage().getString();
        if (!msg.startsWith("[MLKYMC_GRAVES:") || !msg.endsWith("]")) return;

        String content = msg.substring(15, msg.length() - 1);
        activeGraves.clear();
        syncTimes.clear();

        if (!content.isEmpty()) {
            long now = System.currentTimeMillis();
            for (String entry : content.split("\\|")) {
                String[] parts = entry.split(",");
                if (parts.length >= 6) {
                    try {
                        String name = parts[0];
                        int gx = Integer.parseInt(parts[1]);
                        int gy = Integer.parseInt(parts[2]);
                        int gz = Integer.parseInt(parts[3]);
                        int sec = Integer.parseInt(parts[4]);
                        String dim = parts[5];
                        activeGraves.add(new GraveEntry(name, gx, gy, gz, sec, dim));
                        syncTimes.put(name, now);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (net.minecraft.client.Minecraft.getInstance().options.hideGui) return;
        if (activeGraves.isEmpty()) return;
        if (!ClientClassData.hasChosenClass()) return;
        if (ClientClassData.getChosenClass() != com.mlkymc.classes.ClassType.CLERIC) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int screenW = mc.getWindow().getGuiScaledWidth();

        long now = System.currentTimeMillis();

        // Position: right-aligned, 2px from bottom, stacking upward
        int bottomY = screenH - 2;

        for (int i = 0; i < activeGraves.size(); i++) {
            GraveEntry grave = activeGraves.get(i);

            // Client-side countdown from sync time
            Long syncTime = syncTimes.get(grave.name);
            long elapsed = syncTime != null ? (now - syncTime) / 1000 : 0;
            int displaySec = Math.max(0, grave.remainingSec - (int) elapsed);
            if (displaySec <= 0) continue;

            float ratio = (float) displaySec / MAX_TIMER;

            // Dimension color for coordinates
            int coordColor;
            if (grave.dimension.contains("the_nether")) {
                coordColor = 0xFFFF6655;
            } else if (grave.dimension.contains("the_end")) {
                coordColor = 0xFFDD77FF;
            } else {
                coordColor = 0xFF77FF77;
            }

            // Build text and measure width so bar matches text length
            String displayName = grave.name.length() > 10 ? grave.name.substring(0, 10) + "..." : grave.name;
            String coordText = displayName + " " + grave.x + ", " + grave.y + ", " + grave.z;
            int textWidth = mc.font.width(coordText);

            // Right-aligned position
            int entryX = screenW - textWidth - 4;
            int entryY = bottomY - (i * ENTRY_HEIGHT);

            // Draw text above bar
            g.drawString(mc.font, coordText, entryX, entryY - BAR_HEIGHT - 10, coordColor, true);

            // Draw timer bar (same width as text)
            g.fill(entryX, entryY - BAR_HEIGHT, entryX + textWidth, entryY, 0xFF222222);

            int fillWidth = (int) (textWidth * ratio);
            int barColor;
            if (ratio > 0.5f) {
                barColor = 0xFF55FF55; // green
            } else if (ratio > 0.2f) {
                barColor = 0xFFFFFF55; // yellow
            } else {
                barColor = 0xFFFF5555; // red
            }
            if (fillWidth > 0) {
                g.fill(entryX, entryY - BAR_HEIGHT, entryX + fillWidth, entryY, barColor);
            }

        }
    }
}
