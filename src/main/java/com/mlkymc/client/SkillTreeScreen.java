package com.mlkymc.client;

import com.mlkymc.classes.ClassType;
import com.mlkymc.classes.ProfessionType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class SkillTreeScreen extends Screen {
    private static final Identifier BACKGROUND =
            Identifier.parse("mlkymc:textures/gui/skill_tree_gui.png");

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

    private static final ProfessionType[] PROFS = ProfessionType.values();

    // Each passive unlocks at level (index+1)*5: 5, 10, 15, 20, 25, 30, 35, 40, 45, 50
    private static final String[][] PASSIVES = {
            {"Debuff -40%, +2 hp", "Auto-step, +4 hp",
             "Debuff -30%, +6 hp", "Jump 1.5, +8 hp",
             "Debuff -20%, +10 hp", "Wind-up dmg, +12 hp",
             "Debuff -10%, +14 hp", "-50% fall, +16 hp",
             "No debuff, +18 hp", "Speed+, +20 hp"},
            {"EXP debuff -40%", "Heal radius+",
             "EXP debuff -30%", "Potions +25% dur",
             "EXP debuff -20%", "Potion share",
             "EXP debuff -10%", "25% save lapis",
             "No EXP debuff", "Enchant max +1"},
            {"Debuff -40%", "Auto-replant",
             "Debuff -30%", "Breed +25%, fish+1*",
             "Debuff -20%", "2x shear/seed, 2x tick",
             "Debuff -10%", "Rare fish, fish+2*",
             "No debuff", "4x crop tick"},
            {"Debuff -40%", "Haste I, +1 reach",
             "Debuff -30%", "XP from stone",
             "Debuff -20%", "Stars in ore",
             "Debuff -10%", "Craft +1, +2 reach*",
             "No debuff", "More stars"},
            {"Debuff -40%", "25% save fuel",
             "Debuff -30%", "Anvil +10% dur",
             "Debuff -20%", "+1 Resistance",
             "Debuff -10%", "Cap 40->55",
             "No debuff", "+1 Res, water+"},
    };

    // Combined active + effects info per class for bottom panel
    private static final String[][] CLASS_DETAILS = {
            {"Active: Weapon Dash (RMB+sword)", "Active: Wind-up (sprint+hit)", "2x EXP, 2x mob drops", "Weapon dur+, Minimap (M)"},
            {"Active: Heal Pulse (RMB+star)", "Active: Resurrect (RMBgrave)", "2x EXP, Cheaper enchant", "Potions +1, Save levels"},
            {"Active: Nature's Call (RMB+fert)", "Active: Whisperer (Shift+RMB)", "2x EXP, Faster crops", "Twin breed, Fish luck+1"},
            {"Active: Vein Mine (Shift+mine)", "Active: Timber (Shift+chop)", "Active: Auto-Smelt (Shift+RMB)", "2x EXP, Fortune+1, 2x dur"},
            {"Active: Forge Heat (Shift+RMB)", "Active: Repair (RMB+iron)", "Active: Tempered Body (RMBfire)", "2x EXP, Anvil 50%, Armor+"},
    };

    private static final int GUI_W = 256;
    private static final int GUI_H = 256;
    private int guiLeft, guiTop;
    private int selectedTab = 0;
    private int scrollOffset = 0;
    private int descScrollOffset = 0;

    public SkillTreeScreen() {
        super(Component.literal("Skill Tree"));
    }

    @Override
    protected void init() {
        guiLeft = (this.width - GUI_W) / 2;
        guiTop = (this.height - GUI_H) / 2;

        ClassType chosen = ClientClassData.getChosenClass();
        for (int i = 0; i < CLASSES.length; i++) {
            if (CLASSES[i] == chosen) { selectedTab = i; break; }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                guiLeft, guiTop, 0, 0, GUI_W, GUI_H, GUI_W, GUI_H, GUI_W, GUI_H);

        // Title
        drawText(g, "Skill Tree", guiLeft + GUI_W / 2 - 20, guiTop + 13, 0xE0F0FA);

        // --- 5 Tabs: icons only, centered ---
        int tabW = 44, tabGap = 3;
        int tabsTotal = 5 * tabW + 4 * tabGap;
        int tabsX = guiLeft + (GUI_W - tabsTotal) / 2;
        int tabY = guiTop + 32, tabH = 14;

        for (int i = 0; i < 5; i++) {
            int tx = tabsX + i * (tabW + tabGap);

            if (i == selectedTab) {
                g.fill(tx + 1, tabY + 1, tx + tabW - 1, tabY + tabH - 1, 0x40FFFFFF);
            } else if (mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + tabH) {
                g.fill(tx + 1, tabY + 1, tx + tabW - 1, tabY + tabH - 1, 0x20FFFFFF);
            }

            // Icon centered in tab
            int iconSize = 10;
            int iconX = tx + (tabW - iconSize) / 2;
            int iconY = tabY + (tabH - iconSize) / 2;
            g.blit(RenderPipelines.GUI_TEXTURED, CLASS_ICONS[i],
                    iconX, iconY, 0, 0, iconSize, iconSize, 32, 32, 32, 32);
        }

        // --- Info bar ---
        int infoY = guiTop + 52;
        ClassType chosen = ClientClassData.getChosenClass();

        if (selectedTab >= 0) {
            ClassType tabClass = CLASSES[selectedTab];
            ProfessionType tabProf = PROFS[selectedTab];
            boolean isChosen = tabClass == chosen;
            int playerLevel = ClientClassData.getLevel(tabProf);

            // Class icon + name
            g.blit(RenderPipelines.GUI_TEXTURED, CLASS_ICONS[selectedTab],
                    guiLeft + 14, infoY + 2, 0, 0, 14, 14, 32, 32, 32, 32);

            drawText(g, tabClass.getDisplayName(), guiLeft + 32, infoY + 2, tabClass.getColor());
            String status = isChosen ? "[YOUR CLASS]" : "[Passive only]";
            int statusColor = isChosen ? 0x55FF55 : 0x778899;
            drawText(g, status, guiLeft + 32, infoY + 12, statusColor);

            // Level display on XP bar area
            int xpBarX = guiLeft + 98;
            int xpBarW = GUI_W - 112;
            g.fill(xpBarX, infoY + 4, xpBarX + xpBarW, infoY + 14, 0xFF233040);
            drawText(g, "Level " + playerLevel + " / 50", xpBarX + 4, infoY + 5, 0xAABBCC);

            // --- Skill tree nodes ---
            int treeY = guiTop + 80;
            int treeBottom = guiTop + 200;
            int maxLines = (treeBottom - treeY) / 12;

            String[] passives = PASSIVES[selectedTab];

            g.enableScissor(guiLeft + 10, treeY, guiLeft + GUI_W - 10, treeBottom);

            for (int i = scrollOffset; i < passives.length && i < scrollOffset + maxLines; i++) {
                int ly = treeY + (i - scrollOffset) * 12;
                int requiredLevel = (i + 1) * 5;
                boolean unlocked = playerLevel >= requiredLevel;

                // Node box — green fill if unlocked, dark if locked
                g.fill(guiLeft + 14, ly, guiLeft + 22, ly + 8, 0xFF445566);
                if (unlocked) {
                    g.fill(guiLeft + 15, ly + 1, guiLeft + 21, ly + 7, 0xFF338844); // green inner
                } else {
                    g.fill(guiLeft + 15, ly + 1, guiLeft + 21, ly + 7, 0xFF334455); // dark inner
                }

                // Level label
                int textColor = unlocked ? 0x55FF55 : 0x778899;
                drawText(g, requiredLevel + ": " + passives[i], guiLeft + 26, ly, textColor);

                // Line to next
                if (i < passives.length - 1) {
                    int lineColor = unlocked ? 0xFF338844 : 0xFF445566;
                    g.fill(guiLeft + 18, ly + 8, guiLeft + 19, ly + 12, lineColor);
                }
            }

            g.disableScissor();

            // --- Description panel: single column, scrollable ---
            int descY = guiTop + 206;
            int descBottom = guiTop + GUI_H - 10;
            g.enableScissor(guiLeft + 10, descY, guiLeft + GUI_W - 10, descBottom);

            String[] details = CLASS_DETAILS[selectedTab];

            // Build all lines for the description
            java.util.List<String[]> descLines = new java.util.ArrayList<>(); // [text, colorHex]
            if (isChosen) {
                descLines.add(new String[]{"Skills & Effects:", "55FFFF"});
                for (String d : details) descLines.add(new String[]{" " + d, "55FF55"});
            } else {
                descLines.add(new String[]{"Choose this class for:", "778899"});
                for (String d : details) descLines.add(new String[]{" " + d, "556677"});
            }

            int descVisibleLines = (descBottom - descY - 2) / 9;
            int descMaxScroll = Math.max(0, descLines.size() - descVisibleLines);
            descScrollOffset = Math.min(descScrollOffset, descMaxScroll);

            int lineY = descY + 2;
            for (int i = descScrollOffset; i < descLines.size(); i++) {
                if (lineY + 9 > descBottom) break;
                String[] line = descLines.get(i);
                int col = Integer.parseInt(line[1], 16);
                drawText(g, line[0], guiLeft + 14, lineY, col);
                lineY += 9;
            }

            // Scroll indicator if there's more content
            if (descMaxScroll > 0) {
                drawText(g, descScrollOffset < descMaxScroll ? "..." : "", guiLeft + GUI_W - 22, descBottom - 9, 0x556677);
            }

            g.disableScissor();
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawText(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(this.font, Component.literal(text).withColor(color | 0xFF000000), x, y, color | 0xFF000000);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean consumed) {
        if (!consumed && this.minecraft != null) {
            double mx = this.minecraft.mouseHandler.xpos() * this.width / this.minecraft.getWindow().getScreenWidth();
            double my = this.minecraft.mouseHandler.ypos() * this.height / this.minecraft.getWindow().getScreenHeight();

            int tabW = 44, tabGap = 3;
            int tabsTotal = 5 * tabW + 4 * tabGap;
            int tabsX = guiLeft + (GUI_W - tabsTotal) / 2;
            int tabY = guiTop + 32, tabH = 14;

            for (int i = 0; i < 5; i++) {
                int tx = tabsX + i * (tabW + tabGap);
                if (mx >= tx && mx <= tx + tabW && my >= tabY && my <= tabY + tabH) {
                    selectedTab = i;
                    scrollOffset = 0;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, consumed);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selectedTab >= 0) {
            int descY = guiTop + 206;
            // If mouse is in the description panel area, scroll that
            if (mouseY >= descY) {
                descScrollOffset = Math.max(0, descScrollOffset - (int) scrollY);
                return true;
            }
            // Otherwise scroll the passives list
            int maxLines = (200 - 80) / 12;
            String[] passives = PASSIVES[selectedTab];
            int maxScroll = Math.max(0, passives.length - maxLines);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
