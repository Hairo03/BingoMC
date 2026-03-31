package com.hairo.bingomc.round;

import com.hairo.bingomc.BingoMC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.entity.Player;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;


public class RoundServiceTest extends com.hairo.bingomc.TestBase {

    private RoundService roundService;

    @BeforeEach
    public void setUp() throws Exception {
        // TestBase sets up `server` and `plugin` and performs MockBukkit bootstrapping
        // Create a mock world service as an anonymous subclass to avoid test discovery loading
        // a top-level helper that pulls in heavy runtime dependencies.
        com.hairo.bingomc.worlds.BingoWorldService mockWorldService = new com.hairo.bingomc.worlds.BingoWorldService(plugin) {
            @Override
            public Map<UUID, com.hairo.bingomc.worlds.PlayerWorldSet> provisionRoundWorldSets(List<Player> players, long worldSeed) {
                Map<UUID, com.hairo.bingomc.worlds.PlayerWorldSet> map = new HashMap<>();
                for (Player p : players) {
                    com.hairo.bingomc.worlds.PlayerWorldSet set = new com.hairo.bingomc.worlds.PlayerWorldSet(
                            "bingo_overworld_" + p.getUniqueId(),
                            "bingo_nether_" + p.getUniqueId(),
                            "bingo_end_" + p.getUniqueId(),
                            "group_bingo_" + p.getUniqueId(),
                            worldSeed);
                    map.put(p.getUniqueId(), set);
                }
                return map;
            }

            @Override
            public void activateRoundWorldSets(Map<UUID, com.hairo.bingomc.worlds.PlayerWorldSet> createdWorldSets) {
                // No-op for test.
            }

            @Override
            public void cleanupManagedBingoWorldsOnShutdown(String mainWorldName) {
                // No-op for test.
            }
        };
        // Obtain GoalManager from the plugin via reflection
        java.lang.reflect.Field gmField = BingoMC.class.getDeclaredField("goalManager");
        gmField.setAccessible(true);
        com.hairo.bingomc.goals.core.GoalManager goalManager = (com.hairo.bingomc.goals.core.GoalManager) gmField.get(plugin);
        // Initialize RoundService with the mock world service
        String mainWorldName = org.bukkit.Bukkit.getWorlds().isEmpty() ? "world" : org.bukkit.Bukkit.getWorlds().get(0).getName();
        long countdownSeconds = plugin.getConfig().getLong("preparation-countdown-seconds", 60L);
        roundService = new RoundService(
                plugin,
                goalManager,
                mockWorldService,
                mainWorldName,
                5L * 60L,
                countdownSeconds,
                component -> component
        );
        roundService.initialize();
    }

    @Test
    public void testStartRoundFailsWhenNoOnlinePlayers() {
        // Ensure no players are online
        assertTrue(server.getOnlinePlayers().isEmpty());
        boolean started = roundService.startRound(123L);
        assertFalse(started, "Round should not start when no players are online");
    }

    @Test
    public void testStartRoundEntersPreparation() {
        // Add a mock player
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
}
