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
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.mvplugins.multiverse.external.vavr.control.Option;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.options.CreateWorldOptions;
import org.mvplugins.multiverse.core.world.options.DeleteWorldOptions;
import org.mvplugins.multiverse.inventories.MultiverseInventoriesApi;
import org.mvplugins.multiverse.inventories.profile.group.WorldGroup;
import org.mvplugins.multiverse.inventories.profile.group.WorldGroupManager;
import org.mvplugins.multiverse.netherportals.MultiverseNetherPortals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BingoMC extends JavaPlugin implements Listener {

    private final GoalManager goalManager = new GoalManager();
    private ConsumeTracker consumeTracker;
    private Timer timer;
    private boolean gameRunning;
    private boolean timerExpiredHandled;
    private BossBar timerBossBar;
    private final Set<UUID> roundParticipants = new HashSet<>();
    private final Map<UUID, PlayerWorldSet> activeRoundWorldSets = new HashMap<>();
    private final Map<UUID, PlayerWorldSet> previousRoundWorldSets = new HashMap<>();
    private String mainWorldName;
    private MultiverseCoreApi multiverseCoreApi;
    private MultiverseInventoriesApi multiverseInventoriesApi;
    private MultiverseNetherPortals multiverseNetherPortals;

    private static final long GAME_DURATION_SECONDS = 300L;

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
        if (!initializeMultiverseApis()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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
            player.sendMessage(Component.text("A round is currently running. You will be able to join next round."));
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

    previousRoundWorldSets.clear();
    previousRoundWorldSets.putAll(activeRoundWorldSets);
    activeRoundWorldSets.clear();
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

        if (!cleanupPreviousRoundWorlds()) {
            return false;
        }

        long roundToken = System.currentTimeMillis();
        Map<UUID, PlayerWorldSet> createdWorldSets = new HashMap<>();
        for (Player player : onlinePlayers) {
            PlayerWorldSet worldSet = buildPlayerWorldSet(player, roundToken);
            if (!createPlayerWorldSet(worldSet)) {
                getLogger().severe("Aborting round start because world setup failed for " + player.getName());
                cleanupWorldSets(createdWorldSets.values());
                return false;
            }
            createdWorldSets.put(player.getUniqueId(), worldSet);

            if (!configurePlayerPortalLinks(worldSet)) {
                getLogger().severe("Aborting round start because portal link setup failed for " + player.getName());
                cleanupWorldSets(createdWorldSets.values());
                return false;
            }

            if (!configurePlayerInventoryGroup(worldSet)) {
                getLogger().severe("Aborting round start because inventory group setup failed for " + player.getName());
                cleanupWorldSets(createdWorldSets.values());
                return false;
            }
        }

        goalManager.resetAllProgress();
        roundParticipants.clear();
        activeRoundWorldSets.clear();
        activeRoundWorldSets.putAll(createdWorldSets);

        for (Player player : onlinePlayers) {
            roundParticipants.add(player.getUniqueId());
            consumeTracker.clearConsumedItems(player);
            goalManager.onRoundStart(player);

            PlayerWorldSet worldSet = createdWorldSets.get(player.getUniqueId());
            World playerWorld = worldSet == null ? null : Bukkit.getWorld(worldSet.overworldName);
            if (playerWorld == null) {
                getLogger().severe("Player world missing after creation for " + player.getName());
                cleanupWorldSets(createdWorldSets.values());
                activeRoundWorldSets.clear();
                roundParticipants.clear();
                return false;
            }
            player.teleportAsync(playerWorld.getSpawnLocation());
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

    private boolean initializeMultiverseApis() {
        try {
            multiverseCoreApi = MultiverseCoreApi.get();
            multiverseInventoriesApi = MultiverseInventoriesApi.get();
        } catch (IllegalStateException e) {
            getLogger().severe("Failed to initialize Multiverse API: " + e.getMessage());
            return false;
        }

        if (!(getServer().getPluginManager().getPlugin("Multiverse-NetherPortals") instanceof MultiverseNetherPortals plugin)) {
            getLogger().severe("Multiverse-NetherPortals plugin instance is unavailable.");
            return false;
        }
        multiverseNetherPortals = plugin;
        return true;
    }

    private boolean cleanupPreviousRoundWorlds() {
        if (previousRoundWorldSets.isEmpty()) {
            return true;
        }

        boolean success = cleanupWorldSets(previousRoundWorldSets.values());
        if (success) {
            previousRoundWorldSets.clear();
        }
        return success;
    }

    private boolean cleanupWorldSets(Iterable<PlayerWorldSet> worldSets) {
        Set<String> deletedWorldNames = new HashSet<>();
        boolean success = true;

        for (PlayerWorldSet worldSet : worldSets) {
            if (!removeInventoryGroup(worldSet.inventoryGroupName)) {
                success = false;
            }

            if (!deleteWorldIfPresent(worldSet.overworldName, deletedWorldNames)) {
                success = false;
            }
            if (!deleteWorldIfPresent(worldSet.netherName, deletedWorldNames)) {
                success = false;
            }
            if (!deleteWorldIfPresent(worldSet.endName, deletedWorldNames)) {
                success = false;
            }
        }

        return success;
    }

    private boolean deleteWorldIfPresent(String worldName, Set<String> deletedWorldNames) {
        if (deletedWorldNames.contains(worldName)) {
            return true;
        }

        Option<org.mvplugins.multiverse.core.world.MultiverseWorld> worldOption = multiverseCoreApi.getWorldManager().getWorld(worldName);
        if (worldOption.isEmpty()) {
            deletedWorldNames.add(worldName);
            return true;
        }

        final boolean[] failed = new boolean[] { false };
        multiverseCoreApi.getWorldManager()
            .deleteWorld(DeleteWorldOptions.world(worldOption.get()))
            .onFailure(reason -> {
                failed[0] = true;
                getLogger().severe("Failed deleting world " + worldName + ": " + reason);
            });

        if (!failed[0]) {
            deletedWorldNames.add(worldName);
        }
        return !failed[0];
    }

    private boolean removeInventoryGroup(String groupName) {
        WorldGroupManager manager = multiverseInventoriesApi.getWorldGroupManager();
        WorldGroup existing = manager.getGroup(groupName);
        if (existing == null) {
            return true;
        }
        boolean removed = manager.removeGroup(existing);
        if (!removed) {
            getLogger().warning("Could not remove inventory group " + groupName);
        }
        return removed;
    }

    private boolean createPlayerWorldSet(PlayerWorldSet worldSet) {
        return createWorld(worldSet.overworldName, Environment.NORMAL)
            && createWorld(worldSet.netherName, Environment.NETHER)
            && createWorld(worldSet.endName, Environment.THE_END);
    }

    private boolean createWorld(String worldName, Environment environment) {
        final boolean[] failed = new boolean[] { false };
        multiverseCoreApi.getWorldManager()
            .createWorld(CreateWorldOptions.worldName(worldName)
                .environment(environment)
                .generateStructures(true)
                .useSpawnAdjust(true)
                .doFolderCheck(true))
            .onFailure(reason -> {
                failed[0] = true;
                getLogger().severe("Failed creating world " + worldName + ": " + reason);
            });

        if (failed[0]) {
            return false;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().severe("World creation returned success but Bukkit world is missing: " + worldName);
            return false;
        }
        return true;
    }

    private boolean configurePlayerPortalLinks(PlayerWorldSet worldSet) {
        boolean netherForward = multiverseNetherPortals.addWorldLink(worldSet.overworldName, worldSet.netherName, PortalType.NETHER);
        boolean netherBackward = multiverseNetherPortals.addWorldLink(worldSet.netherName, worldSet.overworldName, PortalType.NETHER);
        boolean endForward = multiverseNetherPortals.addWorldLink(worldSet.overworldName, worldSet.endName, PortalType.ENDER);
        boolean endBackward = multiverseNetherPortals.addWorldLink(worldSet.endName, worldSet.overworldName, PortalType.ENDER);

        if (!netherForward || !netherBackward || !endForward || !endBackward) {
            getLogger().severe("Failed linking personal portal worlds for " + worldSet.overworldName);
            return false;
        }

        return multiverseNetherPortals.saveMVNPConfig();
    }

    private boolean configurePlayerInventoryGroup(PlayerWorldSet worldSet) {
        try {
            WorldGroupManager manager = multiverseInventoriesApi.getWorldGroupManager();

            WorldGroup oldGroup = manager.getGroup(worldSet.inventoryGroupName);
            if (oldGroup != null) {
                manager.removeGroup(oldGroup);
            }

            WorldGroup group = manager.newEmptyGroup(worldSet.inventoryGroupName);
            group.addWorld(worldSet.overworldName, false);
            group.addWorld(worldSet.netherName, false);
            group.addWorld(worldSet.endName, false);
            manager.updateGroup(group);
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed configuring inventory group " + worldSet.inventoryGroupName + ": " + e.getMessage());
            return false;
        }
    }

    private PlayerWorldSet buildPlayerWorldSet(Player player, long roundToken) {
        String uuid = player.getUniqueId().toString().replace("-", "");
        String shortUuid = uuid.substring(0, 8);
        String roundId = Long.toString(roundToken, 36);
        String base = ("bingo_" + shortUuid + "_" + roundId).toLowerCase(Locale.ROOT);

        return new PlayerWorldSet(
            base,
            base + "_nether",
            base + "_the_end",
            "group_" + base
        );
    }

    private static final class PlayerWorldSet {
        private final String overworldName;
        private final String netherName;
        private final String endName;
        private final String inventoryGroupName;

        private PlayerWorldSet(String overworldName, String netherName, String endName, String inventoryGroupName) {
            this.overworldName = overworldName;
            this.netherName = netherName;
            this.endName = endName;
            this.inventoryGroupName = inventoryGroupName;
        }
    }

}