package com.mlkymc.client;

import com.mlkymc.classes.ClassType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Renders a charge bar over the XP bar area when the player is holding
 * the Primary Power key. Works like the horse jump bar.
 *
 * Only visible for classes that use hold-to-charge (Adventurer Weapon Dash).
 */
public class PowerChargeHud {

    @SubscribeEvent
    public void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (!ModKeybinds.isPrimaryHeld()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Only show for classes that use charge (Adventurer)
        ClassType chosen = ClientClassData.getChosenClass();
        if (chosen != ClassType.ADVENTURER) return;

        float charge = ModKeybinds.getChargePercent(); // 0.0 to 1.0
        if (charge <= 0) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // XP bar position: centered, 182px wide, at bottom of screen
        int barWidth = 182;
        int barHeight = 5;
        int barX = (screenW - barWidth) / 2;
        int barY = screenH - 32 + 3;

        // Background (dark)
        g.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF000000);

        // Charge fill - color transitions from blue to yellow to red
        int fillWidth = (int) (barWidth * charge);
        int color;
        if (charge < 0.5f) {
            // Blue to yellow (0-50%)
            int r = (int) (charge * 2 * 255);
            int gr = (int) (charge * 2 * 255);
            color = 0xFF000000 | (r << 16) | (gr << 8) | 0xFF;
        } else {
            // Yellow to green (50-100%)
            int r = (int) ((1.0f - (charge - 0.5f) * 2) * 255);
            color = 0xFF000000 | (r << 16) | 0xFF00;
        }

        g.fill(barX, barY, barX + fillWidth, barY + barHeight, color);

        // Border
        g.fill(barX, barY, barX + barWidth, barY + 1, 0xFF333333); // Top
        g.fill(barX, barY + barHeight - 1, barX + barWidth, barY + barHeight, 0xFF333333); // Bottom

        // "CHARGE" text above bar
        String text = (int)(charge * 100) + "%";
        int textWidth = mc.font.width(text);
        g.drawString(mc.font, text, (screenW - textWidth) / 2, barY - 10, 0xFFFFFF, true);
    }
}
