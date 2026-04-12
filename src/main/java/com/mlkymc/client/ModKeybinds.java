package com.mlkymc.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mlkymc.classes.ClassType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public class ModKeybinds {

    private static final KeyMapping.Category MLKYMC_CATEGORY = new KeyMapping.Category(
            Identifier.parse("mlkymc:keybinds")
    );

    public static KeyMapping KEY_CLASS_MENU;
    public static KeyMapping KEY_PRIMARY_POWER;
    public static KeyMapping KEY_SECONDARY_POWER;

    // Ignore keybinds for a few ticks after closing a screen (prevents chat leaking)
    private static int screenCloseCooldown = 0;
    private static boolean wasScreenOpen = false;

    // Primary power charge tracking (for hold-to-charge skills like Dash)
    private static boolean primaryHeld = false;
    private static long primaryHeldStartTick = 0;
    private static boolean chargeCancelled = false;
    private static long fullChargeTick = -1; // tick when charge first hit 100%

    private static final int MAX_CHARGE_TICKS = 20;     // 1 second to reach 100%
    private static final int OVERCHARGE_TICKS = 20;      // 1 second grace before cancel

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(MLKYMC_CATEGORY);

        KEY_CLASS_MENU = new KeyMapping(
                "key.mlkymc.class_menu",
                InputConstants.KEY_K,
                MLKYMC_CATEGORY
        );
        KEY_PRIMARY_POWER = new KeyMapping(
                "key.mlkymc.primary_power",
                InputConstants.KEY_R,
                MLKYMC_CATEGORY
        );
        KEY_SECONDARY_POWER = new KeyMapping(
                "key.mlkymc.secondary_power",
                InputConstants.KEY_X,
                MLKYMC_CATEGORY
        );
        event.register(KEY_CLASS_MENU);
        event.register(KEY_PRIMARY_POWER);
        event.register(KEY_SECONDARY_POWER);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Track screen close — ignore keybinds for 5 ticks after closing chat/screen
        if (mc.screen != null) {
            wasScreenOpen = true;
            // Consume any queued presses while screen is open
            if (KEY_PRIMARY_POWER != null) while (KEY_PRIMARY_POWER.consumeClick()) {}
            if (KEY_SECONDARY_POWER != null) while (KEY_SECONDARY_POWER.consumeClick()) {}
            if (KEY_CLASS_MENU != null) while (KEY_CLASS_MENU.consumeClick()) {}
            return;
        }
        if (wasScreenOpen) {
            wasScreenOpen = false;
            screenCloseCooldown = 5;
            // Consume any leftover presses from typing
            if (KEY_PRIMARY_POWER != null) while (KEY_PRIMARY_POWER.consumeClick()) {}
            if (KEY_SECONDARY_POWER != null) while (KEY_SECONDARY_POWER.consumeClick()) {}
            if (KEY_CLASS_MENU != null) while (KEY_CLASS_MENU.consumeClick()) {}
        }
        if (screenCloseCooldown > 0) {
            screenCloseCooldown--;
            if (KEY_PRIMARY_POWER != null) while (KEY_PRIMARY_POWER.consumeClick()) {}
            if (KEY_SECONDARY_POWER != null) while (KEY_SECONDARY_POWER.consumeClick()) {}
            if (KEY_CLASS_MENU != null) while (KEY_CLASS_MENU.consumeClick()) {}
            return;
        }

        // --- Class Menu (K) ---
        if (KEY_CLASS_MENU != null && KEY_CLASS_MENU.consumeClick()) {
            mc.setScreen(new ClassSelectionScreen());
        }

        // --- Primary Power ---
        if (KEY_PRIMARY_POWER != null) {
            handlePrimaryPower(mc);
        }

        // --- Secondary Power ---
        if (KEY_SECONDARY_POWER != null && KEY_SECONDARY_POWER.consumeClick()) {
            handleSecondaryPower(mc);
        }
    }

    private void handlePrimaryPower(Minecraft mc) {
        if (KEY_PRIMARY_POWER.isDown()) {
            if (!primaryHeld && !chargeCancelled) {
                // Just started holding
                primaryHeld = true;
                primaryHeldStartTick = mc.player.tickCount;
                fullChargeTick = -1;

                if (!ClientClassData.hasChosenClass()) {
                    mc.player.displayClientMessage(
                            Component.literal("No class chosen! Press [K] to select.").withColor(0xFF5555), true);
                    return;
                }
            }

            // Check for overcharge cancel (only for Adventurer dash)
            if (primaryHeld && ClientClassData.getChosenClass() == ClassType.ADVENTURER) {
                long holdTicks = mc.player.tickCount - primaryHeldStartTick;

                // Track when charge first hits 100%
                if (holdTicks >= MAX_CHARGE_TICKS && fullChargeTick < 0) {
                    fullChargeTick = mc.player.tickCount;
                }

                // If held too long past full charge, cancel
                if (fullChargeTick > 0 && mc.player.tickCount - fullChargeTick >= OVERCHARGE_TICKS) {
                    primaryHeld = false;
                    chargeCancelled = true;
                    mc.player.displayClientMessage(
                            Component.literal("Dash cancelled — overcharged!").withColor(0xFF5555), true);
                }
            }
        } else {
            if (primaryHeld) {
                // Key was released — fire the skill
                primaryHeld = false;

                if (!ClientClassData.hasChosenClass()) return;

                long holdTicks = mc.player.tickCount - primaryHeldStartTick;
                int chargePercent = (int) Math.min(holdTicks * 5, 100);

                mc.player.connection.sendChat("[MLKYMC_PRIMARY:" + chargePercent + "]");
            }
            // Reset cancel flag when key is fully released
            chargeCancelled = false;
            fullChargeTick = -1;
        }

        // Consume any queued clicks to prevent double-firing
        while (KEY_PRIMARY_POWER.consumeClick()) {}
    }

    private void handleSecondaryPower(Minecraft mc) {
        if (!ClientClassData.hasChosenClass()) {
            mc.player.displayClientMessage(
                    Component.literal("No class chosen! Press [K] to select.").withColor(0xFF5555), true);
            return;
        }

        // All classes send secondary to server
        mc.player.connection.sendChat("[MLKYMC_SECONDARY]");
    }

    // --- Charge state for HUD rendering ---
    public static boolean isPrimaryHeld() { return primaryHeld; }
    public static boolean isChargeCancelled() { return chargeCancelled; }

    public static float getChargePercent() {
        if (!primaryHeld) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        long holdTicks = mc.player.tickCount - primaryHeldStartTick;
        return Math.min(holdTicks * 5f / 100f, 1.0f);
    }

    /** Returns 0.0-1.0 for how far into the overcharge window (0 = just hit 100%, 1 = about to cancel) */
    public static float getOverchargePercent() {
        if (!primaryHeld || fullChargeTick < 0) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        long overTicks = mc.player.tickCount - fullChargeTick;
        return Math.min((float) overTicks / OVERCHARGE_TICKS, 1.0f);
    }
}
