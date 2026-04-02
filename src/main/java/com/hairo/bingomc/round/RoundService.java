package com.hairo.bingomc.round;

import com.hairo.bingomc.events.TimerExpiredEvent;
import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.goals.util.Timer;
import com.hairo.bingomc.worlds.BingoWorldService;
import com.hairo.bingomc.worlds.PlayerWorldSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class RoundService {

    private final JavaPlugin plugin;
    private final GoalManager goalManager;
    private final BingoWorldService worldService;
    private final String mainWorldName;
    private final long defaultGameDurationSeconds;
    private final long preparationCountdownSeconds;

    private final RoundParticipants participants;
    private final RoundDisplay display;
    private final RoundTaskTicker taskTicker;

    private Timer timer;
    private boolean gameRunning = false;
    private boolean gamePreparing = false;
    private long pendingRoundDurationSeconds;

    public RoundService(
        JavaPlugin plugin,
        GoalManager goalManager,
        BingoWorldService worldService,
        String mainWorldName,
        long gameDurationSeconds,
        long preparationCountdownSeconds,
        Function<Component, Component> prefixer
    ) {
        this.plugin = plugin;
        this.goalManager = goalManager;
        this.worldService = worldService;
        this.mainWorldName = mainWorldName;
        this.defaultGameDurationSeconds = gameDurationSeconds;
        this.preparationCountdownSeconds = preparationCountdownSeconds;

        this.participants = new RoundParticipants();
        this.display = new RoundDisplay(prefixer, participants);
        this.taskTicker = new RoundTaskTicker(plugin, goalManager, display, participants);
    }

    public void initialize() {
        timer = new Timer();
        timer.setLimitSeconds(defaultGameDurationSeconds);
        timer.reset();
        gameRunning = false;
    }

    public void startTicker() {
        if (timer != null && timer.isRunning()) {
            taskTicker.startGameTicker(timer);
        }
    }

    public void onDisable() {
        // Stop any running tickers and timers
        taskTicker.stopPreparationTicker();
        taskTicker.stopGameTicker();

        if (timer != null && timer.isRunning()) {
            timer.stop();
        }

        // Ensure preparation flags are cleared for participants
        if (gamePreparing) {
            gamePreparing = false;
            participants.applyPreparationState(false);
        }

        // If a game was running, attempt graceful cleanup: teleport participants back and log
        if (gameRunning) {
            World mainWorld = Bukkit.getWorld(mainWorldName);
            if (mainWorld != null) {
                for (UUID playerId : participants.getParticipants()) {
                    Player p = Bukkit.getPlayer(playerId);
                    if (p != null && p.isOnline()) {
                        try {
                            p.teleport(mainWorld.getSpawnLocation());
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to teleport player during shutdown: " + e.getMessage());
                        }
                    }
                }
            }
            display.broadcastMessage("Bingo round ended due to server shutdown.", NamedTextColor.YELLOW);
            gameRunning = false;
        }

        // Cleanup worlds and UI
        worldService.cleanupManagedBingoWorldsOnShutdown(mainWorldName);
        display.hideBossBar();

        // Clear participant list and related state
        participants.clear();
    }

    public void onPlayerJoin(Player player) {
        if (gameRunning) {
            player.sendMessage(display.getPrefixedString("A round is currently running. You can join in the next round.", NamedTextColor.YELLOW));
        }
    }

    public void onTimerExpired(TimerExpiredEvent event) {
        String seconds = String.valueOf(event.getElapsedSeconds());
        concludeRound(
            "Time limit reached after " + seconds + " seconds.",
            "Timer expired after " + seconds + " seconds."
        );
    }

    public boolean startRound(long worldSeed) {
        return startRound(worldSeed, defaultGameDurationSeconds);
    }
    
    public boolean startRound(long worldSeed, long selectedDurationSeconds) {
        if (gameRunning || gamePreparing) {
            return false;
        }
        
        long roundDurationSeconds = selectedDurationSeconds > 0 ? selectedDurationSeconds : defaultGameDurationSeconds;

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (onlinePlayers.isEmpty()) {
            return false;
        }

        var createdWorldSets = worldService.provisionRoundWorldSets(onlinePlayers, worldSeed);
        if (createdWorldSets == null) {
            return false;
        }

        // Verify worlds exist for all participants before mutating state
        for (UUID participantId : participants.getParticipants()) {
            Player player = Bukkit.getPlayer(participantId);
            if (player == null) continue;
            
            PlayerWorldSet worldSet = createdWorldSets.get(participantId);
            if (worldSet == null) {
                plugin.getLogger().severe("Player world missing after creation for " + player.getName());
                worldService.cleanupWorldSets(createdWorldSets.values());
                worldService.clearTrackedRoundWorlds();
                return false;
            }
            World playerWorld = Bukkit.getWorld(worldSet.overworldName());
            if (playerWorld == null) {
                plugin.getLogger().severe("Player world not loaded for " + player.getName());
                worldService.cleanupWorldSets(createdWorldSets.values());
                worldService.clearTrackedRoundWorlds();
                return false;
            }
        }

        // Activate and teleport players synchronously to ensure they're in their round worlds
        worldService.activateRoundWorldSets(createdWorldSets);
        
        // Initialize goal state before teleportation
        goalManager.resetAllProgress();
        try {
            for (UUID participantId : participants.getParticipants()) {
                Player player = Bukkit.getPlayer(participantId);
                if (player == null) continue;
                goalManager.onRoundStart(player);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize goals during round start: " + e.getMessage());
            worldService.cleanupWorldSets(createdWorldSets.values());
            worldService.clearTrackedRoundWorlds();
            participants.clear();
            goalManager.resetAllProgress();
            return false;
        }

        // Now teleport players
        for (UUID participantId : participants.getParticipants()) {
            Player player = Bukkit.getPlayer(participantId);
            if (player == null) continue;

            PlayerWorldSet worldSet = createdWorldSets.get(participantId);
            World playerWorld = Bukkit.getWorld(worldSet.overworldName());
            try {
                player.teleport(centeredSpawn(playerWorld));
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to teleport player " + player.getName() + ": " + e.getMessage());
                worldService.cleanupWorldSets(createdWorldSets.values());
                worldService.clearTrackedRoundWorlds();
                participants.clear();
                goalManager.resetAllProgress();
                return false;
            }
        }

        // Show title and broadcast after teleportation, before scheduling tasks
        display.broadcastPreparationStart(preparationCountdownSeconds);

        beginPreparation(roundDurationSeconds);
        return true;
    }

    public boolean stopRound(String actorName) {
        if (!gameRunning && !gamePreparing) {
            return false;
        }

        if (gamePreparing) {
            taskTicker.stopPreparationTicker();
            gamePreparing = false;
        }

        participants.applyPreparationState(false);

        if (timer != null && timer.isRunning()) {
            timer.stop();
        }

        taskTicker.stopGameTicker();

        concludeRound(
            "Bingo round stopped early by " + actorName + ".",
            "Bingo round stopped early by " + actorName + "."
        );
        return true;
    }

    public void primeStartingParticipants() {
        participants.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            participants.add(player.getUniqueId());
        }
    }

    public void showStartingTitle() {
        display.showStartingTitle();
    }

    public void clearStartingTitle() {
        display.clearStartingTitle();
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public boolean isGamePreparing() {
        return gamePreparing;
    }

    public boolean isParticipant(UUID playerId) {
        return participants.isParticipant(playerId);
    }

    public boolean isParticipant(Player player) {
        return participants.isParticipant(player);
    }

    public long getRoundRemainingSeconds() {
        return timer != null ? timer.getRemainingSeconds() : 0L;
    }

    private static Location centeredSpawn(World world) {
        Location spawn = world.getSpawnLocation();
        spawn.setX(Math.floor(spawn.getX()) + 0.5);
        spawn.setZ(Math.floor(spawn.getZ()) + 0.5);
        return spawn;
    }

    private void beginPreparation(long roundDurationSeconds) {
        pendingRoundDurationSeconds = roundDurationSeconds;
        gamePreparing = true;
        participants.applyPreparationState(true);

        taskTicker.startPreparationTicker(preparationCountdownSeconds, this::launchRound);
    }

    private void launchRound() {
        gamePreparing = false;
        participants.applyPreparationState(false);

        display.showGoTitle();

        timer.reset();
        timer.setLimitSeconds(pendingRoundDurationSeconds);
        timer.start();

        // Start the in-game ticker now that the timer is running
        taskTicker.startGameTicker(timer);
        gameRunning = true;

        display.broadcastRoundStart(pendingRoundDurationSeconds);
    }

    private void concludeRound(String broadcastMessage, String logMessage) {
        gameRunning = false;

        World mainWorld = Bukkit.getWorld(mainWorldName);
        if (mainWorld != null) {
            for (UUID playerId : participants.getParticipants()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.teleport(mainWorld.getSpawnLocation());
                }
            }
        }

        display.broadcastMessage(broadcastMessage, NamedTextColor.YELLOW);
        plugin.getLogger().info(logMessage);

        List<UUID> ranking = new ArrayList<>(participants.getParticipants());
        ranking.sort(Comparator.comparingInt((UUID id) -> goalManager.getPoints(id)).reversed());

        List<RoundDisplay.RankEntry> entries = ranking.stream()
            .map(id -> {
                String name = Bukkit.getOfflinePlayer(id).getName();
                return new RoundDisplay.RankEntry(name != null ? name : id.toString(), goalManager.getPoints(id));
            })
            .toList();
        display.broadcastRanking(entries);
        display.hideBossBar();
        
        // We mark worlds for deletion, but don't immediately delete them.
        // To allow players to teleport out and provide admins a chance to investigate or view worlds after round conclusion if needed.
        worldService.moveActiveToPreviousRound();
        participants.clear();
    }
}
