package com.mlkymc.registry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 2-block tall scarecrow. Bottom half renders the full model.
 * Top half is invisible (for collision/placement blocking only).
 * Breaking either half breaks both and drops the item from the bottom.
 */
public class ScarecrowBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ScarecrowBlock> CODEC = simpleCodec(ScarecrowBlock::new);

    /** Radius in blocks that the scarecrow prevents hostile mob spawns */
    public static final int SPAWN_PREVENTION_RADIUS = 32;

    /** Tracks placed scarecrow positions for efficient spawn checks. Persisted to
     *  a JSON file so the set survives server restarts. */
    private static final java.util.Set<BlockPos> activeScarecrows = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static java.nio.file.Path saveFile;
    private static boolean dirty = false;

    /** Called from MlkyMC.onServerStarting to set the save path and load persisted data. */
    public static void reload(java.nio.file.Path worldDataDir) {
        saveFile = worldDataDir.resolve("mlkymc_scarecrows.json");
        activeScarecrows.clear();
        if (java.nio.file.Files.exists(saveFile)) {
            try (var reader = java.nio.file.Files.newBufferedReader(saveFile)) {
                var type = new com.google.gson.reflect.TypeToken<java.util.Set<long[]>>() {}.getType();
                java.util.Set<long[]> loaded = new com.google.gson.Gson().fromJson(reader, type);
                if (loaded != null) {
                    for (long[] coords : loaded) {
                        if (coords.length == 3) {
                            activeScarecrows.add(new BlockPos((int) coords[0], (int) coords[1], (int) coords[2]));
                        }
                    }
                }
            } catch (Exception e) {
                com.mlkymc.MlkyMC.LOGGER.warn("[Scarecrow] Failed to load scarecrow positions", e);
            }
        }
        com.mlkymc.MlkyMC.LOGGER.info("[Scarecrow] Loaded {} scarecrow positions", activeScarecrows.size());
    }

    /** Save the current set to disk. Called periodically and on server stop. */
    public static void save() {
        if (saveFile == null || !dirty) return;
        dirty = false;
        try {
            java.nio.file.Files.createDirectories(saveFile.getParent());
            var positions = new java.util.ArrayList<long[]>();
            for (BlockPos pos : activeScarecrows) {
                positions.add(new long[]{pos.getX(), pos.getY(), pos.getZ()});
            }
            try (var writer = java.nio.file.Files.newBufferedWriter(saveFile)) {
                new com.google.gson.Gson().toJson(positions, writer);
            }
        } catch (Exception e) {
            com.mlkymc.MlkyMC.LOGGER.warn("[Scarecrow] Failed to save scarecrow positions", e);
        }
    }

    public static void registerScarecrow(BlockPos pos) {
        if (activeScarecrows.add(pos.immutable())) {
            dirty = true;
            save();
        }
    }

    public static void unregisterScarecrow(BlockPos pos) {
        if (activeScarecrows.remove(pos)) {
            dirty = true;
            save();
        }
    }

    public static boolean isProtectedByScarecrow(BlockPos spawnPos) {
        double rSq = SPAWN_PREVENTION_RADIUS * SPAWN_PREVENTION_RADIUS;
        for (BlockPos scarecrowPos : activeScarecrows) {
            if (scarecrowPos.distSqr(spawnPos) <= rSq) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInScarecrowRange(BlockPos pos) {
        return isProtectedByScarecrow(pos);
    }

    /**
     * Tick crop growth boost around scarecrows. Called from level tick.
     * 10% bonus growth — gives one extra random tick per ~10 ticks to crops in range.
     * Non-stackable: multiple scarecrows don't multiply the effect.
     */
    public static void tickCropBoost(net.minecraft.server.level.ServerLevel level) {
        if (activeScarecrows.isEmpty()) return;
        // Run every 10 ticks (0.5 seconds)
        if (level.getGameTime() % 10 != 0) return;

        for (BlockPos scarecrowPos : activeScarecrows) {
            // Only process if the chunk is loaded
            if (!level.isLoaded(scarecrowPos)) continue;

            // Pick a random crop in range to give a bonus tick
            // Instead of iterating all blocks, pick random positions (efficient)
            var random = level.getRandom();
            for (int attempt = 0; attempt < 3; attempt++) {
                int dx = random.nextInt(SPAWN_PREVENTION_RADIUS * 2 + 1) - SPAWN_PREVENTION_RADIUS;
                int dz = random.nextInt(SPAWN_PREVENTION_RADIUS * 2 + 1) - SPAWN_PREVENTION_RADIUS;
                // Scan from ground level up
                BlockPos checkPos = new BlockPos(
                        scarecrowPos.getX() + dx,
                        scarecrowPos.getY() + random.nextInt(5) - 2,
                        scarecrowPos.getZ() + dz);

                if (!level.isLoaded(checkPos)) continue;

                var state = level.getBlockState(checkPos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock
                        || state.getBlock() instanceof net.minecraft.world.level.block.StemBlock
                        || state.getBlock() instanceof net.minecraft.world.level.block.SugarCaneBlock
                        || state.getBlock() instanceof net.minecraft.world.level.block.CactusBlock) {
                    state.randomTick(level, checkPos, random);
                }
            }
        }
    }
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    // Bottom half: legs + body + arms (arms extend along X in model space)
    private static final VoxelShape SHAPE_BOTTOM = net.minecraft.world.phys.shapes.Shapes.or(
            Block.box(6, 0, 7, 10, 8, 11),    // legs (post)
            Block.box(1, 8, 3, 15, 16, 13)    // body + arms area
    );
    // Top half: head
    private static final VoxelShape SHAPE_TOP = Block.box(0, 0, 0, 16, 16, 16);

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public ScarecrowBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
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

        // Check if there's room for the top half
        if (pos.getY() < level.getMaxY() && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection())
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null; // Can't place, no room
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        // Place the upper half
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        // Register for spawn prevention
        if (!level.isClientSide()) {
            registerScarecrow(pos);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        DoubleBlockHalf half = state.getValue(HALF);
        BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState otherState = level.getBlockState(otherPos);

        if (otherState.is(this) && otherState.getValue(HALF) != half) {
            level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
            level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
        }

        // Unregister spawn prevention (use bottom pos regardless of which half was broken)
        if (!level.isClientSide()) {
            BlockPos bottomPos = half == DoubleBlockHalf.LOWER ? pos : pos.below();
            unregisterScarecrow(bottomPos);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return true;
    }
}
