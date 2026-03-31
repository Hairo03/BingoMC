package com.hairo.bingomc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import org.bukkit.entity.Player;

public abstract class TestBase {

    protected ServerMock server;
    protected BingoMC plugin;

    @BeforeEach
    public void setUpBase() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BingoMC.class);
    }

    @AfterEach
    public void tearDownBase() {
        MockBukkit.unmock();
    }

    protected Player addFakePlayer(String name) {
        try {
            java.util.UUID uuid = java.util.UUID.randomUUID();
            Class<?> playerIface = org.bukkit.entity.Player.class;
            Class<?> offlineIface = org.bukkit.OfflinePlayer.class;
            Object proxy = Proxy.newProxyInstance(playerIface.getClassLoader(), new Class[]{playerIface, offlineIface}, (p, m, a) -> {
                String mn = m.getName();
                if (mn.equals("getUniqueId")) return uuid;
                if (mn.equals("getName")) return name;
                if (mn.equals("isOnline")) return true;
                if (mn.equals("getLocation")) {
                    if (org.bukkit.Bukkit.getWorlds().isEmpty()) return null;
                    return org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
                }
                Class<?> rt = m.getReturnType();
                if (rt == boolean.class) return false;
                if (rt == int.class) return 0;
                if (rt == long.class) return 0L;
                if (rt == double.class) return 0.0;
                return null;
            });

            // Add proxy to the server's player list internals
            Field playerListField = server.getClass().getDeclaredField("playerList");
            playerListField.setAccessible(true);
            Object playerList = playerListField.get(server);

            Field onlinePlayersField = playerList.getClass().getDeclaredField("onlinePlayers");
            onlinePlayersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<Object> onlineSet = (java.util.Set<Object>) onlinePlayersField.get(playerList);
            onlineSet.add(proxy);

            Field offlinePlayersField = playerList.getClass().getDeclaredField("offlinePlayers");
            offlinePlayersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<Object> offlineSet = (java.util.Set<Object>) offlinePlayersField.get(playerList);
            offlineSet.add(proxy);

            return (Player) proxy;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
