package com.hairo.bingomc.goals.core;

import java.util.Set;
import org.bukkit.entity.Player;

public interface PlayerGoal {
    String id();

    Set<GoalTrigger> triggers();

    boolean isComplete(Player player);

    String descriptionText();
}
