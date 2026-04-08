package com.hairo.bingomc.goals.config;

import com.hairo.bingomc.goals.config.RandomGoalPoolService.RandomGoalEntry;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class GoalRandomizerService {

    /** Difficulty tiers in the order they appear in the written goals.yml. */
    private static final List<String> TARGET_ORDER = List.of("easy", "normal", "advanced", "hard", "extreme");


    private final JavaPlugin plugin;
    private final RandomGoalPoolService poolService;
    private final GoalsService goalsService;

    public GoalRandomizerService(JavaPlugin plugin, RandomGoalPoolService poolService, GoalsService goalsService) {
        this.plugin = plugin;
        this.poolService = poolService;
        this.goalsService = goalsService;
    }

    /**
     * Picks a balanced set of goals from the random pool and writes them to goals.yml,
     * then reloads the goal manager.
     *
     * @return null on success, or an error message string on failure
     */
    public String randomize() {
        List<RandomGoalEntry> pool = poolService.loadPool();
        if (pool.isEmpty()) {
            return "Random goal pool is empty — check random_goals.yml";
        }

        // Group by difficulty
        Map<String, List<RandomGoalEntry>> byDifficulty = new LinkedHashMap<>();
        for (String diff : TARGET_ORDER) {
            byDifficulty.put(diff, new ArrayList<>());
        }
        for (RandomGoalEntry entry : pool) {
            byDifficulty.computeIfAbsent(entry.difficulty(), k -> new ArrayList<>()).add(entry);
        }

        // Select a balanced set from each difficulty bucket
        List<RandomGoalEntry> selected = new ArrayList<>();
        for (String difficulty : TARGET_ORDER) {
            int target = getTargetCount(difficulty);
            List<RandomGoalEntry> bucket = byDifficulty.getOrDefault(difficulty, List.of());
            selected.addAll(selectBalanced(bucket, target));
        }

        if (selected.isEmpty()) {
            return "No goals could be selected — check difficulty tags in random_goals.yml";
        }

        // Collect selected IDs for filtering the raw YAML
        Set<String> selectedIds = selected.stream()
            .map(e -> e.goal().goal().id())
            .collect(Collectors.toSet());

        // Re-read the raw map data from the pool file (needed to write goals.yml faithfully)
        File poolFile = new File(plugin.getDataFolder(), "random_goals.yml");
        YamlConfiguration poolConfig = YamlConfiguration.loadConfiguration(poolFile);
        List<Map<?, ?>> rawPool = poolConfig.getMapList("random_goals");

        // Filter raw entries to the selected IDs, preserving tier order
        List<Map<?, ?>> selectedRaw = rawPool.stream()
            .filter(m -> selectedIds.contains(String.valueOf(m.get("id")).trim()))
            .collect(Collectors.toList());

        // Write to goals.yml
        YamlConfiguration goalsConfig = new YamlConfiguration();
        goalsConfig.set("goals", selectedRaw);

        File goalsFile = new File(plugin.getDataFolder(), "goals.yml");
        try {
            goalsConfig.save(goalsFile);
        } catch (IOException e) {
            return "Failed to write goals.yml: " + e.getMessage();
        }

        // Reload the goal manager from the newly written file
        if (!goalsService.reloadGoals(false)) {
            return "Goals written but failed to reload — check server logs for details";
        }

        plugin.getLogger().info("Goals randomized: " + selected.size() + " goals written to goals.yml");
        return null; // success
    }

    private int getTargetCount(String difficulty) {
        return plugin.getConfig().getInt("goal-randomizer." + difficulty + "-count", switch (difficulty) {
            case "easy"     -> 7;
            case "normal"   -> 9;
            case "advanced" -> 8;
            case "hard"     -> 3;
            case "extreme"  -> 1;
            default         -> 0;
        });
    }

    /**
     * Picks up to {@code target} goals from {@code bucket}, cycling round-robin across
     * playstyles to maximise variety. Both the playstyle order and each playstyle's
     * internal list are shuffled fresh on every call.
     */
    private List<RandomGoalEntry> selectBalanced(List<RandomGoalEntry> bucket, int target) {
        if (bucket.isEmpty() || target == 0) return List.of();

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Group by playstyle and shuffle each group
        Map<String, List<RandomGoalEntry>> byPlaystyle = new LinkedHashMap<>();
        for (RandomGoalEntry entry : bucket) {
            byPlaystyle.computeIfAbsent(entry.playstyle(), k -> new ArrayList<>()).add(entry);
        }
        for (List<RandomGoalEntry> list : byPlaystyle.values()) {
            Collections.shuffle(list, rng);
        }

        // Shuffle the playstyle cycling order
        List<String> playstyles = new ArrayList<>(byPlaystyle.keySet());
        Collections.shuffle(playstyles, rng);

        // Round-robin across playstyles until target is reached or all exhausted
        List<RandomGoalEntry> result = new ArrayList<>();
        Map<String, Integer> indices = new HashMap<>();

        while (result.size() < target) {
            boolean anyPicked = false;
            for (String playstyle : playstyles) {
                if (result.size() >= target) break;
                List<RandomGoalEntry> list = byPlaystyle.get(playstyle);
                int idx = indices.getOrDefault(playstyle, 0);
                if (idx < list.size()) {
                    result.add(list.get(idx));
                    indices.put(playstyle, idx + 1);
                    anyPicked = true;
                }
            }
            if (!anyPicked) break; // all playstyle buckets exhausted before target reached
        }

        return result;
    }
}
