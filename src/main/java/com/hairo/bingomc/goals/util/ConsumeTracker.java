package com.hairo.bingomc.goals.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public class ConsumeTracker {
    private static final String METADATA_KEY = "consumed_items";
    private final JavaPlugin plugin;

    public ConsumeTracker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Material> getConsumedItems(Player player) {
        if (player.getMetadata(METADATA_KEY).isEmpty()) {
            return new ArrayList<>();
        }
        Object value = player.getMetadata(METADATA_KEY).get(0).value();
        return value instanceof List ? (List<Material>) value : new ArrayList<>();
    }

    public void recordConsumedItem(Player player, Material material) {
        List<Material> consumed = getConsumedItems(player);
        consumed.add(material);
        player.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, consumed));
    }
}
