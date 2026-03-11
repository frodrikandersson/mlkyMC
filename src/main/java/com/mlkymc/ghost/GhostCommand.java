package com.mlkymc.ghost;

import com.mlkymc.revive.ReviveManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class GhostCommand {

    private final GhostManager ghostManager;
    private final ReviveManager reviveManager;

    public GhostCommand(GhostManager ghostManager, ReviveManager reviveManager) {
        this.ghostManager = ghostManager;
        this.reviveManager = reviveManager;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mlkymc").then(
                Commands.literal("ghost").requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("setstatue").executes(ctx -> setStatue(ctx.getSource())))
                        .then(Commands.literal("revive")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> revive(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
        ));
    }

    private int setStatue(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        reviveManager.setStatueLocation(player);
        player.sendSystemMessage(Component.literal("Revive statue location set!").withColor(0x55FF55));
        return 1;
    }

    private int revive(CommandSourceStack source, ServerPlayer target) {
        if (!ghostManager.isGhost(target.getUUID())) {
            source.sendFailure(Component.literal(target.getName().getString() + " is not a ghost."));
            return 0;
        }

        reviveManager.revive(target);
        source.sendSuccess(() -> Component.literal("Revived " + target.getName().getString() + ".").withColor(0x55FF55), true);
        return 1;
    }

    private int status(CommandSourceStack source) {
        List<ServerPlayer> ghosts = ghostManager.getOnlineGhosts();
        if (ghosts.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No ghosts online.").withColor(0xAAAAAA), false);
        } else {
            source.sendSuccess(() -> Component.literal("Online ghosts (" + ghosts.size() + "):").withColor(0xFFAA00), false);
            for (ServerPlayer ghost : ghosts) {
                source.sendSuccess(() -> Component.literal("  - " + ghost.getName().getString()).withColor(0xAAAAAA), false);
            }
        }
        return 1;
    }
}
