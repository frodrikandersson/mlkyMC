package com.mlkymc.client;

import com.mlkymc.classes.ClassType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Renders a purple/blue Soul Energy bar over the vanilla XP bar position
 * when the Cleric is in Soul Energy Mode.
 * Synced via [MLKYMC_SOUL:amount:mode] chat messages.
 */
public class SoulEnergyHud {

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String msg = event.getMessage().getString();
        if (msg.startsWith("[MLKYMC_SOUL:") && msg.endsWith("]")) {
            String value = msg.substring(13, msg.length() - 1);
            String[] parts = value.split(":");
            if (parts.length >= 2) {
                try {
                    ClientClassData.setSoulEnergy(Integer.parseInt(parts[0]));
                    ClientClassData.setSoulEnergyMode(parts[1].equals("1"));
                    if (parts.length >= 3) {
                        ClientClassData.setAltarSE(Integer.parseInt(parts[2]));
                    }
                } catch (NumberFormatException ignored) {}
            }
            event.setCanceled(true);
        }
    }

    /**
     * Hide the vanilla XP bar and level number when in Soul Energy Mode.
     */
    @SubscribeEvent
    public void onRenderHudPre(RenderGuiLayerEvent.Pre event) {
        if (SpectralEnergyHud.isGhost()) return; // Ghost HUD takes priority
        if (ClientClassData.getChosenClass() != ClassType.CLERIC) return;
        if (!ClientClassData.isSoulEnergyMode()) return;

        var layerName = event.getName();
        if (layerName.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.EXPERIENCE_LEVEL)
                || layerName.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CONTEXTUAL_INFO_BAR)
                || layerName.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (SpectralEnergyHud.isGhost()) return; // Ghost HUD takes priority
        if (ClientClassData.getChosenClass() != ClassType.CLERIC) return;
        if (!ClientClassData.isSoulEnergyMode()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int barWidth = 182;
        int barHeight = 5;
        int barX = (screenW - barWidth) / 2;
        int barY = screenH - 32 + 3;

        // Background (dark purple)
        g.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF1A0033);

        // SE fill - purple to bright purple gradient
        int se = ClientClassData.getSoulEnergy();
        int fillWidth = (int) (barWidth * (se / 100.0));
        if (fillWidth > 0) {
            // Gradient from deep purple (0%) to bright magenta (100%)
            float ratio = se / 100.0f;
            int r = (int) (100 + ratio * 155); // 100 -> 255
            int gr = (int) (30 + ratio * 50);  // 30 -> 80
            int b = (int) (180 + ratio * 75);  // 180 -> 255
            int fillColor = 0xFF000000 | (r << 16) | (gr << 8) | b;
            g.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);
        }

        // Border
        g.fill(barX - 1, barY, barX, barY + barHeight, 0xFF2A0055);
        g.fill(barX + barWidth, barY, barX + barWidth + 1, barY + barHeight, 0xFF2A0055);
        g.fill(barX - 1, barY - 1, barX + barWidth + 1, barY, 0xFF2A0055);
        g.fill(barX - 1, barY + barHeight, barX + barWidth + 1, barY + barHeight + 1, 0xFF2A0055);

        // Text: "X/100" or "X/100 + altarSE" centered inside the bar
        int altarSE = ClientClassData.getAltarSE();
        String text = altarSE > 0 ? se + " / 100 + " + altarSE : se + "/100";
        int textW = mc.font.width(text);
        int textX = (screenW - textW) / 2;
        int textY = barY - 2; // inside the bar vertically
        g.drawString(mc.font, text, textX, textY, 0xFFFFFFFF, true);
    }
}
