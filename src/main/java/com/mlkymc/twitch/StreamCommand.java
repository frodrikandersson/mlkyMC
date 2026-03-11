package com.mlkymc.twitch;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class StreamCommand {

    private final StreamerManager streamerManager;

    public StreamCommand(StreamerManager streamerManager) {
        this.streamerManager = streamerManager;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mlkymc").then(
            Commands.literal("streams")
                .executes(ctx -> listStreams(ctx.getSource()))
                .then(Commands.literal("register")
                        .requires(s -> s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("mcName", StringArgumentType.word())
                                .then(Commands.argument("twitchName", StringArgumentType.word())
                                        .executes(ctx -> registerStreamer(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "mcName"),
                                                StringArgumentType.getString(ctx, "twitchName"))))))
                .then(Commands.literal("link")
                        .then(Commands.argument("twitchName", StringArgumentType.word())
                                .executes(ctx -> selfLink(ctx.getSource(), StringArgumentType.getString(ctx, "twitchName")))))
                .then(Commands.literal("unlink")
                        .executes(ctx -> selfUnlink(ctx.getSource())))
                .then(Commands.literal("unregister")
                        .requires(s -> s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("mcName", StringArgumentType.word())
                                .executes(ctx -> unregisterStreamer(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "mcName")))))
                .then(Commands.literal("list")
                        .requires(s -> s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> listRegistered(ctx.getSource())))));
    }

    private int listStreams(CommandSourceStack source) {
        source.sendSuccess(() -> streamerManager.buildStreamListMessage(), false);
        return 1;
    }

    private int registerStreamer(CommandSourceStack source, String mcName, String twitchName) {
        streamerManager.registerStreamer(mcName, twitchName);
        source.sendSuccess(() -> Component.literal("Registered " + mcName + " -> twitch.tv/" + twitchName).withColor(0x55FF55), false);
        return 1;
    }

    private int unregisterStreamer(CommandSourceStack source, String mcName) {
        if (!streamerManager.isRegistered(mcName)) {
            source.sendFailure(Component.literal(mcName + " is not registered as a streamer."));
            return 0;
        }
        streamerManager.unregisterStreamer(mcName);
        source.sendSuccess(() -> Component.literal("Unregistered " + mcName + " from streamers.").withColor(0x55FF55), false);
        return 1;
    }

    private int selfLink(CommandSourceStack source, String twitchName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }
        String mcName = player.getName().getString();
        streamerManager.registerStreamer(mcName, twitchName);
        source.sendSuccess(() -> Component.literal("Linked your account to twitch.tv/" + twitchName).withColor(0x55FF55), false);
        return 1;
    }

    private int selfUnlink(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }
        String mcName = player.getName().getString();
        if (!streamerManager.isRegistered(mcName)) {
            source.sendFailure(Component.literal("You don't have a linked Twitch channel."));
            return 0;
        }
        streamerManager.unregisterStreamer(mcName);
        source.sendSuccess(() -> Component.literal("Unlinked your Twitch channel.").withColor(0x55FF55), false);
        return 1;
    }

    private int listRegistered(CommandSourceStack source) {
        var streamers = streamerManager.getStreamers();
        if (streamers.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No streamers registered.").withColor(0xAAAAAA), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("Registered streamers:\n");
        for (var entry : streamers.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(" -> twitch.tv/").append(entry.getValue()).append("\n");
        }
        source.sendSuccess(() -> Component.literal(sb.toString().trim()).withColor(0xFFFFFF), false);
        return 1;
    }
}
