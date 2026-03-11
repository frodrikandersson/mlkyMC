package com.mlkymc.skills;

import com.mlkymc.economy.MilkyStar;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class SkillListener {

    private final SkillManager skillManager;

    private static final Set<Block> LOG_BLOCKS = Set.of(
            Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG,
            Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG,
            Blocks.CHERRY_LOG, Blocks.PALE_OAK_LOG, Blocks.CRIMSON_STEM, Blocks.WARPED_STEM
    );

    public SkillListener(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockState state = event.getState();
        Block block = state.getBlock();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Farming
        if (block instanceof CropBlock crop && crop.isMaxAge(state)) {
            int farmLevel = skillManager.getLevel(player.getUUID(), SkillType.FARMING);
            skillManager.addXp(player, SkillType.FARMING, 5);

            double starChance = 0.05 + (farmLevel * 0.003);
            if (random.nextDouble() < starChance) {
                dropMilkyStars(level, event.getPos(), 1);
            }

            if (farmLevel >= 10) {
                double bonusCropChance = farmLevel * 0.005;
                if (random.nextDouble() < bonusCropChance) {
                    Block.dropResources(state, level, event.getPos());
                }
            }
        }

        // Woodcutting
        if (LOG_BLOCKS.contains(block)) {
            int woodLevel = skillManager.getLevel(player.getUUID(), SkillType.WOODCUTTING);
            skillManager.addXp(player, SkillType.WOODCUTTING, 5);

            double starChance = 0.03 + (woodLevel * 0.002);
            if (random.nextDouble() < starChance) {
                dropMilkyStars(level, event.getPos(), 1);
            }

            if (woodLevel >= 10) {
                double extraLogChance = woodLevel * 0.004;
                if (random.nextDouble() < extraLogChance) {
                    level.addFreshEntity(new ItemEntity(level,
                            event.getPos().getX() + 0.5, event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5,
                            new ItemStack(block.asItem())));
                }
            }
        }
    }

    @SubscribeEvent
    public void onFish(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        int fishLevel = skillManager.getLevel(player.getUUID(), SkillType.FISHING);
        skillManager.addXp(player, SkillType.FISHING, 10);

        double starChance = 0.10 + (fishLevel * 0.005);
        if (ThreadLocalRandom.current().nextDouble() < starChance) {
            int amount = 1 + (fishLevel / 20);
            player.getInventory().add(MilkyStar.create(amount));
        }
    }

    private void dropMilkyStars(ServerLevel level, BlockPos pos, int amount) {
        ItemStack stars = MilkyStar.create(amount);
        level.addFreshEntity(new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stars));
    }
}
