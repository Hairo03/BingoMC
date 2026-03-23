package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.Set;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;

public class ConsumeItemGoal implements PlayerGoal {
    private final String id;
    private final Material item;
    private final int amount;

    public ConsumeItemGoal(String id, Material item, int amount) {
        this.id = id;
        this.item = item;
        this.amount = amount;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<GoalTrigger> triggers() {
        return EnumSet.of(GoalTrigger.CONSUME);
    }

    @Override
    public boolean isComplete(Player player) {
        List<Material> consumed = player.getMetadata("consumed_items")
            .stream()
            .map(m -> (Material) m.value())
            .toList();
        return consumed.stream().filter(m -> m == item).count() >= amount;
    }

    @Override
    public String completionText() {
        return "Goal complete: Consume " + amount + " " + item.name().toLowerCase();
    }
}
