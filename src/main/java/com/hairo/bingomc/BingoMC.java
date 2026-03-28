package com.hairo.bingomc;

import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.config.GoalConfigService;
import com.hairo.bingomc.goals.config.GoalLoadResult;
import com.hairo.bingomc.goals.util.ConsumeTracker;
import com.hairo.bingomc.goals.util.Timer;
import com.hairo.bingomc.gui.GoalsAdminGui;
import com.hairo.bingomc.gui.GoalsViewerGui;
import com.hairo.bingomc.gui.NewGameGui;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import xyz.xenondevs.invui.InvUI;
import com.hairo.bingomc.commands.BingoCommand;


// TODO: Add InvUI for showing goals and progress


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
    private GoalConfigService goalConfigService;
    private GoalsViewerGui goalsViewerGui;
    private GoalsAdminGui goalsAdminGui;
    private NewGameGui newGameGui;
    private String mainWorldName;
    private MultiverseCoreApi multiverseCoreApi;
    private MultiverseInventoriesApi multiverseInventoriesApi;
    private MultiverseNetherPortals multiverseNetherPortals;

    private static final long GAME_DURATION_SECONDS = 300L;

    @Override
    public void onEnable() {
        consumeTracker = new ConsumeTracker(this);
        goalConfigService = new GoalConfigService(this, consumeTracker);
        goalsViewerGui = new GoalsViewerGui(this);
        goalsAdminGui = new GoalsAdminGui(this);
        newGameGui = new NewGameGui();
        InvUI.getInstance().setPlugin(this);

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
        //cleanupStaleBingoAutoloadEntries();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new GoalEventListener(this, goalManager, consumeTracker), this);

        BingoCommand bingoCommandHandler = new BingoCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
            bingoCommandHandler.register(event.registrar())
        );
        
        getLogger().info("BingoMC has been enabled!");

        if (!reloadGoalsFromDisk(true)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

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
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }

        cleanupManagedBingoWorldsOnShutdown();

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
        String seconds = String.valueOf(event.getElapsedSeconds());
        concludeRound(
            "Time limit reached after " + seconds + " seconds.",
            "Timer expired after " + seconds + " seconds."
        );
    }

    private void concludeRound(String broadcastMessage, String logMessage) {
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

        Bukkit.broadcast(Component.text(broadcastMessage));
        getLogger().info(logMessage);

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

    public void sendBingoUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /bingo <start|stop|goals>"));
    }

    public void sendGoalsUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /bingo goals [admin|validate|reload]"));
    }

    public boolean handleStartCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /bingo start."));
            return true;
        }
        if (!player.isOp()) {
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
        newGameGui.open(player).thenAccept(confirmed -> {
            if (!confirmed) {
                sender.sendMessage(Component.text("Bingo round start cancelled."));
                return;
            }

            Bukkit.getScheduler().runTask(this, () -> {
                long selectedSeed = newGameGui.getWorldSeed();
                boolean started = startGame(selectedSeed);
                if (started) {
                    sender.sendMessage(Component.text("Bingo round started."));
                } else {
                    sender.sendMessage(Component.text("Could not start Bingo round."));
                }
            });
        });
        return true;
    }

    public boolean handleStopCommand(CommandSender sender) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage(Component.text("Only operators can stop a Bingo round."));
            return true;
        }
        if (!gameRunning) {
            sender.sendMessage(Component.text("No Bingo round is currently running."));
            return true;
        }

        if (timer.isRunning()) {
            timer.stop();
        }
        timerExpiredHandled = true;

        concludeRound(
            "Bingo round stopped early by " + sender.getName() + ".",
            "Bingo round stopped early by " + sender.getName() + "."
        );
        sender.sendMessage(Component.text("Bingo round stopped."));
        return true;
    }

    public boolean handleGoalsViewCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the goals UI."));
            return true;
        }
        goalsViewerGui.open(player);
        return true;
    }

    public boolean handleGoalsAdminCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the admin UI."));
            return true;
        }
        if (!player.hasPermission("bingomc.goals.admin")) {
            player.sendMessage(Component.text("You do not have permission to manage goals."));
            return true;
        }
        goalsAdminGui.open(player);
        return true;
    }

    public boolean handleGoalsValidateCommand(CommandSender sender) {
        GoalLoadResult result = goalConfigService.loadGoals();
        if (result.isValid()) {
            sender.sendMessage(Component.text("goals.yml is valid. Loaded " + result.goals().size() + " enabled goals."));
        } else {
            sender.sendMessage(Component.text("goals.yml has validation errors:"));
            for (String error : result.errors()) {
                sender.sendMessage(Component.text("- " + error));
            }
        }
        return true;
    }

    public boolean handleGoalsReloadCommand(CommandSender sender) {
        if (sender instanceof Player player && !player.hasPermission("bingomc.goals.admin")) {
            sender.sendMessage(Component.text("You do not have permission to reload goals."));
            return true;
        }
        if (gameRunning) {
            sender.sendMessage(Component.text("Cannot reload goals while a round is running."));
            return true;
        }
        boolean loaded = reloadGoalsFromDisk(false);
        sender.sendMessage(Component.text(loaded ? "Goals reloaded." : "Could not reload goals; see console for details."));
        return true;
    }

    public GoalManager getGoalManager() {
        return goalManager;
    }

    public long getRoundRemainingSeconds() {
        return timer.getRemainingSeconds();
    }

    private boolean reloadGoalsFromDisk(boolean startup) {
        GoalLoadResult result = goalConfigService.loadGoals();
        if (!result.isValid()) {
            for (String error : result.errors()) {
                getLogger().severe("Goal config error: " + error);
            }
            if (startup) {
                getLogger().severe("Plugin startup aborted due to invalid goals.yml");
            }
            return false;
        }

        goalManager.clearRegisteredGoals();
        for (var loaded : result.goals()) {
            goalManager.registerGoal(loaded.goal(), loaded.points());
        }
        getLogger().info("Loaded " + result.goals().size() + " goals from goals.yml");
        return true;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    private boolean startGame(long worldSeed) {
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
            PlayerWorldSet worldSet = buildPlayerWorldSet(player, roundToken, worldSeed);
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

    private File getMultiverseWorldsFile() {
        return new File(getDataFolder().getParentFile(), "Multiverse-Core/worlds.yml");
    }

    private void cleanupManagedBingoWorldsOnShutdown() {
        Set<String> managedWorldNames = collectManagedBingoWorldNames();
        if (managedWorldNames.isEmpty()) {
            return;
        }

        World mainWorld = Bukkit.getWorld(mainWorldName);
        if (mainWorld != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                World playerWorld = player.getWorld();
                if (playerWorld != null && managedWorldNames.contains(playerWorld.getName())) {
                    player.teleport(mainWorld.getSpawnLocation());
                }
            }
        }

        List<PlayerWorldSet> trackedWorldSets = new ArrayList<>();
        trackedWorldSets.addAll(activeRoundWorldSets.values());
        trackedWorldSets.addAll(previousRoundWorldSets.values());

        boolean success = removeInventoryGroupsForManagedWorlds(managedWorldNames);
        success = cleanupWorldSets(trackedWorldSets) && success;

        Set<String> deletedWorldNames = new HashSet<>();
        for (PlayerWorldSet worldSet : trackedWorldSets) {
            deletedWorldNames.add(worldSet.overworldName);
            deletedWorldNames.add(worldSet.netherName);
            deletedWorldNames.add(worldSet.endName);
        }

        for (String worldName : managedWorldNames) {
            if (!deleteWorldIfPresent(worldName, deletedWorldNames)) {
                success = false;
            }
        }

        if (success) {
            getLogger().info("Deleted all managed Bingo worlds during shutdown.");
        } else {
            getLogger().warning("Shutdown completed with one or more Bingo world cleanup failures.");
        }
    }

    private boolean removeInventoryGroupsForManagedWorlds(Set<String> managedWorldNames) {
        WorldGroupManager manager = multiverseInventoriesApi.getWorldGroupManager();
        Set<String> groupNamesToRemove = new HashSet<>();

        for (String worldName : managedWorldNames) {
            List<WorldGroup> groupsForWorld = manager.getGroupsForWorld(worldName);
            if (groupsForWorld == null) {
                continue;
            }

            for (WorldGroup group : groupsForWorld) {
                if (group == null || group.isDefault()) {
                    continue;
                }
                if (isManagedBingoGroup(group, managedWorldNames)) {
                    groupNamesToRemove.add(group.getName());
                }
            }
        }

        boolean success = true;
        for (String groupName : groupNamesToRemove) {
            WorldGroup existing = manager.getGroup(groupName);
            if (existing == null) {
                continue;
            }
            if (!manager.removeGroup(existing)) {
                success = false;
                getLogger().warning("Could not remove inventory group " + groupName + " during shutdown cleanup.");
            }
        }

        return success;
    }

    private boolean isManagedBingoGroup(WorldGroup group, Set<String> managedWorldNames) {
        if (group.getName().toLowerCase(Locale.ROOT).startsWith("group_bingo_")) {
            return true;
        }

        Set<String> configuredWorlds = group.getConfigWorlds();
        if (configuredWorlds == null || configuredWorlds.isEmpty()) {
            return false;
        }

        boolean containsManagedWorld = false;
        for (String worldName : configuredWorlds) {
            if (managedWorldNames.contains(worldName)) {
                containsManagedWorld = true;
                continue;
            }
            return false;
        }

        return containsManagedWorld;
    }

    private Set<String> collectManagedBingoWorldNames() {
        Set<String> worldNames = new HashSet<>();

        for (PlayerWorldSet worldSet : activeRoundWorldSets.values()) {
            worldNames.add(worldSet.overworldName);
            worldNames.add(worldSet.netherName);
            worldNames.add(worldSet.endName);
        }
        for (PlayerWorldSet worldSet : previousRoundWorldSets.values()) {
            worldNames.add(worldSet.overworldName);
            worldNames.add(worldSet.netherName);
            worldNames.add(worldSet.endName);
        }

        for (World world : Bukkit.getWorlds()) {
            if (isManagedBingoWorldName(world.getName())) {
                worldNames.add(world.getName());
            }
        }

        File worldsFile = getMultiverseWorldsFile();
        if (worldsFile.exists()) {
            YamlConfiguration worldsConfig = YamlConfiguration.loadConfiguration(worldsFile);
            ConfigurationSection worldsSection = worldsConfig.getConfigurationSection("worlds");
            if (worldsSection != null) {
                for (String worldName : worldsSection.getKeys(false)) {
                    if (isManagedBingoWorldName(worldName)) {
                        worldNames.add(worldName);
                    }
                }
            }
        }

        return worldNames;
    }

    private boolean isManagedBingoWorldName(String worldName) {
        if (worldName == null || !worldName.startsWith("bingo_")) {
            return false;
        }
        return !worldName.equalsIgnoreCase(mainWorldName);
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

        boolean folderExists = isWorldFolderPresent(worldName);
        Option<org.mvplugins.multiverse.core.world.MultiverseWorld> worldOption = multiverseCoreApi.getWorldManager().getWorld(worldName);
        if (worldOption.isEmpty()) {
            if (folderExists && !deleteWorldFolder(worldName)) {
                getLogger().severe("Failed deleting unregistered world folder " + worldName);
                return false;
            }
            deletedWorldNames.add(worldName);
            return true;
        }

        final boolean[] failed = new boolean[] { false };
        multiverseCoreApi.getWorldManager()
            .deleteWorld(DeleteWorldOptions.world(worldOption.get()))
            .onFailure(reason -> {
                // If files were manually deleted, treat stale Multiverse metadata as non-blocking.
                if (!folderExists && Bukkit.getWorld(worldName) == null) {
                    getLogger().warning("World folder for " + worldName + " is already missing; ignoring delete failure: " + reason);
                    return;
                }

                if (Bukkit.getWorld(worldName) == null && deleteWorldFolder(worldName)) {
                    getLogger().warning("Delete API failed for " + worldName + ", but world folder was removed directly: " + reason);
                    return;
                }

                failed[0] = true;
                getLogger().severe("Failed deleting world " + worldName + ": " + reason);
            });

        if (!failed[0]) {
            deletedWorldNames.add(worldName);
        }
        return !failed[0];
    }

    private boolean isWorldFolderPresent(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        return worldFolder.exists();
    }

    private boolean deleteWorldFolder(String worldName) {
        Path worldFolder = new File(Bukkit.getWorldContainer(), worldName).toPath();
        if (!Files.exists(worldFolder)) {
            return true;
        }

        try (var paths = Files.walk(worldFolder)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            return true;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                getLogger().severe("Failed deleting world folder " + worldName + ": " + ioException.getMessage());
                return false;
            }
            throw e;
        } catch (IOException e) {
            getLogger().severe("Failed walking world folder " + worldName + ": " + e.getMessage());
            return false;
        }
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
        return createWorld(worldSet.overworldName, Environment.NORMAL, worldSet.seed)
            && createWorld(worldSet.netherName, Environment.NETHER, worldSet.seed)
            && createWorld(worldSet.endName, Environment.THE_END, worldSet.seed);
    }

    private boolean createWorld(String worldName, Environment environment, long seed) {
        final boolean[] failed = new boolean[] { false };
        multiverseCoreApi.getWorldManager()
            .createWorld(CreateWorldOptions.worldName(worldName)
                .seed(seed)
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

    private PlayerWorldSet buildPlayerWorldSet(Player player, long roundToken, long worldSeed) {
        String uuid = player.getUniqueId().toString().replace("-", "");
        String shortUuid = uuid.substring(0, 8);
        String roundId = Long.toString(roundToken, 36);
        String base = ("bingo_" + shortUuid + "_" + roundId).toLowerCase(Locale.ROOT);

        return new PlayerWorldSet(
            base,
            base + "_nether",
            base + "_the_end",
            "group_" + base,
            worldSeed
        );
    }

    private static final class PlayerWorldSet {
        private final String overworldName;
        private final String netherName;
        private final String endName;
        private final String inventoryGroupName;
        private final long seed;

        private PlayerWorldSet(String overworldName, String netherName, String endName, String inventoryGroupName, long seed) {
            this.overworldName = overworldName;
            this.netherName = netherName;
            this.endName = endName;
            this.inventoryGroupName = inventoryGroupName;
            this.seed = seed;
        }
    }

}