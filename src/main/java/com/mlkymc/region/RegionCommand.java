package com.mlkymc.region;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.Collection;
import java.util.StringJoiner;

public class RegionCommand {

    private final RegionManager manager;

    public RegionCommand(RegionManager manager) {
        this.manager = manager;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mlkymc").then(
                Commands.literal("region")
                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("wand").executes(ctx -> giveWand(ctx.getSource())))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                manager.getAllRegions().stream().map(r -> r.name), builder))
                                        .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("info")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                manager.getAllRegions().stream().map(r -> r.name), builder))
                                        .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("flag")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                manager.getAllRegions().stream().map(r -> r.name), builder))
                                        .then(Commands.argument("flag", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        java.util.Arrays.stream(RegionFlag.values()).map(RegionFlag::getId), builder))
                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(ctx -> setFlag(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "name"),
                                                                StringArgumentType.getString(ctx, "flag"),
                                                                BoolArgumentType.getBool(ctx, "value")))))))
                        .then(Commands.literal("priority")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                manager.getAllRegions().stream().map(r -> r.name), builder))
                                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                                .executes(ctx -> setPriority(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        IntegerArgumentType.getInteger(ctx, "value"))))))
                        .then(Commands.literal("flags").executes(ctx -> listFlags(ctx.getSource())))
                        .then(Commands.literal("setspawn").executes(ctx -> setSpawn(ctx.getSource())))
        ));
    }

    private int giveWand(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("Region Wand").withColor(0xFFAA00));
        player.getInventory().add(stack);
        player.sendSystemMessage(Component.literal("Region wand (stick) given. Left-click = pos1, Right-click = pos2.").withColor(0x55FF55));
        return 1;
    }

    private int create(CommandSourceStack source, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        if (manager.getRegion(name) != null) {
            source.sendFailure(Component.literal("Region '" + name + "' already exists."));
            return 0;
        }

        int[] p1 = manager.getPos1(player.getUUID());
        int[] p2 = manager.getPos2(player.getUUID());
        String dim = manager.getPosDim(player.getUUID());

        if (p1 == null || p2 == null || dim == null) {
            source.sendFailure(Component.literal("Select two positions first with the region wand (wooden axe)."));
            return 0;
        }

        Region region = manager.createRegion(name, dim, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]);
        manager.clearSelection(player.getUUID());

        player.sendSystemMessage(Component.literal("Region '" + region.name + "' created (" + region.blockCount() + " blocks).").withColor(0x55FF55));
        player.sendSystemMessage(Component.literal("Use /mlkymc region flag " + region.name + " <flag> true to set flags.").withColor(0xFFFF55));
        return 1;
    }

    private int delete(CommandSourceStack source, String name) {
        if (manager.deleteRegion(name)) {
            source.sendSuccess(() -> Component.literal("Region '" + name + "' deleted.").withColor(0x55FF55), true);
            return 1;
        }
        source.sendFailure(Component.literal("Region '" + name + "' not found."));
        return 0;
    }

    private int list(CommandSourceStack source) {
        Collection<Region> regions = manager.getAllRegions();
        if (regions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No regions defined.").withColor(0xFFAA00), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("--- Regions (" + regions.size() + ") ---").withColor(0xFFAA00), false);
        for (Region region : regions) {
            int flagCount = region.flags.size();
            source.sendSuccess(() -> Component.literal(
                    "  " + region.name + " [" + region.dimension + "] (" +
                            region.x1 + "," + region.y1 + "," + region.z1 + " -> " +
                            region.x2 + "," + region.y2 + "," + region.z2 + ") " +
                            flagCount + " flags, priority " + region.priority
            ).withColor(0xFFFF55), false);
        }
        return 1;
    }

    private int info(CommandSourceStack source, String name) {
        Region region = manager.getRegion(name);
        if (region == null) {
            source.sendFailure(Component.literal("Region '" + name + "' not found."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("--- Region: " + region.name + " ---").withColor(0xFFAA00), false);
        source.sendSuccess(() -> Component.literal("  Dimension: " + region.dimension).withColor(0xFFFF55), false);
        source.sendSuccess(() -> Component.literal("  Bounds: " +
                region.x1 + "," + region.y1 + "," + region.z1 + " -> " +
                region.x2 + "," + region.y2 + "," + region.z2 +
                " (" + region.blockCount() + " blocks)").withColor(0xFFFF55), false);
        source.sendSuccess(() -> Component.literal("  Priority: " + region.priority).withColor(0xFFFF55), false);

        if (region.flags.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  Flags: none").withColor(0xAAAAAA), false);
        } else {
            StringJoiner joiner = new StringJoiner(", ");
            region.flags.forEach(joiner::add);
            source.sendSuccess(() -> Component.literal("  Flags: " + joiner).withColor(0x55FF55), false);
        }
        return 1;
    }

    private int setFlag(CommandSourceStack source, String name, String flagId, boolean value) {
        Region region = manager.getRegion(name);
        if (region == null) {
            source.sendFailure(Component.literal("Region '" + name + "' not found."));
            return 0;
        }

        RegionFlag flag = RegionFlag.fromId(flagId);
        if (flag == null) {
            source.sendFailure(Component.literal("Unknown flag '" + flagId + "'. Use /mlkymc region flags to see available flags."));
            return 0;
        }

        region.setFlag(flag, value);
        manager.save();

        String state = value ? "enabled" : "disabled";
        source.sendSuccess(() -> Component.literal("Flag '" + flag.getId() + "' " + state + " for region '" + region.name + "'.").withColor(0x55FF55), true);
        return 1;
    }

    private int setPriority(CommandSourceStack source, String name, int priority) {
        Region region = manager.getRegion(name);
        if (region == null) {
            source.sendFailure(Component.literal("Region '" + name + "' not found."));
            return 0;
        }

        region.priority = priority;
        manager.save();

        source.sendSuccess(() -> Component.literal("Priority for '" + region.name + "' set to " + priority + ".").withColor(0x55FF55), true);
        return 1;
    }

    private int setSpawn(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());
        float angle = player.getYRot();

        var server = player.level().getServer();
        String cmd = "setworldspawn " + x + " " + y + " " + z + " " + angle;
        server.getCommands().performPrefixedCommand(source, cmd);

        player.sendSystemMessage(Component.literal(
                "World spawn set to " + x + ", " + y + ", " + z + " (angle " + String.format("%.1f", angle) + ")").withColor(0x55FF55));
        return 1;
    }

    private int listFlags(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("--- Available Flags ---").withColor(0xFFAA00), false);
        for (RegionFlag flag : RegionFlag.values()) {
            source.sendSuccess(() -> Component.literal("  " + flag.getId() + " - " + flag.getDescription()).withColor(0xFFFF55), false);
        }
        return 1;
    }
}
