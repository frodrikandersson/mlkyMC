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
 * Radio block — functionally identical to a Speaker (receives audio from
 * Microphones on the same frequency). Uses SpeakerBlockEntity for data storage.
 * Will later support a music playlist mode.
 */
public class RadioBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<RadioBlock> CODEC = simpleCodec(RadioBlock::new);

    public RadioBlock(Properties properties) {
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
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return InteractionResult.PASS;

        if (!(level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)) return InteractionResult.PASS;

        if (sp.isShiftKeyDown()) {
            // Shift+right-click: always open settings GUI
            if (!speaker.canAccess(sp.getUUID())) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Locked by " + speaker.getLockedByName() + "!").withColor(0xFF5555));
                return InteractionResult.SUCCESS;
            }
            sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (cid, inv, p) -> new RadioSettingsMenu(cid, inv, sp, pos, true),
                    net.minecraft.network.chat.Component.literal("Radio Settings")
            ));
        } else {
            // Normal right-click
            if (speaker.isMusicMode() && RadioMusicPlayer.hasMusic()) {
                // In music mode: cycle to next song
                int nextIndex = (speaker.getSongIndex() + 1) % RadioMusicPlayer.getPlaylist().size();
                speaker.setSongIndex(nextIndex);

                var vcPlugin = RadioVoicechatPlugin.getServerApi();
                var vcApi = RadioVoicechatPlugin.getApi();
                if (vcPlugin != null && vcApi != null) {
                    RadioMusicPlayer.stopPlaying(pos);
                    RadioMusicPlayer.startPlaying(vcPlugin, vcApi, pos, sl, nextIndex, speaker.getVolume());
                }

                String songName = RadioMusicPlayer.getPlaylist().get(nextIndex);
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Now playing: " + songName).withColor(0x55FFFF), true);
            } else {
                // In speaker mode or no music: open settings
                if (!speaker.canAccess(sp.getUUID())) {
                    sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Locked by " + speaker.getLockedByName() + "!").withColor(0xFF5555));
                    return InteractionResult.SUCCESS;
                }
                sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (cid, inv, p) -> new RadioSettingsMenu(cid, inv, sp, pos, true),
                        net.minecraft.network.chat.Component.literal("Radio Settings")
                ));
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            // Only register as a speaker if not in music mode
            if (!speaker.isMusicMode()) {
                var rm = RadioManager.getInstance();
                if (rm != null) {
                    rm.registerSpeaker(pos, level.dimension().identifier().toString(),
                            speaker.getFrequency(), speaker.getRange());
                }
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
                                         net.minecraft.world.entity.player.Player player) {
        if (!level.isClientSide()) {
            RadioMusicPlayer.stopPlaying(pos);
            var rm = RadioManager.getInstance();
            if (rm != null) {
                rm.unregisterSpeaker(pos, level.dimension().identifier().toString());
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
