package com.hairo.bingomc.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import com.hairo.bingomc.BingoMC;
import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.util.ConsumeTracker;

public class GoalEventListener implements Listener {
    private final BingoMC plugin;
    private final GoalManager goalManager;
    private final ConsumeTracker consumeTracker;

    public GoalEventListener(BingoMC plugin, GoalManager goalManager) {
        this.plugin = plugin;
        this.goalManager = goalManager;
        this.consumeTracker = new ConsumeTracker(plugin);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> goalManager.evaluate(player, GoalTrigger.INVENTORY));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> goalManager.evaluate(player, GoalTrigger.INVENTORY));
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> goalManager.evaluate(player, GoalTrigger.INVENTORY));
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> goalManager.evaluate(player, GoalTrigger.INVENTORY));
    }

    @EventHandler
    public void onMount(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> goalManager.evaluate(player, GoalTrigger.MOUNT));
        }
    }

    @EventHandler
    public void onCraft(org.bukkit.event.inventory.CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> goalManager.evaluate(player, GoalTrigger.ITEM_CRAFT));
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        consumeTracker.recordConsumedItem(player, event.getItem().getType());
        Bukkit.getScheduler().runTask(plugin, () -> goalManager.evaluate(player, GoalTrigger.CONSUME));
    }

    @EventHandler
    public void onFishCatch(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> goalManager.evaluate(player, GoalTrigger.FISHING));
        }
    }

    @EventHandler
    public void onEntityKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> goalManager.evaluate(player, GoalTrigger.KILL_ENTITY));
        }
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> goalManager.evaluate(player, GoalTrigger.ADVANCEMENT));
    }
}
