package com.hairo.bingomc.goals.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class GoalManager {
    private final List<PlayerGoal> goals = new ArrayList<>();
    private final Map<String, Integer> pointsByGoalId = new HashMap<>();
    private final Map<UUID, Set<String>> completedByPlayer = new HashMap<>();
    private Consumer<Player> completionCallback;
    private final Map<UUID, Map<String, Integer>> lastKnownProgress = new HashMap<>();

    public void setCompletionCallback(Consumer<Player> callback) {
        this.completionCallback = callback;
    }

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

    public void onItemConsumed(Player player, Material material) {
        for (PlayerGoal goal : goals) {
            if (goal instanceof ConsumeAwareGoal consumeAwareGoal) {
                consumeAwareGoal.onItemConsumed(player, material);
            }
        }
    }

    public void evaluate(Player player, GoalTrigger trigger) {
        Set<String> completed = completedByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
        boolean anyCompleted = false;
        List<Component> progressComponents = new ArrayList<>();

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
                anyCompleted = true;
                int points = pointsByGoalId.getOrDefault(id, 1);
                Bukkit.broadcast(
                        Component.text()
                                .append(Component.text("[Bingo] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                                .append(Component.text(player.getName() + " completed goal: ", NamedTextColor.GREEN))
                                .append(Component.text(goal.descriptionText(), NamedTextColor.WHITE,
                                        TextDecoration.BOLD))
                                .append(Component.text(" (", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                                .append(Component.text("+" + points + " pts", NamedTextColor.AQUA, TextDecoration.BOLD))
                                .append(Component.text(")", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                                .build());
                player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            } else if (trigger != GoalTrigger.PERIODIC && goal instanceof AmountBasedGoal amountGoal) {
                int current = amountGoal.currentProgress(player);
                Map<String, Integer> playerProgress = lastKnownProgress
                        .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
                int last = playerProgress.getOrDefault(id, 0);
                playerProgress.put(id, current);
                if (current > last) {
                    progressComponents.add(
                            Component.text(goal.descriptionText() + "  ", NamedTextColor.YELLOW)
                                    .append(Component.text(current + "/" + amountGoal.amount(),
                                            NamedTextColor.WHITE, TextDecoration.BOLD)));
                }
            }
        }

        if (!progressComponents.isEmpty()) {
            Component bar = progressComponents.get(0);
            for (int i = 1; i < progressComponents.size(); i++) {
                bar = bar.append(Component.text("  |  ", NamedTextColor.GRAY))
                        .append(progressComponents.get(i));
            }
            player.sendActionBar(bar);
        }

        if (anyCompleted && completionCallback != null) {
            completionCallback.accept(player);
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
        List<RoundAwareGoal> applied = new ArrayList<>();
        try {
            for (PlayerGoal goal : goals) {
                if (goal instanceof RoundAwareGoal roundAwareGoal) {
                    roundAwareGoal.onRoundStart(player);
                    applied.add(roundAwareGoal);
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().severe("Failed to initialize goals for player "
                    + (player != null ? player.getName() : "unknown") + ": " + e.getMessage());
            completedByPlayer.remove(player.getUniqueId());
            for (RoundAwareGoal goal : applied) {
                goal.onRoundReset();
            }
            throw e;
        }
    }

    public void resetAllProgress() {
        completedByPlayer.clear();
        lastKnownProgress.clear();
        for (PlayerGoal goal : goals) {
            if (goal instanceof RoundAwareGoal roundAwareGoal) {
                roundAwareGoal.onRoundReset();
            }
        }
    }
}
