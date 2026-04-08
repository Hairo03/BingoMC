package com.hairo.bingomc.goals.core;

import org.bukkit.entity.Player;

public interface AmountBasedGoal {
    int amount();

    int currentProgress(Player player);
}
