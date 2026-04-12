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

    // Mob star drops are now handled per-class in SpecialEffectHandler (Adventurer exclusive)
    // This listener is intentionally empty to avoid duplicate drops.
}
