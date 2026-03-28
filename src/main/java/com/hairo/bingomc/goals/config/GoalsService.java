package com.hairo.bingomc.goals.config;

import com.hairo.bingomc.goals.core.GoalManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class GoalsService {

    private final JavaPlugin plugin;
    private final GoalConfigService goalConfigService;
    private final GoalManager goalManager;

    public GoalsService(JavaPlugin plugin, GoalConfigService goalConfigService, GoalManager goalManager) {
        this.plugin = plugin;
        this.goalConfigService = goalConfigService;
        this.goalManager = goalManager;
    }

    public GoalLoadResult validateGoals() {
        return goalConfigService.loadGoals();
    }

    public boolean reloadGoals(boolean startup) {
        GoalLoadResult result = goalConfigService.loadGoals();
        if (!result.isValid()) {
            for (String error : result.errors()) {
                plugin.getLogger().severe("Goal config error: " + error);
            }
            if (startup) {
                plugin.getLogger().severe("Plugin startup aborted due to invalid goals.yml");
            }
            return false;
        }

        goalManager.clearRegisteredGoals();
        for (var loaded : result.goals()) {
            goalManager.registerGoal(loaded.goal(), loaded.points());
        }
        plugin.getLogger().info("Loaded " + result.goals().size() + " goals from goals.yml");
        return true;
    }
}
