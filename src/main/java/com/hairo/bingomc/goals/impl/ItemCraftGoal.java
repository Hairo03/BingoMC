package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;

public class ItemCraftGoal implements PlayerGoal {
    private final String id;
    private final Material item;
    private final int amount;

    public ItemCraftGoal(String id, Material item, int amount) {
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
        return EnumSet.of(GoalTrigger.ITEM_CRAFT);
    }

    @Override
    public boolean isComplete(Player player) {
        return player.getStatistic(Statistic.CRAFT_ITEM, item) >= amount;
    }

    @Override
    public String completionText() {
        return "Goal complete: Crafted " + amount + " " + item.name().toLowerCase();
    }
    
}
