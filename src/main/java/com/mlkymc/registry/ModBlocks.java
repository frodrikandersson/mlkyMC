package com.mlkymc.registry;

import com.mlkymc.MlkyMC;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All custom blocks registered by mlkyMC.
 * Main blocks use FacingBlock (directional placement).
 * Trophies use TrophyBlock (directional + only on Trophy Base).
 */
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MlkyMC.MOD_ID);

    // --- Main Blocks (FacingBlock for directional placement) ---

    public static final DeferredBlock<Block> WARP_ANCHOR = BLOCKS.register(
            "warp_anchor",
            id -> new FacingBlock(BlockBehaviour.Properties.of()
                    .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.5f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.LODESTONE)
                    .noOcclusion()
                    .lightLevel(state -> 5)));

    public static final DeferredBlock<Block> TROPHY_BASE = BLOCKS.register(
            "trophy_base",
            id -> new FacingBlock(BlockBehaviour.Properties.of()
                    .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, id))
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final DeferredBlock<Block> SCARECROW = BLOCKS.register(
            "scarecrow",
            id -> new ScarecrowBlock(BlockBehaviour.Properties.of()
                    .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(1.0f)
                    .sound(SoundType.GRASS)
                    .noOcclusion()));

    public static final DeferredBlock<Block> SOUL_FORGE = BLOCKS.register(
            "soul_forge",
            id -> new SoulForgeBlock(BlockBehaviour.Properties.of()
                    .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(5.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.ANVIL)
                    .noOcclusion()));

    // --- Trophies (TrophyBlock: directional + only on Trophy Base) ---

    private static BlockBehaviour.Properties trophyProps(net.minecraft.resources.Identifier id) {
        return BlockBehaviour.Properties.of()
                .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, id))
                .mapColor(MapColor.GOLD)
                .strength(1.5f)
                .sound(SoundType.STONE)
                .noOcclusion();
    }

    public static final DeferredBlock<Block> TROPHY_WITHER = BLOCKS.register("trophy_wither", id -> new TrophyBlock(trophyProps(id)));
    public static final DeferredBlock<Block> TROPHY_DRAGON = BLOCKS.register("trophy_dragon", id -> new TrophyBlock(trophyProps(id)));
    public static final DeferredBlock<Block> TROPHY_ELDER_GUARDIAN = BLOCKS.register("trophy_elder_guardian", id -> new TrophyBlock(trophyProps(id)));
    public static final DeferredBlock<Block> TROPHY_WARDEN = BLOCKS.register("trophy_warden", id -> new TrophyBlock(trophyProps(id)));
    public static final DeferredBlock<Block> TROPHY_BLAZE = BLOCKS.register("trophy_blaze", id -> new TrophyBlock(trophyProps(id)));
    public static final DeferredBlock<Block> TROPHY_ENDER = BLOCKS.register("trophy_ender", id -> new TrophyBlock(trophyProps(id)));
    public static final DeferredBlock<Block> TROPHY_CREEPER = BLOCKS.register("trophy_creeper", id -> new TrophyBlock(trophyProps(id)));
    public static final DeferredBlock<Block> TROPHY_SPIDER = BLOCKS.register("trophy_spider", id -> new TrophyBlock(trophyProps(id)));
    public static final DeferredBlock<Block> TROPHY_PHANTOM = BLOCKS.register("trophy_phantom", id -> new TrophyBlock(trophyProps(id)));
    public static final DeferredBlock<Block> TROPHY_WITCH = BLOCKS.register("trophy_witch", id -> new TrophyBlock(trophyProps(id)));

    // --- Block Items ---

    public static final DeferredItem<BlockItem> WARP_ANCHOR_ITEM = ModItems.ITEMS.registerSimpleBlockItem(WARP_ANCHOR);
    public static final DeferredItem<BlockItem> TROPHY_BASE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TROPHY_BASE);
    public static final DeferredItem<BlockItem> SCARECROW_ITEM = ModItems.ITEMS.registerSimpleBlockItem(SCARECROW);
    public static final DeferredItem<BlockItem> SOUL_FORGE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(SOUL_FORGE);

    public static final DeferredItem<BlockItem> TROPHY_WITHER_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TROPHY_WITHER);
    public static final DeferredItem<BlockItem> TROPHY_DRAGON_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TROPHY_DRAGON);
    public static final DeferredItem<BlockItem> TROPHY_ELDER_GUARDIAN_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TROPHY_ELDER_GUARDIAN);
    public static final DeferredItem<BlockItem> TROPHY_WARDEN_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TROPHY_WARDEN);
    public static final DeferredItem<BlockItem> TROPHY_BLAZE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TROPHY_BLAZE);
    public static final DeferredItem<BlockItem> TROPHY_ENDER_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TROPHY_ENDER);
    public static final DeferredItem<BlockItem> TROPHY_CREEPER_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TROPHY_CREEPER);
    public static final DeferredItem<BlockItem> TROPHY_SPIDER_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TROPHY_SPIDER);
    public static final DeferredItem<BlockItem> TROPHY_PHANTOM_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TROPHY_PHANTOM);
    public static final DeferredItem<BlockItem> TROPHY_WITCH_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TROPHY_WITCH);
}
