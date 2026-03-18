package com.mlkymc.grave;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Handles grave creation on death and grave interaction (right-click to loot).
 */
public class GraveListener {

    private final GraveManager graveManager;

    public GraveListener(GraveManager graveManager) {
        this.graveManager = graveManager;
    }

    /**
     * On player death: create a grave with their inventory.
     * HIGH priority so we capture items before vanilla drops them.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        GraveData grave = graveManager.createGrave(player, level);
        if (grave != null) {
            player.sendSystemMessage(Component.literal("Your items are in a grave at " +
                    grave.pos.getX() + ", " + grave.pos.getY() + ", " + grave.pos.getZ())
                    .withColor(0xFFAA00));
        }
    }

    /**
     * Right-click a grave block to claim items.
     * Owner can access immediately. Others can access after 10 minutes.
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        String dim = level.dimension().identifier().toString();
        GraveData grave = graveManager.getGrave(dim, pos);
        if (grave == null) return;

        event.setCanceled(true);

        boolean isOwner = player.getUUID().equals(grave.ownerUUID);
        long currentTime = level.getGameTime();

        if (isOwner) {
            // Owner can always access
            graveManager.claimGrave(player, grave, level);
            player.sendSystemMessage(Component.literal("Grave claimed! Items restored.").withColor(0x55FF55));
        } else if (grave.canOtherPlayerAccess(currentTime)) {
            // Others can access after 10 minutes
            graveManager.claimGrave(player, grave, level);
            player.sendSystemMessage(Component.literal("Claimed " + grave.ownerName + "'s uncollected grave.").withColor(0xFFAA00));
        } else {
            long remainingTicks = 12000 - (currentTime - grave.deathTime);
            int remainingSeconds = (int) (remainingTicks / 20);
            int mins = remainingSeconds / 60;
            int secs = remainingSeconds % 60;
            player.sendSystemMessage(Component.literal("This is " + grave.ownerName + "'s grave. Accessible in " +
                    mins + "m " + secs + "s.").withColor(0xFF5555));
        }
    }
}
