package com.hairo.bingomc.worlds;

import org.bukkit.Bukkit;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.options.CreateWorldOptions;
import org.mvplugins.multiverse.core.world.options.DeleteWorldOptions;
import org.mvplugins.multiverse.external.vavr.control.Option;
import org.mvplugins.multiverse.inventories.MultiverseInventoriesApi;
import org.mvplugins.multiverse.inventories.profile.group.WorldGroup;
import org.mvplugins.multiverse.inventories.profile.group.WorldGroupManager;
import org.mvplugins.multiverse.netherportals.MultiverseNetherPortals;
import org.mvplugins.multiverse.inventories.share.Sharables;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BingoWorldService {

    private final JavaPlugin plugin;
    private final Map<UUID, PlayerWorldSet> activeRoundWorldSets = new HashMap<>();
    private final Map<UUID, PlayerWorldSet> previousRoundWorldSets = new HashMap<>();

    private MultiverseCoreApi multiverseCoreApi;
    private MultiverseInventoriesApi multiverseInventoriesApi;
    private MultiverseNetherPortals multiverseNetherPortals;

    public BingoWorldService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initializeApis() {
        try {
            multiverseCoreApi = MultiverseCoreApi.get();
            multiverseInventoriesApi = MultiverseInventoriesApi.get();
        } catch (IllegalStateException e) {
            plugin.getLogger().severe("Failed to initialize Multiverse API: " + e.getMessage());
            return false;
        }

        if (!(plugin.getServer().getPluginManager().getPlugin("Multiverse-NetherPortals") instanceof MultiverseNetherPortals portalsPlugin)) {
            plugin.getLogger().severe("Multiverse-NetherPortals plugin instance is unavailable.");
            return false;
        }

        multiverseNetherPortals = portalsPlugin;
        return true;
    }

    public Map<UUID, PlayerWorldSet> provisionRoundWorldSets(List<Player> players, long worldSeed) {
        if (!cleanupPreviousRoundWorlds()) {
            return null;
        }

        long roundToken = System.currentTimeMillis();
        Map<UUID, PlayerWorldSet> createdWorldSets = new HashMap<>();

        for (Player player : players) {
            PlayerWorldSet worldSet = buildPlayerWorldSet(player, roundToken, worldSeed);
            if (!createPlayerWorldSet(worldSet)) {
                plugin.getLogger().severe("Aborting round start because world setup failed for " + player.getName());
                cleanupWorldSets(createdWorldSets.values());
                return null;
            }
            createdWorldSets.put(player.getUniqueId(), worldSet);

            if (!configurePlayerPortalLinks(worldSet)) {
                plugin.getLogger().severe("Aborting round start because portal link setup failed for " + player.getName());
                cleanupWorldSets(createdWorldSets.values());
                return null;
            }

            if (!configurePlayerInventoryGroup(worldSet)) {
                plugin.getLogger().severe("Aborting round start because inventory group setup failed for " + player.getName());
                cleanupWorldSets(createdWorldSets.values());
                return null;
            }
        }

        return createdWorldSets;
    }

    public void activateRoundWorldSets(Map<UUID, PlayerWorldSet> createdWorldSets) {
        activeRoundWorldSets.clear();
        activeRoundWorldSets.putAll(createdWorldSets);
    }

    public void moveActiveToPreviousRound() {
        previousRoundWorldSets.clear();
        previousRoundWorldSets.putAll(activeRoundWorldSets);
        activeRoundWorldSets.clear();
    }

    public void clearTrackedRoundWorlds() {
        activeRoundWorldSets.clear();
        previousRoundWorldSets.clear();
    }

    public boolean cleanupWorldSets(Iterable<PlayerWorldSet> worldSets) {
        Set<String> deletedWorldNames = new HashSet<>();
        boolean success = true;

        for (PlayerWorldSet worldSet : worldSets) {
            if (!removeInventoryGroup(worldSet.inventoryGroupName())) {
                success = false;
            }

            if (!deleteWorldIfPresent(worldSet.overworldName(), deletedWorldNames)) {
                success = false;
            }
            if (!deleteWorldIfPresent(worldSet.netherName(), deletedWorldNames)) {
                success = false;
            }
            if (!deleteWorldIfPresent(worldSet.endName(), deletedWorldNames)) {
                success = false;
            }
        }

        return success;
    }

    public void cleanupManagedBingoWorldsOnShutdown(String mainWorldName) {
        Set<String> managedWorldNames = collectManagedBingoWorldNames(mainWorldName);
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
            deletedWorldNames.add(worldSet.overworldName());
            deletedWorldNames.add(worldSet.netherName());
            deletedWorldNames.add(worldSet.endName());
        }

        for (String worldName : managedWorldNames) {
            if (!deleteWorldIfPresent(worldName, deletedWorldNames)) {
                success = false;
            }
        }

        if (success) {
            plugin.getLogger().info("Deleted all managed Bingo worlds during shutdown.");
        } else {
            plugin.getLogger().warning("Shutdown completed with one or more Bingo world cleanup failures.");
        }
    }

    private File getMultiverseWorldsFile() {
        return new File(plugin.getDataFolder().getParentFile(), "Multiverse-Core/worlds.yml");
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
                plugin.getLogger().warning("Could not remove inventory group " + groupName + " during shutdown cleanup.");
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

    private Set<String> collectManagedBingoWorldNames(String mainWorldName) {
        Set<String> worldNames = new HashSet<>();

        for (PlayerWorldSet worldSet : activeRoundWorldSets.values()) {
            worldNames.add(worldSet.overworldName());
            worldNames.add(worldSet.netherName());
            worldNames.add(worldSet.endName());
        }
        for (PlayerWorldSet worldSet : previousRoundWorldSets.values()) {
            worldNames.add(worldSet.overworldName());
            worldNames.add(worldSet.netherName());
            worldNames.add(worldSet.endName());
        }

        for (World world : Bukkit.getWorlds()) {
            if (isManagedBingoWorldName(world.getName(), mainWorldName)) {
                worldNames.add(world.getName());
            }
        }

        File worldsFile = getMultiverseWorldsFile();
        if (worldsFile.exists()) {
            YamlConfiguration worldsConfig = YamlConfiguration.loadConfiguration(worldsFile);
            ConfigurationSection worldsSection = worldsConfig.getConfigurationSection("worlds");
            if (worldsSection != null) {
                for (String worldName : worldsSection.getKeys(false)) {
                    if (isManagedBingoWorldName(worldName, mainWorldName)) {
                        worldNames.add(worldName);
                    }
                }
            }
        }

        return worldNames;
    }

    private boolean isManagedBingoWorldName(String worldName, String mainWorldName) {
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

    private boolean deleteWorldIfPresent(String worldName, Set<String> deletedWorldNames) {
        if (deletedWorldNames.contains(worldName)) {
            return true;
        }

        boolean folderExists = isWorldFolderPresent(worldName);
        Option<org.mvplugins.multiverse.core.world.MultiverseWorld> worldOption = multiverseCoreApi.getWorldManager().getWorld(worldName);
        if (worldOption.isEmpty()) {
            if (folderExists && !deleteWorldFolder(worldName)) {
                plugin.getLogger().severe("Failed deleting unregistered world folder " + worldName);
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
                    plugin.getLogger().warning("World folder for " + worldName + " is already missing; ignoring delete failure: " + reason);
                    return;
                }

                if (Bukkit.getWorld(worldName) == null && deleteWorldFolder(worldName)) {
                    plugin.getLogger().warning("Delete API failed for " + worldName + ", but world folder was removed directly: " + reason);
                    return;
                }

                failed[0] = true;
                plugin.getLogger().severe("Failed deleting world " + worldName + ": " + reason);
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
                plugin.getLogger().severe("Failed deleting world folder " + worldName + ": " + ioException.getMessage());
                return false;
            }
            throw e;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed walking world folder " + worldName + ": " + e.getMessage());
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
            plugin.getLogger().warning("Could not remove inventory group " + groupName);
        }
        return removed;
    }

    private boolean createPlayerWorldSet(PlayerWorldSet worldSet) {
        return createWorld(worldSet.overworldName(), Environment.NORMAL, worldSet.seed())
            && createWorld(worldSet.netherName(), Environment.NETHER, worldSet.seed())
            && createWorld(worldSet.endName(), Environment.THE_END, worldSet.seed());
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
                plugin.getLogger().severe("Failed creating world " + worldName + ": " + reason);
            });

        if (failed[0]) {
            return false;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("World creation returned success but Bukkit world is missing: " + worldName);
            return false;
        }
        return true;
    }

    private boolean configurePlayerPortalLinks(PlayerWorldSet worldSet) {
        boolean netherForward = multiverseNetherPortals.addWorldLink(worldSet.overworldName(), worldSet.netherName(), PortalType.NETHER);
        boolean netherBackward = multiverseNetherPortals.addWorldLink(worldSet.netherName(), worldSet.overworldName(), PortalType.NETHER);
        boolean endForward = multiverseNetherPortals.addWorldLink(worldSet.overworldName(), worldSet.endName(), PortalType.ENDER);
        boolean endBackward = multiverseNetherPortals.addWorldLink(worldSet.endName(), worldSet.overworldName(), PortalType.ENDER);

        if (!netherForward || !netherBackward || !endForward || !endBackward) {
            plugin.getLogger().severe("Failed linking personal portal worlds for " + worldSet.overworldName());
            return false;
        }

        return multiverseNetherPortals.saveMVNPConfig();
    }

    private boolean configurePlayerInventoryGroup(PlayerWorldSet worldSet) {
        try {
            WorldGroupManager manager = multiverseInventoriesApi.getWorldGroupManager();

            WorldGroup oldGroup = manager.getGroup(worldSet.inventoryGroupName());
            if (oldGroup != null) {
                manager.removeGroup(oldGroup);
            }

            WorldGroup group = manager.newEmptyGroup(worldSet.inventoryGroupName());
            group.getShares().mergeShares(Sharables.ALL_DEFAULT);
            group.addWorld(worldSet.overworldName(), false);
            group.addWorld(worldSet.netherName(), false);
            group.addWorld(worldSet.endName(), false);
            manager.updateGroup(group);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed configuring inventory group " + worldSet.inventoryGroupName() + ": " + e.getMessage());
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
}
