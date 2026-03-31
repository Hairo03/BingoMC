package com.hairo.bingomc.round;

import com.hairo.bingomc.BingoMC;
import com.hairo.bingomc.worlds.BingoWorldService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

@Disabled
public class RealMultiverseTest {
    private ServerMock server;
    private BingoMC plugin;
    private RoundService roundService;
    private BingoWorldService worldService;

    @BeforeEach
    public void setUp() throws Exception {
        server = MockBukkit.mock();
        // Load required Multiverse plugins for real integration
        try {
            Class<?> coreCls = Class.forName("com.onarandombox.MultiverseCore");
            MockBukkit.load((Class<? extends org.bukkit.plugin.java.JavaPlugin>) coreCls);
        } catch (ClassNotFoundException ignored) {}
        try {
            Class<?> portalsCls = Class.forName("com.onarandombox.MultiverseNetherPortals");
            MockBukkit.load((Class<? extends org.bukkit.plugin.java.JavaPlugin>) portalsCls);
        } catch (ClassNotFoundException ignored) {}
        try {
            Class<?> invCls = Class.forName("com.onarandombox.MultiverseInventories");
            MockBukkit.load((Class<? extends org.bukkit.plugin.java.JavaPlugin>) invCls);
        } catch (ClassNotFoundException ignored) {}
        // Main world for plugin init
        server.addSimpleWorld("world");
        plugin = MockBukkit.load(BingoMC.class);
        Field f = BingoMC.class.getDeclaredField("roundService");
        f.setAccessible(true);
        roundService = (RoundService) f.get(plugin);
        Field wsField = RoundService.class.getDeclaredField("worldService");
        wsField.setAccessible(true);
        worldService = (BingoWorldService) wsField.get(roundService);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void testMultiversePluginsLoaded() throws Exception {
        // Verify Multiverse plugins are present via the world service's internal fields
        Field coreField = BingoWorldService.class.getDeclaredField("multiverseCoreApi");
        coreField.setAccessible(true);
        assertNotNull(coreField.get(worldService), "MultiverseCoreApi should be initialized");
        Field portalsField = BingoWorldService.class.getDeclaredField("multiverseNetherPortals");
        portalsField.setAccessible(true);
        assertNotNull(portalsField.get(worldService), "MultiverseNetherPortals should be initialized");
    }

    @Test
    public void testStartRoundWithRealMultiverse() {
        // Add a player to allow round start
        server.addSimpleWorld("test");
        server.addPlayer();
        boolean started = roundService.startRound(1L);
        assertTrue(started, "Round should start with a player present");
        assertTrue(roundService.isGamePreparing(), "Game should be in preparation state");
    }
}
