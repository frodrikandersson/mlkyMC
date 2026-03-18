package com.mlkymc.registry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Custom grappling hook projectile. Extends FishingHook with:
 * - Frozen state: when the hook lands on a surface, tick() stops all physics.
 * - Entity hook state: when the hook hits an entity, it follows that entity.
 * - Proper entity type for custom renderer.
 */
public class GrapplingHookEntity extends FishingHook {

    private boolean frozen = false;
    private Entity attachedEntity = null;

    public GrapplingHookEntity(EntityType<? extends FishingHook> type, Level level) {
        super(type, level);
    }

    public GrapplingHookEntity(ServerPlayer player, Level level) {
        super(ModEntities.GRAPPLING_HOOK.get(), level, 0, 0);
        this.setOwner(player);
        player.fishing = this;

        float yRot = player.getYRot();
        float cosYaw = (float) Math.cos(Math.toRadians(-yRot) - Math.PI);
        float sinYaw = (float) Math.sin(Math.toRadians(-yRot) - Math.PI);
        double x = player.getX() - sinYaw * 0.3;
        double y = player.getEyeY();
        double z = player.getZ() - cosYaw * 0.3;
        this.setPos(x, y, z);

        Vec3 look = player.getLookAngle();
        this.setDeltaMovement(look.scale(2.2));
    }

    public void freeze() {
        this.frozen = true;
        this.setDeltaMovement(Vec3.ZERO);
    }

    public boolean isFrozen() {
        return frozen;
    }

    public boolean isAttachedToEntity() {
        return attachedEntity != null && attachedEntity.isAlive();
    }

    public Entity getAttachedEntity() {
        return attachedEntity;
    }

    /**
     * Returns true if the hook has landed (either on a block or an entity).
     */
    public boolean hasLanded() {
        return frozen || isAttachedToEntity();
    }

    private boolean isAdjacentToSolid() {
        net.minecraft.core.BlockPos pos = this.blockPosition();
        Level level = this.level();
        return isSolid(level, pos)
                || isSolid(level, pos.below())
                || isSolid(level, pos.above())
                || isSolid(level, pos.north())
                || isSolid(level, pos.south())
                || isSolid(level, pos.east())
                || isSolid(level, pos.west());
    }

    private static boolean isSolid(Level level, net.minecraft.core.BlockPos pos) {
        var state = level.getBlockState(pos);
        // Not air, not liquid (water/lava)
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    @Override
    public void tick() {
        if (frozen) {
            this.tickCount++;
            if (this.tickCount > 200) {
                this.discard();
            }
            return;
        }

        if (attachedEntity != null) {
            // Attached to an entity — follow it
            this.tickCount++;
            if (!attachedEntity.isAlive()) {
                // Entity died, detach
                attachedEntity = null;
                this.discard();
                return;
            }
            // Move hook to entity's position (centered on their body)
            this.setPos(
                    attachedEntity.getX(),
                    attachedEntity.getY(0.5),
                    attachedEntity.getZ()
            );
            this.setDeltaMovement(Vec3.ZERO);

            if (this.tickCount > 200) {
                this.discard();
            }
            return;
        }

        // Normal FishingHook physics while flying
        super.tick();

        // After super.tick(), check if we hit something
        if (this.tickCount > 3) {
            // Check if vanilla hooked an entity via onHitEntity
            Entity hooked = this.getHookedIn();
            if (hooked != null) {
                attachedEntity = hooked;
                this.setDeltaMovement(Vec3.ZERO);
                return;
            }

            // Check block collision
            if (this.onGround() || this.horizontalCollision || this.verticalCollision) {
                freeze();
                return;
            }

            Vec3 vel = this.getDeltaMovement();
            if (vel.lengthSqr() < 0.5 && isAdjacentToSolid()) {
                freeze();
            }
        }
    }
}
