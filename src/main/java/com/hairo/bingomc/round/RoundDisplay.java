package com.hairo.bingomc.round;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class RoundDisplay {

    private final Function<Component, Component> prefixer;
    private final BossBar timerBossBar;
    private final RoundParticipants participants;

    public RoundDisplay(Function<Component, Component> prefixer, RoundParticipants participants) {
        this.prefixer = prefixer;
        this.participants = participants;
        this.timerBossBar = BossBar.bossBar(
                bossBarTime("00:00"),
                0.0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS);
    }

    public void showStartingTitle() {
        Title startingTitle = Title.title(
                Component.text("Bingo game starting...", NamedTextColor.GOLD),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(5), Duration.ofMillis(200)));
        forEachOnlineParticipant(player -> player.showTitle(startingTitle));
    }

    public void clearStartingTitle() {
        forEachOnlineParticipant(Player::resetTitle);
    }

    public void showGoTitle() {
        Title goTitle = Title.title(
                Component.text("GO!", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(0), Duration.ofSeconds(1), Duration.ofMillis(500)));
        forEachOnlineParticipant(player -> player.showTitle(goTitle));
    }

    public void updatePreparationDisplay(long remainingSeconds) {
        String timeDisplay = formatClock(remainingSeconds);

        // Action bar for participants
        if (remainingSeconds > 10) {
            Component actionBar = Component.text("Round starts in: ", NamedTextColor.YELLOW)
                    .append(Component.text(timeDisplay, NamedTextColor.WHITE, TextDecoration.BOLD));
            forEachOnlineParticipant(player -> player.sendActionBar(actionBar));
        } else {
            // Clear immediately once we switch to title countdown mode.
            forEachOnlineParticipant(player -> player.sendActionBar(Component.empty()));
        }

        // Countdown title in last 10 seconds
        if (remainingSeconds <= 10) {
            NamedTextColor countColor = remainingSeconds <= 5 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
            Title countTitle = Title.title(
                    Component.text(remainingSeconds, countColor, TextDecoration.BOLD),
                    Component.empty(),
                    Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(1100), Duration.ofMillis(0)));
            forEachOnlineParticipant(player -> player.showTitle(countTitle));
        }
    }

    public void broadcastPreparationStart(long countdownSeconds) {
        broadcastMessage(
                Component.text("Bingo round is starting! Prepare yourself. Round begins in ", NamedTextColor.GOLD)
                        .append(Component.text(formatClock(countdownSeconds), NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text("...", NamedTextColor.GOLD)));
    }

    public void broadcastRoundStart(long durationSeconds) {
        broadcastMessage(
                Component.text("Bingo round has started. You have ", NamedTextColor.GREEN)
                        .append(Component.text(formatClock(durationSeconds), NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" remaining.", NamedTextColor.GREEN))
                        .append(Component.text(" Use ", NamedTextColor.YELLOW))
                        .append(Component.text("/bingo goals", NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(" to view your objectives.", NamedTextColor.YELLOW)));
    }

    public void updateGameTimerDisplay(long remainingMillis, long limitMillis) {
        long remainingSeconds = remainingMillis / 1000;
        String display = formatClock(remainingSeconds);

        if (timerBossBar != null) {
            forEachOnlineParticipant(player -> player.showBossBar(timerBossBar));

            float progress = limitMillis > 0 ? (float) remainingMillis / (float) limitMillis : 0.0f;
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            timerBossBar.progress(progress);
            timerBossBar.name(bossBarTime(display));
        }
    }

    public record RankEntry(String name, int points) {
    }

    public void broadcastRanking(List<RankEntry> ranking) {
        broadcastMessage("Final Scores", NamedTextColor.GOLD);
        if (ranking.isEmpty()) {
            broadcastMessage("No participants in this round.", NamedTextColor.GRAY);
            return;
        }
        int rank = 1;
        for (RankEntry entry : ranking) {
            Component line = Component.text(rank + ". ", NamedTextColor.YELLOW)
                    .append(Component.text(entry.name(), NamedTextColor.WHITE))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(entry.points() + " pts", NamedTextColor.AQUA));
            broadcastComponent(line);
            rank++;
        }
    }

    public void hideBossBar() {
        forEachOnlineParticipant(player -> player.hideBossBar(timerBossBar));
    }

    public void broadcastMessage(String message, NamedTextColor color) {
        Bukkit.broadcast(prefixer.apply(Component.text(message, color)));
    }

    public void broadcastMessage(Component message) {
        Bukkit.broadcast(prefixer.apply(message));
    }

    public void broadcastComponent(Component message) {
        Bukkit.broadcast(prefixer.apply(message));
    }

    public Component getPrefixedString(String message, NamedTextColor color) {
        return prefixer.apply(Component.text(message, color));
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

    private void forEachOnlineParticipant(Consumer<Player> action) {
        for (UUID id : participants.getParticipants()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                action.accept(player);
            }
        }
    }
}
