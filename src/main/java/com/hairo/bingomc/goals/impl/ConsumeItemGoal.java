package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.AmountBasedGoal;
import com.hairo.bingomc.goals.core.ConsumeAwareGoal;
import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;
import com.hairo.bingomc.goals.core.RoundAwareGoal;

public class ConsumeItemGoal implements PlayerGoal, AmountBasedGoal, ConsumeAwareGoal, RoundAwareGoal {
    private final String id;
    private final Material item;
    private final int amount;
    private final Material icon;

    private final Map<UUID, Integer> consumeCount = new HashMap<>();

    public ConsumeItemGoal(String id, Material item, int amount, Material icon) {
        this.id = id;
        this.item = item;
        this.amount = amount;
        this.icon = icon;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public int amount() {
        return amount;
    }

    @Override
    public Set<GoalTrigger> triggers() {
        return EnumSet.of(GoalTrigger.CONSUME);
    }

    @Override
    public void onItemConsumed(Player player, Material material) {
        if (material == item) {
            UUID playerId = player.getUniqueId();
            consumeCount.put(playerId, consumeCount.getOrDefault(playerId, 0) + 1);
        }
    }

    @Override
    public boolean isComplete(Player player) {
        return consumeCount.getOrDefault(player.getUniqueId(), 0) >= amount;
    }

    @Override
    public void onRoundReset() {
        consumeCount.clear();
    }

    @Override
    public void onRoundStart(Player player) {
        consumeCount.put(player.getUniqueId(), 0);
    }

    @Override
    public String descriptionText() {
        return "Consume " + amount + " " + item.name().toLowerCase().replace('_', ' ');
    }
}
