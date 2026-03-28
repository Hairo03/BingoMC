package com.hairo.bingomc.gui;

import java.util.concurrent.CompletableFuture;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.BoundItem;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.AnvilWindow;

public final class NewGameGui {
    private long worldSeed = 0;

    public long getWorldSeed() {
        return worldSeed;
    }

    public CompletableFuture<Boolean> open(Player player) {
        worldSeed = 0;
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        BoundItem confirm = BoundItem.builder()
            .setItemProvider(new ItemBuilder(Material.EMERALD)
            .setName("<green><bold>Start Round")
            .addLoreLines("<gray>Create a fresh Bingo world", "<gray>with the entered seed."))
                .addClickHandler((item, gui, click) -> {
                    player.closeInventory();
                    gui.closeForAllViewers();
                    result.complete(true);
                })
                .build();

        BoundItem cancel = BoundItem.builder()
            .setItemProvider(new ItemBuilder(Material.BARRIER)
                .setName("<red><bold>Cancel")
                .addLoreLines("<gray>Abort this round setup."))
                .addClickHandler((item, gui, click) -> {
                    player.closeInventory();
                    gui.closeForAllViewers();
                    result.complete(false);
                })
                .build();

        Gui gui = Gui.builder()
                .setStructure(
            "# # # # # # # # #",
            "# # # n # y # # #",
            "# # # # # # # # #",
            "# # # # # # # # #")
            .addIngredient('#', Item.simple(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("<dark_gray>")))
                .addIngredient('n', cancel)
                .addIngredient('y', confirm)
                .build();

        AnvilWindow.builder()
                .setViewer(player)
                .setLowerGui(gui)
            .setTitle(
                Component.text("Enter world seed:", NamedTextColor.BLACK, TextDecoration.BOLD))
                .addRenameHandler(seed -> worldSeed = parseSeed(seed))
                .open(player);
        return result;
    }

    private long parseSeed(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            return input.toLowerCase(Locale.ROOT).hashCode();
        }
    }
}
