package com.mlkymc.revive;

import com.mlkymc.config.MlkyConfig;
import com.mlkymc.ghost.GhostManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

public class ReviveListener {

    private final GhostManager ghostManager;
    private final ReviveManager reviveManager;

    public ReviveListener(GhostManager ghostManager, ReviveManager reviveManager) {
        this.ghostManager = ghostManager;
        this.reviveManager = reviveManager;
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ghostManager.isGhost(player.getUUID())) return;

        // Check if holding the revive item
        ItemStack held = player.getMainHandItem();
        Item reviveItem = getReviveItem();
        if (reviveItem == null || !held.is(reviveItem)) return;

        // Check if near statue
        var statueLoc = reviveManager.getStatueLocation();
        if (statueLoc == null) return;
        if (!player.level().dimension().identifier().toString().equals(statueLoc.dimension)) return;

        double dist = player.distanceToSqr(statueLoc.x, statueLoc.y, statueLoc.z);
        if (dist > 9.0) return; // Within 3 blocks

        List<ServerPlayer> ghosts = ghostManager.getOnlineGhosts();
        if (ghosts.isEmpty()) {
            player.sendSystemMessage(Component.literal("No ghosts to revive.").withColor(0xFFFF55));
            return;
        }

        if (ghosts.size() == 1) {
            held.shrink(1);
            reviveManager.revive(ghosts.get(0));
        } else {
            // Multiple ghosts - revive the first one for now
            // TODO: Could add a GUI selector here later
            held.shrink(1);
            reviveManager.revive(ghosts.get(0));
            player.sendSystemMessage(Component.literal("Revived " + ghosts.get(0).getName().getString() + " (first ghost in queue).").withColor(0x55FF55));
        }
    }

    private Item getReviveItem() {
        String itemId = MlkyConfig.getReviveItem();
        Identifier loc = Identifier.parse(itemId);
        return BuiltInRegistries.ITEM.get(loc).map(net.minecraft.core.Holder.Reference::value).orElse(null);
    }
}
