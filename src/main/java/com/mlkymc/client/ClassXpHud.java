package com.mlkymc.client;

import com.mlkymc.classes.ClassType;
import com.mlkymc.classes.ProfessionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Displays the chosen class icon between HP and food bars.
 * Uses 17 pre-drawn frames (0-16) per class that transition from
 * grayscale to full color from bottom to top based on XP progress.
 * Images are 32x32, rendered slightly smaller on screen.
 */
public class ClassXpHud {

    private static final int RENDER_SIZE = 9;
    private static final int MAX_FRAME = 8;

    @SubscribeEvent
    public void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (net.minecraft.client.Minecraft.getInstance().options.hideGui) return;
        if (!ClientClassData.hasChosenClass()) return;
        if (SpectralEnergyHud.isGhost()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ClassType chosenClass = ClientClassData.getChosenClass();
        ProfessionType prof = chosenClass.getMatchingProfession();
        if (prof == null) return;

        int level = ClientClassData.getLevel(prof);
        int xp = ClientClassData.getXp(prof);
        int xpNeeded = ClientClassData.getXpForNextLevel(level);

        // Calculate frame index (0 = fully dark, 8 = fully colored, 16 = max level special)
        int frame;
        if (level >= 50) {
            frame = 16; // max level = special fully-colored frame
        } else if (xpNeeded <= 0) {
            frame = MAX_FRAME;
        } else {
            frame = (int) ((float) xp / xpNeeded * MAX_FRAME);
            frame = Math.max(0, Math.min(MAX_FRAME, frame));
        }

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Position: centered above the XP level number
        int x = (screenW - RENDER_SIZE) / 2;
        int y = screenH - 49;

        String iconName = switch (chosenClass) {
            case ADVENTURER -> "icon_adventurer";
            case CLERIC -> "icon_cleric";
            case FARMHAND -> "icon_farmhand";
            case MINECRAFTER -> "icon_minecrafter";
            case SMITH -> "icon_smith";
            default -> null;
        };
        if (iconName == null) return;

        Identifier icon = Identifier.parse("mlkymc:textures/gui/" + iconName + "_" + frame + ".png");
        // Scale full 32x32 texture down to RENDER_SIZE by setting textureWidth/Height = RENDER_SIZE
        // This maps the full UV range (0-1) to the render area
        g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0f, 0f, RENDER_SIZE, RENDER_SIZE, RENDER_SIZE, RENDER_SIZE);
    }
}
