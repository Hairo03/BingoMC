package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;

public class ReachYLevelGoal implements PlayerGoal {
    public enum Direction {
        UP, DOWN
    }

    private final String id;
    private final int yLevel;
    private final Direction direction;
    private final Material icon;

    public ReachYLevelGoal(String id, int yLevel, Direction direction, Material icon) {
        this.id = id;
        this.yLevel = yLevel;
        this.direction = direction;
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
        return EnumSet.of(GoalTrigger.PERIODIC);
    }

    @Override
    public boolean isComplete(Player player) {
        int currentY = player.getLocation().getBlockY();
        if (direction == Direction.UP) {
            return currentY >= yLevel;
        } else {
            return currentY <= yLevel;
        }
    }

    @Override
    public String descriptionText() {
        if (direction == Direction.UP) {
            return "Reach Y-level " + yLevel + " or higher";
        } else {
            return "Reach Y-level " + yLevel + " or lower";
        }
    }
}
