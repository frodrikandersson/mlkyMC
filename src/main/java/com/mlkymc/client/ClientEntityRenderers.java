package com.mlkymc.client;

import com.mlkymc.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class ClientEntityRenderers {
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        // Our custom grappling hook entity — uses custom bobber texture + chain line
        event.registerEntityRenderer(ModEntities.GRAPPLING_HOOK.get(), GrapplingHookRenderer::new);
        // Also replace vanilla fishing hook renderer so we can conditionally swap texture
        // (in case player switches from grappling hook to fishing rod mid-cast)
        event.registerEntityRenderer(EntityType.FISHING_BOBBER, GrapplingHookRenderer::new);
    }
}
