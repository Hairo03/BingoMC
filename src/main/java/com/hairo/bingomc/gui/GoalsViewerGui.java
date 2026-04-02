package com.hairo.bingomc.gui;

import com.hairo.bingomc.BingoMC;
import com.hairo.bingomc.goals.core.AmountBasedGoal;
import com.hairo.bingomc.goals.core.PlayerGoal;

import io.papermc.paper.datacomponent.DataComponentTypes;

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
    private final GoalsSidebarManager sidebarManager;

    public GoalsViewerGui(BingoMC plugin, GoalsSidebarManager sidebarManager) {
        this.plugin = plugin;
        this.sidebarManager = sidebarManager;
    }

    public void open(Player player) {
        openInternal(player, false);
    }

    public void openSummary(Player player) {
        openInternal(player, true);
    }

    private void openInternal(Player player, boolean isSummary) {
        List<PlayerGoal> goals = plugin.getGoalManager().getRegisteredGoals();
        UUID playerId = player.getUniqueId();
        Set<String> completed = plugin.getGoalManager().getCompletedGoalIds(playerId);
        boolean gameActive = plugin.isGameRunning() || plugin.isPreparationActive();

        Gui gui = Gui.builder()
            .setStructure(
                "# # # # # # # # #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# # # # # # # # #"
            )
            .addIngredient('#', Item.simple(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("<dark_gray>")))
            .build();

        List<Item> goalItems = new ArrayList<>();
        for (PlayerGoal goal : goals) {
            boolean done = completed.contains(goal.id());
            Material icon = done ? Material.EMERALD_BLOCK : goal.icon();
            int points = plugin.getGoalManager().getGoalPoints(goal.id());
            boolean showPinOption = gameActive && !isSummary && !done;

            if (showPinOption) {
                final PlayerGoal finalGoal = goal;
                final AmountBasedGoal amountGoal = goal instanceof AmountBasedGoal a ? a : null;
                final Material finalIcon = icon;

                goalItems.add(Item.builder()
                    .setItemProvider(uuid -> {
                        boolean pinned = sidebarManager.isPinned(playerId, finalGoal.id());
                        List<String> lore = new ArrayList<>();
                        if (amountGoal != null) {
                            int current = amountGoal.currentProgress(player);
                            lore.add("<yellow>Progress: <white>" + current + "<dark_gray>/<white>" + amountGoal.amount());
                        }
                        lore.add("<dark_gray>Reward: <aqua><bold>" + points + " pts");
                        lore.add("<red><bold>Incomplete");
                        lore.add(pinned ? "<gold>\uD83D\uDCCC Pinned to sidebar" : "<gray>Click to pin to sidebar");

                        int stackAmount = amountGoal != null
                            ? Math.max(1, Math.min(amountGoal.currentProgress(player), 64))
                            : 1;

                        return new ItemBuilder(finalIcon)
                            .setName("<yellow>" + finalGoal.descriptionText())
                            .hideTooltip(DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.TOOL)
                            .addLoreLines(lore.toArray(new String[0]))
                            .setAmount(stackAmount);
                    })
                    .addClickHandler((item, click) -> {
                        sidebarManager.togglePin(player, finalGoal.id());
                        for (Item goalItem : goalItems) {
                            goalItem.notifyWindows();
                        }
                    })
                    .build());
            } else {
                List<String> lore = new ArrayList<>();
                if (!done && goal instanceof AmountBasedGoal amountGoal) {
                    int current = amountGoal.currentProgress(player);
                    lore.add("<yellow>Progress: <white>" + current + "<dark_gray>/<white>" + amountGoal.amount());
                }
                lore.add("<dark_gray>Reward: <aqua><bold>" + points + " pts");
                lore.add(done ? "<green><bold>Completed" : "<red><bold>Incomplete");

                int stackAmount;
                if (!done && goal instanceof AmountBasedGoal amountGoal) {
                    stackAmount = Math.max(1, Math.min(amountGoal.currentProgress(player), 64));
                } else {
                    stackAmount = 1;
                }

                goalItems.add(Item.simple(new ItemBuilder(icon)
                    .setName((done ? "<green>" : "<yellow>") + goal.descriptionText())
                    .hideTooltip(DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.TOOL)
                    .addLoreLines(lore.toArray(new String[0]))
                    .setAmount(stackAmount)));
            }
        }

        int maxItems = 28;
        for (int i = 0; i < Math.min(goalItems.size(), maxItems); i++) {
            int row = 1 + (i / 7);
            int col = 1 + (i % 7);
            gui.setItem(col, row, goalItems.get(i));
        }

        int currentPoints = plugin.getGoalManager().getPoints(playerId);

        Component title;
        if (isSummary) {
            title = Component.text("Round Summary", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" \u2014 ", NamedTextColor.DARK_GRAY))
                .append(Component.text(currentPoints + " pts", NamedTextColor.DARK_GREEN));
        } else {
            title = Component.text("Bingo Goals", NamedTextColor.BLACK)
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(currentPoints + " pts", NamedTextColor.DARK_GREEN));
        }

        Window.builder()
            .setViewer(player)
            .setTitle(title)
            .setUpperGui(gui)
            .build()
            .open();
    }
}
