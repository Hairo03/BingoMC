package com.hairo.bingomc;

import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.config.GoalConfigService;
import com.hairo.bingomc.goals.config.GoalLoadResult;
import com.hairo.bingomc.goals.util.ConsumeTracker;
import com.hairo.bingomc.goals.util.Timer;
import com.hairo.bingomc.gui.GoalsAdminGui;
import com.hairo.bingomc.gui.GoalsViewerGui;
import com.hairo.bingomc.gui.NewGameGui;
import com.hairo.bingomc.listeners.GoalEventListener;
import com.hairo.bingomc.events.TimerExpiredEvent;
import com.hairo.bingomc.worlds.BingoWorldService;
import com.hairo.bingomc.worlds.PlayerWorldSet;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import xyz.xenondevs.invui.InvUI;
import com.hairo.bingomc.commands.BingoCommand;

public class BingoMC extends JavaPlugin implements Listener {

    private final GoalManager goalManager = new GoalManager();
    private ConsumeTracker consumeTracker;
    private Timer timer;
    private boolean gameRunning;
    private boolean timerExpiredHandled;
    private BossBar timerBossBar;
    private final Set<UUID> roundParticipants = new HashSet<>();
    private GoalConfigService goalConfigService;
    private GoalsViewerGui goalsViewerGui;
    private GoalsAdminGui goalsAdminGui;
    private NewGameGui newGameGui;
    private String mainWorldName;
    private BingoWorldService worldService;

    private static final long GAME_DURATION_SECONDS = 300L;

    @Override
    public void onEnable() {
        consumeTracker = new ConsumeTracker(this);
        goalConfigService = new GoalConfigService(this, consumeTracker);
        goalsViewerGui = new GoalsViewerGui(this);
        goalsAdminGui = new GoalsAdminGui(this);
        newGameGui = new NewGameGui();
        InvUI.getInstance().setPlugin(this);

        timer = new Timer();
        timer.setLimitSeconds(GAME_DURATION_SECONDS);
        timer.reset();
        gameRunning = false;
        timerExpiredHandled = false;
        timerBossBar = BossBar.bossBar(
            bossBarTime("00:00"),
            0.0f,
            BossBar.Color.BLUE,
            BossBar.Overlay.PROGRESS
        );

        mainWorldName = Bukkit.getWorlds().get(0).getName();
        worldService = new BingoWorldService(this);
        if (!worldService.initializeApis()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new GoalEventListener(this, goalManager, consumeTracker), this);

        BingoCommand bingoCommandHandler = new BingoCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
            bingoCommandHandler.register(event.registrar())
        );
        
        getLogger().info("BingoMC has been enabled!");

        if (!reloadGoalsFromDisk(true)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!gameRunning) {
                return;
            }

            if (timer.isRunning() && timer.isExpired() && !timerExpiredHandled) {
                timer.stop();
                timerExpiredHandled = true;
                Bukkit.getPluginManager().callEvent(new TimerExpiredEvent(timer.getElapsedMillis()));
                return;
            }

            updateTimerDisplay();

            for (Player player : Bukkit.getOnlinePlayers()) {
                goalManager.evaluate(player, GoalTrigger.PERIODIC);
            }
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }

        if (worldService != null) {
            worldService.cleanupManagedBingoWorldsOnShutdown(mainWorldName);
        }

        if (timerBossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(timerBossBar);
            }
        }
        getLogger().info("BingoMC has been disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(prefixed(
            Component.text("Welcome, ", NamedTextColor.GREEN)
                .append(Component.text(player.getName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.GREEN))
        ));
        getLogger().info(player.getName() + " joined the server");

        if (timerBossBar != null && gameRunning) {
            player.sendMessage(prefixed(Component.text(
                "A round is currently running. You can join in the next round.",
                NamedTextColor.YELLOW
            )));
        }
    }

    @EventHandler
    public void onTimerExpired(TimerExpiredEvent event) {
        String seconds = String.valueOf(event.getElapsedSeconds());
        concludeRound(
            "Time limit reached after " + seconds + " seconds.",
            "Timer expired after " + seconds + " seconds."
        );
    }

    private void concludeRound(String broadcastMessage, String logMessage) {
        gameRunning = false;

        World mainWorld = Bukkit.getWorld(mainWorldName);
        if (mainWorld != null) {
            for (UUID playerId : roundParticipants) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.teleportAsync(mainWorld.getSpawnLocation());
                }
            }
        }

        Bukkit.broadcast(prefixed(Component.text(broadcastMessage, NamedTextColor.YELLOW)));
        getLogger().info(logMessage);

        List<UUID> ranking = new ArrayList<>(roundParticipants);
        ranking.sort(Comparator.comparingInt((UUID playerId) -> goalManager.getPoints(playerId)).reversed());

        Bukkit.broadcast(prefixed(Component.text("Final Scores", NamedTextColor.GOLD, TextDecoration.BOLD)));
        if (ranking.isEmpty()) {
            Bukkit.broadcast(prefixed(Component.text("No participants in this round.", NamedTextColor.GRAY)));
        } else {
            int rank = 1;
            for (UUID playerId : ranking) {
                String name = Bukkit.getOfflinePlayer(playerId).getName();
                if (name == null) {
                    name = playerId.toString();
                }
                int points = goalManager.getPoints(playerId);
                Bukkit.broadcast(prefixed(
                    Component.text(rank + ". ", NamedTextColor.YELLOW)
                        .append(Component.text(name, NamedTextColor.WHITE))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(points + " pts", NamedTextColor.AQUA))
                ));
                rank++;
            }
        }

        if (timerBossBar != null) {
            timerBossBar.progress(0.0f);
            timerBossBar.name(bossBarTime("00:00"));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(timerBossBar);
            }
        }

        worldService.moveActiveToPreviousRound();
        roundParticipants.clear();
    }

    private void updateTimerDisplay() {
        if (!timer.hasLimit()) {
            return;
        }

        long remainingSeconds = timer.getRemainingSeconds();
        String display = formatClock(remainingSeconds);

        if (timerBossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showBossBar(timerBossBar);
            }

            float progress = (float) ((double) timer.getRemainingMillis() / (double) timer.getLimitMillis());
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            timerBossBar.progress(progress);
            timerBossBar.name(bossBarTime(display));
        }
    }

    private String formatClock(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void sendBingoUsage(CommandSender sender) {
        sender.sendMessage(prefixed(
            Component.text("Usage: ", NamedTextColor.GRAY)
                .append(Component.text("/bingo <start|stop|goals>", NamedTextColor.AQUA))
        ));
    }

    public void sendGoalsUsage(CommandSender sender) {
        sender.sendMessage(prefixed(
            Component.text("Usage: ", NamedTextColor.GRAY)
                .append(Component.text("/bingo goals [admin|validate|reload]", NamedTextColor.AQUA))
        ));
    }

    public boolean handleStartCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixed(Component.text("Only players can use /bingo start.", NamedTextColor.RED)));
            return true;
        }
        if (!player.isOp()) {
            sender.sendMessage(prefixed(Component.text("Only operators can start a Bingo round.", NamedTextColor.RED)));
            return true;
        }
        if (gameRunning) {
            sender.sendMessage(prefixed(Component.text("A Bingo round is already running.", NamedTextColor.YELLOW)));
            return true;
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            sender.sendMessage(prefixed(Component.text("Cannot start: no players are online.", NamedTextColor.RED)));
            return true;
        }
        newGameGui.open(player).thenAccept(confirmed -> {
            if (!confirmed) {
                sender.sendMessage(prefixed(Component.text("Bingo round start cancelled.", NamedTextColor.YELLOW)));
                return;
            }

            Bukkit.getScheduler().runTask(this, () -> {
                showStartingTitle();
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    long selectedSeed = newGameGui.getWorldSeed();
                    boolean started = startGame(selectedSeed);
                    clearStartingTitle();

                    if (started) {
                        sender.sendMessage(prefixed(Component.text("Bingo round started.", NamedTextColor.GREEN)));
                    } else {
                        sender.sendMessage(prefixed(Component.text("Could not start Bingo round.", NamedTextColor.RED)));
                    }
                }, 1L);
            });
        });
        return true;
    }

    public boolean handleStopCommand(CommandSender sender) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage(prefixed(Component.text("Only operators can stop a Bingo round.", NamedTextColor.RED)));
            return true;
        }
        if (!gameRunning) {
            sender.sendMessage(prefixed(Component.text("No Bingo round is currently running.", NamedTextColor.YELLOW)));
            return true;
        }

        if (timer.isRunning()) {
            timer.stop();
        }
        timerExpiredHandled = true;

        concludeRound(
            "Bingo round stopped early by " + sender.getName() + ".",
            "Bingo round stopped early by " + sender.getName() + "."
        );
        sender.sendMessage(prefixed(Component.text("Bingo round stopped.", NamedTextColor.GREEN)));
        return true;
    }

    public boolean handleGoalsViewCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixed(Component.text("Only players can open the goals UI.", NamedTextColor.RED)));
            return true;
        }
        goalsViewerGui.open(player);
        return true;
    }

    public boolean handleGoalsAdminCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixed(Component.text("Only players can open the admin UI.", NamedTextColor.RED)));
            return true;
        }
        if (!player.hasPermission("bingomc.goals.admin")) {
            player.sendMessage(prefixed(Component.text("You do not have permission to manage goals.", NamedTextColor.RED)));
            return true;
        }
        goalsAdminGui.open(player);
        return true;
    }

    public boolean handleGoalsValidateCommand(CommandSender sender) {
        GoalLoadResult result = goalConfigService.loadGoals();
        if (result.isValid()) {
            sender.sendMessage(prefixed(
                Component.text("goals.yml is valid. Loaded ", NamedTextColor.GREEN)
                    .append(Component.text(result.goals().size(), NamedTextColor.AQUA))
                    .append(Component.text(" enabled goals.", NamedTextColor.GREEN))
            ));
        } else {
            sender.sendMessage(prefixed(Component.text("goals.yml has validation errors:", NamedTextColor.RED)));
            for (String error : result.errors()) {
                sender.sendMessage(prefixed(Component.text("- " + error, NamedTextColor.GRAY)));
            }
        }
        return true;
    }

    public boolean handleGoalsReloadCommand(CommandSender sender) {
        if (sender instanceof Player player && !player.hasPermission("bingomc.goals.admin")) {
            sender.sendMessage(prefixed(Component.text("You do not have permission to reload goals.", NamedTextColor.RED)));
            return true;
        }
        if (gameRunning) {
            sender.sendMessage(prefixed(Component.text("Cannot reload goals while a round is running.", NamedTextColor.YELLOW)));
            return true;
        }
        boolean loaded = reloadGoalsFromDisk(false);
        sender.sendMessage(prefixed(Component.text(
            loaded ? "Goals reloaded." : "Could not reload goals; see console for details.",
            loaded ? NamedTextColor.GREEN : NamedTextColor.RED
        )));
        return true;
    }

    public GoalManager getGoalManager() {
        return goalManager;
    }

    public long getRoundRemainingSeconds() {
        return timer.getRemainingSeconds();
    }

    private boolean reloadGoalsFromDisk(boolean startup) {
        GoalLoadResult result = goalConfigService.loadGoals();
        if (!result.isValid()) {
            for (String error : result.errors()) {
                getLogger().severe("Goal config error: " + error);
            }
            if (startup) {
                getLogger().severe("Plugin startup aborted due to invalid goals.yml");
            }
            return false;
        }

        goalManager.clearRegisteredGoals();
        for (var loaded : result.goals()) {
            goalManager.registerGoal(loaded.goal(), loaded.points());
        }
        getLogger().info("Loaded " + result.goals().size() + " goals from goals.yml");
        return true;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    private boolean startGame(long worldSeed) {
        if (gameRunning) {
            return false;
        }

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (onlinePlayers.isEmpty()) {
            return false;
        }

        var createdWorldSets = worldService.provisionRoundWorldSets(onlinePlayers, worldSeed);
        if (createdWorldSets == null) {
            return false;
        }

        goalManager.resetAllProgress();
        roundParticipants.clear();

        for (Player player : onlinePlayers) {
            roundParticipants.add(player.getUniqueId());
            consumeTracker.clearConsumedItems(player);
            goalManager.onRoundStart(player);

            PlayerWorldSet worldSet = createdWorldSets.get(player.getUniqueId());
            World playerWorld = worldSet == null ? null : Bukkit.getWorld(worldSet.overworldName());
            if (playerWorld == null) {
                getLogger().severe("Player world missing after creation for " + player.getName());
                worldService.cleanupWorldSets(createdWorldSets.values());
                worldService.clearTrackedRoundWorlds();
                roundParticipants.clear();
                return false;
            }
            player.teleportAsync(playerWorld.getSpawnLocation());
        }

        worldService.activateRoundWorldSets(createdWorldSets);

        timer.reset();
        timer.setLimitSeconds(GAME_DURATION_SECONDS);
        timer.start();
        timerExpiredHandled = false;
        gameRunning = true;

        if (timerBossBar != null) {
            timerBossBar.progress(1.0f);
            timerBossBar.name(bossBarTime(formatClock(GAME_DURATION_SECONDS)));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showBossBar(timerBossBar);
            }
        }

        Bukkit.broadcast(prefixed(
            Component.text("Bingo round has started. You have ", NamedTextColor.GREEN)
                .append(Component.text(formatClock(GAME_DURATION_SECONDS), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" minutes.", NamedTextColor.GREEN))
                .append(Component.text(" Use ", NamedTextColor.YELLOW))
                .append(Component.text("/bingo goals", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" to view your objectives.", NamedTextColor.YELLOW))
        ));
        return true;
    }

    private void showStartingTitle() {
        Title startingTitle = Title.title(
            Component.text("Bingo game starting...", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.empty(),
            Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(5), Duration.ofMillis(200))
        );

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.showTitle(startingTitle);
        }
    }

    private void clearStartingTitle() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.resetTitle();
        }
    }

    private Component bossBarTime(String display) {
        return Component.text("Time Left: ", NamedTextColor.AQUA)
            .append(Component.text(display, NamedTextColor.WHITE, TextDecoration.BOLD));
    }

    private Component prefixed(Component message) {
        return Component.text()
            .append(Component.text("[Bingo] ", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(message)
            .build();
    }

}