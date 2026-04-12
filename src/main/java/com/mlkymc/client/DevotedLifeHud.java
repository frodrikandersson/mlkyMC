package com.mlkymc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Shows a Devoted Life indicator in the bottom-left corner.
 * Custom PNG icon + timer text.
 * Synced via [MLKYMC_DEVOTED:READY|seconds|NONE] chat messages.
 */
public class DevotedLifeHud {

    private static final Identifier ICON_ACTIVE = Identifier.parse("mlkymc:textures/gui/devoted_life_active.png");
    private static final Identifier ICON_COOLDOWN = Identifier.parse("mlkymc:textures/gui/devoted_life_cooldown.png");

    // State: -1 = hidden, 0 = ready, >0 = cooldown seconds remaining
    private static int devotedState = -1;

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String msg = event.getMessage().getString();
        if (msg.startsWith("[MLKYMC_DEVOTED:") && msg.endsWith("]")) {
            String value = msg.substring(16, msg.length() - 1);
            if (value.equals("READY")) {
                devotedState = 0;
            } else if (value.equals("NONE")) {
                devotedState = -1;
            } else {
                try {
                    devotedState = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    devotedState = -1;
                }
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (net.minecraft.client.Minecraft.getInstance().options.hideGui) return;
        if (devotedState == -1) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Bottom-left corner, next to mic icon
        int iconX = 34;
        int iconY = screenH - 32; // 4px up
        int iconSize = 16;

        boolean ready = devotedState == 0;

        // Draw the PNG icon
        Identifier icon = ready ? ICON_ACTIVE : ICON_COOLDOWN;
        g.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0f, 0f, iconSize, iconSize, iconSize, iconSize);

        // Only show timer text when on cooldown, centered underneath the icon
        if (!ready) {
            String statusText;
            if (devotedState >= 60) {
                int mins = (devotedState + 59) / 60;
                statusText = mins + "m";
            } else {
                statusText = devotedState + "s";
            }

            int textW = mc.font.width(statusText);
            int textX = iconX + (iconSize - textW) / 2; // Center under icon
            int textY = iconY + iconSize + 2; // Below icon
            g.drawString(mc.font, statusText, textX, textY, 0xFFFF5555, true);
        }
    }
}
