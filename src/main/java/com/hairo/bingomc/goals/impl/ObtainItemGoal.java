package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.hairo.bingomc.goals.core.AmountBasedGoal;
import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;

public class ObtainItemGoal implements PlayerGoal, AmountBasedGoal {
    private final String id;
    private final Material item;
    private final int amount;
    private final Material icon;

    public ObtainItemGoal(String id, Material item, int amount, Material icon) {
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
    public boolean isComplete(Player player) {
        return player.getInventory().contains(item, amount);
    }

    @Override
    public int currentProgress(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == item) {
                count += stack.getAmount();
            }
        }
        return Math.min(count, amount);
    }

    @Override
    public String descriptionText(boolean shortFormat) {
        if (shortFormat) {
            return "Obtain " + item.name().toLowerCase().replace('_', ' ');
        }

        return "Obtain " + amount + " " + item.name().toLowerCase().replace('_', ' ');
    }

	@Override
	public Set<GoalTrigger> triggers() {
		return EnumSet.of(GoalTrigger.INVENTORY, GoalTrigger.PERIODIC);
	}
    
}
