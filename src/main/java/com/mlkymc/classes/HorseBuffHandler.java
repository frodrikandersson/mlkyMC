package com.mlkymc.classes;

import com.mlkymc.classes.ItemBaseValues.AttributeBuff;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Map;

/**
 * Feeds Farmhand-buffed food to horses/donkeys/mules and applies the food's
 * attribute bonuses to the horse. The buff is temporary (uses the food's own
 * mlkymc_buff_duration). When a buffed horse breeds, the foal inherits a
 * permanent 50% genetic echo of the buffs that were active at breeding time.
 *
 * <p>This intercepts {@link PlayerInteractEvent.EntityInteract} before vanilla's
 * {@code AbstractHorse.mobInteract} runs, so vanilla's hardcoded {@code isFood}
 * list is bypassed entirely — any food item with a Farmhand buff is acceptable
 * as horse food. The interaction is gated on sneak so normal right-click still
 * mounts a tame horse.
 *
 * <p>Attribute whitelist: only {@link AttributeBuff#HEALTH}, {@link AttributeBuff#SPEED},
 * and {@link AttributeBuff#JUMP} translate to horses. Other buff types (reach,
 * luck, oxygen, attack damage, etc.) are silently dropped during feeding.
 */
public class HorseBuffHandler {

    // NBT keys stored on horses to track active buffs across chunk unloads
    private static final String NBT_EXPIRY_TICK   = "mlkymc_horse_buff_expiry";
    private static final String NBT_BUFF_HEALTH   = "mlkymc_horse_buff_health";
    private static final String NBT_BUFF_SPEED    = "mlkymc_horse_buff_speed";
    private static final String NBT_BUFF_JUMP     = "mlkymc_horse_buff_jump";
    private static final String NBT_GENERATION    = "mlkymc_horse_buff_generation";

    // Attribute modifier IDs
    private static final Identifier MOD_HEALTH_ID = Identifier.parse("mlkymc:horse_buff_health");
    private static final Identifier MOD_SPEED_ID  = Identifier.parse("mlkymc:horse_buff_speed");
    private static final Identifier MOD_JUMP_ID   = Identifier.parse("mlkymc:horse_buff_jump");

    // Permanent genetic-echo IDs (separate so they don't get cleared on expiry)
    private static final Identifier GENE_HEALTH_ID = Identifier.parse("mlkymc:horse_gene_health");
    private static final Identifier GENE_SPEED_ID  = Identifier.parse("mlkymc:horse_gene_speed");
    private static final Identifier GENE_JUMP_ID   = Identifier.parse("mlkymc:horse_gene_jump");

    // =========================================================================
    // Feed: sneak + right-click a horse/donkey/mule with a Farmhand-buffed food
    // =========================================================================

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        // Sneak-gated so regular right-click can still mount a tame horse
        if (!player.isShiftKeyDown()) return;

        var target = event.getTarget();
        if (!(target instanceof Horse || target instanceof Donkey || target instanceof Mule)) return;
        AbstractHorse horse = (AbstractHorse) target;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (!stack.has(DataComponents.FOOD)) return;
        if (!IngredientBuffHandler.hasBuff(stack)) return;

        // Read the buff map, filter to horse-relevant attributes only
        Map<AttributeBuff, Integer> buffs = IngredientBuffHandler.readBuffs(stack);
        int healthPct = buffs.getOrDefault(AttributeBuff.HEALTH, 0);
        int speedPct  = buffs.getOrDefault(AttributeBuff.SPEED, 0);
        int jumpPct   = buffs.getOrDefault(AttributeBuff.JUMP, 0);

        if (healthPct == 0 && speedPct == 0 && jumpPct == 0) {
            // Food has buffs but none of them are applicable to horses.
            // Don't consume the item — let vanilla handle (or ignore) the interaction.
            return;
        }

        // Apply the buffs as transient attribute modifiers
        long expiryTick = level.getGameTime() + Math.max(200, IngredientBuffHandler.readDuration(stack));
        applyTransientBuffs(horse, healthPct, speedPct, jumpPct);
        writeBuffNbt(horse, expiryTick, healthPct, speedPct, jumpPct);

        // Heal horse based on the food's nutrition (rough 1:2 mapping)
        var food = stack.get(DataComponents.FOOD);
        if (food != null) {
            horse.heal(food.nutrition() * 2.0f);
        }

        // FX: horse eat sound + green speed-line particles (already horse-themed)
        level.playSound(null, horse.getX(), horse.getY(), horse.getZ(),
                SoundEvents.HORSE_EAT, SoundSource.NEUTRAL, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.HEART,
                horse.getX(), horse.getY() + horse.getBbHeight() * 0.75, horse.getZ(),
                6, 0.3, 0.3, 0.3, 0.05);

        // Consume 1 item unless in creative
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        // Feedback to the player
        player.displayClientMessage(Component.literal("Horse enhanced!").withColor(0x55FF55), true);

        // Cancel so vanilla doesn't also try to do something with the interaction
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    // =========================================================================
    // Apply / clear / expire transient buffs
    // =========================================================================

    private void applyTransientBuffs(AbstractHorse horse, int healthPct, int speedPct, int jumpPct) {
        applyPercentModifier(horse.getAttribute(Attributes.MAX_HEALTH), MOD_HEALTH_ID, healthPct);
        applyPercentModifier(horse.getAttribute(Attributes.MOVEMENT_SPEED), MOD_SPEED_ID, speedPct);
        applyPercentModifier(horse.getAttribute(Attributes.JUMP_STRENGTH), MOD_JUMP_ID, jumpPct);

        // Top the horse up to the new max so the HP bonus takes effect visually
        horse.setHealth(horse.getMaxHealth());
    }

    private void applyPercentModifier(AttributeInstance attr, Identifier id, int percent) {
        if (attr == null) return;
        if (attr.getModifier(id) != null) {
            attr.removeModifier(id);
        }
        if (percent <= 0) return;
        attr.addPermanentModifier(new AttributeModifier(
                id, percent / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private void clearTransientBuffs(AbstractHorse horse) {
        removeIfPresent(horse.getAttribute(Attributes.MAX_HEALTH), MOD_HEALTH_ID);
        removeIfPresent(horse.getAttribute(Attributes.MOVEMENT_SPEED), MOD_SPEED_ID);
        removeIfPresent(horse.getAttribute(Attributes.JUMP_STRENGTH), MOD_JUMP_ID);
        horse.getPersistentData().remove(NBT_EXPIRY_TICK);
        horse.getPersistentData().remove(NBT_BUFF_HEALTH);
        horse.getPersistentData().remove(NBT_BUFF_SPEED);
        horse.getPersistentData().remove(NBT_BUFF_JUMP);
        if (horse.getHealth() > horse.getMaxHealth()) {
            horse.setHealth(horse.getMaxHealth());
        }
    }

    private void removeIfPresent(AttributeInstance attr, Identifier id) {
        if (attr != null && attr.getModifier(id) != null) {
            attr.removeModifier(id);
        }
    }

    private void writeBuffNbt(AbstractHorse horse, long expiryTick, int h, int s, int j) {
        CompoundTag nbt = horse.getPersistentData();
        nbt.putLong(NBT_EXPIRY_TICK, expiryTick);
        nbt.putInt(NBT_BUFF_HEALTH, h);
        nbt.putInt(NBT_BUFF_SPEED, s);
        nbt.putInt(NBT_BUFF_JUMP, j);
    }

    // =========================================================================
    // Expiry: scan loaded horses each second, clear buffs past their expiry tick
    // =========================================================================

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % 20 != 0) return; // once per second

        for (var entity : level.getEntities().getAll()) {
            if (!(entity instanceof AbstractHorse horse)) continue;
            if (!(horse instanceof Horse || horse instanceof Donkey || horse instanceof Mule)) continue;
            CompoundTag nbt = horse.getPersistentData();
            if (!nbt.contains(NBT_EXPIRY_TICK)) continue;
            long expiry = nbt.getLongOr(NBT_EXPIRY_TICK, 0L);
            if (level.getGameTime() >= expiry) {
                clearTransientBuffs(horse);
            }
        }
    }

    /**
     * On entity reload (chunk load or server restart):
     * 1. Clear expired transient buffs that timed out while the entity was unloaded.
     * 2. Re-apply permanent genetic modifiers from persistentData. These are stored
     *    in NBT but the runtime attribute modifiers added via addPermanentModifier
     *    are NOT serialized by vanilla — they only exist in memory. So every time a
     *    horse loads back into the world, we must re-read the gene values from NBT
     *    and re-attach the attribute modifiers.
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;
        if (!(horse instanceof Horse || horse instanceof Donkey || horse instanceof Mule)) return;
        CompoundTag nbt = horse.getPersistentData();

        // 1. Transient buff expiry cleanup
        if (nbt.contains(NBT_EXPIRY_TICK)) {
            if (event.getLevel() instanceof ServerLevel level) {
                long expiry = nbt.getLongOr(NBT_EXPIRY_TICK, 0L);
                if (level.getGameTime() >= expiry) {
                    clearTransientBuffs(horse);
                }
            }
        }

        // 2. Re-apply permanent genetic modifiers from stored NBT values
        int geneHealth = nbt.getIntOr("mlkymc_horse_gene_health", 0);
        int geneSpeed  = nbt.getIntOr("mlkymc_horse_gene_speed", 0);
        int geneJump   = nbt.getIntOr("mlkymc_horse_gene_jump", 0);
        if (geneHealth > 0 || geneSpeed > 0 || geneJump > 0) {
            applyPercentModifierPerm(horse.getAttribute(Attributes.MAX_HEALTH), GENE_HEALTH_ID, geneHealth);
            applyPercentModifierPerm(horse.getAttribute(Attributes.MOVEMENT_SPEED), GENE_SPEED_ID, geneSpeed);
            applyPercentModifierPerm(horse.getAttribute(Attributes.JUMP_STRENGTH), GENE_JUMP_ID, geneJump);
        }
    }

    // =========================================================================
    // Breeding: foals inherit a permanent 50% genetic echo of whichever parent(s)
    // had an active buff at breeding time. Echoes stack across generations but
    // attenuate by 50% each time, so infinite-stacking is impossible — by Gen5
    // you're looking at 3.125% inheritance from the original.
    // =========================================================================

    @SubscribeEvent
    public void onBabySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getChild() instanceof AbstractHorse foal)) return;
        if (!(foal instanceof Horse || foal instanceof Donkey || foal instanceof Mule)) return;

        var parentA = event.getParentA();
        var parentB = event.getParentB();

        int geneHealth = 0;
        int geneSpeed  = 0;
        int geneJump   = 0;

        if (parentA instanceof AbstractHorse horseA) {
            int[] fromA = readActiveOrGeneticBuffs(horseA);
            geneHealth = Math.max(geneHealth, fromA[0]);
            geneSpeed  = Math.max(geneSpeed,  fromA[1]);
            geneJump   = Math.max(geneJump,   fromA[2]);
        }
        if (parentB instanceof AbstractHorse horseB) {
            int[] fromB = readActiveOrGeneticBuffs(horseB);
            geneHealth = Math.max(geneHealth, fromB[0]);
            geneSpeed  = Math.max(geneSpeed,  fromB[1]);
            geneJump   = Math.max(geneJump,   fromB[2]);
        }

        // 50% echo — rounded down so trivial parents don't propagate noise
        geneHealth /= 2;
        geneSpeed  /= 2;
        geneJump   /= 2;

        if (geneHealth == 0 && geneSpeed == 0 && geneJump == 0) return;

        // Apply permanent genetic modifiers and stamp the foal's generation
        applyPercentModifierPerm(foal.getAttribute(Attributes.MAX_HEALTH), GENE_HEALTH_ID, geneHealth);
        applyPercentModifierPerm(foal.getAttribute(Attributes.MOVEMENT_SPEED), GENE_SPEED_ID, geneSpeed);
        applyPercentModifierPerm(foal.getAttribute(Attributes.JUMP_STRENGTH), GENE_JUMP_ID, geneJump);

        CompoundTag foalNbt = foal.getPersistentData();
        int parentGen = 0;
        if (parentA instanceof AbstractHorse hA) {
            parentGen = Math.max(parentGen, hA.getPersistentData().getIntOr(NBT_GENERATION, 0));
        }
        if (parentB instanceof AbstractHorse hB) {
            parentGen = Math.max(parentGen, hB.getPersistentData().getIntOr(NBT_GENERATION, 0));
        }
        foalNbt.putInt(NBT_GENERATION, parentGen + 1);
        foalNbt.putInt("mlkymc_horse_gene_health", geneHealth);
        foalNbt.putInt("mlkymc_horse_gene_speed", geneSpeed);
        foalNbt.putInt("mlkymc_horse_gene_jump", geneJump);

        foal.setHealth(foal.getMaxHealth());
    }

    /**
     * Returns the buff percentages this horse has available for inheritance,
     * preferring its currently-active transient buff if present, otherwise its
     * permanent genetic echo from a previous generation.
     */
    private int[] readActiveOrGeneticBuffs(AbstractHorse horse) {
        CompoundTag nbt = horse.getPersistentData();
        int h = 0, s = 0, j = 0;

        // Prefer active transient buff if it hasn't expired yet
        if (nbt.contains(NBT_EXPIRY_TICK) && horse.level() instanceof ServerLevel sl) {
            long expiry = nbt.getLongOr(NBT_EXPIRY_TICK, 0L);
            if (sl.getGameTime() < expiry) {
                h = nbt.getIntOr(NBT_BUFF_HEALTH, 0);
                s = nbt.getIntOr(NBT_BUFF_SPEED, 0);
                j = nbt.getIntOr(NBT_BUFF_JUMP, 0);
            }
        }

        // Add the horse's own genetic echo from its parents, if any
        h += nbt.getIntOr("mlkymc_horse_gene_health", 0);
        s += nbt.getIntOr("mlkymc_horse_gene_speed", 0);
        j += nbt.getIntOr("mlkymc_horse_gene_jump", 0);

        return new int[]{h, s, j};
    }

    private void applyPercentModifierPerm(AttributeInstance attr, Identifier id, int percent) {
        if (attr == null || percent <= 0) return;
        if (attr.getModifier(id) != null) attr.removeModifier(id);
        attr.addPermanentModifier(new AttributeModifier(
                id, percent / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
