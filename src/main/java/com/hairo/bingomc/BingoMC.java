package com.hairo.bingomc;

import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.impl.BlockCountGoal;
import com.hairo.bingomc.goals.impl.ConsumeItemGoal;
import com.hairo.bingomc.goals.impl.ItemCraftGoal;
import com.hairo.bingomc.goals.impl.UseVehicleGoal;
import com.hairo.bingomc.listeners.GoalEventListener;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import com.infernalsuite.asp.loaders.file.FileLoader;
import net.kyori.adventure.text.Component;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BingoMC extends JavaPlugin implements Listener {

    private SlimeLoader loader;
    private final AdvancedSlimePaperAPI asp = AdvancedSlimePaperAPI.instance();
    private final GoalManager goalManager = new GoalManager();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new GoalEventListener(this, goalManager), this);

        getLogger().info("BingoMC has been enabled!");

        loader = new FileLoader(new File("./slime_worlds"));

        goalManager.registerGoal(new BlockCountGoal("collect_dirt_16", Material.DIRT, 16));
        goalManager.registerGoal(new ItemCraftGoal("craft_stick", Material.STICK, 1));
        goalManager.registerGoal(new UseVehicleGoal("mount_horse", Horse.class));
        goalManager.registerGoal(new ConsumeItemGoal("eat_apple", Material.APPLE, 1));

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                goalManager.evaluate(player, GoalTrigger.PERIODIC);
            }
        }, 40L, 40L);
    }

    @Override
    public void onDisable() {
        getLogger().info("BingoMC has been disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(Component.text("Hello " + player.getName() + ", welcome to BingoMC!"));
        getLogger().info(player.getName() + " joined the server");
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command."));
            return true;
        }
        try {
            World world = createAndLoadWorld();
            player.teleportAsync(world.getSpawnLocation());
            sender.sendActionBar(Component.text("Success! Loading new world..."));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error creating/loading world: " + e.getMessage()));
            e.printStackTrace();
        }
        return true;
    }

    public World createAndLoadWorld() throws Exception {
        SlimePropertyMap props = new SlimePropertyMap();

        props.setValue(SlimeProperties.DEFAULT_BIOME, "minecraft:plains");
        props.setValue(SlimeProperties.WORLD_TYPE, "DEFAULT");

        SlimeWorld world = asp.readVanillaWorld(new File("./slime_worlds/worldToBeCloned"), "worldToBeCloned", loader);
        SlimeWorld clonedWorld = world.clone("cloned_world");

        SlimeWorldInstance instance = asp.loadWorld(clonedWorld, false);
        return instance.getBukkitWorld();
    }
}