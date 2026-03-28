package com.hairo.bingomc.gui;

import com.hairo.bingomc.BingoMC;
import com.hairo.bingomc.goals.core.PlayerGoal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

public final class GoalsViewerGui {
    private final BingoMC plugin;

    public GoalsViewerGui(BingoMC plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        List<PlayerGoal> goals = plugin.getGoalManager().getRegisteredGoals();
        UUID playerId = player.getUniqueId();
        Set<String> completed = plugin.getGoalManager().getCompletedGoalIds(playerId);

        Gui gui = Gui.builder()
            .setStructure(
                "a b b b c b b b a",
                "d x x x x x x x d",
                "d x x x x x x x d",
                "d x x x x x x x d",
                "a b b b c b b b a"
            )
            .addIngredient('a', Item.simple(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("<dark_gray>")))
            .addIngredient('b', Item.simple(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName("<gray>")))
            .addIngredient('c', Item.simple(new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).setName("<green><bold>Bingo Goals")))
            .addIngredient('d', Item.simple(new ItemBuilder(Material.GREEN_STAINED_GLASS_PANE).setName("<dark_green>")))
            .build();

        List<Item> goalItems = new ArrayList<>();
        for (PlayerGoal goal : goals) {
            boolean done = completed.contains(goal.id());
            Material icon = done ? Material.EMERALD_BLOCK : Material.YELLOW_CONCRETE;
            int points = plugin.getGoalManager().getGoalPoints(goal.id());

            goalItems.add(Item.simple(new ItemBuilder(icon)
                .setName((done ? "<green><bold>" : "<yellow><bold>") + goal.id())
                .addLoreLines(
                    "<gray>" + goal.completionText(),
                    "<dark_gray>Reward: <aqua><bold>" + points + " pts",
                    done ? "<green><bold>Completed" : "<red><bold>Incomplete"
                )));
        }

        int maxItems = 21;
        for (int i = 0; i < Math.min(goalItems.size(), maxItems); i++) {
            int row = 1 + (i / 7);
            int col = 1 + (i % 7);
            gui.setItem(col, row, goalItems.get(i));
        }

        int currentPoints = plugin.getGoalManager().getPoints(playerId);
        Window window = Window.builder()
            .setViewer(player)
            .setTitle(
                Component.text("Bingo Goals", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(currentPoints + " pts", NamedTextColor.BLUE))
            )
            .setUpperGui(gui)
            .build();
        window.open();
    }
}
