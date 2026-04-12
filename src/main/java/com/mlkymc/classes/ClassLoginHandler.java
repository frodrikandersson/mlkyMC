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

        // Show intro message once per player (persisted)
        ClassData data = classManager.getOrCreate(player);
        if (!data.isIntroShown()) {
            data.setIntroShown(true);
            classManager.save();
            player.level().getServer().execute(() -> player.level().getServer().execute(() -> {
                player.sendSystemMessage(Component.empty());
                player.sendSystemMessage(Component.literal("================================").withColor(0x5599FF));
                player.sendSystemMessage(Component.literal("  Welcome to mlkyMC!").withColor(0xFFFFFF));
                player.sendSystemMessage(Component.literal("================================").withColor(0x5599FF));
                player.sendSystemMessage(Component.empty());
                player.sendSystemMessage(Component.literal("This server features a unique class system").withColor(0xAABBCC));
                player.sendSystemMessage(Component.literal("with 5 classes, each with their own skills,").withColor(0xAABBCC));
                player.sendSystemMessage(Component.literal("abilities, and progression paths.").withColor(0xAABBCC));
                player.sendSystemMessage(Component.empty());
                player.sendSystemMessage(Component.literal("  Adventurer").withColor(0xFF5555)
                        .append(Component.literal(" - Combat & exploration").withColor(0x999999)));
                player.sendSystemMessage(Component.literal("  Cleric").withColor(0xAA55FF)
                        .append(Component.literal(" - Healing, enchanting & revival").withColor(0x999999)));
                player.sendSystemMessage(Component.literal("  Farmhand").withColor(0x55FF55)
                        .append(Component.literal(" - Farming, fishing & cooking").withColor(0x999999)));
                player.sendSystemMessage(Component.literal("  MineCrafter").withColor(0x55FFFF)
                        .append(Component.literal(" - Mining & crafting").withColor(0x999999)));
                player.sendSystemMessage(Component.literal("  Smith").withColor(0xFFAA00)
                        .append(Component.literal(" - Smelting, forging & repair").withColor(0x999999)));
                player.sendSystemMessage(Component.empty());
                player.sendSystemMessage(Component.literal("  Press [K] to choose your class!").withColor(0xFFFF55));
                player.sendSystemMessage(Component.literal("  Your choice is permanent, so choose wisely.").withColor(0xFF7777));
                player.sendSystemMessage(Component.empty());
                player.sendSystemMessage(Component.literal("================================").withColor(0x5599FF));

                // Give the guidebook on first join
                com.mlkymc.guide.GuidebookHelper.giveGuidebook(player);
            }));
        }
    }

    public void sendSync(ServerPlayer player) {
        ClassData data = classManager.getOrCreate(player);
        String syncTag = "[MLKYMC_SYNC:" + data.getChosenClass().name()
                + ":" + data.getLevel(ProfessionType.ADVENTURER)
                + ":" + data.getLevel(ProfessionType.CLERIC)
                + ":" + data.getLevel(ProfessionType.FARMHAND)
                + ":" + data.getLevel(ProfessionType.MINECRAFTER)
                + ":" + data.getLevel(ProfessionType.SMITH)
                + ":" + data.getXp(ProfessionType.ADVENTURER)
                + ":" + data.getXp(ProfessionType.CLERIC)
                + ":" + data.getXp(ProfessionType.FARMHAND)
                + ":" + data.getXp(ProfessionType.MINECRAFTER)
                + ":" + data.getXp(ProfessionType.SMITH)
                + "]";
        player.sendSystemMessage(Component.literal(syncTag).withColor(0x000000));
    }
}
