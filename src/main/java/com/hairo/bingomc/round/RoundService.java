package com.hairo.bingomc.round;

import com.hairo.bingomc.events.TimerExpiredEvent;
import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.util.ConsumeTracker;
import com.hairo.bingomc.goals.util.Timer;
import com.hairo.bingomc.worlds.BingoWorldService;
import com.hairo.bingomc.worlds.PlayerWorldSet;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public class RoundService {

    private final JavaPlugin plugin;
    private final GoalManager goalManager;
    private final ConsumeTracker consumeTracker;
    private final BingoWorldService worldService;
    private final String mainWorldName;
    private final long defaultGameDurationSeconds;
    private final long preparationCountdownSeconds;
    private final Function<Component, Component> prefixer;

    private final Set<UUID> roundParticipants = new HashSet<>();

    private Timer timer;
    private boolean gameRunning;
    private boolean timerExpiredHandled;
    private BossBar timerBossBar;

    private boolean preparationActive = false;
    private long pendingRoundDurationSeconds;
    private BukkitTask preparationTask;
    public RoundService(
        JavaPlugin plugin,
        GoalManager goalManager,
        ConsumeTracker consumeTracker,
        BingoWorldService worldService,
        String mainWorldName,
        long gameDurationSeconds,
        long preparationCountdownSeconds,
        Function<Component, Component> prefixer
    ) {
        this.plugin = plugin;
        this.goalManager = goalManager;
        this.consumeTracker = consumeTracker;
        this.worldService = worldService;
        this.mainWorldName = mainWorldName;
        this.defaultGameDurationSeconds = gameDurationSeconds;
        this.preparationCountdownSeconds = preparationCountdownSeconds;
        this.prefixer = prefixer;
    }

    public void initialize() {
        timer = new Timer();
        timer.setLimitSeconds(defaultGameDurationSeconds);
        timer.reset();
        gameRunning = false;
        timerExpiredHandled = false;
        timerBossBar = BossBar.bossBar(
            bossBarTime("00:00"),
            0.0f,
            BossBar.Color.BLUE,
            BossBar.Overlay.PROGRESS
        );
    }

    public void startTicker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
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

    public void onDisable() {
        if (preparationActive) {
            if (preparationTask != null) {
                preparationTask.cancel();
                preparationTask = null;
            }
            preparationActive = false;
        }

        if (timer != null && timer.isRunning()) {
            timer.stop();
        }

        worldService.cleanupManagedBingoWorldsOnShutdown(mainWorldName);

        if (timerBossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(timerBossBar);
            }
        }
    }

    public void onPlayerJoin(Player player) {
        if (timerBossBar != null && gameRunning) {
            player.sendMessage(prefixer.apply(Component.text(
                "A round is currently running. You can join in the next round.",
                NamedTextColor.YELLOW
            )));
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
        if (gameRunning || preparationActive) {
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

        goalManager.resetAllProgress();
        roundParticipants.clear();

        for (Player player : onlinePlayers) {
            roundParticipants.add(player.getUniqueId());
            consumeTracker.clearConsumedItems(player);
            goalManager.onRoundStart(player);

            PlayerWorldSet worldSet = createdWorldSets.get(player.getUniqueId());
            World playerWorld = worldSet == null ? null : Bukkit.getWorld(worldSet.overworldName());
            if (playerWorld == null) {
                plugin.getLogger().severe("Player world missing after creation for " + player.getName());
                worldService.cleanupWorldSets(createdWorldSets.values());
                worldService.clearTrackedRoundWorlds();
                roundParticipants.clear();
                return false;
            }
            player.teleportAsync(playerWorld.getSpawnLocation());
        }

        worldService.activateRoundWorldSets(createdWorldSets);

        beginPreparation(roundDurationSeconds);
        return true;
    }

    public boolean stopRound(String actorName) {
        if (!gameRunning && !preparationActive) {
            return false;
        }

        if (preparationActive) {
            if (preparationTask != null) {
                preparationTask.cancel();
                preparationTask = null;
            }
            preparationActive = false;

            // Teleport participants back to the main world before cleaning up worlds.
            World mainWorld = Bukkit.getWorld(mainWorldName);
            if (mainWorld == null) {
                plugin.getLogger().warning("Main world missing while aborting preparation; players may remain in personal worlds.");
                worldService.moveActiveToPreviousRound();
                roundParticipants.clear();
                Bukkit.broadcast(prefixer.apply(
                    Component.text("Bingo round preparation cancelled by " + actorName + ".", NamedTextColor.YELLOW)
                ));
                return true;
            }

            List<CompletableFuture<?>> teleportFutures = new ArrayList<>();
            for (UUID playerId : roundParticipants) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    if (timerBossBar != null) {
                        player.hideBossBar(timerBossBar);
                    }
                    try {
                        CompletableFuture<?> f = player.teleportAsync(mainWorld.getSpawnLocation());
                        if (f != null) {
                            teleportFutures.add(f);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to teleport player " + player.getName() + " during abort: " + e.getMessage());
                    }
                }
            }

            if (teleportFutures.isEmpty()) {
                worldService.moveActiveToPreviousRound();
                roundParticipants.clear();
                Bukkit.broadcast(prefixer.apply(
                    Component.text("Bingo round preparation cancelled by " + actorName + ".", NamedTextColor.YELLOW)
                ));
                return true;
            }

            CompletableFuture.allOf(teleportFutures.toArray(new CompletableFuture[0]))
                .orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        plugin.getLogger().warning("One or more teleports failed or timed out while aborting preparation: " + ex.getMessage());
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        worldService.moveActiveToPreviousRound();
                        roundParticipants.clear();
                        Bukkit.broadcast(prefixer.apply(
                            Component.text("Bingo round preparation cancelled by " + actorName + ".", NamedTextColor.YELLOW)
                        ));
                    });
                });

            return true;
        }

        if (timer.isRunning()) {
            timer.stop();
        }
        timerExpiredHandled = true;

        concludeRound(
            "Bingo round stopped early by " + actorName + ".",
            "Bingo round stopped early by " + actorName + "."
        );
        return true;
    }

    public void showStartingTitle() {
        Title startingTitle = Title.title(
            Component.text("Bingo game starting...", NamedTextColor.GOLD),
            Component.empty(),
            Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(5), Duration.ofMillis(200))
        );

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.showTitle(startingTitle);
        }
    }

    public void clearStartingTitle() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.resetTitle();
        }
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public boolean isPreparationActive() {
        return preparationActive;
    }

    public boolean isParticipant(java.util.UUID playerId) {
        return roundParticipants.contains(playerId);
    }

    public boolean isParticipant(Player player) {
        return player != null && isParticipant(player.getUniqueId());
    }

    public long getRoundRemainingSeconds() {
        return timer.getRemainingSeconds();
    }

    private void beginPreparation(long roundDurationSeconds) {
        pendingRoundDurationSeconds = roundDurationSeconds;
        preparationActive = true;

        // Make participants invulnerable and silent during preparation
        for (UUID id : roundParticipants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                try {
                    p.setInvulnerable(true);
                    p.setSilent(true);
                    p.setCanPickupItems(false);
                } catch (Exception ignored) {
                }
            }
        }

        Bukkit.broadcast(prefixer.apply(
            Component.text("Bingo round is starting! Prepare yourself. Round begins in ", NamedTextColor.GOLD)
                .append(Component.text(formatClock(preparationCountdownSeconds), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text("...", NamedTextColor.GOLD))
        ));

        final long[] remaining = {preparationCountdownSeconds};

        preparationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (remaining[0] > 0) {
                String timeDisplay = formatClock(remaining[0]);
                Component actionBar = Component.text("Round starts in: ", NamedTextColor.YELLOW)
                    .append(Component.text(timeDisplay, NamedTextColor.WHITE, TextDecoration.BOLD));

                for (UUID id : roundParticipants) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        p.sendActionBar(actionBar);
                    }
                }

                if (remaining[0] <= 10) {
                    NamedTextColor countColor = remaining[0] <= 5 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
                    Title countTitle = Title.title(
                        Component.text(remaining[0], countColor, TextDecoration.BOLD),
                        Component.empty(),
                        Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(1100), Duration.ofMillis(0))
                    );
                    for (UUID id : roundParticipants) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null && p.isOnline()) {
                            p.showTitle(countTitle);
                        }
                    }
                }

                remaining[0]--;
            } else {
                preparationTask.cancel();
                preparationTask = null;
                launchRound();
            }
        }, 20L, 20L);
    }

    private void launchRound() {
        preparationActive = false;
        // stop altering world game rules; player state restored below

        // Restore participant state
        for (UUID id : roundParticipants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                try {
                    p.setInvulnerable(false);
                    p.setSilent(false);
                    p.setCanPickupItems(true);
                } catch (Exception ignored) {
                }
            }
        }

        Title goTitle = Title.title(
            Component.text("GO!", NamedTextColor.GREEN, TextDecoration.BOLD),
            Component.empty(),
            Title.Times.times(Duration.ofMillis(0), Duration.ofSeconds(1), Duration.ofMillis(500))
        );
        for (UUID id : roundParticipants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.showTitle(goTitle);
            }
        }

        timer.reset();
        timer.setLimitSeconds(pendingRoundDurationSeconds);
        timer.start();
        timerExpiredHandled = false;
        gameRunning = true;

        if (timerBossBar != null) {
            timerBossBar.progress(1.0f);
            timerBossBar.name(bossBarTime(formatClock(pendingRoundDurationSeconds)));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showBossBar(timerBossBar);
            }
        }

        Bukkit.broadcast(prefixer.apply(
            Component.text("Bingo round has started. You have ", NamedTextColor.GREEN)
                .append(Component.text(formatClock(pendingRoundDurationSeconds), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" minutes.", NamedTextColor.GREEN))
                .append(Component.text(" Use ", NamedTextColor.YELLOW))
                .append(Component.text("/bingo goals", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" to view your objectives.", NamedTextColor.YELLOW))
        ));
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

        Bukkit.broadcast(prefixer.apply(Component.text(broadcastMessage, NamedTextColor.YELLOW)));
        plugin.getLogger().info(logMessage);

        List<UUID> ranking = new ArrayList<>(roundParticipants);
        ranking.sort(Comparator.comparingInt((UUID playerId) -> goalManager.getPoints(playerId)).reversed());

        Bukkit.broadcast(prefixer.apply(Component.text("Final Scores", NamedTextColor.GOLD, TextDecoration.BOLD)));
        if (ranking.isEmpty()) {
            Bukkit.broadcast(prefixer.apply(Component.text("No participants in this round.", NamedTextColor.GRAY)));
        } else {
            int rank = 1;
            for (UUID playerId : ranking) {
                String name = Bukkit.getOfflinePlayer(playerId).getName();
                if (name == null) {
                    name = playerId.toString();
                }
                int points = goalManager.getPoints(playerId);
                Bukkit.broadcast(prefixer.apply(
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

    private Component bossBarTime(String display) {
        return Component.text("Time Left: ", NamedTextColor.AQUA)
            .append(Component.text(display, NamedTextColor.WHITE, TextDecoration.BOLD));
    }
}
