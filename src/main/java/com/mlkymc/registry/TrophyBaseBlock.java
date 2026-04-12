package com.mlkymc.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Trophy Base pedestal. Right-click interaction:
 * - Holding trophy + no trophy on base = place trophy on top
 * - Holding trophy + trophy on base = swap them
 * - Empty hand + trophy on base = pick up trophy
 */
public class TrophyBaseBlock extends HorizontalDirectionalBlock {
    @SuppressWarnings("unchecked")
    public static final MapCodec<TrophyBaseBlock> CODEC =
            (MapCodec<TrophyBaseBlock>) (MapCodec<?>) simpleCodec(FacingBlock::new);

    private final VoxelShape shape;

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public TrophyBaseBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        BlockPos abovePos = pos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        boolean hasTrophyAbove = aboveState.getBlock() instanceof TrophyBlock;
        ItemStack held = sp.getMainHandItem();
        boolean holdingTrophy = !held.isEmpty() && held.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof TrophyBlock;

        if (holdingTrophy && !hasTrophyAbove) {
            // Place trophy on top
            placeTrophy(level, abovePos, sp, held);
            return InteractionResult.SUCCESS;
        } else if (holdingTrophy && hasTrophyAbove) {
            // Swap: pick up existing, place held
            ItemStack existingDrop = new ItemStack(aboveState.getBlock().asItem());
            level.removeBlock(abovePos, false);
            placeTrophy(level, abovePos, sp, held);
            if (!sp.getInventory().add(existingDrop)) {
                sp.drop(existingDrop, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        } else if (held.isEmpty() && hasTrophyAbove) {
            // Pick up trophy
            ItemStack trophyItem = new ItemStack(aboveState.getBlock().asItem());
            level.removeBlock(abovePos, false);
            if (!sp.getInventory().add(trophyItem)) {
                sp.drop(trophyItem, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private void placeTrophy(Level level, BlockPos pos, ServerPlayer player, ItemStack held) {
        BlockItem bi = (BlockItem) held.getItem();
        Block trophyBlock = bi.getBlock();
        BlockState trophyState = trophyBlock.defaultBlockState();
        if (trophyState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            trophyState = trophyState.setValue(HorizontalDirectionalBlock.FACING,
                    player.getDirection().getOpposite());
        }
        level.setBlock(pos, trophyState, 3);
        held.shrink(1);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
    }
}
