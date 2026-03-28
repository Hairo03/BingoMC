package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;

public class BlockCountGoal implements PlayerGoal {
    private final String id;
    private final Material block;
    private final int amount;

    public BlockCountGoal(String id, Material block, int amount) {
        this.id = id;
        this.block = block;
        this.amount = amount;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<GoalTrigger> triggers() {
        return EnumSet.of(GoalTrigger.INVENTORY, GoalTrigger.PERIODIC);
    }

    @Override
    public boolean isComplete(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == block) {
                count += stack.getAmount();
            }
        }
        return count >= amount;
    }

    @Override
    public String descriptionText() {
        return "Collect " + amount + " " + block.name().toLowerCase().replace('_', ' ');
    }
}
