package com.mlkymc.dimension;

import com.mlkymc.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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

    // --- Wayfinder Compass: structure locator only ---
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItemWayfinder(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var heldItem = player.getItemInHand(event.getHand());
        if (!heldItem.is(ModItems.WAYFINDER_COMPASS.get())) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        locateNearestStructure(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlockWayfinder(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var heldItem = player.getItemInHand(event.getHand());
        if (!heldItem.is(ModItems.WAYFINDER_COMPASS.get())) return;
        event.setCanceled(true);
        event.setUseBlock(net.minecraft.util.TriState.FALSE);
        event.setUseItem(net.minecraft.util.TriState.FALSE);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        locateNearestStructure(player);
    }

    // --- Dimension Compass: dimension status only ---
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItemDimension(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var heldItem = player.getItemInHand(event.getHand());
        if (!heldItem.is(ModItems.DIMENSION_COMPASS.get())) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        showDimensionStatus(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlockDimension(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var heldItem = player.getItemInHand(event.getHand());
        if (!heldItem.is(ModItems.DIMENSION_COMPASS.get())) return;
        event.setCanceled(true);
        event.setUseBlock(net.minecraft.util.TriState.FALSE);
        event.setUseItem(net.minecraft.util.TriState.FALSE);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        showDimensionStatus(player);
    }

    private void showDimensionStatus(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("--- Dimension Status ---").withColor(0xAA00FF));

        for (String dim : new String[]{"nether", "end"}) {
            String displayName = dim.equals("end") ? "The End" : "The Nether";
            boolean unlocked = dimensionManager.isUnlocked(dim);
            int progress = dimensionManager.getProgress(dim);
            int goal = dimensionManager.getGoal(dim);

            if (unlocked) {
                long remaining = dimensionManager.getRemainingMillis(dim);
                long hours = remaining / 3600000;
                long mins = (remaining % 3600000) / 60000;
                player.sendSystemMessage(Component.literal(
                        "  " + displayName + ": UNLOCKED (" + hours + "h " + mins + "m remaining)")
                        .withColor(0x55FF55));
            } else {
                player.sendSystemMessage(Component.literal(
                        "  " + displayName + ": LOCKED (" + progress + "/" + goal + " Milky Stars)")
                        .withColor(0xFF5555));
            }
        }

        player.sendSystemMessage(Component.literal(
                "Total contributed: Nether=" + dimensionManager.getTotalContributed("nether") +
                ", End=" + dimensionManager.getTotalContributed("end")).withColor(0xAAAAAA));
    }

    private void locateNearestStructure(ServerPlayer player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        player.sendSystemMessage(Component.literal("Searching for structures...").withColor(0x55FFFF));

        // Use /locate equivalent — search for tagged structures
        @SuppressWarnings("unchecked")
        net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure>[] structureTags = new net.minecraft.tags.TagKey[]{
                net.minecraft.tags.StructureTags.VILLAGE,
                net.minecraft.tags.StructureTags.MINESHAFT,
                net.minecraft.tags.StructureTags.OCEAN_RUIN,
                net.minecraft.tags.StructureTags.SHIPWRECK,
                net.minecraft.tags.StructureTags.RUINED_PORTAL,
        };
        String[] structureNames = {"Village", "Mineshaft", "Ocean Ruin", "Shipwreck", "Ruined Portal"};

        net.minecraft.core.BlockPos closest = null;
        String closestName = "";
        double closestDist = Double.MAX_VALUE;

        var registry = serverLevel.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);

        for (int i = 0; i < structureTags.length; i++) {
            try {
                var holderSet = registry.get(structureTags[i]);
                if (holderSet.isEmpty()) continue;

                var result = serverLevel.getChunkSource().getGenerator()
                        .findNearestMapStructure(serverLevel, holderSet.get(), player.blockPosition(), 50, false);
                if (result != null) {
                    net.minecraft.core.BlockPos pos = result.getFirst();
                    double dist = player.blockPosition().distSqr(pos);
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = pos;
                        closestName = structureNames[i];
                    }
                }
            } catch (Exception ignored) {}
        }

        if (closest != null) {
            int dist = (int) Math.sqrt(closestDist);
            int dx = closest.getX() - player.getBlockX();
            int dz = closest.getZ() - player.getBlockZ();
            String dir = getDirection(dx, dz);
            player.sendSystemMessage(Component.literal("Nearest: " + closestName + " (" + dist + " blocks " + dir + ")")
                    .withColor(0x55FF55));
            player.sendSystemMessage(Component.literal("  at X:" + closest.getX() + " Z:" + closest.getZ())
                    .withColor(0xAABBCC));
        } else {
            player.sendSystemMessage(Component.literal("No structures found nearby.").withColor(0xAAAAAA));
        }
    }

    private String getDirection(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz) * 2) return dx > 0 ? "East" : "West";
        if (Math.abs(dz) > Math.abs(dx) * 2) return dz > 0 ? "South" : "North";
        if (dx > 0 && dz > 0) return "SE";
        if (dx > 0 && dz < 0) return "NE";
        if (dx < 0 && dz > 0) return "SW";
        return "NW";
    }
}
