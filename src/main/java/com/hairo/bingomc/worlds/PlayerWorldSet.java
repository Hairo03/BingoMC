package com.hairo.bingomc.worlds;

public record PlayerWorldSet(
    String overworldName,
    String netherName,
    String endName,
    String inventoryGroupName,
    long seed
) {
}
