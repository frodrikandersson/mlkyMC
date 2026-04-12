package net.minecraft.world.entity.animal;

import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class Animal extends AgeableMob {
    protected static final int PARENT_AGE_AFTER_BREEDING = 6000;
    private static final int DEFAULT_IN_LOVE_TIME = 0;
    private int inLove = 0;
    private @Nullable EntityReference<ServerPlayer> loveCause;

    protected Animal(EntityType<? extends Animal> p_27557_, Level p_27558_) {
        super(p_27557_, p_27558_);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
    }

    public static AttributeSupplier.Builder createAnimalAttributes() {
        return Mob.createMobAttributes().add(Attributes.TEMPT_RANGE, 10.0);
    }

    @Override
    protected void customServerAiStep(ServerLevel p_376777_) {
        if (this.getAge() != 0) {
            this.inLove = 0;
        }

        super.customServerAiStep(p_376777_);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.getAge() != 0) {
            this.inLove = 0;
        }

        if (this.inLove > 0) {
            this.inLove--;
            if (this.inLove % 10 == 0) {
                double d0 = this.random.nextGaussian() * 0.02;
                double d1 = this.random.nextGaussian() * 0.02;
                double d2 = this.random.nextGaussian() * 0.02;
                this.level().addParticle(ParticleTypes.HEART, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d0, d1, d2);
            }
        }
    }

    @Override
    protected void actuallyHurt(ServerLevel p_376120_, DamageSource p_341676_, float p_341648_) {
        this.resetLove();
        super.actuallyHurt(p_376120_, p_341676_, p_341648_);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) ? 10.0F : level.getPathfindingCostFromLightLevels(pos);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput p_422575_) {
        super.addAdditionalSaveData(p_422575_);
        p_422575_.putInt("InLove", this.inLove);
        EntityReference.store(this.loveCause, p_422575_, "LoveCause");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput p_422190_) {
        super.readAdditionalSaveData(p_422190_);
        this.inLove = p_422190_.getIntOr("InLove", 0);
        this.loveCause = EntityReference.read(p_422190_, "LoveCause");
    }

    public static boolean checkAnimalSpawnRules(
        EntityType<? extends Animal> entityType, LevelAccessor level, EntitySpawnReason spawnReason, BlockPos pos, RandomSource random
    ) {
        boolean flag = EntitySpawnReason.ignoresLightRequirements(spawnReason) || isBrightEnoughToSpawn(level, pos);
        return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && flag;
    }

    protected static boolean isBrightEnoughToSpawn(BlockAndTintGetter level, BlockPos pos) {
        return level.getRawBrightness(pos, 0) > 8;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel p_376688_) {
        return 1 + this.random.nextInt(3);
    }

    /**
     * Checks if the parameter is an item which this animal can be fed to breed it (wheat, carrots or seeds depending on the animal type)
     */
    public abstract boolean isFood(ItemStack stack);

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (this.isFood(itemstack)) {
            int i = this.getAge();
            if (player instanceof ServerPlayer serverplayer && i == 0 && this.canFallInLove()) {
                this.usePlayerItem(player, hand, itemstack);
                this.setInLove(serverplayer);
                this.playEatingSound();
                return InteractionResult.SUCCESS_SERVER;
            }

            if (this.isBaby()) {
                this.usePlayerItem(player, hand, itemstack);
                this.ageUp(getSpeedUpSecondsWhenFeeding(-i), true);
                this.playEatingSound();
                return InteractionResult.SUCCESS;
            }

            if (this.level().isClientSide()) {
                return InteractionResult.CONSUME;
            }
        }

        return super.mobInteract(player, hand);
    }

    protected void playEatingSound() {
    }

    public boolean canFallInLove() {
        return this.inLove <= 0;
    }

    public void setInLove(@Nullable Player player) {
        this.inLove = 600;
        if (player instanceof ServerPlayer serverplayer) {
            this.loveCause = EntityReference.of(serverplayer);
        }

        this.level().broadcastEntityEvent(this, (byte)18);
    }

    public void setInLoveTime(int inLove) {
        this.inLove = inLove;
    }

    public int getInLoveTime() {
        return this.inLove;
    }

    public @Nullable ServerPlayer getLoveCause() {
        return EntityReference.get(this.loveCause, this.level(), ServerPlayer.class);
    }

    public boolean isInLove() {
        return this.inLove > 0;
    }

    public void resetLove() {
        this.inLove = 0;
    }

    /**
     * Returns {@code true} if the mob is currently able to mate with the specified mob.
     */
    public boolean canMate(Animal otherAnimal) {
        if (otherAnimal == this) {
            return false;
        } else {
            return otherAnimal.getClass() != this.getClass() ? false : this.isInLove() && otherAnimal.isInLove();
        }
    }

    public void spawnChildFromBreeding(ServerLevel level, Animal mate) {
        AgeableMob ageablemob = this.getBreedOffspring(level, mate);
        final net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent event = new net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent(this, mate, ageablemob);
        final boolean cancelled = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled();
        ageablemob = event.getChild();
        if (cancelled) {
            //Reset the "inLove" state for the animals
            this.setAge(6000);
            mate.setAge(6000);
            this.resetLove();
            mate.resetLove();
            return;
        }
        if (ageablemob != null) {
            ageablemob.setBaby(true);
            ageablemob.snapTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
            this.finalizeSpawnChildFromBreeding(level, mate, ageablemob);
            level.addFreshEntityWithPassengers(ageablemob);
        }
    }

    public void finalizeSpawnChildFromBreeding(ServerLevel level, Animal animal, @Nullable AgeableMob baby) {
        Optional.ofNullable(this.getLoveCause()).or(() -> Optional.ofNullable(animal.getLoveCause())).ifPresent(p_466603_ -> {
            p_466603_.awardStat(Stats.ANIMALS_BRED);
            CriteriaTriggers.BRED_ANIMALS.trigger(p_466603_, this, animal, baby);
        });
        this.setAge(6000);
        animal.setAge(6000);
        this.resetLove();
        animal.resetLove();
        level.broadcastEntityEvent(this, (byte)18);
        if (level.getGameRules().get(GameRules.MOB_DROPS)) {
            level.addFreshEntity(new ExperienceOrb(level, this.getX(), this.getY(), this.getZ(), this.getRandom().nextInt(7) + 1));
        }
    }

    @Override
    public void handleEntityEvent(byte p_27562_) {
        if (p_27562_ == 18) {
            for (int i = 0; i < 7; i++) {
                double d0 = this.random.nextGaussian() * 0.02;
                double d1 = this.random.nextGaussian() * 0.02;
                double d2 = this.random.nextGaussian() * 0.02;
                this.level().addParticle(ParticleTypes.HEART, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d0, d1, d2);
            }
        } else {
            super.handleEntityEvent(p_27562_);
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity p_482374_) {
        Direction direction = this.getMotionDirection();
        if (direction.getAxis() == Direction.Axis.Y) {
            return super.getDismountLocationForPassenger(p_482374_);
        } else {
            int[][] aint = DismountHelper.offsetsForDirection(direction);
            BlockPos blockpos = this.blockPosition();
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            for (Pose pose : p_482374_.getDismountPoses()) {
                AABB aabb = p_482374_.getLocalBoundsForPose(pose);

                for (int[] aint1 : aint) {
                    blockpos$mutableblockpos.set(blockpos.getX() + aint1[0], blockpos.getY(), blockpos.getZ() + aint1[1]);
                    double d0 = this.level().getBlockFloorHeight(blockpos$mutableblockpos);
                    if (DismountHelper.isBlockFloorValid(d0)) {
                        Vec3 vec3 = Vec3.upFromBottomCenterOf(blockpos$mutableblockpos, d0);
                        if (DismountHelper.canDismountTo(this.level(), p_482374_, aabb.move(vec3))) {
                            p_482374_.setPose(pose);
                            return vec3;
                        }
                    }
                }
            }

            return super.getDismountLocationForPassenger(p_482374_);
        }
    }
}
