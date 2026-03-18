package com.mlkymc.client;

import com.mlkymc.classes.ClassType;
import com.mlkymc.classes.ProfessionType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Combined class selection + skill overview screen.
 * New players: shows class info + Confirm button to choose.
 * Existing players: shows class info + skill levels (no Confirm).
 */
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

    private static final ProfessionType[] PROFS = ProfessionType.values();

    // ---- Per-class: "Applied to everyone" ----
    // Max 25 chars per line. Use "" for blank lines.
    private static final String[][][] EVERYONE_INFO = {
            { // Adventurer
                    {"DEBUFF:", "CC8888"},
                    {"Hostile mob drop-rate", "CC8888"},
                    {"and drop-chance -50%", "CC8888"},
                    {"", ""},
                    {"Lv5:  Debuff -40%", "99AABB"},
                    {"  Gain +2 max health", "778899"},
                    {"Lv10: Auto-step 1 block", "99AABB"},
                    {"  Gain +4 max health", "778899"},
                    {"Lv15: Debuff -30%", "99AABB"},
                    {"  Gain +6 max health", "778899"},
                    {"Lv20: Jump 1.5 blocks", "99AABB"},
                    {"  Gain +8 max health", "778899"},
                    {"Lv25: Debuff -20%", "99AABB"},
                    {"  Gain +10 max health", "778899"},
                    {"Lv30: Wind-up damage", "99AABB"},
                    {"  Sprint 10 blocks for", "778899"},
                    {"  +5 bonus melee dmg", "778899"},
                    {"  Gain +12 max health", "778899"},
                    {"Lv35: Debuff -10%", "99AABB"},
                    {"  Gain +14 max health", "778899"},
                    {"Lv40: -50% fall damage", "99AABB"},
                    {"  Gain +16 max health", "778899"},
                    {"Lv45: Debuff removed", "99AABB"},
                    {"  Gain +18 max health", "778899"},
                    {"Lv50: Movement speed+", "99AABB"},
                    {"  Gain +20 max health", "778899"},
            },
            { // Cleric
                    {"DEBUFF:", "CC8888"},
                    {"EXP gain -50%", "CC8888"},
                    {"", ""},
                    {"Lv5:  EXP debuff -40%", "99AABB"},
                    {"Lv10: Healing Pulse", "99AABB"},
                    {"  radius increased", "778899"},
                    {"Lv15: EXP debuff -30%", "99AABB"},
                    {"Lv20: Potions you brew", "99AABB"},
                    {"  last 25% longer", "778899"},
                    {"Lv25: EXP debuff -20%", "99AABB"},
                    {"Lv30: Potions you drink", "99AABB"},
                    {"  give weaker version", "778899"},
                    {"  to nearby allies", "778899"},
                    {"Lv35: EXP debuff -10%", "99AABB"},
                    {"Lv40: 25% chance to not", "99AABB"},
                    {"  consume lapis when", "778899"},
                    {"  enchanting", "778899"},
                    {"Lv45: EXP debuff removed", "99AABB"},
                    {"Lv50: Enchanting can go", "99AABB"},
                    {"  +1 above max level", "778899"},
                    {"  (e.g. Sharpness VI)", "778899"},
            },
            { // Farmhand
                    {"DEBUFF:", "CC8888"},
                    {"Neutral mob, fishing &", "CC8888"},
                    {"farming drops -50%", "CC8888"},
                    {"", ""},
                    {"Lv5:  Debuff -40%", "99AABB"},
                    {"Lv10: Auto-replant", "99AABB"},
                    {"  Right-click harvest", "778899"},
                    {"  replants the crop", "778899"},
                    {"Lv15: Debuff -30%", "99AABB"},
                    {"Lv20: Animals you breed", "99AABB"},
                    {"  grow 25% faster &", "778899"},
                    {"  breed CD -25%", "778899"},
                    {"Lv25: Debuff -20%", "99AABB"},
                    {"Lv30: 2x shearing &", "99AABB"},
                    {"  seeds. Crops 2x tick", "778899"},
                    {"Lv35: Debuff -10%", "99AABB"},
                    {"Lv40: Rare fishing", "99AABB"},
                    {"  drops (Saddle, Name", "778899"},
                    {"  Tag, Heart of Sea)", "778899"},
                    {"Lv45: Debuff removed", "99AABB"},
                    {"Lv50: Crops in chunk", "99AABB"},
                    {"  grow at 4x speed", "778899"},
            },
            { // MineCrafter
                    {"DEBUFF:", "CC8888"},
                    {"Mining & woodcutting", "CC8888"},
                    {"drop-chance -50%", "CC8888"},
                    {"", ""},
                    {"Lv5:  Debuff -40%", "99AABB"},
                    {"Lv10: Haste I while", "99AABB"},
                    {"  mining stone.", "778899"},
                    {"  +1 block reach", "778899"},
                    {"Lv15: Debuff -30%", "99AABB"},
                    {"Lv20: Chance to get XP", "99AABB"},
                    {"  from mining stone", "778899"},
                    {"  (not player-placed)", "778899"},
                    {"Lv25: Debuff -20%", "99AABB"},
                    {"Lv30: Rare chance to", "99AABB"},
                    {"  find Milky Stars", "778899"},
                    {"  embedded in ore", "778899"},
                    {"Lv35: Debuff -10%", "99AABB"},
                    {"Lv40: Crafting produces", "99AABB"},
                    {"  +1 output (stacks)", "778899"},
                    {"Lv45: Debuff removed", "99AABB"},
                    {"Lv50: More Milky Stars", "99AABB"},
                    {"  from mining ore", "778899"},
            },
            { // Smith
                    {"DEBUFF:", "CC8888"},
                    {"Smelting speed -50%", "CC8888"},
                    {"", ""},
                    {"Lv5:  Debuff -40%", "99AABB"},
                    {"Lv10: 25% chance to not", "99AABB"},
                    {"  consume fuel when", "778899"},
                    {"  smelting", "778899"},
                    {"Lv15: Debuff -30%", "99AABB"},
                    {"Lv20: Items repaired on", "99AABB"},
                    {"  anvil gain +10% bonus", "778899"},
                    {"  durability above max", "778899"},
                    {"Lv25: Debuff -20%", "99AABB"},
                    {"Lv30: Permanent +1", "99AABB"},
                    {"  Resistance effect", "778899"},
                    {"Lv35: Debuff -10%", "99AABB"},
                    {"Lv40: Too Expensive cap", "99AABB"},
                    {"  raised from 40 to 55", "778899"},
                    {"Lv45: Debuff removed", "99AABB"},
                    {"Lv50: +1 Resistance", "99AABB"},
                    {"  (stacks with Lv30)", "778899"},
                    {"  Breathe underwater", "778899"},
                    {"  longer", "778899"},
            },
    };

    // ---- Per-class: "Exclusive" ----
    private static final String[][][] EXCLUSIVE_INFO = {
            { // Adventurer
                    {"ACTIVE SKILLS:", "55FFFF"},
                    {"Weapon Dash:", "AABBCC"},
                    {"  Hold RMB with sword", "778899"},
                    {"  to dash forward.", "778899"},
                    {"  Distance scales with", "778899"},
                    {"  hold duration.", "778899"},
                    {"Wind-up Damage:", "AABBCC"},
                    {"  Sprint 10 blocks to", "778899"},
                    {"  charge +5 bonus dmg.", "778899"},
                    {"  Lasts 10s, resets on", "778899"},
                    {"  hit.", "778899"},
                    {"", ""},
                    {"SPECIAL EFFECTS:", "55FF55"},
                    {"  2x EXP gain", "88CC88"},
                    {"  Weapon durability+", "88CC88"},
                    {"  2x hostile mob drops", "88CC88"},
                    {"  Minimap HUD (M key)", "88CC88"},
                    {"", ""},
                    {"EXCLUSIVE PASSIVES:", "FFAA00"},
                    {"  Lv20: Mobs shown on", "CCAA55"},
                    {"    minimap", "CCAA55"},
                    {"  Lv50: Non-crouched", "CCAA55"},
                    {"    players on minimap", "CCAA55"},
                    {"    (stand still 10s)", "CCAA55"},
                    {"", ""},
                    {"CRAFTABLE ITEMS:", "B4C8DC"},
                    {"  Wayfinder Compass", "99AABB"},
                    {"  Warp Anchor + Stone", "99AABB"},
                    {"  Trophy Base", "99AABB"},
                    {"  10 Mob Trophies", "99AABB"},
                    {"  Grappling Hook", "99AABB"},
                    {"  Waystone Shard", "99AABB"},
            },
            { // Cleric
                    {"ACTIVE SKILLS:", "55FFFF"},
                    {"Healing Pulse:", "AABBCC"},
                    {"  RMB with Milky Star", "778899"},
                    {"  to heal nearby players", "778899"},
                    {"  Costs EXP + 1 star.", "778899"},
                    {"  Scales with level.", "778899"},
                    {"Resurrection:", "AABBCC"},
                    {"  RMB at player grave.", "778899"},
                    {"  Within 60s: 1 star.", "778899"},
                    {"  After 60s: 1 totem.", "778899"},
                    {"  10min cooldown.", "778899"},
                    {"", ""},
                    {"SPECIAL EFFECTS:", "55FF55"},
                    {"  2x EXP gain", "88CC88"},
                    {"  Cheaper enchanting", "88CC88"},
                    {"  Potions stronger +1", "88CC88"},
                    {"  Chance to save levels", "88CC88"},
                    {"", ""},
                    {"EXCLUSIVE PASSIVES:", "FFAA00"},
                    {"  Lv50: Resurrection", "CCAA55"},
                    {"    cooldown halved", "CCAA55"},
                    {"", ""},
                    {"CRAFTABLE ITEMS:", "B4C8DC"},
                    {"  Totem of Resurrection", "99AABB"},
                    {"  Holy Water", "99AABB"},
                    {"  Enchanted Golden Apple", "99AABB"},
                    {"  Blessing Scroll", "99AABB"},
                    {"  Bottle o' Enchanting", "99AABB"},
                    {"  Blessed Ember", "99AABB"},
            },
            { // Farmhand
                    {"ACTIVE SKILLS:", "55FFFF"},
                    {"Nature's Call:", "AABBCC"},
                    {"  Crouch to nurture", "778899"},
                    {"  crops in 5-block", "778899"},
                    {"  radius. ~10x growth.", "778899"},
                    {"Whisperer:", "AABBCC"},
                    {"  Shift+RMB. Charms", "778899"},
                    {"  hostile mobs to fight", "778899"},
                    {"  for you (10s). Neutral", "778899"},
                    {"  mobs follow (1min).", "778899"},
                    {"  1min cooldown.", "778899"},
                    {"", ""},
                    {"SPECIAL EFFECTS:", "55FF55"},
                    {"  2x EXP gain", "88CC88"},
                    {"  Passive crop growth", "88CC88"},
                    {"  Twin breeding chance", "88CC88"},
                    {"  Fish luck/lure +1", "88CC88"},
                    {"", ""},
                    {"EXCLUSIVE PASSIVES:", "FFAA00"},
                    {"  Lv20: +1 item per", "CCAA55"},
                    {"    fish catch (2 total)", "CCAA55"},
                    {"  Lv30: Lava fishing!", "CCAA55"},
                    {"    Custom nether loot", "CCAA55"},
                    {"  Lv35: Food gives", "CCAA55"},
                    {"    Regen, Speed, Luck", "CCAA55"},
                    {"  Lv40: +2 items per", "CCAA55"},
                    {"    fish catch (3 total)", "CCAA55"},
                    {"  Lv50: Crafted food", "CCAA55"},
                    {"    gives full buffs", "CCAA55"},
                    {"    to any player", "CCAA55"},
                    {"", ""},
                    {"CRAFTABLE ITEMS:", "B4C8DC"},
                    {"  Growth Fertilizer", "99AABB"},
                    {"  Animal Feed", "99AABB"},
                    {"  Scarecrow", "99AABB"},
                    {"  Living Essence", "99AABB"},
                    {"  Spawn Eggs (all mob", "99AABB"},
                    {"    types for spawners)", "99AABB"},
            },
            { // MineCrafter
                    {"ACTIVE SKILLS:", "55FFFF"},
                    {"Vein Mine:", "AABBCC"},
                    {"  Shift+mine ore to", "778899"},
                    {"  break all connected", "778899"},
                    {"  ore (8-18 blocks).", "778899"},
                    {"Timber:", "AABBCC"},
                    {"  Shift+chop log to", "778899"},
                    {"  fell the whole tree.", "778899"},
                    {"Auto-Smelt:", "AABBCC"},
                    {"  Toggle. Ore drops", "778899"},
                    {"  come out smelted.", "778899"},
                    {"", ""},
                    {"SPECIAL EFFECTS:", "55FF55"},
                    {"  2x EXP gain", "88CC88"},
                    {"  Ingredient return", "88CC88"},
                    {"  Fortune +1 built-in", "88CC88"},
                    {"  2x pick/axe durabil.", "88CC88"},
                    {"", ""},
                    {"EXCLUSIVE PASSIVES:", "FFAA00"},
                    {"  Lv20: +1 Haste and", "CCAA55"},
                    {"    +1 Fortune on top", "CCAA55"},
                    {"  Lv30: Crafting always", "CCAA55"},
                    {"    produces 2x output", "CCAA55"},
                    {"  Lv40: Another +1", "CCAA55"},
                    {"    Haste/Fortune.", "CCAA55"},
                    {"    +2 block reach.", "CCAA55"},
                    {"  Lv50: Vein Mine", "CCAA55"},
                    {"    limit doubled", "CCAA55"},
                    {"", ""},
                    {"CRAFTABLE ITEMS:", "B4C8DC"},
                    {"  Reinforced Pickaxe", "99AABB"},
                    {"  Reinforced Axe", "99AABB"},
                    {"  Builder's Wand", "99AABB"},
                    {"  Ender Chest Backpack", "99AABB"},
                    {"  Lodestone", "99AABB"},
                    {"  Resonant Core", "99AABB"},
            },
            { // Smith
                    {"ACTIVE SKILLS:", "55FFFF"},
                    {"Forge Heat:", "AABBCC"},
                    {"  Shift+RMB furnace", "778899"},
                    {"  to create lava in", "778899"},
                    {"  empty fuel slots.", "778899"},
                    {"Emergency Repair:", "AABBCC"},
                    {"  RMB to restore 25%", "778899"},
                    {"  durability. Costs", "778899"},
                    {"  iron. 10s cooldown.", "778899"},
                    {"Tempered Body:", "AABBCC"},
                    {"  Fire resistance", "778899"},
                    {"  (half dmg). Activate", "778899"},
                    {"  to extinguish fire.", "778899"},
                    {"", ""},
                    {"SPECIAL EFFECTS:", "55FF55"},
                    {"  2x EXP gain", "88CC88"},
                    {"  Anvil 50% cheaper", "88CC88"},
                    {"  Faster smelting", "88CC88"},
                    {"  Less armor dur loss", "88CC88"},
                    {"", ""},
                    {"EXCLUSIVE PASSIVES:", "FFAA00"},
                    {"  Lv10: Unbreakable", "CCAA55"},
                    {"    aura. Nearby players", "CCAA55"},
                    {"    10% gain durability", "CCAA55"},
                    {"  Lv30: Anvil never", "CCAA55"},
                    {"    breaks", "CCAA55"},
                    {"  Lv40: +1 Haste and", "CCAA55"},
                    {"    +1 Fortune", "CCAA55"},
                    {"  Lv50: Can combine", "CCAA55"},
                    {"    conflicting enchants", "CCAA55"},
                    {"    (e.g. Smite+Sharp)", "CCAA55"},
                    {"    at 3x level cost", "CCAA55"},
                    {"", ""},
                    {"CRAFTABLE ITEMS:", "B4C8DC"},
                    {"  Netherite Template", "99AABB"},
                    {"  Whetstone", "99AABB"},
                    {"  Armor Plating", "99AABB"},
                    {"  Soul Forge", "99AABB"},
                    {"  Tempered Plate", "99AABB"},
                    {"  Mob Spawner Block", "99AABB"},
                    {"    (needs Farmhand eggs", "99AABB"},
                    {"    to set mob type)", "99AABB"},
            },
    };

    // GUI dimensions match the 320x256 PNG
    private static final int GUI_W = 320;
    private static final int GUI_H = 256;

    // Layout positions (measured from PNG)
    private static final int CLASS_SLOT_Y = 5;       // Top of slot area
    private static final int CLASS_SLOT_H = 38;      // Slot height (y=5 to y=43)
    private static final int CLASS_SLOT_W = 60;      // Each slot width
    private static final int CLASS_SLOT_GAP = 2;     // Gap between slots
    private static final int CLASS_SLOTS_X = 3;      // First slot x

    private static final int INFO_BAR_Y = 46;        // Middle info/header bar
    private static final int INFO_BAR_H = 14;        // Header bar height

    private static final int PANEL_Y = 62;           // Content panels start
    private static final int PANEL_BOTTOM = GUI_H - 6;
    private static final int LEFT_PANEL_X = 5;
    private static final int LEFT_PANEL_W = 152;     // x=5 to x=157
    private static final int RIGHT_PANEL_X = 163;
    private static final int RIGHT_PANEL_W = 152;    // x=163 to x=315

    private int guiLeft, guiTop;
    private int selectedClass = 0;
    private int leftScrollOffset = 0;
    private int rightScrollOffset = 0;
    private Button confirmButton;
    private boolean hasClass;
    private boolean confirmPending = false; // True after first click, waiting for "Certain?" confirmation

    public ClassSelectionScreen() {
        super(Component.literal("Class Selection"));
    }

    @Override
    protected void init() {
        guiLeft = (this.width - GUI_W) / 2;
        guiTop = (this.height - GUI_H) / 2;

        hasClass = ClientClassData.getChosenClass() != ClassType.NONE;

        // Confirm button — only visible when player hasn't chosen a class
        if (!hasClass) {
            int btnX = guiLeft + CLASS_SLOTS_X + selectedClass * (CLASS_SLOT_W + CLASS_SLOT_GAP);
            confirmButton = Button.builder(Component.literal("Confirm"), btn -> onConfirmClick())
                    .bounds(btnX + 3, guiTop + CLASS_SLOT_Y + CLASS_SLOT_H - 19, CLASS_SLOT_W, 20)
                    .build();
            addRenderableWidget(confirmButton);
        }

        // Default to player's chosen class tab if they have one
        if (hasClass) {
            for (int i = 0; i < CLASSES.length; i++) {
                if (CLASSES[i] == ClientClassData.getChosenClass()) {
                    selectedClass = i;
                    break;
                }
            }
        }
    }

    // Scrollbar groove positions (from PNG)
    private static final int LEFT_SB_X = 152;   // x=152-155
    private static final int RIGHT_SB_X = 309;  // x=309-312
    private static final int SB_WIDTH = 4;
    private static final int SB_TOP = 65;
    private static final int SB_BOTTOM = 245;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Background
        g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                guiLeft, guiTop, 0, 0, GUI_W, GUI_H, GUI_W, GUI_H, GUI_W, GUI_H);

        // --- Top row: 5 class slots ---
        for (int i = 0; i < 5; i++) {
            int sx = guiLeft + CLASS_SLOTS_X + i * (CLASS_SLOT_W + CLASS_SLOT_GAP);
            int sy = guiTop + CLASS_SLOT_Y;

            // Selection highlight: Top +1px, Left +1px, Bottom +2px, Right +4px from previous
            if (i == selectedClass) {
                g.fill(sx + 3, sy + 1, sx + CLASS_SLOT_W + 3, sy + CLASS_SLOT_H + 1, 0x30FFFFFF);
            } else if (mouseX >= sx && mouseX <= sx + CLASS_SLOT_W && mouseY >= sy && mouseY <= sy + CLASS_SLOT_H) {
                g.fill(sx + 3, sy + 1, sx + CLASS_SLOT_W + 3, sy + CLASS_SLOT_H + 1, 0x15FFFFFF);
            }

            // Class icon: moved 4px right, 3px down from previous
            int iconSize = 8;
            g.blit(RenderPipelines.GUI_TEXTURED, CLASS_ICONS[i],
                    sx + 9, sy + 7, 0, 0, iconSize, iconSize, 32, 32, 32, 32);

            // Level "0/50": 4px more to the right
            int level = ClientClassData.getLevel(PROFS[i]);
            drawText(g, level + " / 50", sx + 26, sy + 4, 0xCCDDEE);

            // XP: moved 1px to the right
            int xp = ClientClassData.getXp(PROFS[i]);
            int xpNeeded = ClientClassData.getXpForNextLevel(level);
            String xpText = xp + " / " + (xpNeeded > 0 ? xpNeeded : "MAX");
            drawText(g, xpText, sx + 5, sy + 19, 0x778899);
            drawText(g, "XP", sx + 5, sy + 28, 0x556677);
        }

        // Fix #3: Panel titles moved down 3px (infoY + 6 instead of +3)
        int infoY = guiTop + INFO_BAR_Y;
        drawText(g, "Applied to everyone", guiLeft + LEFT_PANEL_X + 4, infoY + 6, 0xE0F0FA);
        drawText(g, CLASSES[selectedClass].getDisplayName() + " Exclusive",
                guiLeft + RIGHT_PANEL_X + 4, infoY + 6, CLASSES[selectedClass].getColor());

        // --- Left panel content ---
        int lpx = guiLeft + LEFT_PANEL_X;
        int lpy = guiTop + PANEL_Y;
        int lpb = guiTop + PANEL_BOTTOM;

        g.enableScissor(lpx + 2, lpy, lpx + LEFT_PANEL_W - 8, lpb);

        int ly = lpy + 2 - leftScrollOffset * 9;

        if (!hasClass) {
            drawText(g, "Choose carefully!", lpx + 4, ly, 0xFFAA00);
            ly += 10;
            drawText(g, "You'll have to reset", lpx + 4, ly, 0xFF5555);
            ly += 9;
            drawText(g, "your levels to change", lpx + 4, ly, 0xFF5555);
            ly += 9;
            drawText(g, "class.", lpx + 4, ly, 0xFF5555);
            ly += 14;
        }

        String[][] everyoneLines = EVERYONE_INFO[selectedClass];
        for (String[] line : everyoneLines) {
            if (!line[0].isEmpty() && ly < lpb && ly > lpy - 9) {
                int color = Integer.parseInt(line[1], 16);
                drawText(g, line[0], lpx + 4, ly, color);
            }
            ly += 9;
        }
        g.disableScissor();

        // --- Right panel content ---
        int rpx = guiLeft + RIGHT_PANEL_X;
        int rpy = guiTop + PANEL_Y;
        int rpb = guiTop + PANEL_BOTTOM;

        g.enableScissor(rpx + 2, rpy, rpx + RIGHT_PANEL_W - 8, rpb);

        String[][] exclusiveLines = EXCLUSIVE_INFO[selectedClass];
        ly = rpy + 2 - rightScrollOffset * 9;
        for (String[] line : exclusiveLines) {
            if (!line[0].isEmpty() && ly < rpb && ly > rpy - 9) {
                int color = Integer.parseInt(line[1], 16);
                drawText(g, line[0], rpx + 4, ly, color);
            }
            ly += 9;
        }
        g.disableScissor();

        // Fix #4: Scrollbar thumbs
        drawScrollbar(g, guiLeft + LEFT_SB_X, guiTop + SB_TOP, guiTop + SB_BOTTOM,
                leftScrollOffset, getTotalLines(EVERYONE_INFO[selectedClass]) + (hasClass ? 0 : 5));
        drawScrollbar(g, guiLeft + RIGHT_SB_X, guiTop + SB_TOP, guiTop + SB_BOTTOM,
                rightScrollOffset, getTotalLines(EXCLUSIVE_INFO[selectedClass]));

        // Confirm button position
        if (confirmButton != null) {
            int btnX = guiLeft + CLASS_SLOTS_X + selectedClass * (CLASS_SLOT_W + CLASS_SLOT_GAP);
            confirmButton.setX(btnX + 3);
            confirmButton.setY(guiTop + CLASS_SLOT_Y + CLASS_SLOT_H - 19);
            confirmButton.setWidth(CLASS_SLOT_W);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawScrollbar(GuiGraphics g, int x, int top, int bottom, int scrollOffset, int totalLines) {
        int trackH = bottom - top;
        int visibleLines = trackH / 9;
        if (totalLines <= visibleLines) return;

        int maxScroll = Math.max(1, totalLines - visibleLines);
        float scrollFraction = Math.min(1.0f, (float) scrollOffset / maxScroll);

        int thumbH = Math.max(10, trackH * visibleLines / totalLines);
        int thumbY = top + (int) ((trackH - thumbH) * scrollFraction);

        // Clamp thumb within groove
        thumbY = Math.max(top, Math.min(bottom - thumbH, thumbY));

        // Thumb
        g.fill(x, thumbY, x + SB_WIDTH, thumbY + thumbH, 0xAA8899AA);
        g.fill(x, thumbY, x + SB_WIDTH, thumbY + 1, 0x40FFFFFF);
    }

    private int getTotalLines(String[][] lines) {
        return lines.length;
    }

    private void drawText(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(this.font, Component.literal(text).withColor(color | 0xFF000000), x, y, color | 0xFF000000);
    }


    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean consumed) {
        if (!consumed && this.minecraft != null) {
            double mx = this.minecraft.mouseHandler.xpos() * this.width / this.minecraft.getWindow().getScreenWidth();
            double my = this.minecraft.mouseHandler.ypos() * this.height / this.minecraft.getWindow().getScreenHeight();

            // Check class slot clicks
            for (int i = 0; i < 5; i++) {
                int sx = guiLeft + CLASS_SLOTS_X + i * (CLASS_SLOT_W + CLASS_SLOT_GAP);
                int sy = guiTop + CLASS_SLOT_Y;
                if (mx >= sx && mx <= sx + CLASS_SLOT_W && my >= sy && my <= sy + CLASS_SLOT_H) {
                    selectedClass = i;
                    leftScrollOffset = 0;
                    rightScrollOffset = 0;
                    // Reset confirm state when switching classes
                    confirmPending = false;
                    if (confirmButton != null) {
                        confirmButton.setMessage(Component.literal("Confirm"));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, consumed);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rpx = guiLeft + RIGHT_PANEL_X;
        // If mouse is in right panel, scroll right
        if (mouseX >= rpx) {
            int maxScroll = Math.max(0, EXCLUSIVE_INFO[selectedClass].length - 15);
            rightScrollOffset = Math.max(0, Math.min(maxScroll, rightScrollOffset - (int) scrollY));
            return true;
        }
        // Otherwise scroll left
        int maxScroll = Math.max(0, EVERYONE_INFO[selectedClass].length - 15);
        leftScrollOffset = Math.max(0, Math.min(maxScroll, leftScrollOffset - (int) scrollY));
        return true;
    }

    private void onConfirmClick() {
        if (selectedClass < 0 || hasClass) return;

        if (!confirmPending) {
            // First click: transform to red "Certain?" button
            confirmPending = true;
            confirmButton.setMessage(Component.literal("Certain?").withColor(0xFF5555));
        } else {
            // Second click: actually select the class
            ClassType chosen = CLASSES[selectedClass];
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("mlkymc class select " + chosen.name().toLowerCase());
                this.onClose();
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
