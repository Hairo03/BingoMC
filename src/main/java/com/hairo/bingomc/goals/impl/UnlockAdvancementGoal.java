package com.hairo.bingomc.goals.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import com.hairo.bingomc.goals.core.GoalTrigger;
import com.hairo.bingomc.goals.core.PlayerGoal;
import com.hairo.bingomc.goals.core.RoundAwareGoal;

public class UnlockAdvancementGoal implements PlayerGoal, RoundAwareGoal {
    private static final Set<UUID> RESET_ALL_DONE_THIS_ROUND = new HashSet<>();

    private static final List<NamespacedKey> DEFAULT_BASELINE_ADVANCEMENTS = List.of(
        NamespacedKey.minecraft("story/root"),
        NamespacedKey.minecraft("nether/root"),
        NamespacedKey.minecraft("adventure/root"),
        NamespacedKey.minecraft("end/root"),
        NamespacedKey.minecraft("husbandry/root")
    );

    private final String id;
    private final NamespacedKey advancementKey;
    private final Material icon;

    public UnlockAdvancementGoal(String id, NamespacedKey advancementKey, Material icon) {
        this.id = id;
        this.advancementKey = advancementKey;
        this.icon = icon;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
	public Material icon() {
		return icon;
	}

    @Override
    public Set<GoalTrigger> triggers() {
        return EnumSet.of(GoalTrigger.ADVANCEMENT);
    }

    @Override
    public boolean isComplete(Player player) {
        Advancement advancement = player.getServer().getAdvancement(advancementKey);
        if (advancement == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        return progress.isDone();
    }

    @Override
    public String descriptionText(boolean shortFormat) {
        Advancement advancement = Bukkit.getServer().getAdvancement(advancementKey);
        if (advancement != null && advancement.getDisplay() != null && advancement.getDisplay().title() != null) {
            PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();
            String advancementName = plainSerializer.serialize(advancement.getDisplay().title()).trim();

            if (shortFormat) {
                return "Unlock " + advancementName;
            } 

            return "Unlock the advancement '" + (advancementName.isEmpty() ? advancementKey.getKey() : advancementName) + "'";
        }

        return "Unlock advancement " + advancementKey;
    }

    @Override
    public void onRoundStart(Player player) {
        if (!RESET_ALL_DONE_THIS_ROUND.add(player.getUniqueId())) {
            return;
        }

        resetAllPlayerAdvancements(player);
        awardBaselineAdvancements(player);
    }

    @Override
    public void onRoundReset() {
        RESET_ALL_DONE_THIS_ROUND.clear();
    }

    private void resetAllPlayerAdvancements(Player player) {
        Iterator<Advancement> iterator = player.getServer().advancementIterator();
        while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            revokeAllAwardedCriteria(player, advancement);
        }
    }

    private void awardBaselineAdvancements(Player player) {
        for (NamespacedKey baselineKey : DEFAULT_BASELINE_ADVANCEMENTS) {
            Advancement baselineAdvancement = player.getServer().getAdvancement(baselineKey);
            if (baselineAdvancement == null) {
                continue;
            }

            AdvancementProgress progress = player.getAdvancementProgress(baselineAdvancement);
            Collection<String> remainingCriteria = new ArrayList<>(progress.getRemainingCriteria());
            for (String criterion : remainingCriteria) {
                progress.awardCriteria(criterion);
            }
        }
    }

    private void revokeAllAwardedCriteria(Player player, Advancement advancement) {
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        Collection<String> awardedCriteria = new ArrayList<>(progress.getAwardedCriteria());
        for (String criterion : awardedCriteria) {
            progress.revokeCriteria(criterion);
        }
    }
}
