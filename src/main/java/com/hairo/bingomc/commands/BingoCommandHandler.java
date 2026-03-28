package com.hairo.bingomc.commands;

import com.hairo.bingomc.goals.config.GoalLoadResult;
import com.hairo.bingomc.goals.config.GoalsService;
import com.hairo.bingomc.gui.GoalsAdminGui;
import com.hairo.bingomc.gui.GoalsViewerGui;
import com.hairo.bingomc.gui.NewGameGui;
import com.hairo.bingomc.round.RoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Function;

public class BingoCommandHandler {

    private final JavaPlugin plugin;
    private final RoundService roundService;
    private final GoalsService goalsService;
    private final GoalsViewerGui goalsViewerGui;
    private final GoalsAdminGui goalsAdminGui;
    private final NewGameGui newGameGui;
    private final Function<Component, Component> prefixer;

    public BingoCommandHandler(
        JavaPlugin plugin,
        RoundService roundService,
        GoalsService goalsService,
        GoalsViewerGui goalsViewerGui,
        GoalsAdminGui goalsAdminGui,
        NewGameGui newGameGui,
        Function<Component, Component> prefixer
    ) {
        this.plugin = plugin;
        this.roundService = roundService;
        this.goalsService = goalsService;
        this.goalsViewerGui = goalsViewerGui;
        this.goalsAdminGui = goalsAdminGui;
        this.newGameGui = newGameGui;
        this.prefixer = prefixer;
    }

    public void sendBingoUsage(CommandSender sender) {
        sender.sendMessage(prefixer.apply(
            Component.text("Usage: ", NamedTextColor.GRAY)
                .append(Component.text("/bingo <start|stop|goals>", NamedTextColor.AQUA))
        ));
    }

    public boolean handleStartCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixer.apply(Component.text("Only players can use /bingo start.", NamedTextColor.RED)));
            return true;
        }
        if (!player.isOp()) {
            sender.sendMessage(prefixer.apply(Component.text("Only operators can start a Bingo round.", NamedTextColor.RED)));
            return true;
        }
        if (roundService.isGameRunning()) {
            sender.sendMessage(prefixer.apply(Component.text("A Bingo round is already running.", NamedTextColor.YELLOW)));
            return true;
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            sender.sendMessage(prefixer.apply(Component.text("Cannot start: no players are online.", NamedTextColor.RED)));
            return true;
        }

        newGameGui.open(player).thenAccept(confirmed -> {
            if (!confirmed) {
                sender.sendMessage(prefixer.apply(Component.text("Bingo round start cancelled.", NamedTextColor.YELLOW)));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                roundService.showStartingTitle();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    long selectedSeed = newGameGui.getWorldSeed();
                    boolean started = roundService.startRound(selectedSeed);
                    roundService.clearStartingTitle();

                    if (started) {
                        sender.sendMessage(prefixer.apply(Component.text("Bingo round started.", NamedTextColor.GREEN)));
                    } else {
                        sender.sendMessage(prefixer.apply(Component.text("Could not start Bingo round.", NamedTextColor.RED)));
                    }
                }, 1L);
            });
        });
        return true;
    }

    public boolean handleStopCommand(CommandSender sender) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage(prefixer.apply(Component.text("Only operators can stop a Bingo round.", NamedTextColor.RED)));
            return true;
        }

        if (!roundService.isGameRunning()) {
            sender.sendMessage(prefixer.apply(Component.text("No Bingo round is currently running.", NamedTextColor.YELLOW)));
            return true;
        }

        roundService.stopRound(sender.getName());
        sender.sendMessage(prefixer.apply(Component.text("Bingo round stopped.", NamedTextColor.GREEN)));
        return true;
    }

    public boolean handleGoalsViewCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixer.apply(Component.text("Only players can open the goals UI.", NamedTextColor.RED)));
            return true;
        }
        goalsViewerGui.open(player);
        return true;
    }

    public boolean handleGoalsAdminCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixer.apply(Component.text("Only players can open the admin UI.", NamedTextColor.RED)));
            return true;
        }
        if (!player.hasPermission("bingomc.goals.admin")) {
            player.sendMessage(prefixer.apply(Component.text("You do not have permission to manage goals.", NamedTextColor.RED)));
            return true;
        }
        goalsAdminGui.open(player);
        return true;
    }

    public boolean handleGoalsValidateCommand(CommandSender sender) {
        GoalLoadResult result = goalsService.validateGoals();
        if (result.isValid()) {
            sender.sendMessage(prefixer.apply(
                Component.text("goals.yml is valid. Loaded ", NamedTextColor.GREEN)
                    .append(Component.text(result.goals().size(), NamedTextColor.AQUA))
                    .append(Component.text(" enabled goals.", NamedTextColor.GREEN))
            ));
        } else {
            sender.sendMessage(prefixer.apply(Component.text("goals.yml has validation errors:", NamedTextColor.RED)));
            for (String error : result.errors()) {
                sender.sendMessage(prefixer.apply(Component.text("- " + error, NamedTextColor.GRAY)));
            }
        }
        return true;
    }

    public boolean handleGoalsReloadCommand(CommandSender sender) {
        if (sender instanceof Player player && !player.hasPermission("bingomc.goals.admin")) {
            sender.sendMessage(prefixer.apply(Component.text("You do not have permission to reload goals.", NamedTextColor.RED)));
            return true;
        }

        if (roundService.isGameRunning()) {
            sender.sendMessage(prefixer.apply(Component.text("Cannot reload goals while a round is running.", NamedTextColor.YELLOW)));
            return true;
        }

        boolean loaded = goalsService.reloadGoals(false);
        sender.sendMessage(prefixer.apply(Component.text(
            loaded ? "Goals reloaded." : "Could not reload goals; see console for details.",
            loaded ? NamedTextColor.GREEN : NamedTextColor.RED
        )));
        return true;
    }
}
