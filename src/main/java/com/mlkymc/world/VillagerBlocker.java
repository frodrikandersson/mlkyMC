package com.mlkymc.world;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public class VillagerBlocker {

    @SubscribeEvent
    public void onEntitySpawn(EntityJoinLevelEvent event) {
        if (event.getEntity().getType() != EntityType.VILLAGER) return;
        if (!(event.getEntity() instanceof Villager villager)) return;

        // Allow shopkeeper villagers (tagged with NoAI and custom name)
        if (villager.isNoAi() && villager.hasCustomName()) {
            return;
        }

        event.setCanceled(true);
    }
}
