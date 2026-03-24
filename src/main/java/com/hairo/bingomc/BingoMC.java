package com.hairo.bingomc;

import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.impl.BlockCountGoal;
import com.hairo.bingomc.goals.impl.ConsumeItemGoal;
import com.hairo.bingomc.goals.impl.ItemCraftGoal;
import com.hairo.bingomc.goals.impl.UseVehicleGoal;
import com.hairo.bingomc.goals.util.ConsumeTracker;
import com.hairo.bingomc.goals.util.Timer;
import com.hairo.bingomc.listeners.GoalEventListener;
import com.hairo.bingomc.events.TimerExpiredEvent;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.SlimeWorldInstance;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import com.infernalsuite.asp.loaders.file.FileLoader;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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
    private ConsumeTracker consumeTracker;
    private Timer timer;
    private boolean gameRunning;
    private boolean timerExpiredHandled;
    private BossBar timerBossBar;
    private final Set<UUID> roundParticipants = new HashSet<>();
    private String mainWorldName;

    private static final long GAME_DURATION_SECONDS = 300L;
    private static final String TEMPLATE_WORLD_NAME = "worldToBeCloned";

    @Override
    public void onEnable() {
        consumeTracker = new ConsumeTracker(this);

        timer = new Timer();
        timer.setLimitSeconds(GAME_DURATION_SECONDS);
        timer.reset();
        gameRunning = false;
        timerExpiredHandled = false;
        timerBossBar = BossBar.bossBar(
            Component.text("Time left: 00:00"),
            0.0f,
            BossBar.Color.BLUE,
            BossBar.Overlay.PROGRESS
        );

        mainWorldName = Bukkit.getWorlds().get(0).getName();
        loader = new FileLoader(new File("./slime_worlds/"));

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new GoalEventListener(this, goalManager, consumeTracker), this);
        
        getLogger().info("BingoMC has been enabled!");
        
        goalManager.registerGoal(new BlockCountGoal("collect_dirt_16", Material.DIRT, 16));
        goalManager.registerGoal(new ItemCraftGoal("craft_stick", Material.STICK, 1));
        goalManager.registerGoal(new UseVehicleGoal("mount_horse", Horse.class));
        goalManager.registerGoal(new ConsumeItemGoal("eat_apple", Material.APPLE, 1, consumeTracker));

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!gameRunning) {
                return;
            }

            if (timer.isRunning() && timer.isExpired() && !timerExpiredHandled) {
                timer.stop();
                timerExpiredHandled = true;
                Bukkit.getPluginManager().callEvent(new TimerExpiredEvent(timer.getElapsedMillis()));
                return;
            }

            updateTimerDisplay();

            for (Player player : Bukkit.getOnlinePlayers()) {
                goalManager.evaluate(player, GoalTrigger.PERIODIC);
            }
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (timerBossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(timerBossBar);
            }
        }
        getLogger().info("BingoMC has been disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(Component.text("Hello " + player.getName() + ", welcome to BingoMC!"));
        getLogger().info(player.getName() + " joined the server");

        if (timerBossBar != null && gameRunning) {
            consumeTracker.clearConsumedItems(player);
            goalManager.onRoundStart(player);
            player.showBossBar(timerBossBar);
            roundParticipants.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onTimerExpired(TimerExpiredEvent event) {
        gameRunning = false;

        World mainWorld = Bukkit.getWorld(mainWorldName);
        if (mainWorld != null) {
            for (UUID playerId : roundParticipants) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.teleportAsync(mainWorld.getSpawnLocation());
                }
            }
        }

        String seconds = String.valueOf(event.getElapsedSeconds());
        Bukkit.broadcast(Component.text("Time limit reached after " + seconds + " seconds."));
        getLogger().info("Timer expired after " + seconds + " seconds.");

        List<UUID> ranking = new ArrayList<>(roundParticipants);
        ranking.sort(Comparator.comparingInt((UUID playerId) -> goalManager.getPoints(playerId)).reversed());

        Bukkit.broadcast(Component.text("Final scores:"));
        if (ranking.isEmpty()) {
            Bukkit.broadcast(Component.text("No participants in this round."));
        } else {
            int rank = 1;
            for (UUID playerId : ranking) {
                String name = Bukkit.getOfflinePlayer(playerId).getName();
                if (name == null) {
                    name = playerId.toString();
                }
                int points = goalManager.getPoints(playerId);
                Bukkit.broadcast(Component.text(rank + ". " + name + " - " + points + " pts"));
                rank++;
            }
        }

        if (timerBossBar != null) {
            timerBossBar.progress(0.0f);
            timerBossBar.name(Component.text("Time left: 00:00"));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(timerBossBar);
            }
        }

        roundParticipants.clear();
    }

    private void updateTimerDisplay() {
        if (!timer.hasLimit()) {
            return;
        }

        long remainingSeconds = timer.getRemainingSeconds();
        String display = formatClock(remainingSeconds);

        if (timerBossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showBossBar(timerBossBar);
            }

            float progress = (float) ((double) timer.getRemainingMillis() / (double) timer.getLimitMillis());
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            timerBossBar.progress(progress);
            timerBossBar.name(Component.text("Time left: " + display));
        }
    }

    private String formatClock(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /bingo start"));
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            if (sender instanceof Player player && !player.isOp()) {
                sender.sendMessage(Component.text("Only operators can start a Bingo round."));
                return true;
            }
            if (gameRunning) {
                sender.sendMessage(Component.text("A Bingo round is already running."));
                return true;
            }
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                sender.sendMessage(Component.text("Cannot start: no players are online."));
                return true;
            }
            boolean started = startGame();
            if (started) {
                sender.sendMessage(Component.text("Bingo round started."));
            } else {
                sender.sendMessage(Component.text("Could not start Bingo round."));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("test")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command."));
                return true;
            }
            try {
                String worldName = buildPlayerWorldName(player, "test");
                World world = createAndLoadWorld(worldName);
                player.teleportAsync(world.getSpawnLocation());
                sender.sendActionBar(Component.text("Success! Loading new world..."));
            } catch (Exception e) {
                sender.sendMessage(Component.text("Error creating/loading world: " + e.getMessage()));
                e.printStackTrace();
            }
            return true;
        }

        sender.sendMessage(Component.text("Unknown subcommand. Use /bingo start"));
        return true;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    private boolean startGame() {
        if (gameRunning) {
            return false;
        }

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (onlinePlayers.isEmpty()) {
            return false;
        }

        goalManager.resetAllProgress();
        roundParticipants.clear();
        for (Player player : onlinePlayers) {
            roundParticipants.add(player.getUniqueId());
            consumeTracker.clearConsumedItems(player);
            goalManager.onRoundStart(player);

            try {
                String worldName = buildPlayerWorldName(player, "round");
                World world = createAndLoadWorld(worldName);
                player.teleportAsync(world.getSpawnLocation());
            } catch (Exception e) {
                getLogger().warning("Failed to create personal world for " + player.getName() + ": " + e.getMessage());
                player.sendMessage(Component.text("Could not load your personal Bingo world."));
            }
        }

        timer.reset();
        timer.setLimitSeconds(GAME_DURATION_SECONDS);
        timer.start();
        timerExpiredHandled = false;
        gameRunning = true;

        if (timerBossBar != null) {
            timerBossBar.progress(1.0f);
            timerBossBar.name(Component.text("Time left: " + formatClock(GAME_DURATION_SECONDS)));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showBossBar(timerBossBar);
            }
        }

        Bukkit.broadcast(Component.text("Bingo round has started. You have " + formatClock(GAME_DURATION_SECONDS) + " minutes."));
        return true;
    }

    public World createAndLoadWorld(String worldName) throws Exception {
        SlimePropertyMap props = new SlimePropertyMap();

        props.setValue(SlimeProperties.DEFAULT_BIOME, "minecraft:plains");
        props.setValue(SlimeProperties.WORLD_TYPE, "DEFAULT");

        SlimeWorld world = asp.readVanillaWorld(new File("./slime_worlds/" + TEMPLATE_WORLD_NAME), TEMPLATE_WORLD_NAME, loader);
        SlimeWorld clonedWorld = world.clone(worldName);

        SlimeWorldInstance instance = asp.loadWorld(clonedWorld, false);
        return instance.getBukkitWorld();
    }

    private String buildPlayerWorldName(Player player, String prefix) {
        String uuidPart = player.getUniqueId().toString().replace("-", "").substring(0, 8);
        return ("bingo_" + prefix + "_" + uuidPart + "_" + System.currentTimeMillis()).toLowerCase(Locale.ROOT);
    }

}