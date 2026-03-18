package com.mlkymc.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Removes elytra from end city item frames.
 * Elytra is now only obtainable through crafting.
 */
public class ElytraRemover {

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Only run on server, only in The End
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel().dimension() != Level.END) return;

        // Check if it's an item frame holding an elytra
        if (event.getEntity().getType() == EntityType.ITEM_FRAME
                && event.getEntity() instanceof ItemFrame frame) {
            // The item might not be set yet on join — schedule a check
            if (event.getLevel() instanceof ServerLevel sl) {
                sl.getServer().execute(() -> {
                    if (frame.isAlive() && frame.getItem().is(Items.ELYTRA)) {
                        frame.setItem(net.minecraft.world.item.ItemStack.EMPTY);
                    }
                });
            }
        }
    }
}
