package com.hairo.bingomc.round;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RoundParticipants {

    private final Set<UUID> participants = new HashSet<>();

    public void clear() {
        participants.clear();
    }

    public void add(UUID playerId) {
        participants.add(playerId);
    }

    public boolean isParticipant(UUID playerId) {
        return participants.contains(playerId);
    }

    public boolean isParticipant(Player player) {
        return player != null && isParticipant(player.getUniqueId());
    }

    public Set<UUID> getParticipants() {
        return Set.copyOf(participants);
    }

    public void applyPreparationState(boolean preparing) {
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                applyPreparationStateToPlayer(p, preparing);
            }
        }
    }

    public void applyPreparationStateToPlayer(Player player, boolean preparing) {
        try {
            player.setInvulnerable(preparing);
            player.setSilent(preparing);
            player.setCanPickupItems(!preparing);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to set preparation state for " + player.getName() + ": " + e.getMessage());
        }
    }
}
