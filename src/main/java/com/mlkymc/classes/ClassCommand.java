package com.mlkymc.classes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * Commands for the class system.
 * /mlkymc class select <class> - Choose your class (permanent)
 * /mlkymc class info - View your class and profession levels
 * /mlkymc class info <player> - View another player's info (admin)
 * /mlkymc class setlevel <player> <profession> <level> - Admin set level
 * /mlkymc class addxp <player> <profession> <amount> - Admin add XP
 * /mlkymc class reset <player> - Admin reset a player's class (emergency only)
 */
public class ClassCommand {
    private final ClassManager classManager;

    public ClassCommand(ClassManager classManager) {
        this.classManager = classManager;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mlkymc")
                .then(Commands.literal("class")
                        // class select now handled via [MLKYMC_CLASS_SELECT:] chat message from GUI
                        // /mlkymc class info
                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    showClassInfo(player, player);
                                    return 1;
                                })
                                // /mlkymc class info <player> (admin)
                                .then(Commands.argument("target", EntityArgument.player())
                                        .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                        .executes(ctx -> {
                                            ServerPlayer viewer = ctx.getSource().getPlayerOrException();
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            showClassInfo(viewer, target);
                                            return 1;
                                        })))
                        // /mlkymc class setlevel <player> <profession> <level> (admin)
                        .then(Commands.literal("setlevel")
                                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("profession", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (ProfessionType pt : ProfessionType.values()) {
                                                        builder.suggest(pt.name().toLowerCase());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 50))
                                                        .executes(ctx -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                            String profName = StringArgumentType.getString(ctx, "profession").toUpperCase();
                                                            int level = IntegerArgumentType.getInteger(ctx, "level");
                                                            try {
                                                                ProfessionType prof = ProfessionType.valueOf(profName);
                                                                ClassData data = classManager.getOrCreate(target);
                                                                // Set level by adding enough XP (reset first)
                                                                setLevel(data, prof, level);
                                                                classManager.save();
                                                                classManager.sendLevelSync(target);
                                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                                        "Set " + target.getName().getString() + "'s " + prof.getDisplayName() + " to level " + level), true);
                                                            } catch (IllegalArgumentException e) {
                                                                ctx.getSource().sendFailure(Component.literal("Unknown profession: " + profName));
                                                            }
                                                            return 1;
                                                        })))))
                        // /mlkymc class addxp <player> <profession> <amount> (admin)
                        .then(Commands.literal("addxp")
                                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("profession", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (ProfessionType pt : ProfessionType.values()) {
                                                        builder.suggest(pt.name().toLowerCase());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                            String profName = StringArgumentType.getString(ctx, "profession").toUpperCase();
                                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                            try {
                                                                ProfessionType prof = ProfessionType.valueOf(profName);
                                                                classManager.addXp(target, prof, amount);
                                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                                        "Added " + amount + " XP to " + target.getName().getString() + "'s " + prof.getDisplayName()), true);
                                                            } catch (IllegalArgumentException e) {
                                                                ctx.getSource().sendFailure(Component.literal("Unknown profession: " + profName));
                                                            }
                                                            return 1;
                                                        })))))
                        // /mlkymc class reset <player> (admin emergency)
                        .then(Commands.literal("reset")
                                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            // Force reset by creating fresh data
                                            classManager.resetPlayer(target.getUUID());
                                            // Clear all active power states (auto-smelt, jackhammer, etc)
                                            var ph = PowerHandler.getInstance();
                                            if (ph != null) ph.clearAllStates(target);
                                            // Sync full reset to client (all zeros)
                                            classManager.sendLevelSync(target);
                                            // Hide Devoted Life HUD
                                            var psh = com.mlkymc.MlkyMC.getPassiveSkillHandler();
                                            if (psh != null) psh.sendDevotedLifeNone(target);
                                            target.sendSystemMessage(Component.literal("Your class has been reset. Press [K] to choose a new class.").withColor(0xFFFF55));
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Reset " + target.getName().getString() + "'s class data. They must choose again."), true);
                                            return 1;
                                        })))
                        // /mlkymc class resync <player> — force full client resync
                        .then(Commands.literal("resync")
                                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            // Send revive signal to clear ghost HUDs
                                            String reviveMsg = "[MLKYMC_REVIVED:" + target.getName().getString() + "]";
                                            target.sendSystemMessage(Component.literal(reviveMsg).withColor(0x000000));
                                            // Sync class levels
                                            classManager.sendLevelSync(target);
                                            // Sync soul energy
                                            classManager.sendSoulSync(target);
                                            // Clear ghost data if not a ghost
                                            var gm = com.mlkymc.MlkyMC.getGhostManager();
                                            if (gm != null && !gm.isGhost(target.getUUID())) {
                                                var gdm = com.mlkymc.MlkyMC.getGhostDataManager();
                                                if (gdm != null) {
                                                    gdm.remove(target.getUUID());
                                                }
                                            }
                                            // Clear power handler state
                                            var ph = PowerHandler.getInstance();
                                            if (ph != null) {
                                                ph.clearGhostState(target.getUUID());
                                                ph.clearAllStates(target);
                                            }
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Resynced " + target.getName().getString() + "'s client state."), true);
                                            return 1;
                                        })))
                )
                // /mlkymc mimic <form> — ghost mimic activation (used by mimic selection GUI)
                .then(Commands.literal("mimic")
                        .then(Commands.argument("form", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("bat");
                                    builder.suggest("creeper");
                                    builder.suggest("enderman");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                                    var gm = com.mlkymc.MlkyMC.getGhostManager();
                                    if (gm == null || !gm.isGhost(player.getUUID())) {
                                        player.sendSystemMessage(Component.literal("Only ghosts can use Spectral Mimic.").withColor(0xFF5555));
                                        return 0;
                                    }
                                    String form = StringArgumentType.getString(ctx, "form").toLowerCase();
                                    var gdm = com.mlkymc.MlkyMC.getGhostDataManager();
                                    if (gdm != null) {
                                        var data = gdm.getOrCreate(player.getUUID());
                                        gdm.startMimic(player, data, form);
                                    }
                                    return 1;
                                })))
        );
    }

    private void showClassInfo(ServerPlayer viewer, ServerPlayer target) {
        ClassData data = classManager.getOrCreate(target);
        boolean self = viewer.getUUID().equals(target.getUUID());
        String name = self ? "Your" : target.getName().getString() + "'s";

        viewer.sendSystemMessage(Component.literal("=== " + name + " Class Info ===")
                .withStyle(style -> style.withColor(0x55FFFF)));

        if (data.hasChosenClass()) {
            viewer.sendSystemMessage(Component.literal("Class: ").append(data.getChosenClass().getColoredName()));
        } else {
            viewer.sendSystemMessage(Component.literal("Class: ").append(
                    Component.literal("Not yet chosen").withStyle(style -> style.withColor(0xFF5555))));
        }

        viewer.sendSystemMessage(Component.literal(""));

        for (ProfessionType prof : ProfessionType.values()) {
            int level = data.getLevel(prof);
            int xp = data.getXp(prof);
            int needed = data.getXpForNextLevel(prof);
            double debuff = data.getDebuffPercent(prof);

            String debuffStr = debuff > 0 ? " (-" + (int) (debuff * 100) + "% debuff)" : "";
            String exclusive = data.hasExclusiveSkills(prof) ? " [ACTIVE]" : "";

            viewer.sendSystemMessage(Component.literal(prof.getDisplayName() + ": Lv." + level + " (" + xp + "/" + needed + " XP)" + debuffStr + exclusive)
                    .withStyle(style -> style.withColor(prof.getMatchingClass().getColor())));
        }
    }

    private void setLevel(ClassData data, ProfessionType prof, int targetLevel) {
        // Reset XP and set level directly via addXp from 0
        // We need to manipulate via serialize/deserialize workaround
        // For simplicity, just add massive XP from current state
        int currentLevel = data.getLevel(prof);
        if (targetLevel <= currentLevel) {
            // Can't reduce level via this method - would need data reset
            return;
        }
        // Calculate total XP needed from current to target
        int xpNeeded = 0;
        for (int i = currentLevel; i < targetLevel; i++) {
            xpNeeded += ProfessionType.xpForLevel(i);
        }
        xpNeeded -= data.getXp(prof); // subtract current partial XP
        if (xpNeeded > 0) {
            data.addXp(prof, xpNeeded);
        }
    }
}
