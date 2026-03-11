package com.mlkymc.economy;

import com.mlkymc.config.MlkyConfig;
import com.mlkymc.skills.SkillManager;
import com.mlkymc.skills.SkillType;
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

    private final SkillManager skillManager;

    public MobDropListener(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @SubscribeEvent
    public void onMobDeath(LivingDeathEvent event) {
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;
        if (!(event.getEntity() instanceof Monster)) return;

        Entity deadEntity = event.getEntity();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Calculate drop chance with combat skill bonus
        int combatLevel = skillManager.getLevel(player.getUUID(), SkillType.COMBAT);
        double dropChance = MlkyConfig.getMobDropChance() + (combatLevel * 0.005);

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

        // Grant combat XP based on mob type
        int xp = 10;
        if (deadEntity instanceof EnderDragon || deadEntity instanceof WitherBoss) {
            xp = 100;
        } else if (deadEntity instanceof Warden || deadEntity instanceof ElderGuardian) {
            xp = 50;
        }
        skillManager.addXp(player, SkillType.COMBAT, xp);
    }
}
