package com.hairo.bingomc.goals.config;

import java.util.Collections;
import java.util.List;

public final class GoalLoadResult {
    private final List<LoadedGoal> goals;
    private final List<String> errors;

    public GoalLoadResult(List<LoadedGoal> goals, List<String> errors) {
        this.goals = List.copyOf(goals);
        this.errors = List.copyOf(errors);
    }

    public List<LoadedGoal> goals() {
        return Collections.unmodifiableList(goals);
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
