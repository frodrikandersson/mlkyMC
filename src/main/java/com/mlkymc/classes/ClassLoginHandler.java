package com.mlkymc.classes;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Sends the player's class data on login so the client can cache it.
 * Format: [MLKYMC_SYNC:CLASS_NAME:ADV_LVL:CLE_LVL:FAR_LVL:MC_LVL:SMI_LVL]
 */
public class ClassLoginHandler {
    private final ClassManager classManager;

    public ClassLoginHandler(ClassManager classManager) {
        this.classManager = classManager;
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sendSync(player);
    }

    public void sendSync(ServerPlayer player) {
        ClassData data = classManager.getOrCreate(player);
        String syncTag = "[MLKYMC_SYNC:" + data.getChosenClass().name()
                + ":" + data.getLevel(ProfessionType.ADVENTURER)
                + ":" + data.getLevel(ProfessionType.CLERIC)
                + ":" + data.getLevel(ProfessionType.FARMHAND)
                + ":" + data.getLevel(ProfessionType.MINECRAFTER)
                + ":" + data.getLevel(ProfessionType.SMITH)
                + "]";
        player.sendSystemMessage(Component.literal(syncTag).withColor(0x000000));
    }
}
