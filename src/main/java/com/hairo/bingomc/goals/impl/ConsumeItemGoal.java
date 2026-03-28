package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;
import com.hairo.bingomc.goals.util.ConsumeTracker;

public class ConsumeItemGoal implements PlayerGoal {
    private final String id;
    private final Material item;
    private final int amount;
    private final ConsumeTracker consumeTracker;

    public ConsumeItemGoal(String id, Material item, int amount, ConsumeTracker consumeTracker) {
        this.id = id;
        this.item = item;
        this.amount = amount;
        this.consumeTracker = consumeTracker;
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
        List<Material> consumed = consumeTracker.getConsumedItems(player);
        return consumed.stream().filter(m -> m == item).count() >= amount;
    }

    @Override
    public String descriptionText() {
        return "Consume " + amount + " " + item.name().toLowerCase().replace('_', ' ');
    }
}
