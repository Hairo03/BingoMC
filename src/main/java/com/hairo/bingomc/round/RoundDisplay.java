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
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Function;

public class RoundDisplay {

    private final Function<Component, Component> prefixer;
    private final BossBar timerBossBar;
    private final RoundParticipants participants;
    private final Set<UUID> bossBarViewers = new HashSet<>();

    public RoundDisplay(Function<Component, Component> prefixer, RoundParticipants participants) {
        this.prefixer = prefixer;
        this.participants = participants;
        this.timerBossBar = BossBar.bossBar(
            Component.empty(),
            0.0f,
            BossBar.Color.BLUE,
            BossBar.Overlay.PROGRESS
        );
    }

    public void showStartingTitle() {
        Title startingTitle = Title.title(
            Component.text("Bingo game starting...", NamedTextColor.GOLD),
            Component.empty(),
            Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(5), Duration.ofMillis(200))
        );
        for (UUID id : participants.getParticipants()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.showTitle(startingTitle);
            }
        }
    }

    public void clearStartingTitle() {
        for (UUID id : participants.getParticipants()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.resetTitle();
            }
        }
    }

    /**
     * Updates all player displays for both preparation and active phases.
     * Shows a highlighted countdown title in the last 10 seconds.
     */
    public void updateDisplays(long remainingSeconds, long totalSeconds) {
        float progress = totalSeconds > 0 ? (float) remainingSeconds / totalSeconds : 0.0f;
        timerBossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
        timerBossBar.name(
            Component.text("Time Left: ", NamedTextColor.AQUA)
                .append(Component.text(formatClock(remainingSeconds), NamedTextColor.WHITE, TextDecoration.BOLD))
        );

        Title title = null;
        if (remainingSeconds <= 10) {
            NamedTextColor countColor = remainingSeconds <= 5 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
            title = Title.title(
                Component.text(remainingSeconds, countColor, TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(1100), Duration.ofMillis(0))
            );
        }

        // Remove bossbar from viewers that are no longer participants or are offline
        var currentParticipants = participants.getParticipants();
        var viewersCopy = Set.copyOf(bossBarViewers);
        for (UUID viewerId : viewersCopy) {
            if (!currentParticipants.contains(viewerId)) {
                Player p = Bukkit.getPlayer(viewerId);
                if (p != null && p.isOnline()) {
                    p.hideBossBar(timerBossBar);
                }
                bossBarViewers.remove(viewerId);
            }
        }

        for (UUID id : currentParticipants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                if (!bossBarViewers.contains(id)) {
                    player.showBossBar(timerBossBar);
                    bossBarViewers.add(id);
                }
                if (title != null) player.showTitle(title);
            }
        }
    }

    public record RankEntry(String name, int points) {}

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
        for (UUID id : bossBarViewers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.hideBossBar(timerBossBar);
            }
        }
        bossBarViewers.clear();
    }

    public void broadcastMessage(String message, NamedTextColor color) {
        Bukkit.broadcast(prefixer.apply(Component.text(message, color)));
    }

    public void broadcastComponent(Component message) {
        Bukkit.broadcast(prefixer.apply(message));
    }

    public Component getPrefixedString(String message, NamedTextColor color) {
        return prefixer.apply(Component.text(message, color));
    }

    private String formatClock(long totalSeconds) {
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
