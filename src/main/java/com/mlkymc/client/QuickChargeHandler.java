package com.mlkymc.client;

import com.mlkymc.classes.ClassType;
import com.mlkymc.classes.ProfessionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-side handler that speeds up bow draw animation for Adventurers.
 * Only applies quick-charge speed if the draw STARTED while off cooldown.
 */
public class QuickChargeHandler {

    private static boolean onCooldown = false;
    private static long cooldownEndTick = 0;

    // Whether the current draw started with quick-charge available
    private static boolean drawStartedWithQuickCharge = false;
    private static boolean wasUsingBow = false;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.isPaused()) return;

        LocalPlayer player = mc.player;

        // Update cooldown state
        long currentTick = mc.level.getGameTime();
        if (onCooldown && currentTick >= cooldownEndTick) {
            onCooldown = false;
        }

        boolean usingBow = false;
        if (player.isUsingItem()) {
            var item = player.getUseItem();
            usingBow = item.getItem() instanceof BowItem || item.getItem() instanceof CrossbowItem;
        }

        if (usingBow && ClientClassData.getChosenClass() == ClassType.ADVENTURER) {
            // Detect draw start — snapshot cooldown state
            if (!wasUsingBow) {
                drawStartedWithQuickCharge = !onCooldown;
            }

            int advLevel = ClientClassData.getLevel(ProfessionType.ADVENTURER);
            int extraTicks = 0;

            // Lv40 exclusive: always draw 2x faster
            if (advLevel >= 40) {
                extraTicks += 1;
            }

            // Quick-Charge: only if draw started while off cooldown
            if (drawStartedWithQuickCharge) {
                extraTicks += 2;
            }

            if (extraTicks > 0) {
                int remaining = player.getUseItemRemainingTicks();
                if (remaining > 1) {
                    player.useItemRemaining = Math.max(0, remaining - extraTicks);
                }
            }
        } else {
            drawStartedWithQuickCharge = false;
        }

        wasUsingBow = usingBow;
    }

    public static void triggerCooldown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            onCooldown = true;
            cooldownEndTick = mc.level.getGameTime() + 300; // 15s
        }
    }

    public static boolean isOnCooldown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        return onCooldown && mc.level.getGameTime() < cooldownEndTick;
    }
}
