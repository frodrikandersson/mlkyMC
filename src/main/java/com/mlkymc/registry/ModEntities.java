package com.mlkymc.registry;

import com.mlkymc.MlkyMC;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MlkyMC.MOD_ID);

    private static final ResourceKey<EntityType<?>> GRAPPLING_HOOK_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MlkyMC.MOD_ID, "grappling_hook_entity"));

    public static final DeferredHolder<EntityType<?>, EntityType<GrapplingHookEntity>> GRAPPLING_HOOK =
            ENTITIES.register("grappling_hook_entity",
                    () -> EntityType.Builder.<GrapplingHookEntity>of(GrapplingHookEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(4)
                            .updateInterval(5)
                            .build(GRAPPLING_HOOK_KEY));
}
