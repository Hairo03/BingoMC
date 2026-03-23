package com.hairo.bingomc.goals.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public class GoalManager {
    private final List<PlayerGoal> goals = new ArrayList<>();
    private final Map<UUID, Set<String>> completedByPlayer = new HashMap<>();
    private final Map<UUID, Set<Material>> consumedItems = new HashMap<>();

    public void registerGoal(PlayerGoal goal) {
        goals.add(goal);
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
                player.sendMessage(Component.text(goal.completionText()));
            }
        }
    }
}
