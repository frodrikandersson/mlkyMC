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
                    {"HOW TO GAIN XP:", "55FFFF"},
                    {"  Loot new chests", "778899"},
                    {"  Kill hostile mobs", "778899"},
                    {"    (harder = more XP)", "778899"},
                    {"  Craft Adventurer", "778899"},
                    {"    exclusive recipes", "778899"},
                    {"", ""},
                    {"DEBUFF:", "CC8888"},
                    {"Hostile mob drop-rate", "CC8888"},
                    {"and drop-chance -50%", "CC8888"},
                    {"", ""},
                    {"Lv5:  Debuff -40%", "99AABB"},
                    {"  +2 max health (1 heart)", "778899"},
                    {"Lv10: +4 max health", "99AABB"},
                    {"Lv15: Debuff -30%", "99AABB"},
                    {"  +6 max health", "778899"},
                    {"Lv20: +8 max health", "99AABB"},
                    {"Lv25: Debuff -20%", "99AABB"},
                    {"  +10 max health", "778899"},
                    {"Lv30: Wind-up damage", "99AABB"},
                    {"  Move 20 blocks for", "778899"},
                    {"  +5 bonus melee dmg.", "778899"},
                    {"  Resets on hit or", "778899"},
                    {"  1s idle. +12 health", "778899"},
                    {"Lv35: Debuff -10%", "99AABB"},
                    {"  +14 max health", "778899"},
                    {"Lv40: -50% fall damage", "99AABB"},
                    {"  +16 max health", "778899"},
                    {"Lv45: Debuff removed", "99AABB"},
                    {"  +18 max health", "778899"},
                    {"Lv50: +20 max health", "99AABB"},
                    {"  (10 hearts total)", "778899"},
            },
            { // Cleric
                    {"HOW TO GAIN XP:", "55FFFF"},
                    {"  Heal yourself/others", "778899"},
                    {"  Resurrect players", "778899"},
                    {"  Enchant items/books", "778899"},
                    {"  Brew potions (harder", "778899"},
                    {"    ingredient = more XP)", "778899"},
                    {"  Pick up XP orbs", "778899"},
                    {"  Craft Cleric recipes", "778899"},
                    {"", ""},
                    {"DEBUFF:", "CC8888"},
                    {"Regular EXP gain -50%", "CC8888"},
                    {"", ""},
                    {"Lv5:  EXP debuff -40%", "99AABB"},
                    {"Lv10: Faster HP regen", "99AABB"},
                    {"  when food bar is full", "778899"},
                    {"Lv15: EXP debuff -30%", "99AABB"},
                    {"Lv20: +10% food saturation", "99AABB"},
                    {"Lv25: EXP debuff -20%", "99AABB"},
                    {"Lv30: Cauldron Rituals", "99AABB"},
                    {"  Drop items in a water", "778899"},
                    {"  cauldron + jump in to", "778899"},
                    {"  transmute. Heals Soul", "778899"},
                    {"  Fracture + converts", "778899"},
                    {"  ore to cross-class", "778899"},
                    {"  reagents. Sneak in", "778899"},
                    {"  cauldron to retrieve.", "778899"},
                    {"Lv35: EXP debuff -10%", "99AABB"},
                    {"Lv40: 25% chance to not", "99AABB"},
                    {"  consume lapis", "778899"},
                    {"Lv45: EXP debuff removed", "99AABB"},
                    {"Lv50: Better enchants", "99AABB"},
                    {"  from enchanting table", "778899"},
            },
            { // Farmhand
                    {"HOW TO GAIN XP:", "55FFFF"},
                    {"  Breed or kill animals", "778899"},
                    {"  Fish", "778899"},
                    {"  Harvest grown crops", "778899"},
                    {"  Cook food (smelting)", "778899"},
                    {"  Craft Farmhand", "778899"},
                    {"    exclusive recipes", "778899"},
                    {"", ""},
                    {"DEBUFF:", "CC8888"},
                    {"Neutral mob, fishing &", "CC8888"},
                    {"farming drops -50%", "CC8888"},
                    {"", ""},
                    {"Lv5:  Debuff -40%", "99AABB"},
                    {"Lv10: Auto-replant", "99AABB"},
                    {"  Right-click harvest", "778899"},
                    {"  replants the crop", "778899"},
                    {"Lv15: Debuff -30%", "99AABB"},
                    {"Lv20: Breed growth +25%", "99AABB"},
                    {"  Breed cooldown -25%", "778899"},
                    {"Lv25: Debuff -20%", "99AABB"},
                    {"Lv30: 2x shearing &", "99AABB"},
                    {"  seeds. Grass drops", "778899"},
                    {"  more farm seeds.", "778899"},
                    {"  Crops tick 2x speed", "778899"},
                    {"Lv35: Debuff -10%", "99AABB"},
                    {"Lv40: Rare fishing", "99AABB"},
                    {"  drops (Saddle, Name", "778899"},
                    {"  Tag, Heart of Sea)", "778899"},
                    {"Lv45: Debuff removed", "99AABB"},
                    {"Lv50: Crops in 10 block", "99AABB"},
                    {"  radius grow at 4x", "778899"},
            },
            { // MineCrafter
                    {"HOW TO GAIN XP:", "55FFFF"},
                    {"  Mine stone and ores", "778899"},
                    {"  Chop wood", "778899"},
                    {"  Craft items (XP based", "778899"},
                    {"    on material value)", "778899"},
                    {"  Smelt ores to ingots", "778899"},
                    {"  Craft MineCrafter", "778899"},
                    {"    exclusive recipes", "778899"},
                    {"", ""},
                    {"DEBUFF:", "CC8888"},
                    {"Mining & woodcutting", "CC8888"},
                    {"drop-chance -50%", "CC8888"},
                    {"", ""},
                    {"Lv5:  Debuff -40%", "99AABB"},
                    {"Lv10: Haste I (mining)", "99AABB"},
                    {"Lv15: Debuff -30%", "99AABB"},
                    {"Lv20: Small chance XP", "99AABB"},
                    {"  orbs from mining stone", "778899"},
                    {"Lv25: Debuff -20%", "99AABB"},
                    {"Lv30: Medium chance XP", "99AABB"},
                    {"  orbs from stone.", "778899"},
                    {"  Bonus ores from stone", "778899"},
                    {"Lv35: Debuff -10%", "99AABB"},
                    {"Lv40: +1 block reach", "99AABB"},
                    {"Lv45: Debuff removed", "99AABB"},
                    {"Lv50: Good chance XP", "99AABB"},
                    {"  orbs from stone.", "778899"},
            },
            { // Smith
                    {"HOW TO GAIN XP:", "55FFFF"},
                    {"  Smelt non-food items", "778899"},
                    {"  Use anvil to repair", "778899"},
                    {"    or enchant items", "778899"},
                    {"  Dig shovel blocks", "778899"},
                    {"  Craft Smith exclusive", "778899"},
                    {"    recipes", "778899"},
                    {"", ""},
                    {"DEBUFF:", "CC8888"},
                    {"Smelting speed -50%", "CC8888"},
                    {"", ""},
                    {"Lv5:  Smelt debuff -40%", "99AABB"},
                    {"Lv10: +1 Armor (perm.)", "99AABB"},
                    {"  25% fuel saving", "778899"},
                    {"Lv15: Smelt debuff -30%", "99AABB"},
                    {"Lv20: Anvil repair gives", "99AABB"},
                    {"  +10% bonus durability", "778899"},
                    {"Lv25: Smelt debuff -20%", "99AABB"},
                    {"Lv30: +1 Armor (total 2)", "99AABB"},
                    {"Lv35: Smelt debuff -10%", "99AABB"},
                    {"Lv40: Anvil Too Expensive", "99AABB"},
                    {"  cap 40 -> 55 levels", "778899"},
                    {"Lv45: Smelt debuff gone", "99AABB"},
                    {"Lv50: +1 Armor (total 3)", "99AABB"},
                    {"  Breathe underwater", "778899"},
                    {"  longer", "778899"},
            },
    };

    // ---- Per-class: "Exclusive" ----
    private static final String[][][] EXCLUSIVE_INFO = {
            { // Adventurer
                    {"ACTIVE SKILLS:", "55FFFF"},
                    {"Primary: Dash", "AABBCC"},
                    {"  Hold to charge,", "778899"},
                    {"  release to dash.", "778899"},
                    {"  Longer hold = more", "778899"},
                    {"  distance. No cooldown.", "778899"},
                    {"Secondary: Lifesteal", "AABBCC"},
                    {"  10s active, 60s CD.", "778899"},
                    {"  Heal 20% of damage", "778899"},
                    {"  dealt per hit.", "778899"},
                    {"Quick-Charge:", "AABBCC"},
                    {"  Bow draws 3x faster.", "778899"},
                    {"  Auto when off cooldown.", "778899"},
                    {"  15s cooldown.", "778899"},
                    {"", ""},
                    {"SPECIAL EFFECTS:", "55FF55"},
                    {"  2x Adventurer EXP", "88CC88"},
                    {"  Weapon durability+", "88CC88"},
                    {"    (swords, bows, xbows)", "88CC88"},
                    {"", ""},
                    {"EXCLUSIVE PASSIVES:", "FFAA00"},
                    {"  Lv5:  Mob drops +20%", "CCAA55"},
                    {"  Lv10: Quick step", "CCAA55"},
                    {"    (auto 1-block step)", "CCAA55"},
                    {"    Map Wall HUD", "CCAA55"},
                    {"    Stars from mobs", "CCAA55"},
                    {"    Stars from Lootr", "CCAA55"},
                    {"  Lv15: Mob drops +40%", "CCAA55"},
                    {"  Lv20: Mobs on map HUD", "CCAA55"},
                    {"    Jump 1.5 blocks", "CCAA55"},
                    {"  Lv25: Mob drops +60%", "CCAA55"},
                    {"  Lv30: Milky Star chance", "CCAA55"},
                    {"    from mobs increased", "CCAA55"},
                    {"  Lv35: Mob drops +80%", "CCAA55"},
                    {"  Lv40: Bow/xbow charge", "CCAA55"},
                    {"    time halved", "CCAA55"},
                    {"  Lv45: Mob drops +100%", "CCAA55"},
                    {"  Lv50: Movement speed+", "CCAA55"},
                    {"    See non-crouched", "CCAA55"},
                    {"    players on map", "CCAA55"},
                    {"    (stand still 10s)", "CCAA55"},
                    {"    Milky Star chance", "CCAA55"},
                    {"    from mobs highest", "CCAA55"},
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
                    {"Primary: Heal", "AABBCC"},
                    {"  Costs 40 Soul Energy.", "778899"},
                    {"  Heals 3 hearts to all", "778899"},
                    {"  nearby players (8 blk)", "778899"},
                    {"  Upgradeable via Altar.", "778899"},
                    {"Secondary: Soul Conduit", "AABBCC"},
                    {"  Toggle XP/Soul Energy", "778899"},
                    {"  mode. Look at a grave", "778899"},
                    {"  to resurrect instead.", "778899"},
                    {"  10-30 Stars on revive.", "778899"},
                    {"  Within 5min: free.", "778899"},
                    {"  After 5min: needs Totem", "778899"},
                    {"", ""},
                    {"SOUL ENERGY:", "AA55FF"},
                    {"  Toggle SE mode to", "8855CC"},
                    {"  collect Soul Energy", "8855CC"},
                    {"  from XP orbs instead.", "8855CC"},
                    {"  R-click Milky Star in", "8855CC"},
                    {"  SE mode = +20 SE.", "8855CC"},
                    {"  Build a Soul Altar to", "8855CC"},
                    {"  unlock tier abilities!", "8855CC"},
                    {"", ""},
                    {"SPECIAL EFFECTS:", "55FF55"},
                    {"  2x Cleric EXP", "88CC88"},
                    {"  Reduced enchant costs", "88CC88"},
                    {"  Concoction brewing", "88CC88"},
                    {"  Chance to save levels", "88CC88"},
                    {"  Stars from enchanting", "88CC88"},
                    {"  Stars from brewing", "88CC88"},
                    {"    (harder ingredient", "778899"},
                    {"    = more Stars)", "778899"},
                    {"", ""},
                    {"EXCLUSIVE PASSIVES:", "FFAA00"},
                    {"  Lv5:  Regular XP +20%", "CCAA55"},
                    {"  Lv10: Grave tracker HUD", "CCAA55"},
                    {"    Devoted Life", "CCAA55"},
                    {"    (20% HP, 1hr CD)", "CCAA55"},
                    {"  Lv15: Regular XP +40%", "CCAA55"},
                    {"  Lv20: Devoted Life", "CCAA55"},
                    {"    (40% HP, 45min CD)", "CCAA55"},
                    {"    Concoction brewing", "CCAA55"},
                    {"  Lv25: Regular XP +60%", "CCAA55"},
                    {"  Lv30: Potion sharing", "CCAA55"},
                    {"    Combine conflicting", "CCAA55"},
                    {"    enchants (3x cost).", "CCAA55"},
                    {"    Cursed gear applies", "CC5555"},
                    {"    Milky Curse stacks", "CC5555"},
                    {"    to the wearer!", "CC5555"},
                    {"  Lv35: Regular XP +80%", "CCAA55"},
                    {"  Lv40: Devoted Life", "CCAA55"},
                    {"    (60% HP, 30min CD)", "CCAA55"},
                    {"  Lv45: Regular XP +100%", "CCAA55"},
                    {"  Lv50: Enchants +1 max", "CCAA55"},
                    {"    Resurrection CD/2", "CCAA55"},
                    {"    Devoted Life (80%", "CCAA55"},
                    {"    HP, 20min CD)", "CCAA55"},
                    {"", ""},
                    {"CRAFTABLE ITEMS:", "B4C8DC"},
                    {"  Soulstone Brick", "99AABB"},
                    {"  Soul Pillar", "99AABB"},
                    {"  Conduit Core", "99AABB"},
                    {"  Soul Altar Capstone", "99AABB"},
                    {"  Tome of the Soul Warden", "99AABB"},
                    {"  Totem of Resurrection", "99AABB"},
                    {"  Enchanted Golden Apple", "99AABB"},
                    {"  Blessing Scroll", "99AABB"},
                    {"  Bottle o' Enchanting:", "99AABB"},
                    {"    Crouch+RMB glass bottle", "778899"},
                    {"    Costs 20 XP (scales", "778899"},
                    {"    with Cleric XP bonus)", "778899"},
                    {"  Blessed Ember", "99AABB"},
            },
            { // Farmhand
                    {"ACTIVE SKILLS:", "55FFFF"},
                    {"Primary: Nature's Call", "AABBCC"},
                    {"  Toggle crop growth", "778899"},
                    {"  boost in 5-block", "778899"},
                    {"  radius (+50% base).", "778899"},
                    {"  +50% fishing speed.", "778899"},
                    {"  Costs 1 Bone Meal/s", "778899"},
                    {"  while active.", "778899"},
                    {"  Scales with level.", "778899"},
                    {"Secondary: Whisperer", "AABBCC"},
                    {"  Charm hostiles (10s).", "778899"},
                    {"  Friendly mobs follow", "778899"},
                    {"  (1min). 10 block range.", "778899"},
                    {"  1min cooldown.", "778899"},
                    {"", ""},
                    {"SPECIAL EFFECTS:", "55FF55"},
                    {"  2x Farmhand EXP", "88CC88"},
                    {"  Twin breeding chance", "88CC88"},
                    {"  Fish luck/lure +1", "88CC88"},
                    {"", ""},
                    {"EXCLUSIVE PASSIVES:", "FFAA00"},
                    {"  Lv5:  Farm drops +20%", "CCAA55"},
                    {"  Lv10: Nature's Call", "CCAA55"},
                    {"    +100% growth speed", "CCAA55"},
                    {"    Composters: 20% Star", "CCAA55"},
                    {"  Lv15: Farm drops +40%", "CCAA55"},
                    {"  Lv20: +1 fish item (2)", "CCAA55"},
                    {"    Nature +150% growth", "CCAA55"},
                    {"  Lv25: Farm drops +60%", "CCAA55"},
                    {"  Lv30: Nether lava fishing!", "CCAA55"},
                    {"    Nature +200% growth", "CCAA55"},
                    {"    Food crafted gains", "CCAA55"},
                    {"    attribute % buffs", "CCAA55"},
                    {"    from ingredients", "CCAA55"},
                    {"  Lv35: Farm drops +80%", "CCAA55"},
                    {"  Lv40: +1 fish item (3)", "CCAA55"},
                    {"    Nature +250% growth", "CCAA55"},
                    {"  Lv45: Farm drops +100%", "CCAA55"},
                    {"  Lv50: Nature +300%", "CCAA55"},
                    {"    Nature radius -> 10", "CCAA55"},
                    {"", ""},
                    {"CRAFTABLE ITEMS:", "B4C8DC"},
                    {"  Growth Fertilizer", "99AABB"},
                    {"  Animal Feed", "99AABB"},
                    {"  Scarecrow", "99AABB"},
                    {"  Living Essence", "99AABB"},
                    {"  Spawn Eggs (for", "99AABB"},
                    {"    mob spawners)", "99AABB"},
            },
            { // MineCrafter
                    {"ACTIVE SKILLS:", "55FFFF"},
                    {"Vein Mine:", "AABBCC"},
                    {"  Crouch+mine ore with", "778899"},
                    {"  pickaxe to break all", "778899"},
                    {"  connected (8-18+).", "778899"},
                    {"Timber:", "AABBCC"},
                    {"  Crouch+chop log with", "778899"},
                    {"  axe to fell tree.", "778899"},
                    {"  Works on Nether stems.", "778899"},
                    {"Primary: Jackhammer", "AABBCC"},
                    {"  10s instant-mine +", "778899"},
                    {"  knockback on hits.", "778899"},
                    {"  1min cooldown.", "778899"},
                    {"Secondary: Ore Scan", "AABBCC"},
                    {"  Toggle. Highlights", "778899"},
                    {"  exposed ores nearby", "778899"},
                    {"  + player light.", "778899"},
                    {"  Uses 1 coal/80s.", "778899"},
                    {"", ""},
                    {"SPECIAL EFFECTS:", "55FF55"},
                    {"  2x MineCrafter EXP", "88CC88"},
                    {"  Ore Scan (secondary)", "88CC88"},
                    {"  Fortune +1 built-in", "88CC88"},
                    {"  2x pick/axe durabil.", "88CC88"},
                    {"", ""},
                    {"EXCLUSIVE PASSIVES:", "FFAA00"},
                    {"  Lv5:  Mine drops +20%", "CCAA55"},
                    {"  Lv10: +1 Reach. Milky", "CCAA55"},
                    {"    Stars from mining", "CCAA55"},
                    {"  Lv15: Mine drops +40%", "CCAA55"},
                    {"  Lv20: +1 Haste", "CCAA55"},
                    {"  Lv25: Mine drops +60%", "CCAA55"},
                    {"  Lv30: Star chance up.", "CCAA55"},
                    {"    Stars from crafting.", "CCAA55"},
                    {"    Bonus ores from", "CCAA55"},
                    {"    stone. Jackhammer", "CCAA55"},
                    {"    30s + 2 heart dmg", "CCAA55"},
                    {"  Lv35: Mine drops +80%", "CCAA55"},
                    {"  Lv40: +1 Haste", "CCAA55"},
                    {"    +1 Reach (total +2)", "CCAA55"},
                    {"  Lv45: Mine drops +100%", "CCAA55"},
                    {"  Lv50: Vein Mine limit", "CCAA55"},
                    {"    doubled.", "CCAA55"},
                    {"    Milky Star chance up", "CCAA55"},
                    {"    Bonus mats from", "CCAA55"},
                    {"    netherrack (Nether)", "CCAA55"},
                    {"", ""},
                    {"FLETCHER'S FORGE:", "55FFFF"},
                    {"  Right-click Fletching", "778899"},
                    {"  Table to open forge.", "778899"},
                    {"  Place Smith-forged", "778899"},
                    {"  gear + Tempered Plate", "778899"},
                    {"  to apply a modifier", "778899"},
                    {"  (+1.5% to +30%) on", "778899"},
                    {"  all Smith attributes.", "778899"},
                    {"  Reroll: 1 lvl + 1 plate", "778899"},
                    {"", ""},
                    {"CRAFTABLE ITEMS:", "B4C8DC"},
                    {"  Reinforced Pick/Axe", "99AABB"},
                    {"    (gives Vein Mine /", "99AABB"},
                    {"    Timber to anyone!)", "99AABB"},
                    {"  Builder's Wand", "99AABB"},
                    {"  Ender Pouch", "99AABB"},
                    {"  Lodestone", "99AABB"},
                    {"  Resonant Core", "99AABB"},
            },
            { // Smith
                    {"ACTIVE SKILLS:", "55FFFF"},
                    {"Forge Heat:", "AABBCC"},
                    {"  Shift+RMB furnace to", "778899"},
                    {"  fill fuel with lava.", "778899"},
                    {"  +2x smelt speed while", "778899"},
                    {"  forge fuel is active.", "778899"},
                    {"Primary: Temp. Mind", "AABBCC"},
                    {"  No durability loss on", "778899"},
                    {"  all items for 20s.", "778899"},
                    {"  Shovel vein mine (8", "778899"},
                    {"  blocks) while active.", "778899"},
                    {"  40s cooldown.", "778899"},
                    {"Secondary: Temp. Body", "AABBCC"},
                    {"  Passive: fire resist.", "778899"},
                    {"  +100% furnace spd 10s", "778899"},
                    {"  Activate: extinguish", "778899"},
                    {"  fire. 10s cooldown.", "778899"},
                    {"", ""},
                    {"SPECIAL EFFECTS:", "55FF55"},
                    {"  2x Smith EXP", "88CC88"},
                    {"  Anvil 50% cheaper", "88CC88"},
                    {"  Faster smelting", "88CC88"},
                    {"  Half armor dur loss", "88CC88"},
                    {"", ""},
                    {"EXCLUSIVE PASSIVES:", "FFAA00"},
                    {"  Lv5:  Smelt speed +20%", "CCAA55"},
                    {"  Lv10: Stars from smelt", "CCAA55"},
                    {"    (5%/10%/15% scaling)", "CCAA55"},
                    {"  Lv15: Smelt speed +40%", "CCAA55"},
                    {"  Lv20: Unbreakable aura", "CCAA55"},
                    {"    (10% dur gain near)", "CCAA55"},
                    {"  Lv25: Smelt speed +60%", "CCAA55"},
                    {"  Lv30: Anvil never breaks", "CCAA55"},
                    {"  Forge Heat AoE", "CCAA55"},
                    {"    (5 block radius)", "CCAA55"},
                    {"  Lv35: Smelt speed +80%", "CCAA55"},
                    {"  Lv40: Mob slowdown aura", "CCAA55"},
                    {"    Pressure anvil aura", "CCAA55"},
                    {"  Lv45: Smelt speed+100%", "CCAA55"},
                    {"  Lv50: Tempered Dig x2", "CCAA55"},
                    {"    Mind/Body AoE 10 blk", "CCAA55"},
                    {"", ""},
                    {"CRAFTABLE ITEMS:", "B4C8DC"},
                    {"  Netherite Template", "99AABB"},
                    {"  Whetstone", "99AABB"},
                    {"  Armor Plating", "99AABB"},
                    {"  Soul Forge", "99AABB"},
                    {"  Tempered Plate", "99AABB"},
                    {"  Empty Mob Spawner", "99AABB"},
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
    private boolean confirmPending = false;

    // Scrollbar drag state: 0=none, 1=left, 2=right
    private int draggingScrollbar = 0;
    private double dragStartY = 0;
    private int dragStartOffset = 0;

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

            // Chosen class: colored border to make it obvious at a glance
            boolean isChosenClass = hasClass && CLASSES[i] == ClientClassData.getChosenClass();
            if (isChosenClass) {
                int cc = CLASSES[i].getColor() | 0xFF000000;
                // Draw colored border (2px thick)
                g.fill(sx + 2, sy, sx + CLASS_SLOT_W + 4, sy + 1, cc);           // top
                g.fill(sx + 2, sy + CLASS_SLOT_H + 1, sx + CLASS_SLOT_W + 4, sy + CLASS_SLOT_H + 2, cc); // bottom
                g.fill(sx + 2, sy, sx + 3, sy + CLASS_SLOT_H + 2, cc);           // left
                g.fill(sx + CLASS_SLOT_W + 3, sy, sx + CLASS_SLOT_W + 4, sy + CLASS_SLOT_H + 2, cc); // right
            }

            // Selection highlight (currently viewed tab)
            if (i == selectedClass) {
                g.fill(sx + 3, sy + 1, sx + CLASS_SLOT_W + 3, sy + CLASS_SLOT_H + 1, 0x30FFFFFF);
            } else if (mouseX >= sx && mouseX <= sx + CLASS_SLOT_W && mouseY >= sy && mouseY <= sy + CLASS_SLOT_H) {
                g.fill(sx + 3, sy + 1, sx + CLASS_SLOT_W + 3, sy + CLASS_SLOT_H + 1, 0x15FFFFFF);
            }

            // Class icon: moved 4px right, 3px down from previous
            int iconSize = 12;
            g.blit(RenderPipelines.GUI_TEXTURED, CLASS_ICONS[i],
                    sx + 7, sy + 5, 0, 0, iconSize, iconSize, 32, 32, 32, 32);

            // Level "0/50": 4px more to the right
            int level = ClientClassData.getLevel(PROFS[i]);
            drawText(g, level + " / 50", sx + 23, sy + 4, 0xCCDDEE);

            // XP: moved 1px to the right
            int xp = ClientClassData.getXp(PROFS[i]);
            int xpNeeded = ClientClassData.getXpForNextLevel(level);
            String xpText = xp + "/" + (xpNeeded > 0 ? xpNeeded : "MAX");
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

            // Check scrollbar clicks for drag
            int leftSbX = guiLeft + LEFT_SB_X;
            int rightSbX = guiLeft + RIGHT_SB_X;
            int sbTop = guiTop + SB_TOP;
            int sbBottom = guiTop + SB_BOTTOM;

            if (mx >= leftSbX && mx <= leftSbX + SB_WIDTH + 2 && my >= sbTop && my <= sbBottom) {
                draggingScrollbar = 1;
                dragStartY = my;
                dragStartOffset = leftScrollOffset;
                return true;
            }
            if (mx >= rightSbX && mx <= rightSbX + SB_WIDTH + 2 && my >= sbTop && my <= sbBottom) {
                draggingScrollbar = 2;
                dragStartY = my;
                dragStartOffset = rightScrollOffset;
                return true;
            }

            // Check class slot clicks — skip if confirm button was clicked
            boolean onConfirmBtn = confirmButton != null && confirmButton.visible
                    && mx >= confirmButton.getX() && mx <= confirmButton.getX() + confirmButton.getWidth()
                    && my >= confirmButton.getY() && my <= confirmButton.getY() + confirmButton.getHeight();

            for (int i = 0; i < 5; i++) {
                int sx = guiLeft + CLASS_SLOTS_X + i * (CLASS_SLOT_W + CLASS_SLOT_GAP);
                int sy = guiTop + CLASS_SLOT_Y;
                if (mx >= sx && mx <= sx + CLASS_SLOT_W && my >= sy && my <= sy + CLASS_SLOT_H) {
                    if (onConfirmBtn) return super.mouseClicked(event, consumed); // Let the button handle it
                    if (i == selectedClass) return true; // Already selected, don't reset
                    selectedClass = i;
                    leftScrollOffset = 0;
                    rightScrollOffset = 0;
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
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar != 0 && this.minecraft != null) {
            double mouseY = this.minecraft.mouseHandler.ypos() * this.height / this.minecraft.getWindow().getScreenHeight();
            int trackH = SB_BOTTOM - SB_TOP;
            double deltaY = mouseY - dragStartY;
            int totalLines;
            int visibleLines = trackH / 9;

            if (draggingScrollbar == 1) {
                totalLines = getTotalLines(EVERYONE_INFO[selectedClass]) + (hasClass ? 0 : 5);
            } else {
                totalLines = getTotalLines(EXCLUSIVE_INFO[selectedClass]);
            }

            int maxScroll = Math.max(1, totalLines - visibleLines);
            int newOffset = dragStartOffset + (int) (deltaY / trackH * maxScroll);
            newOffset = Math.max(0, Math.min(maxScroll, newOffset));

            if (draggingScrollbar == 1) {
                leftScrollOffset = newOffset;
            } else {
                rightScrollOffset = newOffset;
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        draggingScrollbar = 0;
        return super.mouseReleased(event);
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
                this.minecraft.player.connection.sendChat("[MLKYMC_CLASS_SELECT:" + chosen.name() + "]");
                this.onClose();
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
