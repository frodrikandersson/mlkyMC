package com.mlkymc.registry;

import com.mlkymc.MlkyMC;
import com.mlkymc.shop.ShopBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MlkyMC.MOD_ID);

    @SuppressWarnings("unchecked")
    public static final Supplier<BlockEntityType<ShopBlockEntity>> STALL_DEED =
            (Supplier<BlockEntityType<ShopBlockEntity>>) (Supplier<?>) BLOCK_ENTITIES.register("stall_deed",
                    id -> new BlockEntityType<>(
                            ShopBlockEntity::new,
                            ModBlocks.STALL_DEED.get()
                    ));

    @SuppressWarnings("unchecked")
    public static final Supplier<BlockEntityType<com.mlkymc.radio.MicrophoneBlockEntity>> MICROPHONE_BE =
            (Supplier<BlockEntityType<com.mlkymc.radio.MicrophoneBlockEntity>>) (Supplier<?>) BLOCK_ENTITIES.register("microphone",
                    id -> new BlockEntityType<>(
                            com.mlkymc.radio.MicrophoneBlockEntity::new,
                            ModBlocks.MICROPHONE.get()
                    ));

    @SuppressWarnings("unchecked")
    public static final Supplier<BlockEntityType<com.mlkymc.radio.SpeakerBlockEntity>> SPEAKER_BE =
            (Supplier<BlockEntityType<com.mlkymc.radio.SpeakerBlockEntity>>) (Supplier<?>) BLOCK_ENTITIES.register("speaker",
                    id -> new BlockEntityType<>(
                            com.mlkymc.radio.SpeakerBlockEntity::new,
                            ModBlocks.SPEAKER.get(), ModBlocks.RADIO.get()
                    ));
}
