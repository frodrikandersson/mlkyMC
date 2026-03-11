package com.mlkymc.shop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public class ShopCommand {

    private final ShopManager shopManager;

    public ShopCommand(ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mlkymc").then(
                Commands.literal("shop").requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("create")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> create(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        StringArgumentType.getString(ctx, "name")
                                                )))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("addtrade")
                                .then(Commands.argument("shopId", StringArgumentType.word())
                                        .then(Commands.argument("item", StringArgumentType.word())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("price", IntegerArgumentType.integer(1))
                                                                .then(Commands.argument("selling", BoolArgumentType.bool())
                                                                        .executes(ctx -> addTrade(
                                                                                ctx.getSource(),
                                                                                StringArgumentType.getString(ctx, "shopId"),
                                                                                StringArgumentType.getString(ctx, "item"),
                                                                                IntegerArgumentType.getInteger(ctx, "amount"),
                                                                                IntegerArgumentType.getInteger(ctx, "price"),
                                                                                BoolArgumentType.getBool(ctx, "selling")
                                                                        ))))))))
                        .then(Commands.literal("removetrade")
                                .then(Commands.argument("shopId", StringArgumentType.word())
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .executes(ctx -> removeTrade(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "shopId"),
                                                        IntegerArgumentType.getInteger(ctx, "index")
                                                )))))
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("info")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("respawn")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> respawn(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
        ));
    }

    private int create(CommandSourceStack source, String id, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        if (shopManager.getShopkeeper(id) != null) {
            source.sendFailure(Component.literal("A shopkeeper with ID '" + id + "' already exists."));
            return 0;
        }

        ServerLevel level = player.level();
        shopManager.createShopkeeper(id, name, "none", level,
                player.getX(), player.getY(), player.getZ(), player.getYRot());
        source.sendSuccess(() -> Component.literal("Created shopkeeper '" + name + "' (id: " + id + ")").withColor(0x55FF55), true);
        return 1;
    }

    private int remove(CommandSourceStack source, String id) {
        if (shopManager.getShopkeeper(id) == null) {
            source.sendFailure(Component.literal("No shopkeeper with ID '" + id + "'."));
            return 0;
        }

        shopManager.removeShopkeeper(id);
        source.sendSuccess(() -> Component.literal("Removed shopkeeper '" + id + "'.").withColor(0x55FF55), true);
        return 1;
    }

    private int addTrade(CommandSourceStack source, String shopId, String item, int amount, int price, boolean selling) {
        Shopkeeper shop = shopManager.getShopkeeper(shopId);
        if (shop == null) {
            source.sendFailure(Component.literal("No shopkeeper with ID '" + shopId + "'."));
            return 0;
        }

        shop.addTrade(new Shopkeeper.TradeData(item, amount, price, selling));
        shopManager.save();
        String type = selling ? "SELL" : "BUY";
        source.sendSuccess(() -> Component.literal("Added " + type + " trade: " + amount + "x " + item + " for " + price + " Milky Stars").withColor(0x55FF55), true);
        return 1;
    }

    private int removeTrade(CommandSourceStack source, String shopId, int index) {
        Shopkeeper shop = shopManager.getShopkeeper(shopId);
        if (shop == null) {
            source.sendFailure(Component.literal("No shopkeeper with ID '" + shopId + "'."));
            return 0;
        }

        if (index < 0 || index >= shop.getTrades().size()) {
            source.sendFailure(Component.literal("Trade index out of range. This shop has " + shop.getTrades().size() + " trades."));
            return 0;
        }

        shop.removeTrade(index);
        shopManager.save();
        source.sendSuccess(() -> Component.literal("Removed trade #" + index + " from '" + shopId + "'.").withColor(0x55FF55), true);
        return 1;
    }

    private int list(CommandSourceStack source) {
        Map<String, Shopkeeper> all = shopManager.getAllShopkeepers();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No shopkeepers.").withColor(0xAAAAAA), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("--- Shopkeepers (" + all.size() + ") ---").withColor(0xFFAA00), false);
        for (Map.Entry<String, Shopkeeper> entry : all.entrySet()) {
            Shopkeeper shop = entry.getValue();
            source.sendSuccess(() -> Component.literal(
                    "  " + entry.getKey() + ": " + shop.getName() + " (" + shop.getTrades().size() + " trades)")
                    .withColor(0xFFFF55), false);
        }
        return 1;
    }

    private int info(CommandSourceStack source, String id) {
        Shopkeeper shop = shopManager.getShopkeeper(id);
        if (shop == null) {
            source.sendFailure(Component.literal("No shopkeeper with ID '" + id + "'."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("--- " + shop.getName() + " (" + id + ") ---").withColor(0xFFAA00), false);
        source.sendSuccess(() -> Component.literal("  Location: " + String.format("%.1f, %.1f, %.1f", shop.getX(), shop.getY(), shop.getZ())
                + " in " + shop.getDimension()).withColor(0xFFFF55), false);

        if (shop.getTrades().isEmpty()) {
            source.sendSuccess(() -> Component.literal("  No trades.").withColor(0xAAAAAA), false);
        } else {
            for (int i = 0; i < shop.getTrades().size(); i++) {
                Shopkeeper.TradeData trade = shop.getTrades().get(i);
                String type = trade.selling ? "SELL" : "BUY";
                int idx = i;
                source.sendSuccess(() -> Component.literal(
                        "  #" + idx + " [" + type + "] " + trade.amount + "x " + trade.item + " = " + trade.price + " Stars")
                        .withColor(trade.selling ? 0xFFAA00 : 0x55FF55), false);
            }
        }
        return 1;
    }

    private int respawn(CommandSourceStack source, String id) {
        if (shopManager.getShopkeeper(id) == null) {
            source.sendFailure(Component.literal("No shopkeeper with ID '" + id + "'."));
            return 0;
        }

        shopManager.respawnVillager(id);
        source.sendSuccess(() -> Component.literal("Respawned shopkeeper '" + id + "'.").withColor(0x55FF55), true);
        return 1;
    }
}
