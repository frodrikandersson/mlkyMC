package com.mlkymc.classes;

import com.mlkymc.classes.ItemBaseValues.AttributeBuff;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

/**
 * Applies percentage-based attribute modifiers when food with mlkymc buffs is eaten.
 *
 * Uses ADD_MULTIPLIED_TOTAL operation which inherently scales with the player's
 * current base+equipment stats. When armor changes, the modifier recalculates
 * automatically — no manual update needed.
 */
public class IngredientBuffApplier {

    private static final String MODIFIER_PREFIX = "mlkymc:food_buff_";

    // Track active buffs per player for cleanup on expiry
    // UUID -> Map<AttributeBuff, expiry game tick>
    private static final Map<UUID, Map<AttributeBuff, Long>> activeBuffs = new HashMap<>();

    @SubscribeEvent
    public void onFoodEaten(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        var stack = event.getItem();
        if (!IngredientBuffHandler.hasBuff(stack)) return;

        var buffs = IngredientBuffHandler.readBuffs(stack);
        int durationTicks = IngredientBuffHandler.readDuration(stack);
        if (buffs.isEmpty() || durationTicks <= 0) return;

        long expiryTick = player.level().getGameTime() + durationTicks;

        // Apply each buff as an attribute modifier
        for (var entry : buffs.entrySet()) {
            AttributeBuff type = entry.getKey();
            int percent = entry.getValue();
            double multiplier = percent / 100.0; // 10% = 0.10

            applyModifier(player, type, multiplier);

            // Track expiry
            activeBuffs.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                    .put(type, expiryTick);
        }

        // Notify player
        StringBuilder msg = new StringBuilder("Food buff active: ");
        boolean first = true;
        for (var entry : buffs.entrySet()) {
            if (!first) msg.append(", ");
            String sign = isInvertedBuff(entry.getKey()) ? "-" : "+";
            msg.append(sign).append(entry.getValue()).append("% ")
                    .append(IngredientBuffHandler.formatBuffName(entry.getKey()));
            first = false;
        }
        msg.append(" (").append(durationTicks / 20).append("s)");
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(msg.toString()).withColor(0x55FF55), true);
    }

    /**
     * Check for expired buffs every second and remove them.
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        if (player.level().getGameTime() % 20 != 0) return; // Once per second

        var playerBuffs = activeBuffs.get(player.getUUID());
        if (playerBuffs == null || playerBuffs.isEmpty()) return;

        long now = player.level().getGameTime();
        var expired = new ArrayList<AttributeBuff>();

        for (var entry : playerBuffs.entrySet()) {
            if (now >= entry.getValue()) {
                expired.add(entry.getKey());
            }
        }

        for (AttributeBuff type : expired) {
            removeModifier(player, type);
            playerBuffs.remove(type);
        }

        if (!expired.isEmpty() && playerBuffs.isEmpty()) {
            activeBuffs.remove(player.getUUID());
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Food buff expired.").withColor(0xAAAAAA), true);
        }
    }

    /**
     * Apply a percentage-based attribute modifier for the given buff type.
     */
    private void applyModifier(ServerPlayer player, AttributeBuff type, double multiplier) {
        var attrHolder = getAttributeForBuff(type);
        if (attrHolder == null) return;

        var attrInstance = player.getAttribute(attrHolder);
        if (attrInstance == null) return;

        Identifier modifierId = Identifier.parse(MODIFIER_PREFIX + type.name().toLowerCase());

        // Remove existing modifier of same type (refresh)
        attrInstance.removeModifier(modifierId);

        // Inverted buffs (less fall damage, less burning) use negative multiplier
        double actualMultiplier = isInvertedBuff(type) ? -multiplier : multiplier;

        // Add new modifier: ADD_MULTIPLIED_TOTAL scales with base+equipment
        attrInstance.addTransientModifier(new AttributeModifier(
                modifierId, actualMultiplier,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /**
     * Remove the attribute modifier for the given buff type.
     */
    private void removeModifier(ServerPlayer player, AttributeBuff type) {
        var attrHolder = getAttributeForBuff(type);
        if (attrHolder == null) return;

        var attrInstance = player.getAttribute(attrHolder);
        if (attrInstance == null) return;

        Identifier modifierId = Identifier.parse(MODIFIER_PREFIX + type.name().toLowerCase());
        attrInstance.removeModifier(modifierId);
    }

    /**
     * Map AttributeBuff enum to the vanilla Attribute holder.
     */
    private static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> getAttributeForBuff(AttributeBuff type) {
        return switch (type) {
            case HEALTH -> Attributes.MAX_HEALTH;
            case DAMAGE -> Attributes.ATTACK_DAMAGE;
            case ATTACK_SPEED -> Attributes.ATTACK_SPEED;
            case ARMOR -> Attributes.ARMOR;
            case ARMOR_TOUGHNESS -> Attributes.ARMOR_TOUGHNESS;
            case SPEED -> Attributes.MOVEMENT_SPEED;
            case JUMP -> Attributes.JUMP_STRENGTH;
            case MINING_SPEED -> Attributes.BLOCK_BREAK_SPEED;
            case REACH -> Attributes.BLOCK_INTERACTION_RANGE;
            case ENTITY_REACH -> Attributes.ENTITY_INTERACTION_RANGE;
            case LUCK -> Attributes.LUCK;
            case KNOCKBACK_RESISTANCE -> Attributes.KNOCKBACK_RESISTANCE;
            case OXYGEN -> Attributes.OXYGEN_BONUS;
            case SAFE_FALL -> Attributes.SAFE_FALL_DISTANCE;
            case FALL_DAMAGE -> Attributes.FALL_DAMAGE_MULTIPLIER;
            case STEP_HEIGHT -> Attributes.STEP_HEIGHT;
            case BURNING_TIME -> Attributes.BURNING_TIME;
            case UNDERWATER_MINING -> Attributes.SUBMERGED_MINING_SPEED;
            case SNEAKING_SPEED -> Attributes.SNEAKING_SPEED;
            default -> null;
        };
    }

    /**
     * Some buffs are "inverted" — a positive buff means REDUCING the attribute.
     * FALL_DAMAGE: +10% means 10% LESS fall damage (multiply by -1)
     * BURNING_TIME: +10% means 10% LESS burning (multiply by -1)
     */
    private static boolean isInvertedBuff(AttributeBuff type) {
        return type == AttributeBuff.FALL_DAMAGE || type == AttributeBuff.BURNING_TIME;
    }

    /**
     * Clean up all buffs when a player logs out.
     */
    public static void onPlayerLogout(UUID playerUUID) {
        activeBuffs.remove(playerUUID);
    }

    /**
     * Get the remaining seconds of the longest active food buff for a player.
     * Returns 0 if no buffs active.
     */
    public static int getRemainingSeconds(UUID playerUUID, long currentGameTick) {
        var playerBuffs = activeBuffs.get(playerUUID);
        if (playerBuffs == null || playerBuffs.isEmpty()) return 0;

        long maxExpiry = 0;
        for (long expiry : playerBuffs.values()) {
            if (expiry > maxExpiry) maxExpiry = expiry;
        }
        long remaining = maxExpiry - currentGameTick;
        return remaining > 0 ? (int)(remaining / 20) : 0;
    }
}
