package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;

public class ObtainItemGoal implements PlayerGoal {
    private final String id;
    private final String itemName;
    private final int amount;

    public ObtainItemGoal(String id, String itemName, int amount) {
        this.id = id;
        this.itemName = itemName;
        this.amount = amount;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isComplete(Player player) {
        return player.getInventory().contains(org.bukkit.Material.matchMaterial(itemName), amount);
    }

    @Override
    public String descriptionText() {
        return "Obtain " + amount + " " + itemName.toLowerCase().replace('_', ' ');
    }

	@Override
	public Set<GoalTrigger> triggers() {
		return EnumSet.of(GoalTrigger.INVENTORY, GoalTrigger.PERIODIC);
	}
    
}
