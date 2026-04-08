package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.GeneratedStructure;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;

public class EnterStructureGoal implements PlayerGoal {
    private final String id;
    private final NamespacedKey targetStructureKey;
    private final Material icon;

    public EnterStructureGoal(String id, NamespacedKey targetStructureKey, Material icon) {
        this.id = id;
        this.targetStructureKey = targetStructureKey;
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
        Location loc = player.getLocation();
        for (GeneratedStructure gs : loc.getChunk().getStructures()) {
            NamespacedKey key = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.STRUCTURE)
                    .getKey(gs.getStructure());

            if (targetStructureKey.equals(key)) {
                if (gs.getBoundingBox().contains(loc.toVector())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String descriptionText(boolean shortFormat) {
        if (shortFormat) {
            return "Enter " + targetStructureKey.getKey().replace('_', ' ');
        }

        String keyName = targetStructureKey.getKey().replace('_', ' ');
        return "Find and enter a " + keyName;
    }
}
