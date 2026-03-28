package com.hairo.bingomc.gui;

import com.hairo.bingomc.BingoMC;
import com.hairo.bingomc.goals.core.PlayerGoal;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

public final class GoalsAdminGui {
    private final BingoMC plugin;

    public GoalsAdminGui(BingoMC plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        List<PlayerGoal> goals = plugin.getGoalManager().getRegisteredGoals();

        Gui gui = Gui.builder()
            .setStructure(
                "# # # # # # # # #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# # # # # # # # #"
            )
            .addIngredient('#', Item.simple(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("<gray>")))
            .build();

        int maxItems = 21;
        for (int i = 0; i < Math.min(goals.size(), maxItems); i++) {
            PlayerGoal goal = goals.get(i);
            int points = plugin.getGoalManager().getGoalPoints(goal.id());
            Item item = Item.simple(new ItemBuilder(Material.BOOK)
                .setName("<gold>" + goal.id())
                .addLoreLines(
                    "<gray>" + goal.completionText(),
                    "<gray>Points: <white>" + points,
                    "<yellow>Editing UI actions are being implemented.",
                    "<yellow>Use /bingo goals validate or /bingo goals reload for now."
                ));

            int row = 1 + (i / 7);
            int col = 1 + (i % 7);
            gui.setItem(col, row, item);
        }

        Window.builder()
            .setViewer(player)
            .setTitle("Bingo Goals Admin")
            .setUpperGui(gui)
            .build()
            .open();
    }
}
