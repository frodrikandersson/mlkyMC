package com.mlkymc.registry;

import com.mlkymc.economy.MilkyStar;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Adds tooltip descriptions to mlkyMC items.
 * Client-side only event.
 */
public class ItemTooltipHandler {

    private static Map<Item, String> classRestrictions;

    private static void initRestrictions() {
        if (classRestrictions != null) return;
        classRestrictions = new HashMap<>();

        // Adventurer
        classRestrictions.put(ModItems.WAYSTONE_SHARD.get(), "Adventurer");
        classRestrictions.put(ModItems.WAYFINDER_COMPASS.get(), "Adventurer");
        classRestrictions.put(ModItems.GRAPPLING_HOOK.get(), "Adventurer");
        classRestrictions.put(ModItems.GRAPPLING_HOOK_AMMO.get(), "Adventurer");
        classRestrictions.put(ModItems.WARP_STONE.get(), "Adventurer");
        classRestrictions.put(ModBlocks.WARP_ANCHOR_ITEM.get(), "Adventurer");
        classRestrictions.put(ModBlocks.TROPHY_BASE_ITEM.get(), "Adventurer");
        classRestrictions.put(ModBlocks.TROPHY_WITHER_ITEM.get(), "Adventurer");
        classRestrictions.put(ModBlocks.TROPHY_DRAGON_ITEM.get(), "Adventurer");
        classRestrictions.put(ModBlocks.TROPHY_ELDER_GUARDIAN_ITEM.get(), "Adventurer");
        classRestrictions.put(ModBlocks.TROPHY_WARDEN_ITEM.get(), "Adventurer");
        classRestrictions.put(ModBlocks.TROPHY_BLAZE_ITEM.get(), "Adventurer");
        classRestrictions.put(ModBlocks.TROPHY_ENDER_ITEM.get(), "Adventurer");
        classRestrictions.put(ModBlocks.TROPHY_CREEPER_ITEM.get(), "Adventurer");
        classRestrictions.put(ModBlocks.TROPHY_SPIDER_ITEM.get(), "Adventurer");
        classRestrictions.put(ModBlocks.TROPHY_PHANTOM_ITEM.get(), "Adventurer");
        classRestrictions.put(ModBlocks.TROPHY_WITCH_ITEM.get(), "Adventurer");

        // Cleric
        classRestrictions.put(ModItems.BLESSED_EMBER.get(), "Cleric");
        classRestrictions.put(ModItems.TOTEM_OF_RESURRECTION.get(), "Cleric");
        classRestrictions.put(ModItems.BLESSING_SCROLL.get(), "Cleric");
        classRestrictions.put(ModItems.TOME_OF_THE_SOUL_WARDEN.get(), "Cleric");
        classRestrictions.put(Items.ENCHANTED_GOLDEN_APPLE, "Cleric");
        classRestrictions.put(Items.EXPERIENCE_BOTTLE, "Cleric");
        classRestrictions.put(ModBlocks.SOULSTONE_BRICK_ITEM.get(), "Cleric");
        classRestrictions.put(ModBlocks.SOUL_PILLAR_ITEM.get(), "Cleric");
        classRestrictions.put(ModBlocks.CONDUIT_CORE_ITEM.get(), "Cleric");
        classRestrictions.put(ModBlocks.SOUL_ALTAR_CAPSTONE_ITEM.get(), "Cleric");

        // Farmhand
        classRestrictions.put(ModItems.LIVING_ESSENCE.get(), "Farmhand");
        classRestrictions.put(ModItems.GROWTH_FERTILIZER.get(), "Farmhand");
        classRestrictions.put(ModItems.ANIMAL_FEED.get(), "Farmhand");
        classRestrictions.put(ModItems.SANDWICH.get(), "Farmhand");
        classRestrictions.put(ModBlocks.SCARECROW_ITEM.get(), "Farmhand");
        // Farmhand spawn eggs
        classRestrictions.put(Items.ZOMBIE_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.SKELETON_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.SPIDER_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.CAVE_SPIDER_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.BLAZE_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.SILVERFISH_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.MAGMA_CUBE_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.COW_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.SHEEP_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.CHICKEN_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.PIG_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.RABBIT_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.WOLF_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.CAT_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.BEE_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.HORSE_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.DONKEY_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.FOX_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.FROG_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.GOAT_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.IRON_GOLEM_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.LLAMA_SPAWN_EGG, "Farmhand");
        classRestrictions.put(Items.TURTLE_SPAWN_EGG, "Farmhand");

        // MineCrafter
        classRestrictions.put(ModItems.RESONANT_CORE.get(), "MineCrafter");
        classRestrictions.put(ModItems.REINFORCED_PICKAXE.get(), "MineCrafter");
        classRestrictions.put(ModItems.REINFORCED_AXE.get(), "MineCrafter");
        classRestrictions.put(ModItems.BUILDERS_WAND.get(), "MineCrafter");
        classRestrictions.put(ModItems.ENDER_POUCH.get(), "MineCrafter");
        classRestrictions.put(Items.LODESTONE, "MineCrafter");

        // Smith
        classRestrictions.put(ModItems.TEMPERED_PLATE.get(), "Smith");
        classRestrictions.put(ModItems.WHETSTONE.get(), "Smith");
        classRestrictions.put(ModItems.ARMOR_PLATING.get(), "Smith");
        classRestrictions.put(ModBlocks.SOUL_FORGE_ITEM.get(), "Smith");
        classRestrictions.put(Items.SPAWNER, "Smith");
    }

    private static String getClassRestriction(Item item) {
        initRestrictions();
        return classRestrictions.get(item);
    }

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // Show Soul Energy on Soul Altar Capstone
        if (stack.is(ModBlocks.SOUL_ALTAR_CAPSTONE_ITEM.get())) {
            var capData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (capData != null) {
                var capNbt = capData.copyTag();
                int se = capNbt.getIntOr("mlkymc_altar_se", 0);
                int hw = capNbt.getIntOr("mlkymc_altar_hw", 0);
                if (se > 0 || hw > 0) {
                    event.getToolTip().add(Component.literal("Soul Energy: " + se).withColor(0xAA55FF));
                    event.getToolTip().add(Component.literal("Highest SE: " + hw).withColor(0x777777));
                } else {
                    event.getToolTip().add(Component.literal("Soul Energy: 0").withColor(0x777777));
                }
            } else {
                event.getToolTip().add(Component.literal("Soul Energy: 0").withColor(0x777777));
            }
        }

        // Check for Farmhand-blessed food tag (applies to any item, not just mlkymc namespace)
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null) {
            var nbt = customData.copyTag();
            if (nbt.getBooleanOr("mlkymc_farmhand_food", false)) {
                event.getToolTip().add(Component.literal("Farmhand Blessed").withColor(0x55FF55));
                if (nbt.getBooleanOr("mlkymc_farmhand_food_lv50", false)) {
                    event.getToolTip().add(Component.literal("Regen, Speed, Luck + AoE buff").withColor(0xAAFFAA));
                } else {
                    event.getToolTip().add(Component.literal("Regen I (5s), Speed I (15s), Luck (1m)").withColor(0xAAFFAA));
                }
            }
        }

        // Radio block descriptions
        if (stack.is(ModBlocks.MICROPHONE_ITEM.get())) {
            event.getToolTip().add(Component.literal("Captures nearby voice and").withColor(0xAAAAAA));
            event.getToolTip().add(Component.literal("broadcasts to Speakers on").withColor(0xAAAAAA));
            event.getToolTip().add(Component.literal("the same frequency.").withColor(0xAAAAAA));
            event.getToolTip().add(Component.literal("Right-click to toggle ON/OFF").withColor(0x55FF55));
        }
        if (stack.is(ModBlocks.SPEAKER_ITEM.get())) {
            event.getToolTip().add(Component.literal("Plays audio received from").withColor(0xAAAAAA));
            event.getToolTip().add(Component.literal("Microphones on the same").withColor(0xAAAAAA));
            event.getToolTip().add(Component.literal("frequency. Place anywhere!").withColor(0xAAAAAA));
            event.getToolTip().add(Component.literal("Right-click to view settings").withColor(0x55FFFF));
        }
        if (stack.is(ModBlocks.RADIO_ITEM.get())) {
            event.getToolTip().add(Component.literal("Portable radio receiver.").withColor(0xAAAAAA));
            event.getToolTip().add(Component.literal("Plays audio from Microphones").withColor(0xAAAAAA));
            event.getToolTip().add(Component.literal("on the same frequency.").withColor(0xAAAAAA));
            event.getToolTip().add(Component.literal("Right-click to view settings").withColor(0x55FFFF));
        }

        // Show class restriction for any restricted item (mlkymc or vanilla)
        String className = getClassRestriction(stack.getItem());
        if (className != null) {
            event.getToolTip().add(Component.literal("Craftable by " + className + "s").withColor(0xAA55FF));
        }

        // Show Adaptive enchantment effect description
        if (stack.getTagEnchantments().size() > 0) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null) {
                var enchReg = mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                var adaptiveKey = net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.ENCHANTMENT,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("mlkymc", "adaptive"));
                var holder = enchReg.get(adaptiveKey).orElse(null);
                if (holder != null && stack.getTagEnchantments().getLevel(holder) > 0) {
                    event.getToolTip().add(Component.literal("Class effect varies:").withColor(0xAAAAAA));
                    event.getToolTip().add(Component.literal(" Cleric: SE fills Altar first").withColor(0x777777));
                    event.getToolTip().add(Component.literal(" Adventurer: Air Dash (1x mid-air)").withColor(0x777777));
                    event.getToolTip().add(Component.literal(" Farmhand: Free Nature's Call (no bonemeal), half growth").withColor(0x777777));
                    event.getToolTip().add(Component.literal(" MineCrafter: Ore Scan sees through walls (5 blk)").withColor(0x777777));
                    event.getToolTip().add(Component.literal(" Smith: Ignite nearby on Tempered Body").withColor(0x777777));
                }
            }
        }

        // Only process mlkymc items for other tooltips
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
            tooltip.add(Component.literal("Does NOT revive you on death!").withColor(0xFF5555));
            tooltip.add(Component.literal("Used by a Cleric to resurrect").withColor(0xFFD700));
            tooltip.add(Component.literal("another player's ghost.").withColor(0xFFD700));
            tooltip.add(Component.literal("Required after 5min death window").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.BLESSING_SCROLL.get())) {
            tooltip.add(Component.literal("Choose any enchantment").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Converts to enchanted book").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Costs player XP levels").withColor(0xAAAAAA));
        }

        // --- Farmhand ---
        else if (stack.is(ModItems.GROWTH_FERTILIZER.get())) {
            tooltip.add(Component.literal("AoE bone meal (5 block radius)").withColor(0x55FF55));
            tooltip.add(Component.literal("Not affected by class debuffs").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.ANIMAL_FEED.get())) {
            tooltip.add(Component.literal("Removes breeding cooldown").withColor(0x55FF55));
        }

        // --- MineCrafter ---
        else if (stack.is(ModItems.REINFORCED_PICKAXE.get())) {
            tooltip.add(Component.literal("Grants Vein Mine to any class").withColor(0xFFAA00));
        } else if (stack.is(ModItems.REINFORCED_AXE.get())) {
            tooltip.add(Component.literal("Grants Timber to any class").withColor(0xFFAA00));
        } else if (stack.is(ModItems.BUILDERS_WAND.get())) {
            tooltip.add(Component.literal("Extend placed blocks in facing direction").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Consumes blocks from inventory").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.ENDER_POUCH.get())) {
            tooltip.add(Component.literal("Right-click for portable ender chest").withColor(0xAA00FF));
            tooltip.add(Component.literal("A compact pouch linked to your ender chest").withColor(0xAAAAAA));
        }

        // --- Smith ---
        else if (stack.is(ModItems.WHETSTONE.get())) {
            tooltip.add(Component.literal("Repairs offhand item by +10%").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Single-use").withColor(0xAAAAAA));
        } else if (stack.is(ModItems.ARMOR_PLATING.get())) {
            tooltip.add(Component.literal("Apply to armor for +1 toughness").withColor(0xAAAAAA));
            tooltip.add(Component.literal("Permanent upgrade").withColor(0xAAAAAA));
        }

        // --- Economy ---
        else if (stack.is(ModItems.MARKET_CATALOG.get())) {
            tooltip.add(Component.literal("Browse all market listings").withColor(0xFFAA00));
            tooltip.add(Component.literal("Read-only marketplace view").withColor(0xAAAAAA));
        } else if (stack.is(ModBlocks.STALL_DEED_ITEM.get())) {
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
