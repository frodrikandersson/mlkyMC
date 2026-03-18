package com.mlkymc.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A simple block that rotates to face the player when placed.
 * Supports a custom VoxelShape for non-full-cube blocks.
 */
public class FacingBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FacingBlock> CODEC = simpleCodec(FacingBlock::new);

    private final VoxelShape shape;

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public FacingBlock(Properties properties) {
        this(properties, null);
    }

    public FacingBlock(Properties properties, VoxelShape shape) {
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
        return shape != null ? shape : super.getShape(state, level, pos, context);
    }
}
