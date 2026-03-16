# mlkyMC Class System - Implementation Plan

## Table of Contents
1. [Overview](#overview)
2. [Core Rules](#core-rules)
3. [Class System Core](#class-system-core)
4. [Class Details](#class-details)
5. [Cross-Class Crafting Dependencies](#cross-class-crafting-dependencies)
6. [Custom Items Registry](#custom-items-registry)
7. [Custom Blocks Registry](#custom-blocks-registry)
8. [Textures Needed From Artist](#textures-needed-from-artist)
9. [Implementation Phases](#implementation-phases)

---

## Overview

5 classes designed for groups of 3-5 players. Every player can level all 5 professions, but only their chosen class grants Active Skills and Special Effects. Passive skills unlock for everyone as they level each profession.

At level 0, every player has an initial debuff per class that reduces to 0% at level 45.

**Mod type:** Client + Server (NeoForge 1.21.11). Client-side needed for custom textures, GUIs, minimap overlay, and keybinds.

---

## Core Rules

### Class Selection Rules
1. Players choose ONE class. This choice is **permanent** - it cannot be changed.
2. Class selection GUI must clearly warn: "This choice is permanent! This cannot be undone."
3. Class selection happens on first join or via command. Players cannot progress until they pick a class.

### Leveling Rules
1. All 5 professions can be leveled by ALL players regardless of chosen class.
2. Your chosen class gets **2x EXP** for its own profession (Special Effect).
3. Passive skills unlock at levels 5, 10, 15, 20, 25, 30, 35, 40, 45, 50.
4. Some passive skills are **class-exclusive** - they only activate if you picked that class. Other players who level the profession still get the non-exclusive passives.
5. **Active Skills** and **Special Effects** are ONLY available to players who picked that class.
6. Passive skills are available to ALL players as they level that profession.

### Debuff Rules
1. At level 0, every player starts with a **-50% debuff** in each class's area.
2. The debuff reduces by 10% at levels 5, 15, 25, 35, and 45.
3. At level 45, the debuff reaches 0% (normal rates).
4. Debuffs apply to ALL players, not just the class owner.

### Crafting Rules
1. Class-specific craftable items can ONLY be crafted by players of that class.
2. Cross-class reagents can only be crafted by the producing class - other classes must trade for them.
3. Vanilla re-enabled recipes (Enchanted Golden Apple, Netherite Template, Lodestone) are class-restricted.

### Trophy Rules
1. Trophies are placeable blocks with unique models per type.
2. Trophies only grant their AoE buff when placed on top of a **Trophy Base** block.
3. Without a Trophy Base, trophies are purely decorative.
4. Same trophy type does **NOT** stack - placing 2 Wither Trophies gives Strength I, not Strength II.
5. Different trophy types **DO** stack with each other.
6. Buff applies to **ALL players** in range (~8 blocks), not just the placer.
7. Cost scales with buff power: defensive/offensive buffs = 8 Milky Stars, utility buffs = 1 Milky Star.

### Economy Rules
1. Milky Stars are the custom currency.
2. Milky Star Jar (wallet) has 3 visual stages: empty, half, full.
3. Milky Stars can be found as rare drops, embedded in ore (MineCrafter), or through other class mechanics.

---

## Class System Core

### Class Selection
- Player chooses one class (permanent, cannot be changed)
- Class selection GUI with clear warning about permanence
- Confirm button required to lock in choice

### Leveling
- All 5 professions are leveled independently by all players
- Chosen class gets 2x EXP for its own profession
- Passive skills unlock at levels 5, 10, 15, 20, 25, 30, 35, 40, 45, 50

### Initial Debuffs (Level 0)
| Class | Debuff | Reduction Schedule |
|---|---|---|
| Adventurer | Hostile-mob drop-rates/drop-chance -50% | -10% at lvl 5, 15, 25, 35, 45 |
| Cleric | EXP gain -50% | -10% at lvl 5, 15, 25, 35, 45 |
| Farmhand | Neutral-mob/fishing/farming drop-rates/drop-chance -50% | -10% at lvl 5, 15, 25, 35, 45 |
| MineCrafter | Mining/Woodcutting drop-chance -50% | -10% at lvl 5, 15, 25, 35, 45 |
| Smith | Smelting speed -50% | -10% at lvl 5, 15, 25, 35, 45 |

### EXP Sources
| Class | Activities |
|---|---|
| Adventurer | First-time block travel (1 exp), killing mobs (10+ exp, scales with difficulty) |
| Cleric | Healing/Resurrecting players, enchanting items/books |
| Farmhand | Breeding/killing neutral mobs, fishing, harvesting fully grown crops (not player-placed) |
| MineCrafter | Mining (stones/ores), woodcutting, crafting items (+1 exp/craft) |
| Smith | Smelting items (credited to last player who placed item), anvil repairs, anvil enchanting |

---

## Class Details

### Adventurer
**Related Villager Professions:** Cartographer

#### Active Skills
- **Weapon Dash** - Hold right-click to dash forward. Distance scales with hold duration.

#### Special Effects
- 2x EXP gain for Adventurer class
- Durability increase for weapon-related items
- 2x drop-rate and drop-chance from killing hostile mobs
- **Map Wall HUD** - Keybind toggles a minimap overlay in corner of screen. Renders from explored map data.

#### Passive Skills
| Level | Unlock |
|---|---|
| 5 | Debuff reduced to -40%. +2 hearts |
| 10 | Quick step (auto-step 1-block heights). +4 hearts |
| 15 | Debuff reduced to -30%. +6 hearts |
| 20 | Jump height 1.5 blocks. **Adventurer exclusive:** Mobs shown on Map Wall HUD. +8 hearts |
| 25 | Debuff reduced to -20%. +10 hearts |
| 30 | Speed-based damage bonus on hit. +12 hearts |
| 35 | Debuff reduced to -10%. +14 hearts |
| 40 | Reduced fall damage. +16 hearts |
| 45 | Debuff at 0% (normal rates). +18 hearts |
| 50 | Movement speed increase. +20 hearts. **Adventurer exclusive:** Standing still 10s reveals non-crouched players on minimap; stops when you move |

#### Special Craftable Items

**Wayfinder Compass** - Points to nearest unexplored structure (Stronghold, Monument, etc.)
```
Recipe: Compass + 4 Ender Pearls + 4 Milky Stars
```

**Warp Anchor** - Placeable block. Right-click to set location. Crafts a linked Warp Stone (single use, cooldown teleport back).
```
Recipe: Lodestone + 4 Nether Stars + Ender Eye + Resonant Core [MineCrafter]
```

**Trophy Base** - Placeable block. Trophies only grant their AoE buff (~8 block radius) when placed on top of a Trophy Base. Without a base, trophies are purely decorative. Same trophy type does NOT stack. Different types DO stack. Buffs affect all players in range.
```
Recipe: 4 Oak Planks + 1 Smooth Stone Slab + 1 Gold Nugget
```

**Mob Trophies** - Placeable decorative blocks. Must be placed on top of a Trophy Base to activate their buff.

| Trophy | Recipe | Buff |
|---|---|---|
| Wither Trophy | Wither Skeleton Skull + 8 Milky Stars | Strength I |
| Dragon Trophy | Dragon Egg + 8 Milky Stars | Resistance II |
| Elder Guardian Trophy | Sponge + 2 Prismarine Shard + 6 Milky Stars | Haste I |
| Warden Trophy | Sculk Catalyst + 2 Echo Shard + 6 Milky Stars | Absorption I |
| Blaze Trophy | Blaze Rod + 8 Milky Stars | Fire Resistance |
| Ender Trophy | Ender Pearl + 2 Chorus Fruit + 6 Milky Stars | Slow Falling |
| Creeper Trophy | Creeper Head + 8 Milky Stars | Blast Resistance (custom: reduced explosion damage) |
| Spider Trophy | Spider Eye + Fermented Spider Eye + Trophy Frame + 1 Milky Star | Night Vision |
| Phantom Trophy | Phantom Membrane + Trophy Frame + 1 Milky Star | Slow Falling + Insomnia immunity |
| Witch Trophy | Glass Bottle + Redstone + Glowstone Dust + Trophy Frame + 1 Milky Star | Luck I |

**Grappling Hook** - Throwable, pulls player toward landing point (ender pearl movement, no damage, shorter range)
```
Recipe: Tripwire Hook + 3 Chain + Lead + Tempered Plate [Smith]
```

**Waystone Shard** - Cross-class reagent (used by Cleric and MineCrafter)
```
Recipe: 1 Ender Pearl + 1 Prismarine Shard + 1 Milky Star
```

---

### Cleric
**Related Villager Professions:** Cleric, Librarian

#### Active Skills
- **Healing Pulse** - Right-click with Milky Star to heal nearby players. Costs EXP levels + 1 Milky Star. Heal amount scales with Cleric level.
- **Resurrection** - Right-click a player's death location (grave) within 60s to resurrect at half health (no totem needed). After 60s, consumes a Totem of Resurrection. 10 min cooldown.

#### Special Effects
- 2x EXP gain for Cleric class
- Enchanting costs reduced (fewer lapis/levels)
- Potions brewed are stronger (increased duration or amplifier +1)
- Chance to not consume enchanting levels (scales with level)

#### Passive Skills
| Level | Unlock |
|---|---|
| 5 | EXP debuff reduced to -40% |
| 10 | Healing Pulse radius increased |
| 15 | EXP debuff reduced to -30% |
| 20 | Potions you brew last 25% longer |
| 25 | EXP debuff reduced to -20% |
| 30 | Potions you drink give weaker version to nearby allies |
| 35 | EXP debuff reduced to -10% |
| 40 | 25% chance to not consume lapis when enchanting |
| 45 | EXP debuff at 0% (normal rates) |
| 50 | Enchanting can exceed normal max by +1 level (e.g. Sharpness VI). **Cleric exclusive:** Resurrection cooldown halved |

#### Special Craftable Items

**Totem of Resurrection** - Single-use, consumed by Resurrect skill after 60s window
```
Recipe: Totem of Undying + 4 Golden Apples + 4 Milky Stars + Living Essence [Farmhand]
```

**Holy Water** - Splash potion. Massive damage to hostile mobs, fully heals players, 20s invulnerability (keeps players at half heart minimum instead of dying)
```
Recipe: Potion of Healing + Nether Star + Gold Nuggets + Waystone Shard [Adventurer]
```

**Enchanted Golden Apple** - The uncraftable vanilla item, re-enabled for Clerics only
```
Recipe: Golden Apple + 8 Gold Blocks
```

**Blessing Scroll** - Single-use, applies Unbreaking I to any gear (stacks with existing enchants)
```
Recipe: Paper + Lapis Block + Milky Star
```

**Bottle o' Enchanting** - EXP Bottles
```
Recipe: Glass Bottle + 8 Lapis Lazuli (or Glass Bottle + Emerald Block)
```

**Blessed Ember** - Cross-class reagent (used by Farmhand and Smith)
```
Recipe: 1 Blaze Powder + 1 Gold Ingot + 1 Lapis Block
```

---

### Farmhand
**Related Villager Professions:** Farmer, Fisherman, Shepherd

#### Active Skills
- **Nature's Call** - Right-click with special item to AoE bone-meal all crops in radius. Cooldown scales down with level.
- **Animal Whisperer** - Shift+right-click passive mob to see breeding cooldown/stats. At higher levels, charm hostile mobs to become neutral (1 min CD, 1 min duration).

#### Special Effects
- 2x EXP gain for Farmhand class
- Crops grow faster in radius around Farmhand (passive tick acceleration)
- Breeding produces twins (small chance, scales with level)
- Fishing lure/luck bonus (+1 each, built-in)

#### Passive Skills
| Level | Unlock |
|---|---|
| 5 | Debuff reduced to -40% |
| 10 | Auto-replant harvested crops (right-click harvest replants) |
| 15 | Debuff reduced to -30% |
| 20 | Animals you breed grow to adulthood 25% faster. Lower breeding cooldown 25% |
| 25 | Debuff reduced to -20% |
| 30 | Double wool/honeycomb from shearing. Seeds/saplings chance to double. Crops in your chunk grow at 2x tick speed |
| 35 | Debuff reduced to -10%. **Farmhand exclusive:** Any food you eat gives Regen I (5s), Speed I (15s), Luck (1min - increases drop rate and amount of Milky Stars) |
| 40 | Fishing yields rare items at increased rate (Saddles, Name Tags, Heart of the Sea) |
| 45 | Debuff at 0% (normal rates) |
| 50 | Crops in your chunk grow at 4x tick speed. **Farmhand exclusive:** Any food you craft gives Regen I (10s), Speed I (30s), Luck (2min), +1 Fortune level (2min), +1 Looting level (2min) to any player who eats it |

#### Special Craftable Items

**Beehive** - Pre-populated with bees (vanilla beehives craft empty)
```
Recipe: 6 Planks + 3 Honeycomb + Flower
```

**Growth Fertilizer** - AoE bone meal
```
Recipe: 3 Bone Meal + 1 Rotten Flesh + Milky Star
```

**Animal Feed** - Removes breeding cooldown for target animal
```
Recipe: Golden Carrot + Golden Apple + Wheat + Resonant Core [MineCrafter]
```

**Scarecrow** - Placeable block, prevents hostile mob spawns on nearby farmland
```
Recipe: Hay Bale + Stick + Carved Pumpkin + Blessed Ember [Cleric]
```

**Living Essence** - Cross-class reagent (used by Cleric and Smith)
```
Recipe: 1 Glistering Melon + 1 Honeycomb + 1 Bone Meal
```

---

### MineCrafter
**Related Villager Professions:** Fletcher, Mason, Butcher

#### Active Skills
- **Vein Mine** - Hold shift while mining ore to break all connected same-type ore (8-16 blocks, scales with level). Cooldown.
- **Timber** - Same for wood. Break one log while shifting to fell the whole tree.
- **Auto-Smelt** - Toggle-able. Ore drops come out smelted while mining.

#### Special Effects
- 2x EXP gain for MineCrafter class
- Crafting recipes have chance to return one ingredient (scales with level)
- Built-in Fortune +1 on pickaxe (stacks with enchant)
- Double durability on pickaxes and axes

#### Passive Skills
| Level | Unlock |
|---|---|
| 5 | Debuff reduced to -40% |
| 10 | Haste I equivalent while mining stone |
| 15 | Debuff reduced to -30% |
| 20 | Chance to get XP orbs when mining stone (not player-placed). **MineCrafter exclusive:** +1 Haste and +1 Fortune on top of other similar effects |
| 25 | Debuff reduced to -20% |
| 30 | Rare chance to find Milky Stars embedded in ore. **MineCrafter exclusive:** Crafting items always produces double output |
| 35 | Debuff reduced to -10% |
| 40 | Crafting occasionally produces +1 output (e.g. 4 planks -> 5, stacks with lvl 30 MineCrafter exclusive). **MineCrafter exclusive:** +1 Haste and +1 Fortune on top of other similar effects |
| 45 | Debuff at 0% (normal rates) |
| 50 | Mining has a chance to find Milky Stars embedded in ore. **MineCrafter exclusive:** Vein Mine limit doubled |

#### Special Craftable Items

**Reinforced Pickaxe** - Built-in Unbreaking III + Efficiency II, no enchanting needed
```
Recipe: Netherite Ingot + 2 Diamond Blocks + Stick + Milky Stars + Tempered Plate [Smith]
```

**Reinforced Axe** - Same stats as Reinforced Pickaxe but for axes
```
Recipe: Netherite Ingot + 2 Diamond Blocks + Stick + Milky Stars + Tempered Plate [Smith]
```

**Builder's Wand** - Right-click placed block to extend in facing direction. Consumes block from inventory.
```
Recipe: Blaze Rod + Diamond + 2 Gold Blocks + Waystone Shard [Adventurer]
```

**Ender Chest Backpack** - Portable ender chest access (right-click item, no placement needed)
```
Recipe: Ender Chest + 4 Leather + Milky Stars
```

**Lodestone** - Vanilla Lodestone, re-enabled for MineCrafters with cheaper recipe
```
Recipe: Chiseled Stone Bricks + 4 Iron Ingots + Netherite Scrap
```

**Resonant Core** - Cross-class reagent (used by Adventurer and Farmhand)
```
Recipe: 1 Redstone Block + 1 Amethyst Shard + 1 Iron Ingot
```

---

### Smith
**Related Villager Professions:** Toolsmith, Weaponsmith, Armorer, Leatherworker

#### Active Skills
- **Forge Heat** - Shift+Right-click a non-active furnace to create lava in the empty fuel slots. Cooldown.
- **Emergency Repair** - Right-click equipped armor/tool to restore 25% durability. Costs iron ingots from inventory. 10s cooldown.
- **Tempered Body** - Gain fire resistance (half damage from fire and lava) automatically. Activate skill to extinguish fire. 10s cooldown.

#### Special Effects
- 2x EXP gain for Smith class
- Anvil repairs cost 50% less levels
- Smelting becomes faster than normal at high levels
- Armor worn by Smith takes less durability damage

#### Passive Skills
| Level | Unlock |
|---|---|
| 5 | Smelting debuff reduced to -40% |
| 10 | 25% chance to not consume fuel when smelting. **Smith exclusive:** Unbreakable aura - players close to the Smith have 10% chance to gain durability on items that would normally lose it |
| 15 | Smelting debuff reduced to -30% |
| 20 | Anvil repairs give +10% bonus durability above max |
| 25 | Smelting debuff reduced to -20% |
| 30 | Gain permanent +1 Resistance (stacks with similar effects). **Smith exclusive:** Anvil never breaks |
| 35 | Smelting debuff reduced to -10% |
| 40 | "Too Expensive" cap raised from 40 to 55 levels. **Smith exclusive:** +1 Haste and +1 Fortune on top of other similar effects |
| 45 | Smelting debuff at 0% (normal rates) |
| 50 | Gain permanent +1 Resistance (stacks with lvl 30). Breathe underwater longer. **Smith exclusive:** Can combine normally conflicting enchanted books (e.g. Smite + Sharpness) at 3x level cost |

#### Special Craftable Items

**Netherite Upgrade Smithing Template** - Uncraftable vanilla item, re-enabled for Smiths
```
Recipe: Diamond + Netherite Scrap + Obsidian Block
```

**Whetstone** - Single-use, adds Sharpness I to any weapon (stacks with existing)
```
Recipe: Smooth Stone Slab + Flint + Iron Ingot
```

**Armor Plating** - Apply to any armor for +1 armor toughness permanently
```
Recipe: Iron Block + Diamond + Milky Stars + Living Essence [Farmhand]
```

**Soul Forge** - Placeable block. Enhanced anvil: no "Too Expensive" cap, 50% reduced level costs. Only Smiths can use it. CAN break (even at Smith lvl 30 anvil-never-breaks passive).
```
Recipe: Anvil + 4 Netherite Scraps + 2 Crying Obsidian + Milky Star + Blessed Ember [Cleric]
```

**Tempered Plate** - Cross-class reagent (used by Adventurer and MineCrafter)
```
Recipe: 1 Iron Block + 1 Netherite Scrap + 1 Coal Block
```

---

## Cross-Class Crafting Dependencies

### Dependency Web
```
Adventurer ──Waystone Shard──> Cleric (Holy Water), MineCrafter (Builder's Wand)
Cleric ──Blessed Ember──> Farmhand (Scarecrow), Smith (Soul Forge)
Farmhand ──Living Essence──> Cleric (Resurrection Totem), Smith (Armor Plating)
MineCrafter ──Resonant Core──> Adventurer (Warp Anchor), Farmhand (Animal Feed)
Smith ──Tempered Plate──> Adventurer (Grappling Hook), MineCrafter (Reinforced Tools)
```

### Summary Table
| Reagent | Crafted By | Used By | In Recipe For |
|---|---|---|---|
| Waystone Shard | Adventurer | Cleric | Holy Water |
| Waystone Shard | Adventurer | MineCrafter | Builder's Wand |
| Blessed Ember | Cleric | Farmhand | Scarecrow |
| Blessed Ember | Cleric | Smith | Soul Forge |
| Living Essence | Farmhand | Cleric | Totem of Resurrection |
| Living Essence | Farmhand | Smith | Armor Plating |
| Resonant Core | MineCrafter | Adventurer | Warp Anchor |
| Resonant Core | MineCrafter | Farmhand | Animal Feed |
| Tempered Plate | Smith | Adventurer | Grappling Hook |
| Tempered Plate | Smith | MineCrafter | Reinforced Pickaxe & Axe |

Every class produces 1 reagent, consumes 2 from other classes. The primary loop is:
**Smith -> Adventurer -> Cleric -> Farmhand -> MineCrafter -> Smith**

---

## Custom Items Registry

All items that need to be registered as new NeoForge items:

### Currency & Economy (4)
1. Milky Star (already exists as retextured nether star - may need proper item registration)
2. Milky Star Jar - Empty
3. Milky Star Jar - Half
4. Milky Star Jar - Full

### Cross-Class Reagents (5)
5. Waystone Shard
6. Blessed Ember
7. Living Essence
8. Resonant Core
9. Tempered Plate

### Adventurer Items (3)
10. Wayfinder Compass
11. Warp Stone (linked teleport item, crafted from Warp Anchor block)
12. Grappling Hook

### Cleric Items (4)
13. Totem of Resurrection
14. Holy Water (splash potion item)
15. Blessing Scroll
16. Bottle o' Enchanting (vanilla item, just needs Cleric-only recipe)

### Farmhand Items (2)
17. Growth Fertilizer
18. Animal Feed

### MineCrafter Items (4)
19. Reinforced Pickaxe
20. Reinforced Axe
21. Builder's Wand
22. Ender Chest Backpack

### Smith Items (3)
23. Whetstone
24. Armor Plating
25. Netherite Upgrade Smithing Template (vanilla item, just needs Smith-only recipe)

**Total new item registrations: ~22** (excluding vanilla items that just get new recipes)

---

## Custom Blocks Registry

Blocks that get placed in the world:

1. **Warp Anchor** - Interactable block, stores location, links to Warp Stone
2. **Trophy Base** - Pedestal block. Trophies placed on top activate their AoE buff
3. **Scarecrow** - Prevents mob spawns in radius on farmland
4. **Soul Forge** - Enhanced anvil GUI, Smith-only usage restriction
5. **Mob Trophies (10 types):**
   - Wither Trophy
   - Dragon Trophy
   - Elder Guardian Trophy
   - Warden Trophy
   - Blaze Trophy
   - Ender Trophy
   - Creeper Trophy
   - Spider Trophy
   - Phantom Trophy
   - Witch Trophy

**Total new block registrations: 14**

---

## Textures Needed From Artist

### Item Textures (16x16 px PNG, transparent background)

**Currency & Economy:**
1. `milky_star.png` - Custom currency item [X]
2. `milky_star_jar_empty.png` - Jar empty stage [X]
3. `milky_star_jar_half.png` - Jar half-full stage [X]
4. `milky_star_jar_full.png` - Jar full stage [X]
4. `stall_deed.png` - Player stall deed to summon a villager through milkymc [X]

**Cross-Class Reagents:**
5. `waystone_shard.png` - Magical shard, ender/prismarine themed [X]
6. `blessed_ember.png` - Glowing ember, gold/holy themed [X]
7. `living_essence.png` - Green/nature orb or vial [X]
8. `resonant_core.png` - Redstone/amethyst mechanical core [X]
9. `tempered_plate.png` - Dark metal plate, netherite themed [X]

**Adventurer:**
10. `wayfinder_compass.png` - Magical compass with ender glow [X]
11. `warp_stone.png` - Linked teleport stone (glowing, matches Warp Anchor) [X]
12. `grappling_hook.png` - Hook with chain [X]

**Cleric:**
13. `totem_of_resurrection.png` - Golden totem variant, holy themed [X]
14. `holy_water.png` - Glowing splash bottle, white/gold [X]
15. `blessing_scroll.png` - Rolled scroll with enchant glow [X]

**Farmhand:**
16. `growth_fertilizer.png` - Green sparkly bone meal variant [X]
17. `animal_feed.png` - Golden feed bowl [X]

**MineCrafter:**
18. `reinforced_pickaxe.png` - Netherite pickaxe with diamond accents [X]
19. `reinforced_axe.png` - Netherite axe with diamond accents [X]
20. `builders_wand.png` - Blaze rod wand with diamond tip [X]
21. `ender_chest_backpack.png` - Compact ender chest with leather straps [X]

**Smith:**
22. `whetstone.png` - Small sharpening stone [X]
23. `armor_plating.png` - Metal plate with diamond inlay [X]

**Total item textures: 23**

### Block Models & Textures (Blockbench exports: model JSON + atlas PNG per block)

24. `warp_anchor` [X]
25. `trophy_base` [X]
26. `scarecrow` [X]
27. `soul_forge` (+ chipped/damaged variants) [X]
28. `trophy_wither` [X]
29. `trophy_dragon` [X]
30. `trophy_elder_guardian` [X]
31. `trophy_warden` [X]
32. `trophy_blaze` [X]
33. `trophy_ender` [X]
34. `trophy_creeper` [X]
35. `trophy_spider` [X]
36. `trophy_phantom` [X]
37. `trophy_witch` [X]

**Total block exports: 14**

### GUI Textures

38. `minimap_frame.png` (~128x128) [X]
39. `class_selection_gui.png` (256x256) [X]
40. `skill_tree_gui.png` (256x256) [X]

### GUI Icons (32x32 or 8x8 px PNG)

41. `icon_adventurer.png` (32x32) [X]
42. `icon_cleric.png` (32x32) [X]
43. `icon_farmhand.png` (32x32) [X]
44. `icon_minecrafter.png` (32x32) [X]
45. `icon_smith.png` (32x32) [X]
46. `minimap_player.png` (8x8) [X]
47. `minimap_mob.png` (8x8) [X]

**All textures completed [X]**

---

## Implementation Phases

### Phase 1: Foundation
- Custom item/block registration system
- Class data storage (per-player capability/persistent data)
  - Chosen class (enum: NONE, ADVENTURER, CLERIC, FARMHAND, MINECRAFTER, SMITH)
  - 5 profession levels (int each, 0-50)
  - 5 profession XP values (int each)
  - Wallet balance (Milky Stars count)
  - Cooldown timers for active skills
- Class selection GUI (with permanent warning + confirm button)
  - Left panel: 5 class buttons with icons
  - Right panel: scrollable class info (active skills, special effects, debuffs, passives)
  - Bottom: "This cannot be undone" warning + green confirm button
  - Scrollbar for info panel
- EXP tracking per profession per player
- Debuff system (level-based modifiers: -50% at lvl 0, reducing by 10% at 5/15/25/35/45)
- Milky Star item + Jar (3-stage wallet based on balance)
- 5 reagent items (Waystone Shard, Blessed Ember, Living Essence, Resonant Core, Tempered Plate)

### Phase 2: Passive Skills
- Event listeners for all debuff reductions (levels 5-45)
- Adventurer: hearts (+2 per bracket), quick-step, jump boost, fall damage, speed, speed-based damage
- Cleric: EXP modifiers, healing radius, potion duration, potion sharing, lapis saving, enchant cap +1
- Farmhand: auto-replant, animal growth/breeding cooldown, shearing, fishing rates, tick speed (2x at 30, 4x at 50), food buffs (Farmhand exclusive at 35/50)
- MineCrafter: haste, XP orbs from stone, Milky Star from ore, crafting bonus/double (exclusive at 20/30/40), vein mine limit
- Smith: fuel saving, unbreakable aura (exclusive at 10), anvil durability bonus, resistance (+1 at 30/50), anvil cap, haste/fortune (exclusive at 40), underwater breathing, conflicting enchants (exclusive at 50)

### Phase 3: Active Skills
- Adventurer: Weapon Dash (hold right-click, dash forward)
- Cleric: Healing Pulse (costs Milky Star + EXP), Resurrection (death location tracking + grave system, 60s free / totem after)
- Farmhand: Nature's Call (AoE bone meal), Animal Whisperer (mob charming at higher levels)
- MineCrafter: Vein Mine (shift+mine, connected ore), Timber (shift+chop, whole tree), Auto-Smelt toggle
- Smith: Forge Heat (shift+right-click furnace, lava in fuel), Emergency Repair (25% durability, costs iron, 10s CD), Tempered Body (fire resistance + extinguish, 10s CD)

### Phase 4: Special Effects
- Per-class permanent bonuses (2x EXP, durability, fortune, etc.)
- Adventurer: Map Wall HUD (client-side overlay rendering, minimap_frame.png, player/mob markers)
- Cleric: Enchanting cost reduction, potion amplifier boost, level-not-consumed chance
- Farmhand: Crop tick acceleration, twin breeding, fishing bonus
- MineCrafter: Ingredient return chance, fortune bonus, tool durability
- Smith: Anvil cost reduction, smelting speed, armor durability

### Phase 5: Craftable Items & Blocks
- Register all custom items and blocks
- Implement class-restricted crafting (only correct class can craft their items)
- Cross-class reagent recipes
- Adventurer: Wayfinder Compass, Warp Anchor/Stone, Trophy Base, Trophies (10), Grappling Hook
- Cleric: Totem of Resurrection, Holy Water, Enchanted Golden Apple, Blessing Scroll, EXP Bottles
- Farmhand: Beehive (populated), Growth Fertilizer, Animal Feed, Scarecrow
- MineCrafter: Reinforced Tools, Builder's Wand, Ender Chest Backpack, Lodestone
- Smith: Netherite Template, Whetstone, Armor Plating, Soul Forge

### Phase 6: Polish
- Trophy AoE buff system (block entities with tick, effect application, stacking rules)
- Soul Forge GUI (custom anvil with modified caps/costs, Smith-only restriction, can still break)
- Grappling Hook projectile physics (ender pearl movement, no damage, shorter range)
- Builder's Wand block placement logic (extend row in facing direction)
- Holy Water invulnerability mechanic (half-heart floor for 20s)
- Minimap mob rendering (Adventurer lvl 20, same chevron tinted red for mobs)
- Minimap player rendering (Adventurer lvl 50, same chevron tinted green for other players, only while standing still, only non-crouched players)
- Skill tree GUI (5 tabs per class, 10 node winding path, XP bar, description panel)
- Class selection GUI flow (scrollable class details, confirm dialog)
