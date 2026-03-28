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
import xyz.xenondevs.invui.window.Window;

public final class NewGameGui {
    private long worldSeed;
    private long timeLimit;

    public long getWorldSeed() {
        return worldSeed;
    }

    public long getTimeLimit() {
        return timeLimit;
    }

    public CompletableFuture<Boolean> open(Player player) {
        worldSeed = 0;
        timeLimit = 0;
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        openMainMenu(player, result);
        return result;
    }

    private void openMainMenu(Player player, CompletableFuture<Boolean> result) {
        BoundItem confirm = BoundItem.builder()
                .setItemProvider(new ItemBuilder(Material.EMERALD)
                        .setName("<green><bold>Start Round")
                        .addLoreLines("<gray>Create a fresh Bingo world", "<gray>with selected settings."))
                .addClickHandler((item, gui, click) -> {
                    player.closeInventory();
                    gui.closeForAllViewers();
                    if (!result.isDone()) {
                        result.complete(true);
                    }
                })
                .build();

        BoundItem cancel = BoundItem.builder()
                .setItemProvider(new ItemBuilder(Material.BARRIER)
                        .setName("<red><bold>Cancel")
                        .addLoreLines("<gray>Abort this round setup."))
                .addClickHandler((item, gui, click) -> {
                    player.closeInventory();
                    gui.closeForAllViewers();
                    if (!result.isDone()) {
                        result.complete(false);
                    }
                })
                .build();

        BoundItem seedInput = BoundItem.builder()
                .setItemProvider(new ItemBuilder(Material.WHEAT_SEEDS)
                        .setName("<aqua>Set world seed")
                        .addLoreLines("<gray>Current: <yellow>" + getSeedDisplay(),
                                "<gray>Click to edit."))
                .addClickHandler((item, gui, click) -> {
                    openSeedInputMenu(player, result, createMainMenuWindow(player, result));
                })
                .build();

        BoundItem timeLimitInput = BoundItem.builder()
                .setItemProvider(new ItemBuilder(Material.CLOCK)
                        .setName("<aqua>Set round time limit")
                        .addLoreLines("<gray>Current: <yellow>" + getTimeLimitDisplay(),
                                "<gray>Click to edit."))
                .addClickHandler((item, gui, click) -> {
                    openTimeLimitInputMenu(player, result, createMainMenuWindow(player, result));
                })
                .build();

        Gui mainGui = Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# n # t # s # y #",
                        "# # # # # # # # #")
                .addIngredient('s', seedInput)
                .addIngredient('t', timeLimitInput)
                .addIngredient('n', cancel)
                .addIngredient('y', confirm)
                .addIngredient('#',
                        Item.simple(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("<dark_gray>")))
                .build();

        createMainMenuWindow(player, result, mainGui).open();
    }

        private Window createMainMenuWindow(Player player, CompletableFuture<Boolean> result) {
        BoundItem confirm = BoundItem.builder()
            .setItemProvider(new ItemBuilder(Material.EMERALD)
                .setName("<green><bold>Start Round")
                .addLoreLines("<gray>Create a fresh Bingo world", "<gray>with selected settings."))
            .addClickHandler((item, gui, click) -> {
                player.closeInventory();
                gui.closeForAllViewers();
                if (!result.isDone()) {
                result.complete(true);
                }
            })
            .build();

        BoundItem cancel = BoundItem.builder()
            .setItemProvider(new ItemBuilder(Material.BARRIER)
                .setName("<red><bold>Cancel")
                .addLoreLines("<gray>Abort this round setup."))
            .addClickHandler((item, gui, click) -> {
                player.closeInventory();
                gui.closeForAllViewers();
                if (!result.isDone()) {
                result.complete(false);
                }
            })
            .build();

        BoundItem seedInput = BoundItem.builder()
            .setItemProvider(new ItemBuilder(Material.WHEAT_SEEDS)
                .setName("<aqua>Set world seed")
                .addLoreLines("<gray>Current: <yellow>" + getSeedDisplay(),
                    "<gray>Click to edit in anvil."))
            .addClickHandler((item, gui, click) -> {
                openSeedInputMenu(player, result, createMainMenuWindow(player, result));
            })
            .build();

        BoundItem timeLimitInput = BoundItem.builder()
            .setItemProvider(new ItemBuilder(Material.CLOCK)
                .setName("<aqua>Set round time limit")
                .addLoreLines("<gray>Current: <yellow>" + getTimeLimitDisplay(),
                    "<gray>Click to edit in anvil."))
            .addClickHandler((item, gui, click) -> {
                openTimeLimitInputMenu(player, result, createMainMenuWindow(player, result));
            })
            .build();

        Gui mainGui = Gui.builder()
            .setStructure(
                "# # # # # # # # #",
                "# # # t # s # # #",
                "# # # n # y # # #",
                "# # # # # # # # #")
            .addIngredient('s', seedInput)
            .addIngredient('t', timeLimitInput)
            .addIngredient('n', cancel)
            .addIngredient('y', confirm)
            .addIngredient('#',
                Item.simple(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("<dark_gray>")))
            .build();

        return createMainMenuWindow(player, result, mainGui);
        }

        private Window createMainMenuWindow(Player player, CompletableFuture<Boolean> result, Gui mainGui) {
        return Window.builder()
            .setViewer(player)
            .setTitle(Component.text("Start New Bingo Round", NamedTextColor.BLACK))
            .setUpperGui(mainGui)
            .build();
        }

        private void openSeedInputMenu(Player player, CompletableFuture<Boolean> result, Window fallbackWindow) {
        final long[] pendingSeed = new long[] { worldSeed };

        BoundItem confirmSeed = BoundItem.builder()
                .setItemProvider(new ItemBuilder(Material.LIME_CONCRETE)
                        .setName("<green><bold>Confirm Seed")
                        .addLoreLines("<gray>Save the entered seed.", "<gray>Current staged value will apply."))
                .addClickHandler((item, gui, click) -> {
                    worldSeed = pendingSeed[0];
                    openMainMenu(player, result);
                })
                .build();

        BoundItem cancelSeed = BoundItem.builder()
                .setItemProvider(new ItemBuilder(Material.RED_CONCRETE)
                        .setName("<red><bold>Cancel Seed")
                        .addLoreLines("<gray>Discard entered seed", "<gray>and return to main menu."))
                .addClickHandler((item, gui, click) -> {
                    openMainMenu(player, result);
                })
                .build();

        Gui lowerGui = Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # # n # y # # #",
                        "# # # # # # # # #",
                        "# # # # # # # # #")
                .addIngredient('#',
                        Item.simple(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("<dark_gray>")))
                .addIngredient('n', cancelSeed)
                .addIngredient('y', confirmSeed)
                .build();

        AnvilWindow.builder()
                .setViewer(player)
                .setLowerGui(lowerGui)
                .setFallbackWindow(fallbackWindow)
                .setTitle(Component.text("Set Seed", NamedTextColor.BLACK, TextDecoration.BOLD))
                .addRenameHandler(seed -> pendingSeed[0] = parseSeed(seed))
                .open(player);
    }

    private void openTimeLimitInputMenu(Player player, CompletableFuture<Boolean> result, Window fallbackWindow) {
        final long[] pendingTimeLimit = new long[] { timeLimit };
        final boolean[] hasValidInput = new boolean[] { true };

        BoundItem confirmTime = BoundItem.builder()
                .setItemProvider(new ItemBuilder(Material.LIME_CONCRETE)
                        .setName("<green><bold>Confirm Time")
                        .addLoreLines("<gray>Save the entered time.", "<gray>Value is in minutes."))
                .addClickHandler((item, gui, click) -> {
                    if (hasValidInput[0]) {
                        timeLimit = pendingTimeLimit[0];
                    }
                    openMainMenu(player, result);
                })
                .build();

        BoundItem cancelTime = BoundItem.builder()
                .setItemProvider(new ItemBuilder(Material.RED_CONCRETE)
                        .setName("<red><bold>Cancel Time")
                        .addLoreLines("<gray>Discard entered time", "<gray>and return to main menu."))
                .addClickHandler((item, gui, click) -> {
                    openMainMenu(player, result);
                })
                .build();

        Gui lowerGui = Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # # n # y # # #",
                        "# # # # # # # # #",
                        "# # # # # # # # #")
                .addIngredient('#',
                        Item.simple(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("<dark_gray>")))
                .addIngredient('n', cancelTime)
                .addIngredient('y', confirmTime)
                .build();

        AnvilWindow.builder()
                .setViewer(player)
                .setLowerGui(lowerGui)
            .setFallbackWindow(fallbackWindow)
                .setTitle(Component.text("Set Time (min)", NamedTextColor.BLACK, TextDecoration.BOLD))
                .addRenameHandler(input -> {
                    Long parsed = parseTimeLimitSeconds(input);
                    if (parsed == null) {
                        hasValidInput[0] = false;
                        return;
                    }

                    hasValidInput[0] = true;
                    pendingTimeLimit[0] = parsed;
                })
                .open(player);
    }

    private String getSeedDisplay() {
        return worldSeed == 0 ? "random" : Long.toString(worldSeed);
    }

    private String getTimeLimitDisplay() {
        return timeLimit <= 0 ? "unlimited" : (timeLimit / 60) + " min";
    }

    private Long parseTimeLimitSeconds(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        try {
            int minutes = Integer.parseInt(input.trim());
            if (minutes <= 0) {
                return null;
            }

            return (long) minutes * 60L;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long parseSeed(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            return input.toLowerCase(Locale.ROOT).hashCode();
        }
    }
}
