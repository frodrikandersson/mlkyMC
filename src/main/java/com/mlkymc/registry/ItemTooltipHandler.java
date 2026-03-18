package com.mlkymc.registry;

import com.mlkymc.economy.MilkyStar;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Adds tooltip descriptions to mlkyMC items.
 * Client-side only event.
 */
public class ItemTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // Only process mlkymc items - skip everything else immediately
        var itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !itemId.getNamespace().equals("mlkymc")) return;

        var tooltip = event.getToolTip();

        // --- Milky Star ---
        if (stack.is(ModItems.MILKY_STAR.get())) {
            tooltip.add(Component.literal("Server currency").withColor(0xAAAAAA));
            return;
        }

        // --- Milky Star Jar ---
        if (MilkyStar.isJar(stack)) {
            int balance = MilkyStar.getJarBalance(stack);
            String owner = MilkyStar.getJarOwner(stack);
            tooltip.add(Component.literal(balance + " Milky Stars").withColor(0xFFD700));
            if (!owner.isEmpty()) {
                tooltip.add(Component.literal("Owner: " + owner).withColor(0xAAAAAA));
            }
            tooltip.add(Component.literal("Right-click to deposit/withdraw").withColor(0x55FF55));
            return;
        }

        // --- Wayfinder Compass ---
        if (stack.is(ModItems.WAYFINDER_COMPASS.get())) {
            tooltip.add(Component.literal("Right-click to find nearest structure").withColor(0x55FFFF));
            tooltip.add(Component.literal("Adventurer class item").withColor(0xAAAAAA));
            return;
        }

        // --- Dimension Compass ---
        if (stack.is(ModItems.DIMENSION_COMPASS.get())) {
            tooltip.add(Component.literal("Right-click to view dimension status").withColor(0xAA00FF));
            tooltip.add(Component.literal("Shows Nether/End unlock progress").withColor(0xAAAAAA));
            return;
        }

        // --- Reagents ---
        if (stack.is(ModItems.WAYSTONE_SHARD.get())) {
            tooltip.add(Component.literal("Adventurer reagent").withColor(0x55AA55));
            tooltip.add(Component.literal("Used in Cleric and MineCrafter recipes").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.BLESSED_EMBER.get())) {
            tooltip.add(Component.literal("Cleric reagent").withColor(0xAA55AA));
            tooltip.add(Component.literal("Used in Farmhand and Smith recipes").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.LIVING_ESSENCE.get())) {
            tooltip.add(Component.literal("Farmhand reagent").withColor(0xAAAA00));
            tooltip.add(Component.literal("Used in Cleric and Smith recipes").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.RESONANT_CORE.get())) {
            tooltip.add(Component.literal("MineCrafter reagent").withColor(0x5555FF));
            tooltip.add(Component.literal("Used in Adventurer and Farmhand recipes").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.TEMPERED_PLATE.get())) {
            tooltip.add(Component.literal("Smith reagent").withColor(0xAA5500));
            tooltip.add(Component.literal("Used in Adventurer and MineCrafter recipes").withColor(0xAAAAAA));
        }

        // --- Adventurer ---
        else if (stack.is(ModItems.WARP_STONE.get())) {
            tooltip.add(Component.literal("Single use teleport").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Returns you to its linked Warp Anchor").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.GRAPPLING_HOOK.get())) {
            tooltip.add(Component.literal("Throws a hook — pulls you to blocks").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Hooks entities — reel to meet in middle").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Requires Grappling Hooks as ammo").withColor(0x888888));
        } else if (stack.is(ModItems.GRAPPLING_HOOK_AMMO.get())) {
            tooltip.add(Component.literal("Ammo for the Grappling Hook").withColor(0xAAAAAA));
        }

        // --- Cleric ---
        else if (stack.is(ModItems.TOTEM_OF_RESURRECTION.get())) {
            tooltip.add(Component.literal("Consumed to resurrect a fallen player").withColor(0xFFD700));
            tooltip.add(Component.literal("Used after 60s death window expires").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.HOLY_WATER.get())) {
            tooltip.add(Component.literal("Throw to deal massive damage").withColor(0xFFFFAA));
            tooltip.add(Component.literal("to undead mobs in the area").withColor(0xFFFFAA));
            tooltip.add(Component.literal("Heals + 20s invulnerability").withColor(0x55FF55));
            tooltip.add(Component.literal("for all nearby players").withColor(0x55FF55));
        } else if (stack.is(ModItems.BLESSING_SCROLL.get())) {
            tooltip.add(Component.literal("Apply to gear for Unbreaking I").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Stacks with existing enchants").withColor(0xAAAAAA));
        }

        // --- Farmhand ---
        else if (stack.is(ModItems.GROWTH_FERTILIZER.get())) {
            tooltip.add(Component.literal("AoE bone meal - grows nearby crops").withColor(0x55FF55));
        } else if (stack.is(ModItems.ANIMAL_FEED.get())) {
            tooltip.add(Component.literal("Removes breeding cooldown").withColor(0x55FF55));
        }

        // --- MineCrafter ---
        else if (stack.is(ModItems.REINFORCED_PICKAXE.get())) {
            tooltip.add(Component.literal("Unbreaking III + Efficiency II built-in").withColor(0x55FFFF));
        } else if (stack.is(ModItems.REINFORCED_AXE.get())) {
            tooltip.add(Component.literal("Unbreaking III + Efficiency II built-in").withColor(0x55FFFF));
        } else if (stack.is(ModItems.BUILDERS_WAND.get())) {
            tooltip.add(Component.literal("Extend placed blocks in facing direction").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Consumes blocks from inventory").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.ENDER_CHEST_BACKPACK.get())) {
            tooltip.add(Component.literal("Right-click for portable ender chest").withColor(0xAA00FF));
        }

        // --- Smith ---
        else if (stack.is(ModItems.WHETSTONE.get())) {
            tooltip.add(Component.literal("Apply to weapon for Sharpness I").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Stacks with existing enchants").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.ARMOR_PLATING.get())) {
            tooltip.add(Component.literal("Apply to armor for +1 toughness").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Permanent upgrade").withColor(0xAAAAAA));
        }

        // --- Economy ---
        else if (stack.is(ModItems.MARKET_CATALOG.get())) {
            tooltip.add(Component.literal("Browse all market listings").withColor(0xFFAA00));
            tooltip.add(Component.literal("Read-only marketplace view").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.STALL_DEED.get())) {
            tooltip.add(Component.literal("Place to create a market stall").withColor(0x55FF55));
            tooltip.add(Component.literal("Summons a villager for trading").withColor(0xAAAAAA));
        }

        // --- Blocks ---
        else if (stack.is(ModBlocks.WARP_ANCHOR_ITEM.get())) {
            tooltip.add(Component.literal("Adventurer craftable").withColor(0x55AA55));
            tooltip.add(Component.literal("Place and right-click to set location").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Craft a Warp Stone to teleport back").withColor(0xAAAAAA));
        } else if (stack.is(ModBlocks.TROPHY_BASE_ITEM.get())) {
            tooltip.add(Component.literal("Adventurer craftable").withColor(0x55AA55));
            tooltip.add(Component.literal("Place trophies on top to activate buffs").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Buffs affect all players in ~8 block range").withColor(0xAAAAAA));
        } else if (stack.is(ModBlocks.SCARECROW_ITEM.get())) {
            tooltip.add(Component.literal("Farmhand craftable").withColor(0xAAAA00));
            tooltip.add(Component.literal("Prevents hostile mob spawns (32 blocks)").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Boosts crop growth +10% (32 blocks)").withColor(0x55FF55));
            tooltip.add(Component.literal("Growth boost does not stack with other scarecrows").withColor(0x888888));
        } else if (stack.is(ModBlocks.SOUL_FORGE_ITEM.get())) {
            tooltip.add(Component.literal("Smith craftable").withColor(0xAA5500));
            tooltip.add(Component.literal("Enhanced anvil: no Too Expensive cap").withColor(0xAAAAAA));
            tooltip.add(Component.literal("50% reduced level costs. Smith-only.").withColor(0xAAAAAA));
        }

        // Trophy tooltips
        else if (stack.is(ModBlocks.TROPHY_WITHER_ITEM.get())) {
            trophyTooltip(tooltip, "Strength I", 0xFF5555);
        } else if (stack.is(ModBlocks.TROPHY_DRAGON_ITEM.get())) {
            trophyTooltip(tooltip, "Resistance II", 0x5555FF);
        } else if (stack.is(ModBlocks.TROPHY_ELDER_GUARDIAN_ITEM.get())) {
            trophyTooltip(tooltip, "Haste I", 0x55FFFF);
        } else if (stack.is(ModBlocks.TROPHY_WARDEN_ITEM.get())) {
            trophyTooltip(tooltip, "Absorption I", 0xFFFF55);
        } else if (stack.is(ModBlocks.TROPHY_BLAZE_ITEM.get())) {
            trophyTooltip(tooltip, "Fire Resistance", 0xFFAA00);
        } else if (stack.is(ModBlocks.TROPHY_ENDER_ITEM.get())) {
            trophyTooltip(tooltip, "Slow Falling", 0xAA00FF);
        } else if (stack.is(ModBlocks.TROPHY_CREEPER_ITEM.get())) {
            trophyTooltip(tooltip, "Blast Resistance", 0x55FF55);
        } else if (stack.is(ModBlocks.TROPHY_SPIDER_ITEM.get())) {
            trophyTooltip(tooltip, "Night Vision", 0x994444);
        } else if (stack.is(ModBlocks.TROPHY_PHANTOM_ITEM.get())) {
            trophyTooltip(tooltip, "Slow Falling + Insomnia immunity", 0x6688AA);
        } else if (stack.is(ModBlocks.TROPHY_WITCH_ITEM.get())) {
            trophyTooltip(tooltip, "Luck I", 0x55AA55);
        }
    }

    private void trophyTooltip(java.util.List<Component> tooltip, String buff, int color) {
        tooltip.add(Component.literal("Adventurer craftable").withColor(0x55AA55));
        tooltip.add(Component.literal("Buff: " + buff).withColor(color));
        tooltip.add(Component.literal("Place on Trophy Base to activate").withColor(0xAAAAAA));
        tooltip.add(Component.literal("Same type does NOT stack").withColor(0x888888));
    }
}
