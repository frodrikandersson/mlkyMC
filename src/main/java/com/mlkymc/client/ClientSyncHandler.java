package com.mlkymc.client;

import com.mlkymc.classes.ClassType;
import com.mlkymc.classes.ProfessionType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/**
 * Client-side listener that picks up sync messages from the server.
 * Format: [MLKYMC_SYNC:CLASS_NAME:ADV:CLE:FAR:MC:SMI]
 */
public class ClientSyncHandler {

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String msg = event.getMessage().getString();

        if (msg.startsWith("[MLKYMC_SYNC:") && msg.endsWith("]")) {
            String content = msg.substring(13, msg.length() - 1);
            String[] parts = content.split(":");

            // Parse class
            try {
                ClassType classType = ClassType.valueOf(parts[0]);
                ClientClassData.setChosenClass(classType);
            } catch (IllegalArgumentException e) {
                ClientClassData.reset();
            }

            // Parse levels (indices 1-5) and XP (indices 6-10)
            ProfessionType[] profs = ProfessionType.values();
            for (int i = 0; i < profs.length && i + 1 < parts.length; i++) {
                try {
                    int level = Integer.parseInt(parts[i + 1]);
                    ClientClassData.setLevel(profs[i], level);
                } catch (NumberFormatException ignored) {}
            }
            // Parse XP values (after the 5 levels)
            for (int i = 0; i < profs.length && i + 6 < parts.length; i++) {
                try {
                    int xp = Integer.parseInt(parts[i + 6]);
                    ClientClassData.setXp(profs[i], xp);
                } catch (NumberFormatException ignored) {}
            }

            // Hide the sync message from chat
            event.setCanceled(true);
        }
    }
}
