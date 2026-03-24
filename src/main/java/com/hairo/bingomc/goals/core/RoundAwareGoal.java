package com.hairo.bingomc.goals.core;

import org.bukkit.entity.Player;

public interface RoundAwareGoal {
    void onRoundStart(Player player);

    default void onRoundReset() {
    }
}
