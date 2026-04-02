package com.hairo.bingomc.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import java.util.List;

public class BingoCommand {

    private final BingoCommandHandler commandHandler;

    public BingoCommand(BingoCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    public void register(Commands commands) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bingo")
                .executes(this::executeRoot)
                .then(Commands.literal("start").requires(sender -> sender.getSender().hasPermission("bingomc.start"))
                        .executes(this::executeStart))
                .then(Commands.literal("stop").requires(sender -> sender.getSender().hasPermission("bingomc.stop"))
                        .executes(this::executeStop))
                .then(
                        Commands.literal("goals")
                                .executes(this::executeGoalsView)
                                .then(Commands.literal("admin")
                                        .requires(sender -> sender.getSender().hasPermission("bingomc.goals.admin"))
                                        .executes(this::executeGoalsAdmin))
                                .then(Commands.literal("validate")
                                        .requires(sender -> sender.getSender().hasPermission("bingomc.goals.validate"))
                                        .executes(this::executeGoalsValidate))
                                .then(Commands.literal("reload")
                                        .requires(sender -> sender.getSender().hasPermission("bingomc.goals.reload"))
                                        .executes(this::executeGoalsReload)))
                .then(Commands.literal("export").requires(sender -> sender.getSender().hasPermission("bingomc.export"))
                        .executes(this::executeExport));
        commands.register(root.build(), "Main Bingo command", List.of());
    }

    private int executeRoot(CommandContext<CommandSourceStack> context) {
        commandHandler.sendBingoUsage(context.getSource().getSender());
        return 1;
    }

    private int executeStart(CommandContext<CommandSourceStack> context) {
        commandHandler.handleStartCommand(context.getSource().getSender());
        return 1;
    }

    private int executeStop(CommandContext<CommandSourceStack> context) {
        commandHandler.handleStopCommand(context.getSource().getSender());
        return 1;
    }

    private int executeGoalsView(CommandContext<CommandSourceStack> context) {
        commandHandler.handleGoalsViewCommand(context.getSource().getSender());
        return 1;
    }

    private int executeGoalsAdmin(CommandContext<CommandSourceStack> context) {
        commandHandler.handleGoalsAdminCommand(context.getSource().getSender());
        return 1;
    }

    private int executeGoalsValidate(CommandContext<CommandSourceStack> context) {
        commandHandler.handleGoalsValidateCommand(context.getSource().getSender());
        return 1;
    }

    private int executeGoalsReload(CommandContext<CommandSourceStack> context) {
        commandHandler.handleGoalsReloadCommand(context.getSource().getSender());
        return 1;
    }

    private int executeExport(CommandContext<CommandSourceStack> context) {
        commandHandler.handleExportCommand(context.getSource().getSender());
        return 1;
    }
}
