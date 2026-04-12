package com.mlkymc.radio;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Microphone block — captures nearby player voice and broadcasts to Speaker blocks
 * on the same frequency. Right-click to toggle on/off. Shift+right-click for settings
 * (Phase 2).
 */
public class MicrophoneBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<MicrophoneBlock> CODEC = simpleCodec(MicrophoneBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public MicrophoneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MicrophoneBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        if (sp.isShiftKeyDown()) {
            // Shift+right-click: open settings menu
            if (level.getBlockEntity(pos) instanceof MicrophoneBlockEntity mic) {
                if (!mic.canAccess(sp.getUUID())) {
                    sp.sendSystemMessage(Component.literal(
                            "Locked by " + mic.getLockedByName() + "!").withColor(0xFF5555));
                    return InteractionResult.SUCCESS;
                }
                sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (cid, inv, p) -> new RadioSettingsMenu(cid, inv, sp, pos, false),
                        Component.literal("Microphone Settings")
                ));
            }
            return InteractionResult.SUCCESS;
        }

        // Normal right-click: toggle on/off
        boolean nowActive = !state.getValue(ACTIVE);
        level.setBlock(pos, state.setValue(ACTIVE, nowActive), 3);

        if (level.getBlockEntity(pos) instanceof MicrophoneBlockEntity mic) {
            mic.setActive(nowActive);
            var rm = RadioManager.getInstance();
            if (rm != null) {
                String dim = level.dimension().identifier().toString();
                if (nowActive) {
                    rm.registerMic(pos, dim, mic.getFrequency());
                } else {
                    rm.unregisterMic(pos, dim);
                }
            }
            RadioMusicPlayer.refreshAllRelays();
        }

        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, nowActive ? 1.2f : 0.8f);
        sp.displayClientMessage(Component.literal(nowActive ? "Mic ON (Freq " +
                (level.getBlockEntity(pos) instanceof MicrophoneBlockEntity m ? m.getFrequency() : "?") + ")" : "Mic OFF")
                .withColor(nowActive ? 0x55FF55 : 0xFF5555), true);

        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MicrophoneBlockEntity mic) {
            var rm = RadioManager.getInstance();
            if (rm != null && mic.isActive()) {
                rm.registerMic(pos, level.dimension().identifier().toString(), mic.getFrequency());
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            var rm = RadioManager.getInstance();
            if (rm != null) {
                rm.unregisterMic(pos, level.dimension().identifier().toString());
            }
            RadioMusicPlayer.refreshAllRelays();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
