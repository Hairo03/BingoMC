package com.hairo.bingomc.round;

import com.hairo.bingomc.events.TimerExpiredEvent;
import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.util.Timer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;


public class RoundTaskTicker {

    private final JavaPlugin plugin;
    private final GoalManager goalManager;
    private final RoundPresenter presenter;
    private final RoundParticipants participants;
    
    // Ticker state
    private BukkitTask preparationTask;
    private BukkitTask gameTask;
    private boolean timerExpiredHandled = false;

    public RoundTaskTicker(
        JavaPlugin plugin,
        GoalManager goalManager,
        RoundPresenter presenter,
        RoundParticipants participants
    ) {
        this.plugin = plugin;
        this.goalManager = goalManager;
        this.presenter = presenter;
        this.participants = participants;
    }

    public void startPreparationTicker(long countdownSeconds, Runnable onPreparationComplete) {
        stopPreparationTicker();
        
        final long[] remaining = {countdownSeconds};
        
        preparationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (remaining[0] > 0) {
                presenter.updatePreparationDisplay(remaining[0]);
                remaining[0]--;
            } else {
                presenter.updatePreparationDisplay(0);
                stopPreparationTicker();
                onPreparationComplete.run();
            }
        }, 20L, 20L);
    }

    public void stopPreparationTicker() {
        if (preparationTask != null) {
            preparationTask.cancel();
            preparationTask = null;
        }
    }

    public void startGameTicker(Timer timer) {
        stopGameTicker();
        timerExpiredHandled = false;

        gameTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!timer.isRunning()) return;

            if (timer.isExpired() && !timerExpiredHandled) {
                timer.stop();
                timerExpiredHandled = true;
                stopGameTicker();
                Bukkit.getPluginManager().callEvent(new TimerExpiredEvent(timer.getElapsedMillis()));
                return;
            }

            if (timer.hasLimit()) {
                presenter.updateGameTimerDisplay(timer.getRemainingMillis(), timer.getLimitMillis());
            }

            for (UUID id : participants.getParticipants()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) {
                    goalManager.evaluate(player, GoalTrigger.PERIODIC);
                }
            }
        }, 20L, 20L);
    }

    public void stopGameTicker() {
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }
    }
    
}
