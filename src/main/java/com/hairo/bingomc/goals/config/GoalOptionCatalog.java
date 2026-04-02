package com.hairo.bingomc.goals.config;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.structure.Structure;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lazily builds and caches sorted option lists for each goal parameter type.
 * Reusable by both the CSV exporter and the future in-game editor/autocomplete.
 */
public final class GoalOptionCatalog {

    public static final String TYPE_MATERIAL = "material";
    public static final String TYPE_ENTITY_TYPE = "entity_type";
    public static final String TYPE_DIMENSION = "dimension";
    public static final String TYPE_MATERIAL_TYPE = "material_type";
    public static final String TYPE_STRUCTURE = "structure";
    public static final String TYPE_ADVANCEMENT_KEY = "advancement_key";

    private static final List<String> ALL_TYPES = List.of(
            TYPE_MATERIAL,
            TYPE_ENTITY_TYPE,
            TYPE_DIMENSION,
            TYPE_MATERIAL_TYPE,
            TYPE_STRUCTURE,
            TYPE_ADVANCEMENT_KEY
    );

    private volatile Map<String, List<String>> cache;

    /**
     * Returns a sorted, unmodifiable list of valid values for the given type.
     * Calling this for the first time triggers a full build of all type lists.
     */
    public List<String> getOptions(String type) {
        if (cache == null) buildAll();
        List<String> result = cache.get(type);
        return result != null ? result : List.of();
    }

    /** Returns the canonical ordering of all supported types (column order in the CSV). */
    public List<String> getAllTypes() {
        return ALL_TYPES;
    }

    /** Invalidates the cache so it will be rebuilt on the next {@link #getOptions} call. */
    public void invalidate() {
        cache = null;
    }

    // ------------------------------------------------------------------
    // Internal build
    // ------------------------------------------------------------------

    private synchronized void buildAll() {
        if (cache != null) return; // double-checked locking
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put(TYPE_MATERIAL, buildMaterials());
        map.put(TYPE_ENTITY_TYPE, buildEntityTypes());
        map.put(TYPE_DIMENSION, buildDimensions());
        map.put(TYPE_MATERIAL_TYPE, buildMaterialTypes());
        map.put(TYPE_STRUCTURE, buildStructures());
        map.put(TYPE_ADVANCEMENT_KEY, buildAdvancementKeys());
        cache = map;
    }

    private List<String> buildMaterials() {
        List<String> list = new ArrayList<>();
        for (Material m : Material.values()) {
            if (!m.isLegacy() && m.isItem()) {
                list.add(m.getKey().toString());
            }
        }
        Collections.sort(list);
        return List.copyOf(list);
    }

    private List<String> buildEntityTypes() {
        List<String> list = new ArrayList<>();
        for (EntityType t : EntityType.values()) {
            if (t.isSpawnable()) {
                list.add(t.getKey().toString());
            }
        }
        Collections.sort(list);
        return List.copyOf(list);
    }

    private List<String> buildDimensions() {
        return List.of("minecraft:overworld", "minecraft:the_end", "minecraft:the_nether");
    }

    /** Reflects public static Tag fields in {@code org.bukkit.Tag}, keeping only those
     *  registered under {@code Tag.REGISTRY_ITEMS} (i.e. valid as material_type). */
    private List<String> buildMaterialTypes() {
        List<String> list = new ArrayList<>();
        for (Field field : Tag.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!Tag.class.isAssignableFrom(field.getType())) continue;
            try {
                Tag<?> tag = (Tag<?>) field.get(null);
                NamespacedKey key = tag.getKey();
                if (Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class) != null) {
                    list.add(key.toString());
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        Collections.sort(list);
        return List.copyOf(list);
    }

    private List<String> buildStructures() {
        List<String> list = new ArrayList<>();
        Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        for (Structure structure : registry) {
            // Full namespaced key (e.g. "minecraft:village_plains")
            // GoalConfigService.parseNamespacedKey accepts both "village_plains" and "minecraft:village_plains"
            NamespacedKey key = registry.getKey(structure);
            if (key != null) {
                list.add(key.toString());
            }
        }
        Collections.sort(list);
        return List.copyOf(list);
    }

    private List<String> buildAdvancementKeys() {
        List<String> list = new ArrayList<>();
        Iterator<Advancement> iter = Bukkit.getServer().advancementIterator();
        while (iter.hasNext()) {
            list.add(iter.next().getKey().toString()); // e.g. "minecraft:adventure/sleep_in_bed"
        }
        Collections.sort(list);
        return List.copyOf(list);
    }
}
