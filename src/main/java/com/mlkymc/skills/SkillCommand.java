package com.mlkymc.skills;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SkillCommand {

    private final SkillManager skillManager;

    public SkillCommand(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var skillNode = Commands.literal("skills");

        // /mlkymc skills - show all
        skillNode.executes(ctx -> showAll(ctx.getSource()));

        // /mlkymc skills <type> - show specific
        for (SkillType type : SkillType.values()) {
            skillNode.then(Commands.literal(type.name().toLowerCase())
                    .executes(ctx -> showSkill(ctx.getSource(), type)));
        }

        dispatcher.register(Commands.literal("mlkymc").then(skillNode));
    }

    private int showAll(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("--- Your Skills ---").withColor(0xFFAA00));
        for (SkillType type : SkillType.values()) {
            int level = skillManager.getLevel(player.getUUID(), type);
            int xp = skillManager.getXp(player.getUUID(), type);
            int nextLevelXp = skillManager.getTotalXpForLevel(level + 1);
            player.sendSystemMessage(Component.literal(
                    type.getDisplayName() + ": Level " + level + " (" + xp + "/" + nextLevelXp + " XP)")
                    .withColor(0xFFFF55));
        }
        return 1;
    }

    private int showSkill(CommandSourceStack source, SkillType type) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        int level = skillManager.getLevel(player.getUUID(), type);
        int xp = skillManager.getXp(player.getUUID(), type);
        int nextLevelXp = skillManager.getTotalXpForLevel(level + 1);

        player.sendSystemMessage(Component.literal("--- " + type.getDisplayName() + " ---").withColor(0xFFAA00));
        player.sendSystemMessage(Component.literal("Level: " + level).withColor(0xFFFF55));
        player.sendSystemMessage(Component.literal("XP: " + xp + "/" + nextLevelXp).withColor(0xFFFF55));
        player.sendSystemMessage(Component.literal(type.getDescription()).withColor(0xAAAAAA));
        return 1;
    }
}
