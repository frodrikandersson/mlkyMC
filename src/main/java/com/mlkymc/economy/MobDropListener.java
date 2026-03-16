package com.mlkymc.economy;

import com.mlkymc.config.MlkyConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.concurrent.ThreadLocalRandom;

public class MobDropListener {

    @SubscribeEvent
    public void onMobDeath(LivingDeathEvent event) {
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;
        if (!(event.getEntity() instanceof Monster)) return;

        Entity deadEntity = event.getEntity();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        double dropChance = MlkyConfig.getMobDropChance();

        if (random.nextDouble() >= dropChance) return;

        int min = MlkyConfig.getMobDropMin();
        int max = MlkyConfig.getMobDropMax();
        int amount = random.nextInt(min, max + 1);

        // Boss mobs drop 5x more
        if (deadEntity instanceof EnderDragon || deadEntity instanceof WitherBoss
                || deadEntity instanceof Warden || deadEntity instanceof ElderGuardian) {
            amount *= 5;
        }

        ItemStack stars = MilkyStar.create(amount);
        if (deadEntity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            deadEntity.spawnAtLocation(serverLevel, stars);
        }
    }
}
