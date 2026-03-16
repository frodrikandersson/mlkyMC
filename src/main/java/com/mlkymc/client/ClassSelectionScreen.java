package com.mlkymc.client;

import com.mlkymc.classes.ClassType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ClassSelectionScreen extends Screen {
    private static final Identifier BACKGROUND =
            Identifier.parse("mlkymc:textures/gui/class_selection_gui.png");

    private static final Identifier[] CLASS_ICONS = {
            Identifier.parse("mlkymc:textures/gui/icon_adventurer.png"),
            Identifier.parse("mlkymc:textures/gui/icon_cleric.png"),
            Identifier.parse("mlkymc:textures/gui/icon_farmhand.png"),
            Identifier.parse("mlkymc:textures/gui/icon_minecrafter.png"),
            Identifier.parse("mlkymc:textures/gui/icon_smith.png"),
    };

    private static final ClassType[] CLASSES = {
            ClassType.ADVENTURER, ClassType.CLERIC, ClassType.FARMHAND,
            ClassType.MINECRAFTER, ClassType.SMITH
    };

    // Each line: {text, color_hex} — no formatting codes, just plain text + color
    private static final String[][][] CLASS_INFO = {
            { // Adventurer
                    {"-- ACTIVE (class only) --", "B4C8DC"},
                    {"Weapon Dash: Hold RC", "AABBCC"},
                    {" to dash forward", "AABBCC"},
                    {"", "AABBCC"},
                    {"-- EFFECTS (class only) --", "B4C8DC"},
                    {"2x EXP Adventurer", "AABBCC"},
                    {"Weapon durability+", "AABBCC"},
                    {"2x hostile mob drops", "AABBCC"},
                    {"Minimap HUD overlay", "AABBCC"},
                    {"", "AABBCC"},
                    {"-- DEBUFF (all players) --", "CC8888"},
                    {"Mob drops -50%", "CC8888"},
                    {"", "AABBCC"},
                    {"-- PASSIVES (all players) --", "B4C8DC"},
                    {"5:  -40%, +2 hp", "99AABB"},
                    {"10: Auto-step, +4 hp", "99AABB"},
                    {"15: -30%, +6 hp", "99AABB"},
                    {"20: Jump 1.5, +8 hp", "99AABB"},
                    {" *Class only* Mob map", "88CC88"},
                    {"25: -20%, +10 hp", "99AABB"},
                    {"30: Wind-up dmg, +12 hp", "99AABB"},
                    {"35: -10%, +14 hp", "99AABB"},
                    {"40: -50% fall, +16 hp", "99AABB"},
                    {"45: 0%, +18 hp", "99AABB"},
                    {"50: Speed+, +20 hp", "99AABB"},
                    {" *Class only* Player map", "88CC88"},
                    {"", "AABBCC"},
                    {"-- CRAFTS (class only) --", "B4C8DC"},
                    {"Compass, Warp Anchor", "AABBCC"},
                    {"Trophies, Grapple", "AABBCC"},
                    {"Waystone Shard", "AABBCC"},
            },
            { // Cleric
                    {"-- ACTIVE (class only) --", "B4C8DC"},
                    {"Healing Pulse: RC", "AABBCC"},
                    {" heals nearby, costs", "AABBCC"},
                    {" EXP + 1 Milky Star", "AABBCC"},
                    {"Resurrection: RC at", "AABBCC"},
                    {" death spot, 10min CD", "AABBCC"},
                    {"", "AABBCC"},
                    {"-- EFFECTS (class only) --", "B4C8DC"},
                    {"2x EXP Cleric", "AABBCC"},
                    {"Cheaper enchanting", "AABBCC"},
                    {"Potions stronger +1", "AABBCC"},
                    {"Save enchant levels", "AABBCC"},
                    {"", "AABBCC"},
                    {"-- DEBUFF (all players) --", "CC8888"},
                    {"EXP gain -50%", "CC8888"},
                    {"", "AABBCC"},
                    {"-- PASSIVES (all players) --", "B4C8DC"},
                    {"5:  -40%", "99AABB"},
                    {"10: Heal radius+", "99AABB"},
                    {"15: -30%", "99AABB"},
                    {"20: Potions +25% dur", "99AABB"},
                    {"25: -20%", "99AABB"},
                    {"30: Potion share", "99AABB"},
                    {"35: -10%", "99AABB"},
                    {"40: 25% save lapis", "99AABB"},
                    {"45: 0%", "99AABB"},
                    {"50: Enchant max +1", "99AABB"},
                    {" *Class only* Res CD/2", "88CC88"},
            },
            { // Farmhand
                    {"-- ACTIVE (class only) --", "B4C8DC"},
                    {"Nature's Call: AoE", "AABBCC"},
                    {" bone meal crops", "AABBCC"},
                    {"Animal Whisperer:", "AABBCC"},
                    {" see/charm mobs", "AABBCC"},
                    {"", "AABBCC"},
                    {"-- EFFECTS (class only) --", "B4C8DC"},
                    {"2x EXP Farmhand", "AABBCC"},
                    {"Faster crop growth", "AABBCC"},
                    {"Twin breeding chance", "AABBCC"},
                    {"Fish luck/lure +1", "AABBCC"},
                    {"", "AABBCC"},
                    {"-- DEBUFF (all players) --", "CC8888"},
                    {"Farm/fish drops -50%", "CC8888"},
                    {"", "AABBCC"},
                    {"-- PASSIVES (all players) --", "B4C8DC"},
                    {"5:  -40%", "99AABB"},
                    {"10: Auto-replant", "99AABB"},
                    {"15: -30%", "99AABB"},
                    {"20: Animals +25% grow", "99AABB"},
                    {"25: -20%", "99AABB"},
                    {"30: 2x shear/seed/tick", "99AABB"},
                    {"35: -10%", "99AABB"},
                    {" *Class only* Food buff", "88CC88"},
                    {"40: Rare fish drops", "99AABB"},
                    {"45: 0%", "99AABB"},
                    {"50: 4x crop tick", "99AABB"},
                    {" *Class only* Food all", "88CC88"},
            },
            { // MineCrafter
                    {"-- ACTIVE (class only) --", "B4C8DC"},
                    {"Vein Mine: Shift+mine", "AABBCC"},
                    {" connected ore", "AABBCC"},
                    {"Timber: Shift+chop", "AABBCC"},
                    {"Auto-Smelt: Toggle", "AABBCC"},
                    {"", "AABBCC"},
                    {"-- EFFECTS (class only) --", "B4C8DC"},
                    {"2x EXP MineCrafter", "AABBCC"},
                    {"Return ingredients", "AABBCC"},
                    {"Fortune +1 built-in", "AABBCC"},
                    {"2x pick/axe durabil.", "AABBCC"},
                    {"", "AABBCC"},
                    {"-- DEBUFF (all players) --", "CC8888"},
                    {"Mine/wood drops -50%", "CC8888"},
                    {"", "AABBCC"},
                    {"-- PASSIVES (all players) --", "B4C8DC"},
                    {"5:  -40%", "99AABB"},
                    {"10: Haste I mining", "99AABB"},
                    {"15: -30%", "99AABB"},
                    {"20: XP from stone", "99AABB"},
                    {" *Class only* +Haste+Fort", "88CC88"},
                    {"25: -20%", "99AABB"},
                    {"30: Stars in ore", "99AABB"},
                    {" *Class only* 2x craft", "88CC88"},
                    {"35: -10%", "99AABB"},
                    {"40: Craft +1 output", "99AABB"},
                    {" *Class only* +Haste+Fort", "88CC88"},
                    {"45: 0%", "99AABB"},
                    {"50: More stars", "99AABB"},
                    {" *Class only* VeinMine 2x", "88CC88"},
            },
            { // Smith
                    {"-- ACTIVE (class only) --", "B4C8DC"},
                    {"Forge Heat: Shift+RC", "AABBCC"},
                    {" furnace = lava fuel", "AABBCC"},
                    {"Emergency Repair: RC", "AABBCC"},
                    {" 25% dur, costs iron", "AABBCC"},
                    {"Tempered Body: Fire", "AABBCC"},
                    {" resist, extinguish", "AABBCC"},
                    {"", "AABBCC"},
                    {"-- EFFECTS (class only) --", "B4C8DC"},
                    {"2x EXP Smith", "AABBCC"},
                    {"Anvil 50% cheaper", "AABBCC"},
                    {"Faster smelting", "AABBCC"},
                    {"Less armor dur loss", "AABBCC"},
                    {"", "AABBCC"},
                    {"-- DEBUFF (all players) --", "CC8888"},
                    {"Smelt speed -50%", "CC8888"},
                    {"", "AABBCC"},
                    {"-- PASSIVES (all players) --", "B4C8DC"},
                    {"5:  -40%", "99AABB"},
                    {"10: 25% save fuel", "99AABB"},
                    {" *Class only* Unbreak aura", "88CC88"},
                    {"15: -30%", "99AABB"},
                    {"20: Anvil +10% dur", "99AABB"},
                    {"25: -20%", "99AABB"},
                    {"30: +1 Resistance", "99AABB"},
                    {" *Class only* Anvil safe", "88CC88"},
                    {"35: -10%", "99AABB"},
                    {"40: Cap 40->55 lvl", "99AABB"},
                    {" *Class only* +Haste+Fort", "88CC88"},
                    {"45: 0%", "99AABB"},
                    {"50: +1 Res, water+", "99AABB"},
                    {" *Class only* Merge ench", "88CC88"},
            },
    };

    private int guiLeft, guiTop;
    private static final int GUI_W = 256;
    private static final int GUI_H = 256;

    // Layout constants matching the texture
    private static final int SLOT_X = 12;
    private static final int SLOT_W = 64;
    private static final int SLOT_H = 35;
    private static final int SLOT_GAP = 2;
    private static final int SLOTS_Y = 38;
    private static final int ICON_SIZE = 10;

    private static final int INFO_X = 86;
    private static final int INFO_W = 155;
    private static final int INFO_Y = 38;
    private static final int INFO_BOTTOM = 224;

    private int selectedClass = -1;
    private int scrollOffset = 0;
    private boolean draggingScrollbar = false;
    private Button confirmButton;

    public ClassSelectionScreen() {
        super(Component.literal("Choose Your Class"));
    }

    @Override
    protected void init() {
        guiLeft = (this.width - GUI_W) / 2;
        guiTop = (this.height - GUI_H) / 2;

        confirmButton = Button.builder(Component.literal("Confirm"), btn -> onConfirm())
                .bounds(guiLeft + 176, guiTop + 229, 66, 16)
                .build();
        confirmButton.active = false;
        addRenderableWidget(confirmButton);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Background texture
        g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                guiLeft, guiTop, 0, 0, GUI_W, GUI_H, GUI_W, GUI_H, GUI_W, GUI_H);

        // Title bar text
        g.drawCenteredString(this.font, Component.literal("Choose Your Class").withColor(0xE0F0FA),
                guiLeft + GUI_W / 2, guiTop + 13, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.literal("This choice is permanent!").withColor(0xFF5555),
                guiLeft + GUI_W / 2, guiTop + 23, 0xFF5555);

        // --- Left panel: 5 class slots ---
        for (int i = 0; i < 5; i++) {
            int sy = guiTop + SLOTS_Y + i * (SLOT_H + SLOT_GAP);
            int sx = guiLeft + SLOT_X;
            ClassType ct = CLASSES[i];

            // Selection/hover highlight
            if (i == selectedClass) {
                g.fill(sx + 1, sy + 1, sx + SLOT_W - 1, sy + SLOT_H - 1, 0x40FFFFFF);
            } else if (mouseX >= sx && mouseX <= sx + SLOT_W && mouseY >= sy && mouseY <= sy + SLOT_H) {
                g.fill(sx + 1, sy + 1, sx + SLOT_W - 1, sy + SLOT_H - 1, 0x20FFFFFF);
            }

            // Icon — positioned inside the circle dot, small
            int iconX = sx + 10;
            int iconY = sy + (SLOT_H - ICON_SIZE) / 2 - 1;
            g.blit(RenderPipelines.GUI_TEXTURED, CLASS_ICONS[i],
                    iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, 32, 32, 32, 32);

            // Class name to the right of the circle
            g.drawString(this.font, Component.literal(ct.getDisplayName()).withColor(ct.getColor()),
                    sx + 22, sy + (SLOT_H - 8) / 2, 0xFFFFFF, true);
        }

        // --- Right panel: class info ---
        // Scrollbar track position (matching the groove in the PNG)
        int scrollTrackX = guiLeft + 241;
        int scrollTrackW = 5;
        int scrollTrackTop = guiTop + 38;
        int scrollTrackBottom = guiTop + 218;
        int scrollTrackH = scrollTrackBottom - scrollTrackTop;

        // Info text area (clipped to panel)
        int infoX = guiLeft + INFO_X;
        int infoY = guiTop + INFO_Y + 2;
        int infoRight = scrollTrackX - 2;
        int infoBottom = guiTop + INFO_BOTTOM;
        int maxLines = (infoBottom - infoY - 14) / 10;

        if (selectedClass >= 0) {
            ClassType ct = CLASSES[selectedClass];

            // Enable scissor to clip text inside the right panel
            g.enableScissor(infoX, infoY, infoRight, infoBottom);

            // Header
            g.drawString(this.font, Component.literal(ct.getDisplayName()).withColor(ct.getColor()),
                    infoX, infoY, 0xFFFFFF, true);
            g.drawString(this.font, Component.literal(ct.getDescription()).withColor(0x8098AA),
                    infoX, infoY + 11, 0x8098AA, false);

            // Scrollable info lines
            String[][] lines = CLASS_INFO[selectedClass];
            int contentY = infoY + 24;

            for (int i = scrollOffset; i < lines.length && i < scrollOffset + maxLines; i++) {
                int color = Integer.parseInt(lines[i][1], 16) | 0xFF000000;
                g.drawString(this.font, Component.literal(lines[i][0]).withColor(color),
                        infoX, contentY, color, false);
                contentY += 10;
            }

            g.disableScissor();

            // Scrollbar thumb
            if (lines.length > maxLines) {
                int thumbH = Math.max(10, scrollTrackH * maxLines / lines.length);
                int maxScroll = Math.max(1, lines.length - maxLines);
                int thumbY = scrollTrackTop + (scrollTrackH - thumbH) * scrollOffset / maxScroll;
                g.fill(scrollTrackX, thumbY, scrollTrackX + scrollTrackW, thumbY + thumbH, 0x90AAAAAA);
            }
        } else {
            // No class selected — instructions
            g.drawCenteredString(this.font, Component.literal("Select a class on the left").withColor(0x778899),
                    guiLeft + INFO_X + INFO_W / 2, guiTop + 80, 0x778899);
            g.drawCenteredString(this.font, Component.literal("to view its details.").withColor(0x778899),
                    guiLeft + INFO_X + INFO_W / 2, guiTop + 94, 0x778899);

            g.drawCenteredString(this.font, Component.literal("You can only choose ONCE.").withColor(0xFF5555),
                    guiLeft + INFO_X + INFO_W / 2, guiTop + 120, 0xFF5555);

            g.drawCenteredString(this.font, Component.literal("All players level all professions,").withColor(0x778899),
                    guiLeft + INFO_X + INFO_W / 2, guiTop + 145, 0x778899);
            g.drawCenteredString(this.font, Component.literal("but only your chosen class gets").withColor(0x778899),
                    guiLeft + INFO_X + INFO_W / 2, guiTop + 157, 0x778899);
            g.drawCenteredString(this.font, Component.literal("Active Skills & Special Effects.").withColor(0x778899),
                    guiLeft + INFO_X + INFO_W / 2, guiTop + 169, 0x778899);

            g.drawCenteredString(this.font, Component.literal("Ask an admin to reset if needed.").withColor(0x556677),
                    guiLeft + INFO_X + INFO_W / 2, guiTop + 195, 0x556677);
        }

        // Bottom bar
        g.drawString(this.font, Component.literal("This cannot").withColor(0xFF5555),
                guiLeft + 14, guiTop + 231, 0xFF5555, true);
        g.drawString(this.font, Component.literal("be undone!").withColor(0xCC4444),
                guiLeft + 14, guiTop + 241, 0xCC4444, true);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private int getMaxLines() {
        return (guiTop + INFO_BOTTOM - (guiTop + INFO_Y + 2) - 14) / 10;
    }

    private int getMaxScroll() {
        if (selectedClass < 0) return 0;
        return Math.max(0, CLASS_INFO[selectedClass].length - getMaxLines());
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean consumed) {
        if (!consumed && this.minecraft != null) {
            double mx = this.minecraft.mouseHandler.xpos() * this.width / this.minecraft.getWindow().getScreenWidth();
            double my = this.minecraft.mouseHandler.ypos() * this.height / this.minecraft.getWindow().getScreenHeight();

            // Check scrollbar click
            int scrollTrackX = guiLeft + 241;
            int scrollTrackTop = guiTop + 38;
            int scrollTrackBottom = guiTop + 218;
            if (selectedClass >= 0 && mx >= scrollTrackX && mx <= scrollTrackX + 8
                    && my >= scrollTrackTop && my <= scrollTrackBottom) {
                draggingScrollbar = true;
                updateScrollFromMouse(my, scrollTrackTop, scrollTrackBottom);
                return true;
            }

            // Check class slot clicks
            for (int i = 0; i < 5; i++) {
                int sy = guiTop + SLOTS_Y + i * (SLOT_H + SLOT_GAP);
                int sx = guiLeft + SLOT_X;
                if (mx >= sx && mx <= sx + SLOT_W && my >= sy && my <= sy + SLOT_H) {
                    selectedClass = i;
                    scrollOffset = 0;
                    confirmButton.active = true;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, consumed);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar && selectedClass >= 0 && this.minecraft != null) {
            double my = this.minecraft.mouseHandler.ypos() * this.height / this.minecraft.getWindow().getScreenHeight();
            int scrollTrackTop = guiTop + 38;
            int scrollTrackBottom = guiTop + 218;
            updateScrollFromMouse(my, scrollTrackTop, scrollTrackBottom);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    private void updateScrollFromMouse(double mouseY, int trackTop, int trackBottom) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) return;
        double ratio = (mouseY - trackTop) / (trackBottom - trackTop);
        ratio = Math.max(0, Math.min(1, ratio));
        scrollOffset = (int) Math.round(ratio * maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selectedClass >= 0) {
            int maxScroll = getMaxScroll();
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void onConfirm() {
        if (selectedClass < 0) return;
        ClassType chosen = CLASSES[selectedClass];
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand("mlkymc class select " + chosen.name().toLowerCase());
            ClientClassData.setChosenClass(chosen);
        }
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
