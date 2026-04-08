package com.hairo.bingomc.goals.core;

import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public interface PlayerGoal {
    String id();

    Material icon();

    Set<GoalTrigger> triggers();

    boolean isComplete(Player player);

    default String descriptionText() {
        return descriptionText(false);
    }

    String descriptionText(boolean shortFormat);
}
