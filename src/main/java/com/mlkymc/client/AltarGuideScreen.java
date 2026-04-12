package com.mlkymc.client;

import com.mlkymc.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Visual guide for building the Soul Altar multiblock structure.
 * Shows a 3x3 grid of item icons for each layer (bottom and top).
 */
public class AltarGuideScreen extends Screen {

    private static final int GRID_SIZE = 3;
    private static final int CELL_SIZE = 32;
    private static final int GRID_PADDING = 4;

    private int currentLayer = 0; // 0 = bottom, 1 = top
    private int guiLeft, guiTop;
    private static final int GUI_WIDTH = 200;
    private static final int GUI_HEIGHT = 270;

    // Bottom layer: 8 Soulstone Brick edges + 1 Conduit Core center
    private static final ItemStack[][] BOTTOM_LAYER = new ItemStack[3][3];
    // Top layer: 4 Soul Pillars at corners + Capstone center + 4 air
    private static final ItemStack[][] TOP_LAYER = new ItemStack[3][3];

    static {
        ItemStack soulstone = new ItemStack(ModBlocks.SOULSTONE_BRICK_ITEM.get());
        ItemStack conduit = new ItemStack(ModBlocks.CONDUIT_CORE_ITEM.get());
        ItemStack pillar = new ItemStack(ModBlocks.SOUL_PILLAR_ITEM.get());
        ItemStack capstone = new ItemStack(ModBlocks.SOUL_ALTAR_CAPSTONE_ITEM.get());
        ItemStack air = ItemStack.EMPTY;

        // Bottom: all soulstone except center = conduit
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                BOTTOM_LAYER[r][c] = soulstone;
        BOTTOM_LAYER[1][1] = conduit;

        // Top: corners = pillar, center = capstone, edges = air
        TOP_LAYER[0][0] = pillar;  TOP_LAYER[0][1] = air;      TOP_LAYER[0][2] = pillar;
        TOP_LAYER[1][0] = air;     TOP_LAYER[1][1] = capstone;  TOP_LAYER[1][2] = air;
        TOP_LAYER[2][0] = pillar;  TOP_LAYER[2][1] = air;       TOP_LAYER[2][2] = pillar;
    }

    public AltarGuideScreen() {
        super(Component.literal("Soul Altar Building Guide"));
    }

    @Override
    protected void init() {
        guiLeft = (width - GUI_WIDTH) / 2;
        guiTop = (height - GUI_HEIGHT) / 2;

        // Layer tabs
        addRenderableWidget(Button.builder(Component.literal("Bottom Layer"), b -> currentLayer = 0)
                .bounds(guiLeft + 5, guiTop + 25, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Top Layer"), b -> currentLayer = 1)
                .bounds(guiLeft + 105, guiTop + 25, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Background
        g.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xDD111111);
        // Border
        g.fill(guiLeft - 1, guiTop - 1, guiLeft + GUI_WIDTH + 1, guiTop, 0xFF5544AA);
        g.fill(guiLeft - 1, guiTop + GUI_HEIGHT, guiLeft + GUI_WIDTH + 1, guiTop + GUI_HEIGHT + 1, 0xFF5544AA);
        g.fill(guiLeft - 1, guiTop, guiLeft, guiTop + GUI_HEIGHT, 0xFF5544AA);
        g.fill(guiLeft + GUI_WIDTH, guiTop, guiLeft + GUI_WIDTH + 1, guiTop + GUI_HEIGHT, 0xFF5544AA);

        // Title
        g.drawCenteredString(font, Component.literal("Soul Altar Guide").withColor(0xAA55FF),
                guiLeft + GUI_WIDTH / 2, guiTop + 8, 0xFFFFFF);

        // Highlight active tab button
        int activeTabX = currentLayer == 0 ? guiLeft + 5 : guiLeft + 105;
        g.fill(activeTabX - 1, guiTop + 24, activeTabX + 91, guiTop + 46, 0x4455FF55);

        // Active layer indicator
        String layerName = currentLayer == 0 ? "Bottom Layer (Place First)" : "Top Layer (Place on Top)";
        g.drawCenteredString(font, Component.literal(layerName).withColor(0xFFFF55),
                guiLeft + GUI_WIDTH / 2, guiTop + 50, 0xFFFFFF);

        // Draw 3x3 grid
        ItemStack[][] layer = currentLayer == 0 ? BOTTOM_LAYER : TOP_LAYER;
        int gridStartX = guiLeft + (GUI_WIDTH - (GRID_SIZE * (CELL_SIZE + GRID_PADDING) - GRID_PADDING)) / 2;
        int gridStartY = guiTop + 65;

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int cx = gridStartX + col * (CELL_SIZE + GRID_PADDING);
                int cy = gridStartY + row * (CELL_SIZE + GRID_PADDING);

                // Cell background
                ItemStack item = layer[row][col];
                if (item.isEmpty()) {
                    // Air slot - dark with X
                    g.fill(cx, cy, cx + CELL_SIZE, cy + CELL_SIZE, 0xFF222233);
                    g.drawCenteredString(font, "Air", cx + CELL_SIZE / 2, cy + CELL_SIZE / 2 - 4, 0x555566);
                } else {
                    // Block slot
                    g.fill(cx, cy, cx + CELL_SIZE, cy + CELL_SIZE, 0xFF333344);
                    // Render the item icon centered in the cell
                    int itemX = cx + (CELL_SIZE - 16) / 2;
                    int itemY = cy + (CELL_SIZE - 16) / 2;
                    g.renderItem(item, itemX, itemY);
                }

                // Border for cell
                g.fill(cx - 1, cy - 1, cx + CELL_SIZE + 1, cy, 0xFF555577);
                g.fill(cx - 1, cy + CELL_SIZE, cx + CELL_SIZE + 1, cy + CELL_SIZE + 1, 0xFF555577);
                g.fill(cx - 1, cy, cx, cy + CELL_SIZE, 0xFF555577);
                g.fill(cx + CELL_SIZE, cy, cx + CELL_SIZE + 1, cy + CELL_SIZE, 0xFF555577);

                // Tooltip on hover
                if (mouseX >= cx && mouseX < cx + CELL_SIZE && mouseY >= cy && mouseY < cy + CELL_SIZE) {
                    g.fill(cx, cy, cx + CELL_SIZE, cy + CELL_SIZE, 0x44FFFFFF);
                    if (!item.isEmpty()) {
                        g.setTooltipForNextFrame(font, item, mouseX, mouseY);
                    }
                }
            }
        }

        // Materials needed
        int matY = gridStartY + GRID_SIZE * (CELL_SIZE + GRID_PADDING) + 10;
        g.drawString(font, Component.literal("Materials Needed:").withColor(0xFFAA00),
                guiLeft + 10, matY, 0xFFFFFF);
        matY += 12;

        g.renderItem(new ItemStack(ModBlocks.SOULSTONE_BRICK_ITEM.get()), guiLeft + 10, matY);
        g.drawString(font, "x8 Soulstone Brick", guiLeft + 30, matY + 4, 0xAABBCC);
        matY += 18;

        g.renderItem(new ItemStack(ModBlocks.CONDUIT_CORE_ITEM.get()), guiLeft + 10, matY);
        g.drawString(font, "x1 Conduit Core", guiLeft + 30, matY + 4, 0xAABBCC);
        matY += 18;

        g.renderItem(new ItemStack(ModBlocks.SOUL_PILLAR_ITEM.get()), guiLeft + 10, matY);
        g.drawString(font, "x4 Soul Pillar", guiLeft + 30, matY + 4, 0xAABBCC);
        matY += 18;

        g.renderItem(new ItemStack(ModBlocks.SOUL_ALTAR_CAPSTONE_ITEM.get()), guiLeft + 10, matY);
        g.drawString(font, "x1 Capstone (center top)", guiLeft + 30, matY + 4, 0xAABBCC);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
