package com.mlkymc.registry;

import com.mlkymc.MlkyMC;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MlkyMC.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MLKYMC_TAB =
            TABS.register("mlkymc_tab", () -> CreativeModeTab.builder()
                    .title(Component.literal("mlkyMC"))
                    .icon(() -> new ItemStack(ModItems.MILKY_STAR.get()))
                    .displayItems((params, output) -> {
                        // Currency
                        output.accept(ModItems.MILKY_STAR.get());
                        output.accept(ModItems.MILKY_STAR_JAR_EMPTY.get());
                        output.accept(ModItems.MILKY_STAR_JAR_HALF.get());
                        output.accept(ModItems.MILKY_STAR_JAR_FULL.get());
                        output.accept(ModItems.STALL_DEED.get());
                        output.accept(ModItems.MARKET_CATALOG.get());
                        output.accept(ModItems.DIMENSION_COMPASS.get());

                        // Reagents
                        output.accept(ModItems.WAYSTONE_SHARD.get());
                        output.accept(ModItems.BLESSED_EMBER.get());
                        output.accept(ModItems.LIVING_ESSENCE.get());
                        output.accept(ModItems.RESONANT_CORE.get());
                        output.accept(ModItems.TEMPERED_PLATE.get());

                        // Adventurer
                        output.accept(ModItems.WAYFINDER_COMPASS.get());
                        output.accept(ModItems.WARP_STONE.get());
                        output.accept(ModItems.GRAPPLING_HOOK.get());
                        output.accept(ModItems.GRAPPLING_HOOK_AMMO.get());

                        // Cleric
                        output.accept(ModItems.TOTEM_OF_RESURRECTION.get());
                        output.accept(ModItems.HOLY_WATER.get());
                        output.accept(ModItems.BLESSING_SCROLL.get());
                        output.accept(ModItems.TOME_OF_SOUL_WARDEN.get());

                        // Farmhand
                        output.accept(ModItems.GROWTH_FERTILIZER.get());
                        output.accept(ModItems.ANIMAL_FEED.get());

                        // MineCrafter
                        output.accept(ModItems.REINFORCED_PICKAXE.get());
                        output.accept(ModItems.REINFORCED_AXE.get());
                        output.accept(ModItems.BUILDERS_WAND.get());
                        output.accept(ModItems.ENDER_CHEST_BACKPACK.get());

                        // Smith
                        output.accept(ModItems.WHETSTONE.get());
                        output.accept(ModItems.ARMOR_PLATING.get());

                        // Blocks
                        output.accept(ModBlocks.WARP_ANCHOR_ITEM.get());
                        output.accept(ModBlocks.TROPHY_BASE_ITEM.get());
                        output.accept(ModBlocks.SCARECROW_ITEM.get());
                        output.accept(ModBlocks.SOUL_FORGE_ITEM.get());

                        // Soul Altar blocks
                        output.accept(ModBlocks.SOULSTONE_BRICK_ITEM.get());
                        output.accept(ModBlocks.SOUL_PILLAR_ITEM.get());
                        output.accept(ModBlocks.CONDUIT_CORE_ITEM.get());
                        output.accept(ModBlocks.SOUL_ALTAR_CAPSTONE_ITEM.get());

                        // Trophies
                        output.accept(ModBlocks.TROPHY_WITHER_ITEM.get());
                        output.accept(ModBlocks.TROPHY_DRAGON_ITEM.get());
                        output.accept(ModBlocks.TROPHY_ELDER_GUARDIAN_ITEM.get());
                        output.accept(ModBlocks.TROPHY_WARDEN_ITEM.get());
                        output.accept(ModBlocks.TROPHY_BLAZE_ITEM.get());
                        output.accept(ModBlocks.TROPHY_ENDER_ITEM.get());
                        output.accept(ModBlocks.TROPHY_CREEPER_ITEM.get());
                        output.accept(ModBlocks.TROPHY_SPIDER_ITEM.get());
                        output.accept(ModBlocks.TROPHY_PHANTOM_ITEM.get());
                        output.accept(ModBlocks.TROPHY_WITCH_ITEM.get());
                    })
                    .build());
}
