package com.mlkymc.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Soul Forge — enhanced anvil. Opens anvil GUI.
 * Uses a custom AnvilMenu subclass that doesn't check for vanilla anvil block.
 */
public class SoulForgeBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SoulForgeBlock> CODEC = simpleCodec(SoulForgeBlock::new);

    // Anvil-like shape: wide base, narrow pillar, wider top
    private static final VoxelShape BASE = Block.box(2, 0, 2, 14, 4, 14);
    private static final VoxelShape PILLAR = Block.box(4, 4, 3, 12, 5, 13);
    private static final VoxelShape TOP = Block.box(3, 5, 1, 13, 10, 15);
    private static final VoxelShape SHAPE_NS = Shapes.or(BASE, PILLAR, TOP);

    // Rotated 90 degrees for east/west
    private static final VoxelShape BASE_EW = Block.box(2, 0, 2, 14, 4, 14);
    private static final VoxelShape PILLAR_EW = Block.box(3, 4, 4, 13, 5, 12);
    private static final VoxelShape TOP_EW = Block.box(1, 5, 3, 15, 10, 13);
    private static final VoxelShape SHAPE_EW = Shapes.or(BASE_EW, PILLAR_EW, TOP_EW);

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public SoulForgeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return (facing == Direction.EAST || facing == Direction.WEST) ? SHAPE_EW : SHAPE_NS;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider(
                    (containerId, playerInv, p) -> new SoulForgeMenu(containerId, playerInv, ContainerLevelAccess.create(level, pos)),
                    Component.literal("Soul Forge")
            ));
            sp.awardStat(Stats.INTERACT_WITH_ANVIL);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Custom AnvilMenu that accepts Soul Forge block instead of vanilla anvil.
     */
    private static class SoulForgeMenu extends AnvilMenu {
        private final ContainerLevelAccess access;

        public SoulForgeMenu(int containerId, Inventory playerInv, ContainerLevelAccess access) {
            super(containerId, playerInv, access);
            this.access = access;
        }

        @Override
        public boolean stillValid(Player player) {
            return this.access.evaluate((level, pos) -> {
                if (!level.getBlockState(pos).is(ModBlocks.SOUL_FORGE.get())) return false;
                return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
            }, true).booleanValue();
        }
    }
}
