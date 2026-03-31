package com.hairo.bingomc.goals.core;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public interface ConsumeAwareGoal {
    void onItemConsumed(Player player, Material material);
}
