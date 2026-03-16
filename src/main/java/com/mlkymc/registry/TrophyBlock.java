package com.mlkymc.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Trophy block that can only be placed on top of a Trophy Base.
 * Rotates to face the player when placed.
 */
public class TrophyBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<TrophyBlock> CODEC = simpleCodec(TrophyBlock::new);

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public TrophyBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos below = context.getClickedPos().below();
        BlockState belowState = context.getLevel().getBlockState(below);

        // Only allow placement on Trophy Base
        if (!belowState.is(ModBlocks.TROPHY_BASE.get())) {
            var player = context.getPlayer();
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal("Trophies can only be placed on a Trophy Base!").withColor(0xFF5555));
            }
            return null; // Cancel placement
        }

        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(ModBlocks.TROPHY_BASE.get());
    }
}
