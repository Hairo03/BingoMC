package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;
import com.hairo.bingomc.goals.core.RoundAwareGoal;

public class ItemCraftGoal implements PlayerGoal, RoundAwareGoal {
    private final String id;
    private final Material item;
    private final int amount;
    private final Map<UUID, Integer> roundBaselineCraftCount = new HashMap<>();

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
        int currentCount = player.getStatistic(Statistic.CRAFT_ITEM, item);
        int baseline = roundBaselineCraftCount.getOrDefault(player.getUniqueId(), currentCount);
        return currentCount - baseline >= amount;
    }

    @Override
    public String completionText() {
        return "Goal complete: Crafted " + amount + " " + item.name().toLowerCase();
    }

    @Override
    public void onRoundStart(Player player) {
        roundBaselineCraftCount.put(player.getUniqueId(), player.getStatistic(Statistic.CRAFT_ITEM, item));
    }

    @Override
    public void onRoundReset() {
        roundBaselineCraftCount.clear();
    }
    
}
