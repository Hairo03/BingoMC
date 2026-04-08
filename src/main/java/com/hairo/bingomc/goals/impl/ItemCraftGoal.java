package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.AmountBasedGoal;
import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;
import com.hairo.bingomc.goals.core.RoundAwareGoal;

public class ItemCraftGoal implements PlayerGoal, RoundAwareGoal, AmountBasedGoal {
    private final String id;
    private final Material item;
    private final int amount;
    private final Map<UUID, Integer> roundBaselineCraftCount = new HashMap<>();
    private final Material icon;

    public ItemCraftGoal(String id, Material item, int amount, Material icon) {
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
        return EnumSet.of(GoalTrigger.ITEM_CRAFT);
    }

    @Override
    public boolean isComplete(Player player) {
        int currentCount = player.getStatistic(Statistic.CRAFT_ITEM, item);
        int baseline = roundBaselineCraftCount.getOrDefault(player.getUniqueId(), currentCount);
        return currentCount - baseline >= amount;
    }

    @Override
    public String descriptionText(boolean shortFormat) {
        if (shortFormat) {
            return "Craft " + item.name().toLowerCase().replace('_', ' ');
        }

        return "Craft " + amount + " " + item.name().toLowerCase().replace('_', ' ');
    }

    @Override
    public void onRoundStart(Player player) {
        roundBaselineCraftCount.put(player.getUniqueId(), player.getStatistic(Statistic.CRAFT_ITEM, item));
    }

    @Override
    public int currentProgress(Player player) {
        int current = player.getStatistic(Statistic.CRAFT_ITEM, item);
        int baseline = roundBaselineCraftCount.getOrDefault(player.getUniqueId(), current);
        return Math.max(0, Math.min(current - baseline, amount));
    }

    @Override
    public void onRoundReset() {
        roundBaselineCraftCount.clear();
    }
    
}
