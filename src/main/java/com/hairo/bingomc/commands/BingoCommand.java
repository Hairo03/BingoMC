package com.hairo.bingomc.commands;

import com.hairo.bingomc.BingoMC;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import java.util.List;

public class BingoCommand {

    private final BingoMC plugin;

    public BingoCommand(BingoMC plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bingo")
            .executes(this::executeRoot)
            .then(Commands.literal("start").executes(this::executeStart))
            .then(Commands.literal("stop").executes(this::executeStop))
            .then(
                Commands.literal("goals")
                    .executes(this::executeGoalsView)
                    .then(Commands.literal("admin").executes(this::executeGoalsAdmin))
                    .then(Commands.literal("validate").executes(this::executeGoalsValidate))
                    .then(Commands.literal("reload").executes(this::executeGoalsReload))
            );
        commands.register(root.build(), "Main Bingo command", List.of());
    }

    private int executeRoot(CommandContext<CommandSourceStack> context) {
        plugin.sendBingoUsage(context.getSource().getSender());
        return 1;
    }

    private int executeStart(CommandContext<CommandSourceStack> context) {
        plugin.handleStartCommand(context.getSource().getSender());
        return 1;
    }

    private int executeStop(CommandContext<CommandSourceStack> context) {
        plugin.handleStopCommand(context.getSource().getSender());
        return 1;
    }

    private int executeGoalsView(CommandContext<CommandSourceStack> context) {
        plugin.handleGoalsViewCommand(context.getSource().getSender());
        return 1;
    }

    private int executeGoalsAdmin(CommandContext<CommandSourceStack> context) {
        plugin.handleGoalsAdminCommand(context.getSource().getSender());
        return 1;
    }

    private int executeGoalsValidate(CommandContext<CommandSourceStack> context) {
        plugin.handleGoalsValidateCommand(context.getSource().getSender());
        return 1;
    }

    private int executeGoalsReload(CommandContext<CommandSourceStack> context) {
        plugin.handleGoalsReloadCommand(context.getSource().getSender());
        return 1;
    }
}
