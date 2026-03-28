package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;
import com.hairo.bingomc.goals.core.RoundAwareGoal;

public class KillEntityGoal implements PlayerGoal, RoundAwareGoal {
    private final String id;
    private final EntityType entityType;
    private final int amount;
    private final Map<UUID, Integer> roundBaselineKillCount = new HashMap<>();

    public KillEntityGoal(String id, EntityType entityType, int amount) {
        this.id = id;
        this.entityType = entityType;
        this.amount = amount;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<GoalTrigger> triggers() {
        return EnumSet.of(GoalTrigger.KILL_ENTITY);
    }

    @Override
    public boolean isComplete(Player player) {
        int currentCount = player.getStatistic(Statistic.KILL_ENTITY, entityType);
        int baseline = roundBaselineKillCount.getOrDefault(player.getUniqueId(), currentCount);
        return currentCount - baseline >= amount;
    }

    @Override
    public String descriptionText() {
        return "Kill " + amount + " " + entityType.name().toLowerCase().replace('_', ' ');
    }

    @Override
    public void onRoundStart(Player player) {
        roundBaselineKillCount.put(player.getUniqueId(), player.getStatistic(Statistic.KILL_ENTITY, entityType));
    }

    @Override
    public void onRoundReset() {
        roundBaselineKillCount.clear();
    }

}
