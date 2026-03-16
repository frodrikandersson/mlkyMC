package com.mlkymc.dimension;

import com.mlkymc.economy.MilkyStar;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class DimensionCommand {

    private final DimensionManager dimensionManager;

    public DimensionCommand(DimensionManager dimensionManager) {
        this.dimensionManager = dimensionManager;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mlkymc").then(
                Commands.literal("dimension")
                        .executes(ctx -> showStatus(ctx.getSource()))
                        .then(Commands.literal("status").executes(ctx -> showStatus(ctx.getSource())))
                        .then(Commands.literal("contribute")
                                .then(Commands.literal("nether")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> contribute(ctx.getSource(), "nether", IntegerArgumentType.getInteger(ctx, "amount")))))
                                .then(Commands.literal("end")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> contribute(ctx.getSource(), "end", IntegerArgumentType.getInteger(ctx, "amount"))))))
                        .then(Commands.literal("unlock").requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                                .then(Commands.literal("nether").executes(ctx -> unlock(ctx.getSource(), "nether")))
                                .then(Commands.literal("end").executes(ctx -> unlock(ctx.getSource(), "end"))))
                        .then(Commands.literal("lock").requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                                .then(Commands.literal("nether").executes(ctx -> lock(ctx.getSource(), "nether")))
                                .then(Commands.literal("end").executes(ctx -> lock(ctx.getSource(), "end"))))
                        .then(Commands.literal("reset").requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                                .then(Commands.literal("nether").executes(ctx -> reset(ctx.getSource(), "nether")))
                                .then(Commands.literal("end").executes(ctx -> reset(ctx.getSource(), "end"))))
        ));
    }

    private int showStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("--- Dimension Status ---").withColor(0xFFAA00), false);
        showDimensionStatus(source, "nether", "The Nether");
        showDimensionStatus(source, "end", "The End");
        return 1;
    }

    private void showDimensionStatus(CommandSourceStack source, String dimension, String displayName) {
        int progress = dimensionManager.getProgress(dimension);
        int goal = dimensionManager.getGoal(dimension);
        int total = dimensionManager.getTotalContributed(dimension);
        boolean unlocked = dimensionManager.isUnlocked(dimension);

        if (unlocked) {
            long remaining = dimensionManager.getRemainingMillis(dimension);
            String timeStr = formatTime(remaining);
            source.sendSuccess(() -> Component.literal(displayName + ": UNLOCKED (" + timeStr + " remaining)").withColor(0x55FF55), false);
        } else {
            int percent = goal > 0 ? (int) ((progress * 100L) / goal) : 0;
            String bar = buildProgressBar(progress, goal);
            source.sendSuccess(() -> Component.literal(displayName + ": LOCKED").withColor(0xFF5555), false);
            source.sendSuccess(() -> Component.literal("  " + bar + " " + progress + "/" + goal + " (" + percent + "%)").withColor(0xFFAA00), false);
        }
        if (total > 0) {
            source.sendSuccess(() -> Component.literal("  Total contributed: " + total + " Milky Stars").withColor(0xAAAAAA), false);
        }
    }

    private String buildProgressBar(int progress, int goal) {
        int barLength = 20;
        int filled = goal > 0 ? (int) ((progress * (long) barLength) / goal) : 0;
        filled = Math.min(filled, barLength);
        return "\u2588".repeat(filled) + "\u2591".repeat(barLength - filled);
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private int contribute(CommandSourceStack source, String dimension, int amount) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        if (dimensionManager.isUnlocked(dimension)) {
            String displayName = dimension.equals("end") ? "The End" : "The Nether";
            player.sendSystemMessage(Component.literal(displayName + " is already unlocked!").withColor(0xFFFF55));
            return 0;
        }

        int remaining = dimensionManager.getGoal(dimension) - dimensionManager.getProgress(dimension);
        if (amount > remaining) amount = remaining;

        if (!MilkyStar.remove(player, amount)) {
            player.sendSystemMessage(Component.literal("You don't have enough Milky Stars.").withColor(0xFF5555));
            return 0;
        }

        dimensionManager.contribute(dimension, amount);
        String displayName = dimension.equals("end") ? "The End" : "The Nether";
        int progress = dimensionManager.getProgress(dimension);
        int goal = dimensionManager.getGoal(dimension);

        final int finalAmount = amount;
        player.sendSystemMessage(Component.literal("Contributed " + finalAmount + " Milky Star(s) to unlock " + displayName + "!").withColor(0x55FF55));

        if (dimensionManager.isUnlocked(dimension)) {
            int unlockHours = dimensionManager.getUnlockHours(dimension);
            player.level().getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal(displayName + " has been unlocked for " + unlockHours + " hours!").withColor(0x55FF55), false);
        } else {
            player.level().getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal(player.getName().getString() + " contributed " + finalAmount + " Milky Star(s) to " + displayName + "! (" + progress + "/" + goal + ")").withColor(0xFFAA00), false);
        }
        return 1;
    }

    private int unlock(CommandSourceStack source, String dimension) {
        dimensionManager.forceUnlock(dimension);
        String displayName = dimension.equals("end") ? "The End" : "The Nether";
        int hours = dimensionManager.getUnlockHours(dimension);
        source.sendSuccess(() -> Component.literal("Force-unlocked " + displayName + " for " + hours + " hours.").withColor(0x55FF55), true);
        return 1;
    }

    private int lock(CommandSourceStack source, String dimension) {
        dimensionManager.forceLock(dimension);
        String displayName = dimension.equals("end") ? "The End" : "The Nether";
        source.sendSuccess(() -> Component.literal("Force-locked " + displayName + ".").withColor(0x55FF55), true);
        return 1;
    }

    private int reset(CommandSourceStack source, String dimension) {
        dimensionManager.reset(dimension);
        String displayName = dimension.equals("end") ? "The End" : "The Nether";
        source.sendSuccess(() -> Component.literal("Reset " + displayName + " progress and lock state.").withColor(0x55FF55), true);
        return 1;
    }
}
