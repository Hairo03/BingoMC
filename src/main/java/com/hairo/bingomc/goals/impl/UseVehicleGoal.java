package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;

public class UseVehicleGoal implements PlayerGoal {
    private final String id;
    private final EntityType vehicleType;

    public UseVehicleGoal(String id, Class<? extends Entity> vehicle) {
        this(id, resolveEntityType(vehicle));
    }

    public UseVehicleGoal(String id, EntityType vehicleType) {
        this.id = id;
        this.vehicleType = vehicleType;
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
        Entity vehicle = player.getVehicle();
        return vehicle != null && vehicle.getType() == vehicleType;
    }

    @Override
    public String completionText() {
        return "Goal complete: Used a " + vehicleType.name().toLowerCase();
    }

    private static EntityType resolveEntityType(Class<? extends Entity> vehicleClass) {
        for (EntityType entityType : EntityType.values()) {
            Class<?> entityClass = entityType.getEntityClass();
            if (entityClass != null && vehicleClass.isAssignableFrom(entityClass)) {
                return entityType;
            }
        }
        throw new IllegalArgumentException("Cannot resolve entity type for class: " + vehicleClass.getName());
    }
    
}
