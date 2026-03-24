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

    // Primary power charge tracking (for hold-to-charge skills like Weapon Dash)
    private static boolean primaryHeld = false;
    private static long primaryHeldStartTick = 0;

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
        if (mc.player == null || mc.screen != null) return;

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
            // Key is being held
            if (!primaryHeld) {
                // Just started holding
                primaryHeld = true;
                primaryHeldStartTick = mc.player.tickCount;

                if (!ClientClassData.hasChosenClass()) {
                    mc.player.displayClientMessage(
                            Component.literal("No class chosen! Press [K] to select.").withColor(0xFF5555), true);
                    return;
                }
            }
            // While held: show charge bar for classes that need it (Adventurer)
            // The XP bar overlay is handled in PowerChargeHud
        } else if (primaryHeld) {
            // Key was released
            primaryHeld = false;

            if (!ClientClassData.hasChosenClass()) return;

            long holdTicks = mc.player.tickCount - primaryHeldStartTick;
            int chargePercent = (int) Math.min(holdTicks * 5, 100); // 20 ticks = 100%

            // Send to server with charge amount
            mc.player.connection.sendChat("[MLKYMC_PRIMARY:" + chargePercent + "]");
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

        ClassType chosen = ClientClassData.getChosenClass();

        switch (chosen) {
            case ADVENTURER -> {
                MinimapHud.toggle();
                String state = MinimapHud.isEnabled() ? "ON" : "OFF";
                mc.player.displayClientMessage(
                        Component.literal("Map Wall HUD: " + state).withColor(0x55FFFF), true);
            }
            default -> {
                // Other classes: send to server for server-side handling
                mc.player.connection.sendChat("[MLKYMC_SECONDARY]");
            }
        }
    }

    // --- Charge state for HUD rendering ---
    public static boolean isPrimaryHeld() { return primaryHeld; }
    public static float getChargePercent() {
        if (!primaryHeld) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        long holdTicks = mc.player.tickCount - primaryHeldStartTick;
        return Math.min(holdTicks * 5f / 100f, 1.0f); // 0.0 to 1.0 over 20 ticks
    }
}
