package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;

public class ChangeDimensionGoal implements PlayerGoal {
    private final String id;
    private final Environment targetDimension;
    private final Material icon;

    public ChangeDimensionGoal(String id, Environment targetDimension, Material icon) {
		this.id = id;
		this.targetDimension = targetDimension;
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
	public Set<GoalTrigger> triggers() {
		return EnumSet.of(GoalTrigger.CHANGE_DIMENSION);
	}

	@Override
	public boolean isComplete(Player player) {
        return player.getWorld().getEnvironment() == targetDimension;
	}

	@Override
	public String descriptionText(boolean shortFormat) {
		if (shortFormat) {
			return "Enter " + targetDimension.name().toLowerCase();
		}

        return "Enter the " + targetDimension.name().toLowerCase();
	}
}
