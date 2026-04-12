package com.mlkymc.registry;

import com.mlkymc.classes.ConcoctionHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Custom potion-like item for Concoctions.
 * Drinkable: hold right-click to drink, applies random effect.
 * Splash: throw on right-click, applies effect in area on impact.
 * Lingering: throw on right-click, applies effect in area on impact.
 */
public class ConcoctionItem extends Item {

    public enum ConcoctionType { DRINKABLE, SPLASH, LINGERING }

    private final ConcoctionType type;

    public ConcoctionItem(Properties properties, ConcoctionType type) {
        super(properties);
        this.type = type;
    }

    public ConcoctionType getConcoctionType() {
        return type;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return type == ConcoctionType.DRINKABLE ? 32 : 0;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return type == ConcoctionType.DRINKABLE ? ItemUseAnimation.DRINK : ItemUseAnimation.NONE;
    }


    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (type == ConcoctionType.DRINKABLE) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        } else {
            // Splash/Lingering: throw a proper concoction projectile
            if (!level.isClientSide() && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
                var projectile = new ThrownConcoctionEntity(sl, sp, stack.copy());
                projectile.shootFromRotation(sp, sp.getXRot(), sp.getYRot(), -20.0f, 0.5f, 1.0f);
                sl.addFreshEntity(projectile);

                sl.playSound(null, sp.blockPosition(),
                        SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS,
                        0.5f, 0.4f / (level.random.nextFloat() * 0.4f + 0.8f));
            }
            stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof ServerPlayer sp) {
            ConcoctionHandler.applyConcoction(sp, stack);
            // Drinking sound — use burp sound like vanilla potions
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        stack.shrink(1);
        return stack;
    }
}
