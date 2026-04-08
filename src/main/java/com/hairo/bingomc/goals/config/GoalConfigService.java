package com.hairo.bingomc.goals.config;

import com.hairo.bingomc.goals.impl.ReachYLevelGoal;
import com.hairo.bingomc.goals.impl.EnterStructureGoal;
import com.hairo.bingomc.goals.impl.ObtainItemTypeGoal;
import com.hairo.bingomc.goals.impl.ChangeDimensionGoal;
import com.hairo.bingomc.goals.impl.ConsumeItemGoal;
import com.hairo.bingomc.goals.impl.ItemCraftGoal;
import com.hairo.bingomc.goals.impl.KillEntityGoal;
import com.hairo.bingomc.goals.impl.ObtainItemGoal;
import com.hairo.bingomc.goals.impl.UnlockAdvancementGoal;
import com.hairo.bingomc.goals.impl.UseVehicleGoal;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

public final class GoalConfigService {
    private final JavaPlugin plugin;

    @FunctionalInterface
    private interface GoalFactory {
        LoadedGoal create(String id, int points, Map<?, ?> section);
    }

    private static final Map<String, Environment> DIMENSION_MAP = Map.of(
            "minecraft:overworld", Environment.NORMAL,
            "minecraft:the_nether", Environment.NETHER,
            "minecraft:the_end", Environment.THE_END
    );

    private final Map<String, GoalFactory> factories;

    public GoalConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.factories = Map.of(
                "craft_item", (id, points, section) -> new LoadedGoal(new ItemCraftGoal(id, parseMaterial(section, "material"), parseAmount(section, "amount", 1), parseIconMaterial(section, "icon")), points),
                "consume_item", (id, points, section) -> new LoadedGoal(new ConsumeItemGoal(id, parseMaterial(section, "material"), parseAmount(section, "amount", 1), parseIconMaterial(section, "icon")), points),
                "use_vehicle", (id, points, section) -> new LoadedGoal(new UseVehicleGoal(id, parseEntityType(section, "entity_type"), parseIconMaterial(section, "icon")), points),
                "obtain_item", (id, points, section) -> new LoadedGoal(new ObtainItemGoal(id, parseMaterial(section, "material"), parseAmount(section, "amount", 1), parseIconMaterial(section, "icon")), points),
                "kill_entity", (id, points, section) -> new LoadedGoal(new KillEntityGoal(id, parseEntityType(section, "entity_type"), parseAmount(section, "amount", 1), parseIconMaterial(section, "icon")), points),
                "unlock_advancement", (id, points, section) -> new LoadedGoal(new UnlockAdvancementGoal(id, parseAdvancementKey(section, "advancement_key"), parseIconMaterial(section, "icon")), points),
                "change_dimension", (id, points, section) -> new LoadedGoal(new ChangeDimensionGoal(id, parseEnvironment(section, "dimension"), parseIconMaterial(section, "icon")), points),
                "reach_y_level", (id, points, section) -> new LoadedGoal(new ReachYLevelGoal(id, readInt(section, "level", 0), parseDirection(section, "direction"), parseIconMaterial(section, "icon")), points),
                "enter_structure", (id, points, section) -> new LoadedGoal(new EnterStructureGoal(id, parseNamespacedKey(section, "structure"), parseIconMaterial(section, "icon")), points),
                "obtain_item_type", (id, points, section) -> new LoadedGoal(new ObtainItemTypeGoal(id, parseMaterialType(section, "material_type"), parseNamespacedKey(section, "material_type"), parseAmount(section, "amount", 1), parseIconMaterial(section, "icon")), points));
    }

    public GoalLoadResult loadGoals() {
        ensureGoalsFileExists();
        return loadGoalsFrom(new File(plugin.getDataFolder(), "goals.yml"));
    }

    public GoalLoadResult loadGoalsFrom(File file) {
        return loadGoalsFrom(file, "goals");
    }

    public GoalLoadResult loadGoalsFrom(File file, String listKey) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> rawGoals = config.getMapList(listKey);

        if (rawGoals.isEmpty()) {
            return new GoalLoadResult(List.of(), List.of("Missing 'goals' list in " + file.getName()));
        }

        List<LoadedGoal> loaded = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < rawGoals.size(); i++) {
            Map<?, ?> section = rawGoals.get(i);

            String id = readString(section, "id").trim();
            if (id.isEmpty()) {
                errors.add("goals[" + i + "] is missing id.");
                continue;
            }
            if (!ids.add(id)) {
                errors.add("Duplicate goal id: " + id);
                continue;
            }

            boolean enabled = readBoolean(section, "enabled", true);
            if (!enabled) {
                continue;
            }

            int points = Math.max(1, readInt(section, "points", 1));
            String type = readString(section, "type").trim().toLowerCase(Locale.ROOT);

            try {
                LoadedGoal goal = createGoal(id, type, points, section);
                loaded.add(goal);
            } catch (IllegalArgumentException ex) {
                errors.add("goals[" + i + "] (" + id + "): " + ex.getMessage());
            }
        }

        if (loaded.isEmpty() && errors.isEmpty()) {
            errors.add("No enabled goals found in " + file.getName() + ".");
        }

        return new GoalLoadResult(loaded, errors);
    }

    private LoadedGoal createGoal(String id, String type, int points, Map<?, ?> section) {
        GoalFactory factory = factories.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown goal type: " + type);
        }
        return factory.create(id, points, section);
    }

    private String readString(Map<?, ?> section, String key) {
        Object value = section.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private int readInt(Map<?, ?> section, String key, int defaultValue) {
        Object value = section.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean readBoolean(Map<?, ?> section, String key, boolean defaultValue) {
        Object value = section.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private void ensureGoalsFileExists() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File file = new File(plugin.getDataFolder(), "goals.yml");
        if (!file.exists()) {
            plugin.saveResource("goals.yml", false);
        }
    }

    private Material parseMaterial(Map<?, ?> section, String key) {
        String value = readString(section, key).trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing material");
        }
        Material material = Material.matchMaterial(value);
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + value);
        }
        return material;
    }

    private EntityType parseEntityType(Map<?, ?> section, String key) {
        String value = readString(section, key).trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing entity_type");
        }
        if (!value.contains(":")) {
            value = "minecraft:" + value;
        }
        NamespacedKey nsKey = NamespacedKey.fromString(value);
        if (nsKey == null) {
            throw new IllegalArgumentException("Invalid entity type key: " + value);
        }
        EntityType entityType = Registry.ENTITY_TYPE.get(nsKey);
        if (entityType == null) {
            throw new IllegalArgumentException("Unknown entity type: " + value);
        }
        if (!entityType.isSpawnable()) {
            throw new IllegalArgumentException("Entity type is not spawnable: " + value);
        }
        return entityType;
    }

    private int parseAmount(Map<?, ?> section, String key, int min) {
        int amount = readInt(section, key, min);
        if (amount < min) {
            throw new IllegalArgumentException(key + " must be >= " + min);
        }
        return amount;
    }

    private NamespacedKey parseAdvancementKey(Map<?, ?> section, String key) {
        String value = readString(section, key).trim();
        return parseAdvancementKeyValue(value, key);
    }

    private NamespacedKey parseAdvancementKeyValue(String value, String keyLabel) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing " + keyLabel);
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }

        NamespacedKey parsed = NamespacedKey.fromString(normalized);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid advancement key in " + keyLabel + ": " + value);
        }

        if (plugin.getServer().getAdvancement(parsed) == null) {
            throw new IllegalArgumentException("Unknown advancement key in " + keyLabel + ": " + parsed);
        }

        return parsed;
    }

    private Environment parseEnvironment(Map<?, ?> section, String string) {
        String value = readString(section, string).trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing dimension");
        }
        if (!value.contains(":")) {
            value = "minecraft:" + value;
        }
        Environment env = DIMENSION_MAP.get(value);
        if (env == null) {
            throw new IllegalArgumentException("Unknown dimension: " + value + " (expected minecraft:overworld, minecraft:the_nether, or minecraft:the_end)");
        }
        return env;
    }

    private NamespacedKey parseNamespacedKey(Map<?, ?> section, String key) {
        String value = readString(section, key).trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        if (!value.contains(":")) {
            value = "minecraft:" + value;
        }
        NamespacedKey parsed = NamespacedKey.fromString(value);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid namespaced key in " + key + ": " + value);
        }
        return parsed;
    }

    private ReachYLevelGoal.Direction parseDirection(Map<?, ?> section, String key) {
        String value = readString(section, key).trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing direction, expected UP or DOWN");
        }
        try {
            return ReachYLevelGoal.Direction.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown direction: " + value + ", expected UP or DOWN");
        }
    }

    private Tag<Material> parseMaterialType(Map<?, ?> section, String key) {
        NamespacedKey tagKey = parseNamespacedKey(section, key);
        Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, tagKey, Material.class);
        if (tag == null) {
            throw new IllegalArgumentException("Unknown item type tag: " + tagKey);
        }
        return tag;
    }

    private Material parseIconMaterial(Map<?, ?> section, String key) {
        String value = readString(section, key).trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return Material.ORANGE_WOOL; // default icon
        }
        Material material = Material.matchMaterial(value);
        if (material == null) {
            throw new IllegalArgumentException("Unknown icon material: " + value);
        }
        return material;
    }
}
