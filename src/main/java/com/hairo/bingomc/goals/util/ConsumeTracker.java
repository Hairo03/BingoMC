package com.hairo.bingomc.goals.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public class ConsumeTracker {
    private static final String METADATA_KEY = "consumed_items";
    private final JavaPlugin plugin;

    public ConsumeTracker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Material> getConsumedItems(Player player) {
        List<MetadataValue> metadataValues = player.getMetadata(METADATA_KEY);
        for (MetadataValue metadataValue : metadataValues) {
            if (metadataValue.getOwningPlugin() != plugin) {
                continue;
            }

            Object value = metadataValue.value();
            if (!(value instanceof List<?> rawList)) {
                return new ArrayList<>();
            }

            List<Material> materials = new ArrayList<>();
            for (Object entry : rawList) {
                if (entry instanceof Material material) {
                    materials.add(material);
                }
            }
            return materials;
        }
        return new ArrayList<>();
    }

    public void recordConsumedItem(Player player, Material material) {
        List<Material> consumed = getConsumedItems(player);
        consumed.add(material);
        player.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, consumed));
    }

    public void clearConsumedItems(Player player) {
        player.removeMetadata(METADATA_KEY, plugin);
    }
}
