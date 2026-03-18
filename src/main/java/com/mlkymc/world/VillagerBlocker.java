package com.mlkymc.world;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerBlocker {
    private static final Logger LOGGER = LoggerFactory.getLogger("mlkymc");

    @SubscribeEvent
    public void onEntitySpawn(EntityJoinLevelEvent event) {
        // Only block on server side — client entity data (NoAI, name, tags) hasn't synced yet
        if (event.getLevel().isClientSide()) return;

        if (event.getEntity().getType() != EntityType.VILLAGER) return;
        if (!(event.getEntity() instanceof Villager villager)) return;

        // Allow shopkeeper/stall villagers (tagged with NoAI and custom name, or mlkymc tags)
        if (villager.isNoAi() && villager.hasCustomName()) {
            return;
        }
        if (villager.getTags().contains("mlkymc_stall")) {
            return;
        }

        LOGGER.debug("[VillagerBlocker] Blocking villager: noAI={}, name={}, tags={}",
                villager.isNoAi(), villager.hasCustomName() ? villager.getName().getString() : "none",
                villager.getTags());
        event.setCanceled(true);
    }
}
