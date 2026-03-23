package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;

public class UseVehicleGoal implements PlayerGoal {
    private final String id;
    private final Class<? extends Entity> vehicle;

    public UseVehicleGoal(String id, Class<? extends Entity> vehicle) {
        this.id = id;
        this.vehicle = vehicle;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<GoalTrigger> triggers() {
        return EnumSet.of(GoalTrigger.MOUNT);
    }

    @Override
    public boolean isComplete(Player player) {
        return player.isInsideVehicle();
    }

    @Override
    public String completionText() {
        return "Goal complete: Used a " + vehicle.getSimpleName().toLowerCase();
    }
    
}
