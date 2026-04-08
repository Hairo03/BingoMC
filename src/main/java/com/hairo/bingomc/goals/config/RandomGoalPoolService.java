package com.hairo.bingomc.goals.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class RandomGoalPoolService {

    public record RandomGoalEntry(LoadedGoal goal, String difficulty, String playstyle) {}

    private static final String POOL_FILE_NAME = "random_goals.yml";
    private static final String LIST_KEY = "random_goals";

    private final JavaPlugin plugin;
    private final GoalConfigService goalConfigService;

    public RandomGoalPoolService(JavaPlugin plugin, GoalConfigService goalConfigService) {
        this.plugin = plugin;
        this.goalConfigService = goalConfigService;
    }

    /**
     * Loads and validates the random goal pool. Call at startup.
     * Logs warnings for invalid entries but never aborts the plugin.
     *
     * @return list of valid pool entries ready for use by the randomizer
     */
    public List<RandomGoalEntry> loadPool() {
        ensurePoolFileExists();
        File poolFile = new File(plugin.getDataFolder(), POOL_FILE_NAME);

        // Read metadata (difficulty, playstyle) per goal id
        YamlConfiguration config = YamlConfiguration.loadConfiguration(poolFile);
        List<Map<?, ?>> rawList = config.getMapList(LIST_KEY);
        Map<String, String[]> metaById = new HashMap<>();
        for (Map<?, ?> section : rawList) {
            String id = readString(section, "id");
            String difficulty = readString(section, "difficulty").toLowerCase();
            String playstyle  = readString(section, "playstyle").toLowerCase();
            if (!id.isEmpty()) {
                metaById.put(id, new String[]{difficulty, playstyle});
            }
        }

        // Validate via GoalConfigService (runs full Bukkit-registry checks)
        GoalLoadResult result = goalConfigService.loadGoalsFrom(poolFile, LIST_KEY);
        if (!result.errors().isEmpty()) {
            plugin.getLogger().warning("random_goals.yml has " + result.errors().size() + " invalid entries (these will be excluded from the pool):");
            for (String error : result.errors()) {
                plugin.getLogger().warning("  " + error);
            }
        }

        List<RandomGoalEntry> pool = new ArrayList<>();
        for (LoadedGoal loaded : result.goals()) {
            String[] meta = metaById.get(loaded.goal().id());
            String difficulty = meta != null ? meta[0] : "";
            String playstyle  = meta != null ? meta[1] : "";
            pool.add(new RandomGoalEntry(loaded, difficulty, playstyle));
        }

        plugin.getLogger().info("Random goal pool: " + pool.size() + " valid entries loaded from " + POOL_FILE_NAME);
        return pool;
    }

    private void ensurePoolFileExists() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File file = new File(plugin.getDataFolder(), POOL_FILE_NAME);
        if (!file.exists()) {
            try (InputStream in = plugin.getResource(POOL_FILE_NAME)) {
                if (in == null) {
                    plugin.getLogger().warning("Bundled " + POOL_FILE_NAME + " not found — random goal pool will be empty.");
                    return;
                }
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to copy " + POOL_FILE_NAME + " to data folder: " + e.getMessage());
            }
        }
    }

    private String readString(Map<?, ?> section, String key) {
        Object value = section.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
