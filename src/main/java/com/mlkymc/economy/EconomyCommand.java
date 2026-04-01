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
                        .then(Commands.literal("give")
                                .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> give(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")
                                                )))))
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

    private static int give(CommandSourceStack source, ServerPlayer target, int amount) {
        for (var stack : MilkyStar.createAll(amount)) {
            target.getInventory().add(stack);
        }
        source.sendSuccess(() -> Component.literal("Gave " + amount + " Milky Star(s) to " + target.getName().getString() + ".").withColor(0x55FF55), true);
        target.sendSystemMessage(Component.literal("Received " + amount + " Milky Star(s).").withColor(0x55FF55));
        return 1;
    }
}
