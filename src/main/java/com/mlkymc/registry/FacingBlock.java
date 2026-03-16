package com.mlkymc.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A simple block that rotates to face the player when placed.
 * Used for blocks with a distinct front face (trophies, scarecrow, soul forge, etc.)
 */
public class FacingBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FacingBlock> CODEC = simpleCodec(FacingBlock::new);

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public FacingBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face toward the player (same as player look direction, so front faces them)
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }
}
