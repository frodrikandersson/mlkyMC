package com.mlkymc.economy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

public class EconomyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mlkymc").then(
                Commands.literal("economy")
                        .then(Commands.literal("balance").executes(ctx -> balance(ctx.getSource())))
                        .then(Commands.literal("pay")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> pay(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")
                                                )))))
                        .then(Commands.literal("give")
                                .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> give(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")
                                                )))))
                        .then(Commands.literal("wallet").executes(ctx -> wallet(ctx.getSource())))
        ));
    }

    private static int balance(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
        int bal = MilkyStar.count(player);
        player.sendSystemMessage(Component.literal("Balance: " + bal + " Milky Star(s)")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFD700))));
        return 1;
    }

    private static int pay(CommandSourceStack source, ServerPlayer target, int amount) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        if (player.equals(target)) {
            player.sendSystemMessage(Component.literal("You can't pay yourself.").withColor(0xFF5555));
            return 0;
        }

        if (!MilkyStar.remove(player, amount)) {
            player.sendSystemMessage(Component.literal("You don't have enough Milky Stars.").withColor(0xFF5555));
            return 0;
        }

        for (var stack : MilkyStar.createAll(amount)) {
            target.getInventory().add(stack);
        }
        player.sendSystemMessage(Component.literal("Paid " + amount + " Milky Star(s) to " + target.getName().getString() + ".").withColor(0x55FF55));
        target.sendSystemMessage(Component.literal("Received " + amount + " Milky Star(s) from " + player.getName().getString() + ".").withColor(0x55FF55));
        return 1;
    }

    private static int wallet(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        // Check if player already has a wallet
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (MilkyStar.isWallet(player.getInventory().getItem(i))) {
                player.sendSystemMessage(Component.literal("You already have a wallet!").withColor(0xFF5555));
                return 0;
            }
        }

        player.getInventory().add(MilkyStar.createJar(player.getName().getString()));
        player.sendSystemMessage(Component.literal("You received a Milky Star Jar! Right-click to open it.").withColor(0x55FF55));
        return 1;
    }

    private static int give(CommandSourceStack source, ServerPlayer target, int amount) {
        for (var stack : MilkyStar.createAll(amount)) {
            target.getInventory().add(stack);
        }
        source.sendSuccess(() -> Component.literal("Gave " + amount + " Milky Star(s) to " + target.getName().getString() + ".").withColor(0x55FF55), true);
        target.sendSystemMessage(Component.literal("Received " + amount + " Milky Star(s).").withColor(0x55FF55));
        return 1;
    }
}
