package com.hairo.bingomc;

import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.goals.config.GoalConfigService;
import com.hairo.bingomc.goals.config.GoalsService;
import com.hairo.bingomc.goals.util.ConsumeTracker;
import com.hairo.bingomc.commands.BingoCommandHandler;
import com.hairo.bingomc.gui.GoalsAdminGui;
import com.hairo.bingomc.gui.GoalsViewerGui;
import com.hairo.bingomc.gui.NewGameGui;
import com.hairo.bingomc.listeners.GoalEventListener;
import com.hairo.bingomc.listeners.PreparationEventListener;
import com.hairo.bingomc.events.TimerExpiredEvent;
import com.hairo.bingomc.round.RoundService;
import com.hairo.bingomc.worlds.BingoWorldService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import xyz.xenondevs.invui.InvUI;
import com.hairo.bingomc.commands.BingoCommand;
import java.util.UUID;

public class BingoMC extends JavaPlugin implements Listener {

    private static final long DEFAULT_GAME_DURATION_SECONDS = 5L * 60L;

    private final GoalManager goalManager = new GoalManager();
    private ConsumeTracker consumeTracker;
    private GoalsViewerGui goalsViewerGui;
    private GoalsAdminGui goalsAdminGui;
    private NewGameGui newGameGui;
    private GoalsService goalsService;
    private RoundService roundService;
    private BingoCommandHandler commandHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        consumeTracker = new ConsumeTracker(this);
        GoalConfigService goalConfigService = new GoalConfigService(this, consumeTracker);
        goalsService = new GoalsService(this, goalConfigService, goalManager);
        goalsViewerGui = new GoalsViewerGui(this);
        goalsAdminGui = new GoalsAdminGui(this);
        newGameGui = new NewGameGui();
        InvUI.getInstance().setPlugin(this);

        String mainWorldName = Bukkit.getWorlds().get(0).getName();
        BingoWorldService worldService = new BingoWorldService(this);
        if (!worldService.initializeApis()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        long countdownSeconds = getConfig().getLong("preparation-countdown-seconds", 60L);

        roundService = new RoundService(
            this,
            goalManager,
            consumeTracker,
            worldService,
            mainWorldName,
            DEFAULT_GAME_DURATION_SECONDS,
            countdownSeconds,
            this::prefixed
        );
        roundService.initialize();

        commandHandler = new BingoCommandHandler(
            this,
            roundService,
            goalsService,
            goalsViewerGui,
            goalsAdminGui,
            newGameGui,
            this::prefixed
        );

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new GoalEventListener(this, goalManager, consumeTracker), this);
        getServer().getPluginManager().registerEvents(new PreparationEventListener(this), this);

        BingoCommand bingoCommand = new BingoCommand(commandHandler);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
            bingoCommand.register(event.registrar())
        );
        
        getLogger().info("BingoMC has been enabled!");

        if (!goalsService.reloadGoals(true)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        roundService.startTicker();
    }

    @Override
    public void onDisable() {
        if (roundService != null) {
            roundService.onDisable();
        }
        getLogger().info("BingoMC has been disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(prefixed(
            Component.text("Welcome, ", NamedTextColor.GREEN)
                .append(Component.text(player.getName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.GREEN))
        ));
        getLogger().info(player.getName() + " joined the server");

        roundService.onPlayerJoin(player);
    }

    @EventHandler
    public void onTimerExpired(TimerExpiredEvent event) {
        roundService.onTimerExpired(event);
    }

    public GoalManager getGoalManager() {
        return goalManager;
    }

    public long getRoundRemainingSeconds() {
        return roundService.getRoundRemainingSeconds();
    }

    public boolean isGameRunning() {
        return roundService.isGameRunning();
    }

    public boolean isPreparationActive() {
        return roundService.isPreparationActive();
    }

    public boolean isParticipant(UUID playerId) {
        return roundService != null && roundService.isParticipant(playerId);
    }

    public boolean isParticipant(org.bukkit.entity.Player player) {
        return player != null && isParticipant(player.getUniqueId());
    }

    private Component prefixed(Component message) {
        return Component.text()
            .append(Component.text("[Bingo] ", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(message)
            .build();
    }

}