package com.mlkymc.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

public class ModKeybinds {

    private static final KeyMapping.Category MLKYMC_CATEGORY = new KeyMapping.Category(
            Identifier.parse("mlkymc:keybinds")
    );

    public static KeyMapping KEY_CLASS_MENU;
    public static KeyMapping KEY_MINIMAP;

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(MLKYMC_CATEGORY);

        KEY_CLASS_MENU = new KeyMapping(
                "key.mlkymc.class_menu",
                InputConstants.KEY_K,
                MLKYMC_CATEGORY
        );
        KEY_MINIMAP = new KeyMapping(
                "key.mlkymc.minimap",
                InputConstants.KEY_M,
                MLKYMC_CATEGORY
        );
        event.register(KEY_CLASS_MENU);
        event.register(KEY_MINIMAP);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        if (KEY_CLASS_MENU != null && KEY_CLASS_MENU.consumeClick()) {
            if (ClientClassData.hasChosenClass()) {
                mc.setScreen(new SkillTreeScreen());
            } else {
                mc.setScreen(new ClassSelectionScreen());
            }
        }

        if (KEY_MINIMAP != null && KEY_MINIMAP.consumeClick()) {
            if (ClientClassData.getChosenClass() == com.mlkymc.classes.ClassType.ADVENTURER) {
                MinimapHud.toggle();
            } else {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Minimap is an Adventurer class skill!").withColor(0xFF5555), true);
            }
        }
    }
}
