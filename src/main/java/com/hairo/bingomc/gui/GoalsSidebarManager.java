package com.hairo.bingomc.gui;

import com.hairo.bingomc.goals.core.AmountBasedGoal;
import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.goals.core.PlayerGoal;
import me.catcoder.sidebar.ProtocolSidebar;
import me.catcoder.sidebar.Sidebar;
import me.catcoder.sidebar.text.TextIterators;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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

    private final Map<UUID, Sidebar<Component>> playerSidebars = new HashMap<>();
    private final Map<UUID, LinkedHashSet<String>> pinnedGoalIds = new HashMap<>();

    public GoalsSidebarManager(JavaPlugin plugin, GoalManager goalManager) {
        this.plugin = plugin;
        this.goalManager = goalManager;
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

    public void clearAll() {
        for (Map.Entry<UUID, Sidebar<Component>> entry : playerSidebars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                entry.getValue().removeViewer(player);
            }
        }
        playerSidebars.clear();
        pinnedGoalIds.clear();
    }

    private void buildSidebar(Player player) {
        Sidebar<Component> old = playerSidebars.get(player.getUniqueId());
        if (old != null) {
            old.removeViewer(player);
        }

        Sidebar<Component> sidebar = ProtocolSidebar.newAdventureSidebar(
                TextIterators.textFadeHypixel("BINGO"), plugin);

        sidebar.getObjective().scoreNumberFormatBlank();

        sidebar.addUpdatableLine(p ->
                Component.text("Points: ", NamedTextColor.GRAY)
                        .append(Component.text(goalManager.getPoints(p) + " pts",
                                NamedTextColor.GOLD, TextDecoration.BOLD)));

        sidebar.addBlankLine();

        List<PlayerGoal> allGoals = goalManager.getRegisteredGoals();
        Set<String> completedGoalIds = goalManager.getCompletedGoalIds(player.getUniqueId());
        LinkedHashSet<String> pins = pinnedGoalIds.computeIfAbsent(player.getUniqueId(), k -> new LinkedHashSet<>());
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

        if (pinnedShown > 0) {
            sidebar.addLine(Component.text("── Pinned ──", NamedTextColor.YELLOW, TextDecoration.BOLD));
            for (int i = 0; i < pinnedShown; i++) {
                final PlayerGoal goal = pinned.get(i);
                sidebar.addUpdatableLine(p -> formatGoalLine(p, goal, true));
            }
        }

        if (restShown > 0) {
            sidebar.addLine(Component.text("── Goals ──", NamedTextColor.WHITE));
            for (int i = 0; i < restShown; i++) {
                final PlayerGoal goal = rest.get(i);
                sidebar.addUpdatableLine(p -> formatGoalLine(p, goal, false));
            }
        }

        sidebar.updateLinesPeriodically(0, 20);
        sidebar.addViewer(player);

        playerSidebars.put(player.getUniqueId(), sidebar);
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
