package com.mlkymc.pvp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PvP tagging system. When two players attack each other, both are tagged
 * for 30 seconds. While tagged:
 * - Healing abilities (Soul Skill, Enhanced Heal, Soul Mend) skip them
 * - Aura of Protection skips them
 * - Smith slow aura CAN affect them (hostile effect)
 */
public class PvPTagManager {

    private static final int PVP_DURATION_TICKS = 600; // 30 seconds
    private static final Map<UUID, Long> pvpTags = new HashMap<>();

    public static boolean isPvPTagged(UUID playerUUID) {
        Long expiry = pvpTags.get(playerUUID);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public static void tagPlayer(ServerPlayer player) {
        long now = System.currentTimeMillis();
        boolean wasTagged = isPvPTagged(player.getUUID());
        pvpTags.put(player.getUUID(), now + (PVP_DURATION_TICKS * 50L)); // 30s in ms

        if (!wasTagged) {
            player.displayClientMessage(
                    Component.literal("PvP Mode! (30s)").withColor(0xFF5555), true);
        }
    }

    @SubscribeEvent
    public void onPlayerDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer target)) return;

        var source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer attacker)) return;

        // Both players are tagged
        tagPlayer(attacker);
        tagPlayer(target);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        // Check for tag expiry — notify once when it expires
        Long expiry = pvpTags.get(player.getUUID());
        if (expiry != null && System.currentTimeMillis() >= expiry) {
            pvpTags.remove(player.getUUID());
            player.displayClientMessage(
                    Component.literal("PvP Mode ended.").withColor(0x55FF55), true);
        }
    }
}
