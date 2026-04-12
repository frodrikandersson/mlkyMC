package com.mlkymc.radio;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.BlockHitResult;

/**
 * Speaker block — plays audio received from Microphone blocks on the same frequency.
 * Right-click shows current frequency (Phase 2: opens settings GUI).
 */
public class SpeakerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<SpeakerBlock> CODEC = simpleCodec(SpeakerBlock::new);

    public SpeakerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        if (level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            if (!speaker.canAccess(sp.getUUID())) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Locked by " + speaker.getLockedByName() + "!").withColor(0xFF5555));
                return InteractionResult.SUCCESS;
            }
            sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (cid, inv, p) -> new RadioSettingsMenu(cid, inv, sp, pos, true),
                    net.minecraft.network.chat.Component.literal("Speaker Settings")
            ));
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            var rm = RadioManager.getInstance();
            if (rm != null) {
                rm.registerSpeaker(pos, level.dimension().identifier().toString(),
                        speaker.getFrequency(), speaker.getRange());
            }
            RadioMusicPlayer.refreshAllRelays();
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
                                         net.minecraft.world.entity.player.Player player) {
        if (!level.isClientSide()) {
            var rm = RadioManager.getInstance();
            if (rm != null) {
                rm.unregisterSpeaker(pos, level.dimension().identifier().toString());
            }
            RadioMusicPlayer.refreshAllRelays();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
