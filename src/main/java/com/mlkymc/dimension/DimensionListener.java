package com.mlkymc.dimension;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

public class DimensionListener {

    private final DimensionManager dimensionManager;

    public DimensionListener(DimensionManager dimensionManager) {
        this.dimensionManager = dimensionManager;
    }

    @SubscribeEvent
    public void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String dimension = null;
        if (event.getDimension() == Level.NETHER) {
            dimension = "nether";
        } else if (event.getDimension() == Level.END) {
            dimension = "end";
        }

        if (dimension == null) return;

        if (!dimensionManager.isUnlocked(dimension)) {
            event.setCanceled(true);

            int progress = dimensionManager.getProgress(dimension);
            int goal = dimensionManager.getGoal(dimension);
            String displayName = dimension.equals("end") ? "The End" : "The Nether";

            player.sendSystemMessage(Component.literal(displayName + " is locked!").withColor(0xFF5555));
            player.sendSystemMessage(Component.literal("Community progress: " + progress + "/" + goal + " Milky Stars").withColor(0xFFAA00));
            player.sendSystemMessage(Component.literal("Use /mlkymc dimension contribute " + dimension + " <amount> to help unlock it!").withColor(0xFFFF55));
        }
    }
}
