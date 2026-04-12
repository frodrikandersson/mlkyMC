package com.mlkymc.shop;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 2-block tall shop stall block. Bottom half holds the BlockEntity with shop data.
 * Right-clicking either half opens the shop GUI.
 */
public class ShopBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<ShopBlock> CODEC = simpleCodec(ShopBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape SHAPE_BOTTOM = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape SHAPE_TOP = Block.box(0, 0, 0, 16, 16, 16);

    public ShopBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? SHAPE_BOTTOM : SHAPE_TOP;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxY() && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);

        // Auto-create market stall when a player places the block
        if (!level.isClientSide() && placer instanceof ServerPlayer sp) {
            var marketManager = com.mlkymc.MlkyMC.getMarketManager();
            if (marketManager != null) {
                String uuid = sp.getStringUUID();

                // Check if player already has a stall
                if (marketManager.getStall(uuid) != null) {
                    sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "You already have a stall! Pick it up first.").withColor(0xFF5555));
                    // Break the block and give it back
                    level.destroyBlock(pos.above(), false);
                    level.destroyBlock(pos, false);
                    sp.getInventory().add(new net.minecraft.world.item.ItemStack(
                            com.mlkymc.registry.ModBlocks.STALL_DEED_ITEM.get()));
                    return;
                }

                // Register stall in market system
                String stallName = sp.getName().getString() + "'s Stall";
                var stallData = new com.mlkymc.market.MarketManager.StallData();
                stallData.ownerName = sp.getName().getString();
                stallData.stallName = stallName;
                stallData.dimension = level.dimension().identifier().toString();
                stallData.x = pos.getX() + 0.5;
                stallData.y = pos.getY();
                stallData.z = pos.getZ() + 0.5;
                stallData.yaw = sp.getYRot();
                marketManager.registerStall(uuid, stallData);

                // Set shop ID on block entity
                var be = level.getBlockEntity(pos);
                if (be instanceof ShopBlockEntity shopBE) {
                    shopBE.setShopId("stall_" + uuid);
                }

                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Stall placed! Other players can right-click to browse your items.").withColor(0x55FF55));
            }
        }
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                        net.minecraft.world.item.ItemStack tool, boolean willHarvest,
                                        net.minecraft.world.level.material.FluidState fluid) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            var be = level.getBlockEntity(lowerPos);

            if (be instanceof ShopBlockEntity shopBE) {
                String shopId = shopBE.getShopId();
                if (shopId.startsWith("stall_")) {
                    String ownerUuid = shopId.substring(6);
                    var marketManager = com.mlkymc.MlkyMC.getMarketManager();
                    if (marketManager != null) {
                        // Prevent breaking if there are listings
                        if (!marketManager.getStallListings(ownerUuid).isEmpty()) {
                            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "Remove all listings first before breaking your stall.").withColor(0xFF5555));
                            return false; // Actually cancel the break
                        }
                        // Unregister stall from market system
                        marketManager.removeStallData(ownerUuid);
                    }
                }
            }
        }

        // Proceed with normal destruction — drop exactly 1 item, clear both halves
        DoubleBlockHalf half = state.getValue(HALF);
        BlockPos lowerPos = half == DoubleBlockHalf.UPPER ? pos.below() : pos;
        BlockPos upperPos = half == DoubleBlockHalf.UPPER ? pos : pos.above();

        // Drop the item from lower half
        if (!level.isClientSide()) {
            Block.popResource(level, lowerPos, new net.minecraft.world.item.ItemStack(this));
        }

        // Clear both halves silently
        level.setBlock(upperPos, Blocks.AIR.defaultBlockState(), 35);
        level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), 35);

        return false; // We handled everything manually
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return true;
    }

    // --- BlockEntity (only on the lower half) ---

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return new ShopBlockEntity(pos, state);
        }
        return null;
    }

    // --- Right-click: open shop GUI ---

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        // If clicking the top half, redirect to bottom half's block entity
        BlockPos bePos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        var be = level.getBlockEntity(bePos);
        if (!(be instanceof ShopBlockEntity shopBE)) return InteractionResult.PASS;

        String shopId = shopBE.getShopId();
        if (shopId.isEmpty()) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("This stall has no shop assigned.").withColor(0xFFAA00));
            return InteractionResult.SUCCESS;
        }

        // Player stall (shopId = "stall_<uuid>")
        String stallOwnerUuid = shopId.startsWith("stall_") ? shopId.substring(6) : null;
        if (stallOwnerUuid == null) return InteractionResult.PASS;

        var marketManager = com.mlkymc.MlkyMC.getMarketManager();
        if (marketManager == null) return InteractionResult.PASS;

        // Owner crouching = pick up stall
        if (stallOwnerUuid.equals(sp.getStringUUID()) && sp.isShiftKeyDown()) {
            if (!marketManager.getStallListings(stallOwnerUuid).isEmpty()) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Remove all listings first before picking up your stall.").withColor(0xFF5555));
                return InteractionResult.SUCCESS;
            }
            marketManager.removeStall(sp);
            sp.getInventory().add(new net.minecraft.world.item.ItemStack(
                    com.mlkymc.registry.ModBlocks.STALL_DEED_ITEM.get()));
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Stall picked up! Deed returned to your inventory.").withColor(0x55FF55));
            return InteractionResult.SUCCESS;
        }

        // Show pickup hint to owner
        if (stallOwnerUuid.equals(sp.getStringUUID())) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Crouch + RMB to pick up your stall").withColor(0xAAAAAA), true);
        }

        com.mlkymc.market.MarketManager.StallData stall = marketManager.getStall(stallOwnerUuid);
        String title = stall != null ? stall.stallName : "Player Stall";
        sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (containerId, playerInv, p) -> com.mlkymc.market.MarketMenu.stall(
                        containerId, playerInv, sp, marketManager, stallOwnerUuid, 0),
                net.minecraft.network.chat.Component.literal(title)
        ));

        return InteractionResult.SUCCESS;
    }
}
