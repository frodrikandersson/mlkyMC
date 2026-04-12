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
        if (net.minecraft.client.Minecraft.getInstance().options.hideGui) return;
        var layerName = event.getName();

        // Cancel vanilla contextual bar (XP bar) — we render our own. BUT: the
        // contextual bar layer also renders the horse jump bar and the camel dash bar
        // when the player is riding. If we cancel it while mounted, those bars vanish.
        // Only suppress while NOT riding a living entity with a jump bar.
        if (layerName.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CONTEXTUAL_INFO_BAR)
                || layerName.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND)) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null
                    && mc.player.getVehicle() instanceof net.minecraft.world.entity.PlayerRideableJumping) {
                // Mounted on a jumpable entity (horse, camel, etc.) — let vanilla
                // render the jump bar instead of our custom XP bar.
                return;
            }
            event.setCanceled(true);
            return;
        }

        // Always cancel vanilla XP level text — we render our own (repositioned)
        if (layerName.equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.EXPERIENCE_LEVEL)) {
            event.setCanceled(true);
        }
    }

    private static final net.minecraft.resources.Identifier XP_BAR_BG =
            net.minecraft.resources.Identifier.withDefaultNamespace("hud/experience_bar_background");
    private static final net.minecraft.resources.Identifier XP_BAR_FILL =
            net.minecraft.resources.Identifier.withDefaultNamespace("hud/experience_bar_progress");

    /**
     * Render custom XP bar + level text (without locator dots).
     */
    @SubscribeEvent
    public void onRenderXpBar(RenderGuiLayerEvent.Post event) {
        if (net.minecraft.client.Minecraft.getInstance().options.hideGui) return;
        // Render after health/food/armor bars on a layer that always fires
        if (!event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.SELECTED_ITEM_NAME)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.gameMode.hasExperience()) return;
        if (SpectralEnergyHud.isGhost()) return;
        if (ClientClassData.getChosenClass() == ClassType.CLERIC && ClientClassData.isSoulEnergyMode()) return;

        // Don't draw our custom XP bar while riding a jumpable entity — vanilla's
        // contextual info bar is rendering the horse jump / camel dash bar in the
        // same screen position and our bar would paint over it.
        if (mc.player.getVehicle() instanceof net.minecraft.world.entity.PlayerRideableJumping) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int barX = (screenW - 182) / 2;
        int barY = screenH - 24 - 5;

        int xpNeeded = mc.player.getXpNeededForNextLevel();
        if (xpNeeded > 0) {
            int filled = (int) (mc.player.experienceProgress * 183.0f);
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    XP_BAR_BG, barX, barY, 182, 5);
            if (filled > 0) {
                g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                        XP_BAR_FILL, 182, 5, 0, 0, barX, barY, filled, 5);
            }
        }

        // Render XP level number (repositioned 3px higher than vanilla)
        int level = mc.player.experienceLevel;
        if (level > 0) {
            net.minecraft.network.chat.Component levelComp =
                    net.minecraft.network.chat.Component.translatable("gui.experience.level", level);
            String levelStr = levelComp.getString();
            int textW = mc.font.width(levelStr);
            int textX = (screenW - textW) / 2;
            int textY = screenH - 24 - 5 - 9; // same as vanilla
            // Draw with black outline using drawString with shadow-like offsets
            g.drawString(mc.font, levelStr, textX + 1, textY, 0xFF000000, false);
            g.drawString(mc.font, levelStr, textX - 1, textY, 0xFF000000, false);
            g.drawString(mc.font, levelStr, textX, textY + 1, 0xFF000000, false);
            g.drawString(mc.font, levelStr, textX, textY - 1, 0xFF000000, false);
            g.drawString(mc.font, levelStr, textX, textY, 0xFF80FF20, false); // green text
        }
    }

    /**
     * Render Soul Energy bar for Clerics in SE mode.
     */
    @SubscribeEvent
    public void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (net.minecraft.client.Minecraft.getInstance().options.hideGui) return;
        if (SpectralEnergyHud.isGhost()) return;
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
