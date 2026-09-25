package com.emerald.haven.quest;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

/**
 * Une quete d'un heros de Haven : qui la donne, dans quel ordre, ce qu'elle paie, sa limite
 * de temps, et comment elle se deroule (lot 3, cahier §86).
 *
 * @param id         identifiant stable (fiche du joueur, langue « quete.emeraldweapons.<id> »)
 * @param giver      le heros qui la donne
 * @param order      son rang chez ce heros : on fait la 0, puis la 1, puis la 2
 * @param invades    une quete de COMBAT : la ville est envahie le temps de la faire (choix du joueur)
 * @param repeatable se refait (le contrat de Torn) : elle paie a chaque fois
 * @param reward     orbes a la premiere reussite (la medaille de bronze aux epreuves de tir)
 * @param timeLimit  tiques pour la reussir, 0 sans limite
 * @param factory    comment elle se deroule ; null : faite par la ville elle-meme (les rues)
 */
public record HavenQuest(String id, HavenHero giver, int order, boolean invades, boolean repeatable, int reward,
                         int timeLimit, @Nullable Factory factory) {

    public interface Factory {
        QuestRun create(HavenQuest quest, ServerLevel level);
    }

    public Component title() {
        return Component.translatable("quete.emeraldweapons." + this.id);
    }

    public Component description() {
        return Component.translatable("quete.emeraldweapons." + this.id + ".desc");
    }

    /** Les epreuves de Tess se jouent a la medaille -- le tir et la course du JET-Board : bronze, argent, or. */
    public boolean medals() {
        return this.giver == HavenHero.TESS;
    }

    /** La course du JET-Board se fait sur la planche : il faut l'avoir achetee. */
    public boolean needsBoard() {
        return "course".equals(this.id);
    }

    /** Ce que paie une medaille : le bronze la recompense, l'argent une fois et demie, l'or le double. */
    public int rewardFor(int medal) {
        return switch (medal) {
            case 2 -> this.reward * 3 / 2;
            case 3 -> this.reward * 2;
            default -> this.reward;
        };
    }
}
