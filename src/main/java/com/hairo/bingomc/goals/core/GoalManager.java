package com.hairo.bingomc.goals.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class GoalManager {
    private final List<PlayerGoal> goals = new ArrayList<>();
    private final Map<String, Integer> pointsByGoalId = new HashMap<>();
    private final Map<UUID, Set<String>> completedByPlayer = new HashMap<>();
    private final Map<UUID, Set<Material>> consumedItems = new HashMap<>();

    public void registerGoal(PlayerGoal goal) {
        registerGoal(goal, 1);
    }

    public void registerGoal(PlayerGoal goal, int points) {
        goals.add(goal);
        pointsByGoalId.put(goal.id(), Math.max(1, points));
    }

    public void clearRegisteredGoals() {
        goals.clear();
        pointsByGoalId.clear();
    }

    public List<PlayerGoal> getRegisteredGoals() {
        return Collections.unmodifiableList(goals);
    }

    public Set<String> getCompletedGoalIds(UUID playerId) {
        return Set.copyOf(completedByPlayer.getOrDefault(playerId, Set.of()));
    }

    public int getGoalPoints(String goalId) {
        return pointsByGoalId.getOrDefault(goalId, 1);
    }

    public void registerConsumedItem(Player player, Material item) {
        consumedItems.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(item);
    }

    public Set<Material> getConsumedItems(Player player) {
        return consumedItems.getOrDefault(player.getUniqueId(), Set.of());
    }

    public void clearConsumedItems(Player player) {
        consumedItems.remove(player.getUniqueId());
    }

    public void evaluate(Player player, GoalTrigger trigger) {
        Set<String> completed = completedByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());

        for (PlayerGoal goal : goals) {
            if (!goal.triggers().contains(trigger)) {
                continue;
            }

            String id = goal.id();
            if (completed.contains(id)) {
                continue;
            }

            if (goal.isComplete(player)) {
                completed.add(id);
                int points = pointsByGoalId.getOrDefault(id, 1);
                Bukkit.broadcast(
                    Component.text()
                        .append(Component.text("[Bingo] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(Component.text(player.getName() + " completed goal: ", NamedTextColor.GREEN))
                        .append(Component.text(goal.descriptionText(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(" (", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                        .append(Component.text("+" + points + " pts", NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(")", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                        .build()
                );
            }
        }
    }

    public int getPoints(Player player) {
        return getPoints(player.getUniqueId());
    }

    public int getPoints(UUID playerId) {
        int points = 0;
        for (String goalId : completedByPlayer.getOrDefault(playerId, Set.of())) {
            points += pointsByGoalId.getOrDefault(goalId, 1);
        }
        return points;
    }

    public void onRoundStart(Player player) {
        for (PlayerGoal goal : goals) {
            if (goal instanceof RoundAwareGoal roundAwareGoal) {
                roundAwareGoal.onRoundStart(player);
            }
        }
    }

    public void resetAllProgress() {
        completedByPlayer.clear();
        consumedItems.clear();
        for (PlayerGoal goal : goals) {
            if (goal instanceof RoundAwareGoal roundAwareGoal) {
                roundAwareGoal.onRoundReset();
            }
        }
    }
}
