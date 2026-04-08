package com.hairo.bingomc.goals.impl;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.hairo.bingomc.goals.core.AmountBasedGoal;
import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;

public class ObtainItemTypeGoal implements PlayerGoal, AmountBasedGoal {
    private final String id;
    private final Tag<Material> tag;
    private final NamespacedKey tagKey;
    private final int amount;
    private final Material icon;

    public ObtainItemTypeGoal(String id, Tag<Material> tag, NamespacedKey tagKey, int amount, Material icon) {
        this.id = id;
        this.tag = tag;
        this.tagKey = tagKey;
        this.amount = amount;
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
    public int amount() {
        return amount;
    }

    @Override
    public Set<GoalTrigger> triggers() {
        return EnumSet.of(GoalTrigger.INVENTORY, GoalTrigger.PERIODIC);
    }

    @Override
    public boolean isComplete(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                if (tag.isTagged(item.getType())) {
                    count += item.getAmount();
                    if (count >= amount) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public int currentProgress(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() != Material.AIR && tag.isTagged(stack.getType())) {
                count += stack.getAmount();
            }
        }
        return Math.min(count, amount);
    }

    @Override
    public String descriptionText(boolean shortFormat) {
        if (shortFormat) {
            return "Obtain any " + tagKey.getKey().replace('_', ' ');
        }

        String name = tagKey.getKey().replace('_', ' ');
        return "Obtain " + amount + " of any " + name;
    }
}
