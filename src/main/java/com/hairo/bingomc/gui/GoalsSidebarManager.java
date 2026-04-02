package com.hairo.bingomc.gui;

import com.hairo.bingomc.goals.core.AmountBasedGoal;
import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.goals.core.PlayerGoal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout;
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent;
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.CollectionSidebarAnimation;
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.SidebarAnimation;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GoalsSidebarManager {

    private static final int MAX_PINNED = 5;
    private static final int MAX_TOTAL_GOALS = 10;

    private final JavaPlugin plugin;
    private final GoalManager goalManager;
    private final ScoreboardLibrary scoreboardLibrary;

    private final Map<UUID, Sidebar> playerSidebars = new HashMap<>();
    private final Map<UUID, BukkitTask> playerTasks = new HashMap<>();
    private final Map<UUID, LinkedHashSet<String>> pinnedGoalIds = new HashMap<>();

    public GoalsSidebarManager(JavaPlugin plugin, GoalManager goalManager) {
        this.plugin = plugin;
        this.goalManager = goalManager;
        ScoreboardLibrary lib;
        try {
            lib = ScoreboardLibrary.loadScoreboardLibrary(plugin);
        } catch (net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException e) {
            lib = new net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary();
            plugin.getLogger().warning("scoreboard-library: no packet adapter available, sidebar will not be visible!");
        }
        this.scoreboardLibrary = lib;
    }

    public void onPlayerRoundStart(Player player) {
        pinnedGoalIds.remove(player.getUniqueId());
        buildSidebar(player);
    }

    public void togglePin(Player player, String goalId) {
        if (goalManager.getCompletedGoalIds(player.getUniqueId()).contains(goalId)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        LinkedHashSet<String> pins = pinnedGoalIds.computeIfAbsent(uuid, k -> new LinkedHashSet<>());
        if (pins.contains(goalId)) {
            pins.remove(goalId);
        } else {
            pins.add(goalId);
        }
        buildSidebar(player);
    }

    public void onGoalCompleted(Player player) {
        if (playerSidebars.containsKey(player.getUniqueId())) {
            buildSidebar(player);
        }
    }

    public boolean isPinned(UUID playerId, String goalId) {
        Set<String> pins = pinnedGoalIds.get(playerId);
        return pins != null && pins.contains(goalId);
    }

    public void close() {
        clearAll();
        scoreboardLibrary.close();
    }

    public void clearAll() {
        for (Map.Entry<UUID, Sidebar> entry : playerSidebars.entrySet()) {
            BukkitTask task = playerTasks.remove(entry.getKey());
            if (task != null) task.cancel();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                entry.getValue().removePlayer(player);
            }
            entry.getValue().close();
        }
        playerSidebars.clear();
        pinnedGoalIds.clear();
    }

    private void buildSidebar(Player player) {
        UUID uuid = player.getUniqueId();

        Sidebar old = playerSidebars.get(uuid);
        if (old != null) {
            BukkitTask oldTask = playerTasks.remove(uuid);
            if (oldTask != null) oldTask.cancel();
            old.removePlayer(player);
            old.close();
        }

        // Build title animation (yellow → gold gradient cycling)
        List<Component> titleFrames = new ArrayList<>();
        float phase = -1f;
        while (phase < 1f) {
            titleFrames.add(MiniMessage.miniMessage().deserialize(
                    "<b><gradient:yellow:gold:" + phase + ">BINGO</gradient></b>"));
            phase += 1f / 8f;
        }
        SidebarAnimation<Component> titleAnimation = new CollectionSidebarAnimation<>(titleFrames);
        SidebarComponent title = SidebarComponent.animatedLine(titleAnimation);

        // Classify goals
        List<PlayerGoal> allGoals = goalManager.getRegisteredGoals();
        Set<String> completedGoalIds = goalManager.getCompletedGoalIds(uuid);
        LinkedHashSet<String> pins = pinnedGoalIds.computeIfAbsent(uuid, k -> new LinkedHashSet<>());
        pins.removeIf(completedGoalIds::contains);

        List<PlayerGoal> pinned = new ArrayList<>();
        List<PlayerGoal> rest = new ArrayList<>();
        for (PlayerGoal goal : allGoals) {
            if (completedGoalIds.contains(goal.id())) continue;
            if (pins.contains(goal.id())) {
                pinned.add(goal);
            } else {
                rest.add(goal);
            }
        }

        int pinnedShown = Math.min(pinned.size(), MAX_PINNED);
        int restShown = Math.min(rest.size(), MAX_TOTAL_GOALS - pinnedShown);

        // Build lines component
        SidebarComponent.Builder linesBuilder = SidebarComponent.builder()
                .addDynamicLine(() -> Component.text("Points: ", NamedTextColor.GRAY)
                        .append(Component.text(goalManager.getPoints(player) + " pts",
                                NamedTextColor.GOLD, TextDecoration.BOLD)))
                .addBlankLine();

        if (pinnedShown > 0) {
            linesBuilder.addStaticLine(Component.text("── Pinned ──", NamedTextColor.YELLOW, TextDecoration.BOLD));
            for (int i = 0; i < pinnedShown; i++) {
                final PlayerGoal goal = pinned.get(i);
                linesBuilder.addDynamicLine(() -> formatGoalLine(player, goal, true));
            }
        }

        if (restShown > 0) {
            linesBuilder.addStaticLine(Component.text("── Goals ──", NamedTextColor.WHITE));
            for (int i = 0; i < restShown; i++) {
                final PlayerGoal goal = rest.get(i);
                linesBuilder.addDynamicLine(() -> formatGoalLine(player, goal, false));
            }
        }

        ComponentSidebarLayout layout = new ComponentSidebarLayout(title, linesBuilder.build());

        Sidebar sidebar = scoreboardLibrary.createSidebar();
        layout.apply(sidebar);
        sidebar.addPlayer(player);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            titleAnimation.nextFrame();
            layout.apply(sidebar);
        }, 0L, 20L);

        playerSidebars.put(uuid, sidebar);
        playerTasks.put(uuid, task);
    }

    private Component formatGoalLine(Player player, PlayerGoal goal, boolean pinned) {
        String label = abbreviate(sidebarLabel(goal));
        String progress = "";
        if (goal instanceof AmountBasedGoal amountGoal) {
            int current = amountGoal.currentProgress(player);
            progress = " " + current + "/" + amountGoal.amount();
        }

        if (pinned) {
            return Component.text("\uD83D\uDCCC ", NamedTextColor.GOLD)
                    .append(Component.text(label + progress, NamedTextColor.YELLOW));
        }
        return Component.text(label + progress, NamedTextColor.WHITE);
    }

    private static String sidebarLabel(PlayerGoal goal) {
        return goal.descriptionText(true);
    }

    private static String abbreviate(String text) {
        return text.length() > 22 ? text.substring(0, 20) + "\u2026" : text;
    }
}
