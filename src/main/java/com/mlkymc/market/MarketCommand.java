package com.mlkymc.market;

import com.mlkymc.economy.MilkyStar;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MarketCommand {

    private final MarketManager marketManager;

    public MarketCommand(MarketManager marketManager) {
        this.marketManager = marketManager;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mlkymc")
                // /mlkymc market ...
                .then(Commands.literal("market")
                        // /mlkymc market sell <price> - list item in marketplace
                        .then(Commands.literal("sell")
                                .then(Commands.argument("price", IntegerArgumentType.integer(1))
                                        .executes(ctx -> marketSell(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "price")
                                        ))))
                        // /mlkymc market cancel <item> [number] - cancel marketplace listing by item name
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestMarketplaceItems(ctx.getSource(), builder))
                                        .executes(ctx -> marketCancel(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "item"),
                                                0
                                        ))
                                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                                .executes(ctx -> marketCancel(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "item"),
                                                        IntegerArgumentType.getInteger(ctx, "number")
                                                )))))
                        // /mlkymc market listings - view your marketplace listings
                        .then(Commands.literal("listings")
                                .executes(ctx -> marketListings(ctx.getSource())))
                        // /mlkymc market collect - collect pending earnings
                        .then(Commands.literal("collect")
                                .executes(ctx -> collect(ctx.getSource())))
                        // /mlkymc market book (op) - give yourself a Market Book
                        .then(Commands.literal("book")
                                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(ctx -> giveMarketBook(ctx.getSource())))
                )
                // /mlkymc stall ...
                .then(Commands.literal("stall")
                        // /mlkymc stall sell <price> - list item in your stall
                        .then(Commands.literal("sell")
                                .then(Commands.argument("price", IntegerArgumentType.integer(1))
                                        .executes(ctx -> stallSell(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "price")
                                        ))))
                        // /mlkymc stall cancel <item> [number] - cancel stall listing by item name
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestStallItems(ctx.getSource(), builder))
                                        .executes(ctx -> stallCancel(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "item"),
                                                0
                                        ))
                                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                                .executes(ctx -> stallCancel(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "item"),
                                                        IntegerArgumentType.getInteger(ctx, "number")
                                                )))))
                        // /mlkymc stall listings - view your stall listings
                        .then(Commands.literal("listings")
                                .executes(ctx -> stallListings(ctx.getSource())))
                        // /mlkymc stall create <name>
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> stallCreate(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")
                                        ))))
                        // /mlkymc stall remove
                        .then(Commands.literal("remove")
                                .executes(ctx -> stallRemove(ctx.getSource())))
                        // /mlkymc stall info
                        .then(Commands.literal("info")
                                .executes(ctx -> stallInfo(ctx.getSource())))
                )
        );
    }

    // ---- Marketplace commands ----

    private int marketSell(CommandSourceStack source, int price) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }

        if (!marketManager.isNearMarketLectern(player)) {
            source.sendFailure(Component.literal("You must be near a marketplace lectern to list items."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Hold the item you want to sell."));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        int amount = held.getCount();
        String itemNbt = MarketManager.serializeItemStack(held, player);

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        MarketListing listing = marketManager.addMarketplaceListing(player, itemId, amount, price, itemNbt);

        source.sendSuccess(() -> Component.literal(
                "Listed " + amount + "x " + itemId.replace("minecraft:", "") +
                " on the marketplace for " + price + " Milky Stars. (ID: " + listing.getId() + ")")
                .withColor(0x55FF55), false);
        return 1;
    }

    private int marketCancel(CommandSourceStack source, String itemName, int number) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }

        if (!marketManager.isNearMarketLectern(player)) {
            source.sendFailure(Component.literal("You must be near a marketplace lectern to cancel listings."));
            return 0;
        }

        String fullId = itemName.contains(":") ? itemName : "minecraft:" + itemName;
        List<MarketListing> myListings = marketManager.getMarketplaceListingsBySeller(player.getStringUUID());
        List<MarketListing> matches = myListings.stream()
                .filter(l -> l.getItemId().equals(fullId))
                .toList();

        if (matches.isEmpty()) {
            source.sendFailure(Component.literal("You have no marketplace listings for " + itemName + "."));
            return 0;
        }

        if (matches.size() > 1 && number == 0) {
            source.sendSuccess(() -> Component.literal("--- Multiple listings for " + itemName + " ---").withColor(0xFFAA00), false);
            for (int i = 0; i < matches.size(); i++) {
                MarketListing l = matches.get(i);
                int idx = i + 1;
                source.sendSuccess(() -> Component.literal(
                        "  #" + idx + ": " + l.getAmount() + "x - " + l.getPrice() + " Milky Stars")
                        .withColor(0xFFFF55), false);
            }
            source.sendSuccess(() -> Component.literal(
                    "Use /mlkymc market cancel " + itemName + " <number> to cancel a specific one.")
                    .withColor(0xAAAAAA), false);
            return 1;
        }

        int index = (number == 0) ? 0 : number - 1;
        if (index < 0 || index >= matches.size()) {
            source.sendFailure(Component.literal("Invalid number. You have " + matches.size() + " listing(s) for " + itemName + "."));
            return 0;
        }

        MarketListing target = matches.get(index);
        if (marketManager.cancelMarketplaceListing(player, target.getId())) {
            source.sendSuccess(() -> Component.literal("Marketplace listing cancelled. Item returned.").withColor(0x55FF55), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to cancel listing."));
            return 0;
        }
    }

    private int marketListings(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }

        List<MarketListing> myListings = marketManager.getMarketplaceListingsBySeller(player.getStringUUID());
        int pending = marketManager.getPendingEarnings(player.getStringUUID());

        if (myListings.isEmpty() && pending == 0) {
            source.sendSuccess(() -> Component.literal("You have no active marketplace listings or pending earnings.").withColor(0xAAAAAA), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("--- Your Marketplace Listings ---").withColor(0xFFAA00), false);

        for (MarketListing l : myListings) {
            String displayName = marketManager.getItemDisplayName(l);
            String rawName = l.getItemId().replace("minecraft:", "");
            source.sendSuccess(() -> Component.literal(
                    "  " + l.getAmount() + "x " + displayName + " - " + l.getPrice() + " Milky Stars" +
                    "  (cancel: /mlkymc market cancel " + rawName + ")")
                    .withColor(0xFFFF55), false);
        }

        if (pending > 0) {
            source.sendSuccess(() -> Component.literal(
                    "Pending earnings: " + pending + " Milky Stars (use /mlkymc market collect)")
                    .withColor(0x55FF55), false);
        }

        return 1;
    }

    private int collect(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }

        int collected = marketManager.collectEarnings(player);
        if (collected == 0) {
            source.sendSuccess(() -> Component.literal("No pending earnings to collect.").withColor(0xAAAAAA), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "Collected " + collected + " Milky Stars!").withColor(0x55FF55), false);
        }
        return 1;
    }

    private int giveMarketBook(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }

        ItemStack book = MilkyStar.createMarketBook();
        if (!player.getInventory().add(book)) {
            player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                    player.level(), player.getX(), player.getY(), player.getZ(), book));
        }

        source.sendSuccess(() -> Component.literal(
                "Market Book given! Place it on a lectern to create a marketplace access point.")
                .withColor(0x55FF55), false);
        return 1;
    }

    // ---- Stall commands ----

    private int stallSell(CommandSourceStack source, int price) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }

        if (marketManager.getStall(player.getStringUUID()) == null) {
            source.sendFailure(Component.literal("You don't have a stall! Place a Stall Deed first."));
            return 0;
        }

        if (!marketManager.isNearOwnStall(player)) {
            source.sendFailure(Component.literal("You must be near your stall to list items."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Hold the item you want to sell."));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        int amount = held.getCount();
        String itemNbt = MarketManager.serializeItemStack(held, player);

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        MarketListing listing = marketManager.addStallListing(player, itemId, amount, price, itemNbt);

        source.sendSuccess(() -> Component.literal(
                "Listed " + amount + "x " + itemId.replace("minecraft:", "") +
                " in your stall for " + price + " Milky Stars. (ID: " + listing.getId() + ")")
                .withColor(0x55FF55), false);
        return 1;
    }

    private int stallCancel(CommandSourceStack source, String itemName, int number) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }

        if (!marketManager.isNearOwnStall(player)) {
            source.sendFailure(Component.literal("You must be near your stall to cancel listings."));
            return 0;
        }

        String fullId = itemName.contains(":") ? itemName : "minecraft:" + itemName;
        List<MarketListing> myListings = marketManager.getStallListings(player.getStringUUID());
        List<MarketListing> matches = myListings.stream()
                .filter(l -> l.getItemId().equals(fullId))
                .toList();

        if (matches.isEmpty()) {
            source.sendFailure(Component.literal("You have no stall listings for " + itemName + "."));
            return 0;
        }

        if (matches.size() > 1 && number == 0) {
            source.sendSuccess(() -> Component.literal("--- Multiple listings for " + itemName + " ---").withColor(0xFFAA00), false);
            for (int i = 0; i < matches.size(); i++) {
                MarketListing l = matches.get(i);
                int idx = i + 1;
                source.sendSuccess(() -> Component.literal(
                        "  #" + idx + ": " + l.getAmount() + "x - " + l.getPrice() + " Milky Stars")
                        .withColor(0xFFFF55), false);
            }
            source.sendSuccess(() -> Component.literal(
                    "Use /mlkymc stall cancel " + itemName + " <number> to cancel a specific one.")
                    .withColor(0xAAAAAA), false);
            return 1;
        }

        int index = (number == 0) ? 0 : number - 1;
        if (index < 0 || index >= matches.size()) {
            source.sendFailure(Component.literal("Invalid number. You have " + matches.size() + " listing(s) for " + itemName + "."));
            return 0;
        }

        MarketListing target = matches.get(index);
        if (marketManager.cancelStallListing(player, target.getId())) {
            source.sendSuccess(() -> Component.literal("Stall listing cancelled. Item returned.").withColor(0x55FF55), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to cancel listing."));
            return 0;
        }
    }

    private int stallListings(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }

        List<MarketListing> myListings = marketManager.getStallListings(player.getStringUUID());

        if (myListings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Your stall has no listings.").withColor(0xAAAAAA), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("--- Your Stall Listings ---").withColor(0xFFAA00), false);

        for (MarketListing l : myListings) {
            String displayName = marketManager.getItemDisplayName(l);
            String rawName = l.getItemId().replace("minecraft:", "");
            source.sendSuccess(() -> Component.literal(
                    "  " + l.getAmount() + "x " + displayName + " - " + l.getPrice() + " Milky Stars" +
                    "  (cancel: /mlkymc stall cancel " + rawName + ")")
                    .withColor(0xFFFF55), false);
        }

        return 1;
    }

    private int stallCreate(CommandSourceStack source, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }

        if (!marketManager.createStall(player, name)) {
            source.sendFailure(Component.literal("You already have a stall! Use /mlkymc stall remove first."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Created your stall '" + name + "'! Other players can right-click it to browse your items.")
                .withColor(0x55FF55), false);
        return 1;
    }

    private int stallRemove(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }

        if (!marketManager.removeStall(player)) {
            source.sendFailure(Component.literal("You don't have a stall."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Stall removed.").withColor(0x55FF55), false);
        return 1;
    }

    private int stallInfo(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }

        MarketManager.StallData stall = marketManager.getStall(player.getStringUUID());
        if (stall == null) {
            source.sendFailure(Component.literal("You don't have a stall. Place a Stall Deed to create one."));
            return 0;
        }

        List<MarketListing> myListings = marketManager.getStallListings(player.getStringUUID());

        source.sendSuccess(() -> Component.literal("--- " + stall.stallName + " ---").withColor(0xFFAA00), false);
        source.sendSuccess(() -> Component.literal(
                "  Location: " + String.format("%.0f, %.0f, %.0f", stall.x, stall.y, stall.z) +
                " in " + stall.dimension).withColor(0xFFFF55), false);
        source.sendSuccess(() -> Component.literal(
                "  Listings: " + myListings.size()).withColor(0xFFFF55), false);

        for (MarketListing l : myListings) {
            String displayName = marketManager.getItemDisplayName(l);
            source.sendSuccess(() -> Component.literal(
                    "    " + l.getAmount() + "x " + displayName + " - " +
                    l.getPrice() + " Milky Stars").withColor(0xAAAAAA), false);
        }

        return 1;
    }

    // ---- Tab-completion suggestions ----

    private CompletableFuture<Suggestions> suggestMarketplaceItems(CommandSourceStack source, SuggestionsBuilder builder) {
        if (source.getEntity() instanceof ServerPlayer player) {
            marketManager.getMarketplaceListingsBySeller(player.getStringUUID()).stream()
                    .map(l -> l.getItemId().replace("minecraft:", ""))
                    .distinct()
                    .filter(name -> name.startsWith(builder.getRemainingLowerCase()))
                    .forEach(builder::suggest);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestStallItems(CommandSourceStack source, SuggestionsBuilder builder) {
        if (source.getEntity() instanceof ServerPlayer player) {
            marketManager.getStallListings(player.getStringUUID()).stream()
                    .map(l -> l.getItemId().replace("minecraft:", ""))
                    .distinct()
                    .filter(name -> name.startsWith(builder.getRemainingLowerCase()))
                    .forEach(builder::suggest);
        }
        return builder.buildFuture();
    }
}
