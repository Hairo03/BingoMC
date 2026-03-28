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
    private final Function<Component, Component> prefixer;

    private final Set<UUID> roundParticipants = new HashSet<>();

    private Timer timer;
    private boolean gameRunning;
    private boolean timerExpiredHandled;
    private BossBar timerBossBar;

    public RoundService(
        JavaPlugin plugin,
        GoalManager goalManager,
        ConsumeTracker consumeTracker,
        BingoWorldService worldService,
        String mainWorldName,
        long gameDurationSeconds,
        Function<Component, Component> prefixer
    ) {
        this.plugin = plugin;
        this.goalManager = goalManager;
        this.consumeTracker = consumeTracker;
        this.worldService = worldService;
        this.mainWorldName = mainWorldName;
        this.defaultGameDurationSeconds = gameDurationSeconds;
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
        if (gameRunning) {
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

        timer.reset();
        timer.setLimitSeconds(roundDurationSeconds);
        timer.start();
        timerExpiredHandled = false;
        gameRunning = true;

        if (timerBossBar != null) {
            timerBossBar.progress(1.0f);
            timerBossBar.name(bossBarTime(formatClock(roundDurationSeconds)));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showBossBar(timerBossBar);
            }
        }

        Bukkit.broadcast(prefixer.apply(
            Component.text("Bingo round has started. You have ", NamedTextColor.GREEN)
                .append(Component.text(formatClock(roundDurationSeconds), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" minutes.", NamedTextColor.GREEN))
                .append(Component.text(" Use ", NamedTextColor.YELLOW))
                .append(Component.text("/bingo goals", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" to view your objectives.", NamedTextColor.YELLOW))
        ));
        return true;
    }

    public boolean stopRound(String actorName) {
        if (!gameRunning) {
            return false;
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

    public long getRoundRemainingSeconds() {
        return timer.getRemainingSeconds();
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
