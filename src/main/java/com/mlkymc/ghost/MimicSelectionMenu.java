package com.mlkymc.ghost;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends clickable chat options for Spectral Mimic form selection.
 */
public class MimicSelectionMenu {

    public static void open(ServerPlayer player) {

        player.sendSystemMessage(Component.literal("=== Spectral Mimic ===").withColor(0x55FFFF));
        player.sendSystemMessage(Component.literal("Select a form:").withColor(0xAAAAAA));

        // Bat
        MutableComponent bat = Component.literal("[Bat]").withStyle(s -> s
                .withColor(0x55FFFF)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/mlkymc mimic bat"))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("15 SE | 20s | Creative flight\nClick to transform!"))));
        player.sendSystemMessage(bat.append(Component.literal(" — 15 SE, 20s, Flight").withColor(0xAAAAAA)));

        // Creeper
        MutableComponent creeper = Component.literal("[Creeper]").withStyle(s -> s
                .withColor(0x55FF55)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/mlkymc mimic creeper"))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("20 SE | 30s | Primary: Knockback blast\nClick to transform!"))));
        player.sendSystemMessage(creeper.append(Component.literal(" — 20 SE, 30s, Knockback blast").withColor(0xAAAAAA)));

        // Enderman
        MutableComponent enderman = Component.literal("[Enderman]").withStyle(s -> s
                .withColor(0xAA55FF)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/mlkymc mimic enderman"))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("25 SE | 10s | Primary: Teleport 20 blocks\nClick to transform!"))));
        player.sendSystemMessage(enderman.append(Component.literal(" — 25 SE, 10s, Teleport").withColor(0xAAAAAA)));
    }
}
