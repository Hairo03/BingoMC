package com.hairo.bingomc.round;

import com.hairo.bingomc.BingoMC;
import com.hairo.bingomc.TestBase;
import com.hairo.bingomc.goals.core.GoalManager;
import com.hairo.bingomc.worlds.BingoWorldService;
import com.hairo.bingomc.worlds.PlayerWorldSet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RoundServiceTest extends TestBase {

    private RoundService roundService;

    @BeforeEach
    public void setUp() throws Exception {
        roundService = newRoundService();
    }

    @Test
    public void testStartRoundFailsWhenNoOnlinePlayers() {
        assertTrue(server.getOnlinePlayers().isEmpty());
        boolean started = roundService.startRound(123L);
        assertFalse(started, "Round should not start when no players are online");
    }

    @Test
    public void testStartRoundEntersPreparation() {
        Player player = addFakePlayer("testplayer");
        assertNotNull(player);

        boolean started = roundService.startRound(456L);
        assertTrue(started, "Round should start when a player is online");
        assertTrue(roundService.isGamePreparing(), "Game should be in preparation state after start");
    }

    @Test
    public void testStopRoundStopsPreparation() {
        Player player = addFakePlayer("testplayer2");
        assertNotNull(player);

        boolean started = roundService.startRound(789L);
        assertTrue(started);
        assertTrue(roundService.isGamePreparing());

        boolean stopped = roundService.stopRound("tester");
        assertTrue(stopped, "stopRound should return true when stopping a preparing game");
        assertFalse(roundService.isGamePreparing(), "Game should no longer be preparing after stop");
        assertFalse(roundService.isGameRunning(), "Game should not be running after stop");
    }

    @Test
    public void primeStartingParticipantsMarksCurrentOnlinePlayers() {
        Player first = addFakePlayer("alpha");
        Player second = addFakePlayer("beta");

        roundService.primeStartingParticipants();

        assertTrue(roundService.isParticipant(first), "First fake player should be marked as a participant");
        assertTrue(roundService.isParticipant(second), "Second fake player should be marked as a participant");
    }

    private RoundService newRoundService() throws Exception {
        Field gmField = BingoMC.class.getDeclaredField("goalManager");
        gmField.setAccessible(true);
        GoalManager goalManager = (GoalManager) gmField.get(plugin);

        BingoWorldService mockWorldService = new BingoWorldService(plugin) {
            @Override
            public Map<UUID, PlayerWorldSet> provisionRoundWorldSets(List<Player> players, long worldSeed) {
                Map<UUID, PlayerWorldSet> map = new HashMap<>();
                for (Player p : players) {
                    PlayerWorldSet set = new PlayerWorldSet(
                        "bingo_overworld_" + p.getUniqueId(),
                        "bingo_nether_" + p.getUniqueId(),
                        "bingo_end_" + p.getUniqueId(),
                        "group_bingo_" + p.getUniqueId(),
                        worldSeed
                    );
                    map.put(p.getUniqueId(), set);
                }
                return map;
            }

            @Override
            public void activateRoundWorldSets(Map<UUID, PlayerWorldSet> createdWorldSets) {
                // No-op for test.
            }

            @Override
            public void cleanupManagedBingoWorldsOnShutdown(String mainWorldName) {
                // No-op for test.
            }
        };

        String mainWorldName = Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().get(0).getName();
        long countdownSeconds = plugin.getConfig().getLong("preparation-countdown-seconds", 60L);
        RoundService service = new RoundService(
            plugin,
            goalManager,
            mockWorldService,
            mainWorldName,
            5L * 60L,
            countdownSeconds,
            component -> component
        );
        service.initialize();
        return service;
    }
}
