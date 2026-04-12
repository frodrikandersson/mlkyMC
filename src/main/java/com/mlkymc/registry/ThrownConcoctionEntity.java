package com.mlkymc.registry;

import com.mlkymc.classes.ConcoctionHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

public class ThrownConcoctionEntity extends ThrowableItemProjectile {

    private int concoctionId = -1;
    private boolean lingering = false;

    public ThrownConcoctionEntity(EntityType<? extends ThrownConcoctionEntity> type, Level level) {
        super(type, level);
    }

    public ThrownConcoctionEntity(Level level, LivingEntity thrower, ItemStack stack) {
        super(ModEntities.THROWN_CONCOCTION.get(), thrower, level, stack);

        // Read concoction data from the item
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            concoctionId = cd.copyTag().getIntOr("mlkymc_concoction_id", -1);
        }
        lingering = stack.is(ModItems.CONCOCTION_LINGERING.get());
    }

    @Override
    protected Item getDefaultItem() {
        return lingering ? ModItems.CONCOCTION_LINGERING.get() : ModItems.CONCOCTION_SPLASH.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide()) return;
        if (!(level() instanceof ServerLevel sl)) return;

        double radius = lingering ? 5.0 : 4.0;
        var pos = result.getLocation();

        // Create fake stack for applyConcoction
        if (concoctionId >= 0) {
            ItemStack fakeStack = new ItemStack(ModItems.CONCOCTION.get());
            var nbt = new CompoundTag();
            nbt.putInt("mlkymc_concoction_id", concoctionId);
            fakeStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

            var entities = sl.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(pos.x - radius, pos.y - radius, pos.z - radius,
                            pos.x + radius, pos.y + radius, pos.z + radius));

            for (var target : entities) {
                ConcoctionHandler.applyConcoction(target, fakeStack);
            }
        }

        // Particles + sound
        sl.sendParticles(ParticleTypes.WITCH,
                pos.x, pos.y + 0.5, pos.z, 30, 1.5, 0.5, 1.5, 0.1);
        sl.playSound(null, net.minecraft.core.BlockPos.containing(pos),
                SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);

        // Lingering: spawn an area effect cloud that re-applies to players who walk into it
        if (lingering && concoctionId >= 0) {
            var cloud = new net.minecraft.world.entity.AreaEffectCloud(sl, pos.x, pos.y, pos.z);
            cloud.setRadius(3.0f);
            cloud.setRadiusOnUse(-0.5f);
            cloud.setWaitTime(10);
            cloud.setDuration(600); // 30 seconds
            cloud.setRadiusPerTick(-3.0f / 600.0f); // shrink over lifetime
            // Purple color for the cloud particles
            // Tag the cloud so our tick handler can apply concoction effects
            cloud.addTag("mlkymc_concoction_cloud");
            cloud.addTag("mlkymc_concoction_cloud_id_" + concoctionId);
            sl.addFreshEntity(cloud);
        }

        discard();
    }

    // Data persists via getPersistentData() automatically
    public void saveConcoctionData() {
        getPersistentData().putInt("ConcoctionId", concoctionId);
        getPersistentData().putBoolean("Lingering", lingering);
    }

    public void loadConcoctionData() {
        concoctionId = getPersistentData().getIntOr("ConcoctionId", -1);
        lingering = getPersistentData().getBooleanOr("Lingering", false);
    }
}
