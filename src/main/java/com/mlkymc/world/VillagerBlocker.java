package com.mlkymc.world;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public class VillagerBlocker {
    private static final Logger LOGGER = LoggerFactory.getLogger("mlkymc");

    @SubscribeEvent
    public void onEntitySpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity().getType() != EntityType.VILLAGER) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        var villager = event.getEntity();
        double x = villager.getX();
        double y = villager.getY();
        double z = villager.getZ();

        // Check if a player is nearby — conversion requires a player to cure the zombie villager.
        // Structure/natural spawns happen in unloaded chunks or far from players.
        boolean playerNearby = sl.players().stream()
                .anyMatch(p -> p.distanceToSqr(x, y, z) < 30 * 30);

        if (playerNearby) {
            // Drop Milky Stars (30-60)
            int stars = 30 + ThreadLocalRandom.current().nextInt(31);
            var starStack = com.mlkymc.economy.MilkyStar.create(stars);
            sl.addFreshEntity(new ItemEntity(sl, x, y + 0.5, z, starStack));

            // 1/100 chance for Totem of Undying
            if (ThreadLocalRandom.current().nextInt(100) == 0) {
                sl.addFreshEntity(new ItemEntity(sl, x, y + 0.5, z, new ItemStack(Items.TOTEM_OF_UNDYING)));
            }

            // Broadcast message to nearby players
            for (var player : sl.players()) {
                if (player.distanceToSqr(x, y, z) < 50 * 50) {
                    player.sendSystemMessage(Component.literal(
                            "\"Thank you for saving my soul... take this as a token of my gratitude.\"")
                            .withColor(0x55FF55));
                }
            }

            LOGGER.info("[VillagerBlocker] Zombie villager cured at {},{},{} — dropped {} Milky Stars",
                    (int) x, (int) y, (int) z, stars);
        }

        event.setCanceled(true);
    }

}
