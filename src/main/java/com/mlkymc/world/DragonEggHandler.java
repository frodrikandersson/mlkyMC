package com.mlkymc.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Ensures the Ender Dragon always drops a Dragon Egg when killed,
 * even on subsequent respawns (vanilla only spawns the egg on the first kill).
 */
public class DragonEggHandler {

    private long eggPlacementTick = -1;
    private ServerLevel eggPlacementLevel = null;

    @SubscribeEvent
    public void onDragonDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;
        if (!(dragon.level() instanceof ServerLevel level)) return;

        // Schedule egg placement 220 ticks after death (after death animation)
        eggPlacementTick = level.getGameTime() + 220;
        eggPlacementLevel = level;
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (eggPlacementTick < 0 || eggPlacementLevel == null) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level != eggPlacementLevel) return;

        if (level.getGameTime() >= eggPlacementTick) {
            BlockPos origin = BlockPos.ZERO;
            BlockPos eggPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EndPodiumFeature.getLocation(origin));

            if (!level.getBlockState(eggPos).is(Blocks.DRAGON_EGG)) {
                level.setBlockAndUpdate(eggPos, Blocks.DRAGON_EGG.defaultBlockState());
            }

            eggPlacementTick = -1;
            eggPlacementLevel = null;
        }
    }
}
