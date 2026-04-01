package com.mlkymc.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes elytra from end city item frames.
 * Elytra is now only obtainable through crafting.
 */
public class ElytraRemover {

    // Pending item frames to check (entity ID + tick when to check)
    private final List<PendingCheck> pendingChecks = new ArrayList<>();

    private record PendingCheck(int entityId, long checkTick) {}

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel().dimension() != Level.END) return;

        if (event.getEntity().getType() == EntityType.ITEM_FRAME
                && event.getEntity() instanceof ItemFrame frame) {
            // Immediately check
            if (frame.getItem().is(Items.ELYTRA)) {
                frame.setItem(net.minecraft.world.item.ItemStack.EMPTY);
                return;
            }
            // Schedule delayed checks (item might not be loaded yet)
            if (event.getLevel() instanceof ServerLevel sl) {
                long tick = sl.getGameTime();
                synchronized (pendingChecks) {
                    pendingChecks.add(new PendingCheck(frame.getId(), tick + 5));
                    pendingChecks.add(new PendingCheck(frame.getId(), tick + 20));
                    pendingChecks.add(new PendingCheck(frame.getId(), tick + 60));
                }
            }
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel().dimension() != Level.END) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;

        long now = sl.getGameTime();
        synchronized (pendingChecks) {
            var it = pendingChecks.iterator();
            while (it.hasNext()) {
                var check = it.next();
                if (now >= check.checkTick) {
                    it.remove();
                    var entity = sl.getEntity(check.entityId);
                    if (entity instanceof ItemFrame frame && frame.isAlive()) {
                        if (frame.getItem().is(Items.ELYTRA)) {
                            frame.setItem(net.minecraft.world.item.ItemStack.EMPTY);
                        }
                    }
                }
            }
        }
    }
}
