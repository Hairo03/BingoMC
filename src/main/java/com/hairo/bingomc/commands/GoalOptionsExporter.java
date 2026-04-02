package com.hairo.bingomc.commands;

import com.hairo.bingomc.goals.config.GoalOptionCatalog;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes a columnar CSV file listing all valid values for every goal parameter type.
 * Uses {@link GoalOptionCatalog} to obtain the sorted value lists, keeping the export
 * logic decoupled from the data-gathering logic.
 *
 * <p>CSV layout:
 * <pre>
 *   material,entity_type,dimension,material_type,structure,advancement_key
 *   ACACIA_BOAT,ALLAY,NETHER,banners,minecraft:ancient_city,minecraft:adventure/sleep_in_bed
 *   ACACIA_BUTTON,ARMADILLO,NORMAL,beds,minecraft:bastion_remnant,...
 *   ...
 * </pre>
 * Columns with fewer values than the longest column are padded with empty cells.
 */
public final class GoalOptionsExporter {

    private final JavaPlugin plugin;
    private final GoalOptionCatalog catalog;

    public GoalOptionsExporter(JavaPlugin plugin, GoalOptionCatalog catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
    }

    /**
     * Generates (or overwrites) {@code <data-folder>/goal-options.csv}.
     *
     * @return the absolute path of the written file
     * @throws IOException if the file cannot be written
     */
    public Path export() throws IOException {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        Path outputPath = plugin.getDataFolder().toPath().resolve("goal-options.csv");

        List<String> types = catalog.getAllTypes();
        List<List<String>> columns = types.stream()
                .map(catalog::getOptions)
                .toList();

        int maxRows = columns.stream().mapToInt(List::size).max().orElse(0);

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8))) {
            // Header row
            writer.println(String.join(",", types));

            // Data rows — shorter columns produce empty cells
            for (int row = 0; row < maxRows; row++) {
                StringBuilder sb = new StringBuilder();
                for (int col = 0; col < columns.size(); col++) {
                    if (col > 0) sb.append(',');
                    List<String> column = columns.get(col);
                    if (row < column.size()) {
                        sb.append(column.get(row));
                    }
                }
                writer.println(sb);
            }
        }

        return outputPath;
    }
}
