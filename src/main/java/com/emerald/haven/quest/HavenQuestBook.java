package com.emerald.haven.quest;

import com.emerald.haven.quest.runs.BrutesRun;
import com.emerald.haven.quest.runs.ChestsRun;
import com.emerald.haven.quest.runs.EcosRun;
import com.emerald.haven.quest.runs.EliteRun;
import com.emerald.haven.quest.runs.FishingRun;
import com.emerald.haven.quest.runs.GullsRun;
import com.emerald.haven.quest.runs.HoldRun;
import com.emerald.haven.quest.runs.HuntRun;
import com.emerald.haven.quest.runs.JetRaceRun;
import com.emerald.haven.quest.runs.MarketRun;
import com.emerald.haven.quest.runs.PatrolRun;
import com.emerald.haven.quest.runs.RangeRun;
import com.emerald.haven.quest.runs.RecklessRun;
import com.emerald.haven.quest.runs.RingsRun;
import com.emerald.haven.quest.runs.TaxiRun;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * LE LIVRE DES QUETES DE HAVEN (lot 3, cahier §86) : les dix-huit quetes des six heros,
 * dans l'ordre ou chacun les donne, ce qu'elles paient et leur limite de temps -- et la
 * course du JET-Board (cahier §100), quatrieme de Tess, facultative et rejouable.
 *
 * Les prix suivent le §85.1 : trente a quatre-vingts orbes, l'or double aux epreuves de tir.
 * Les quetes de Torn, Sig et Samos sont celles du COMBAT : elles envahissent la ville (choix
 * du joueur) ; la conduite, le tir et l'eau se font en ville paisible.
 */
public final class HavenQuestBook {

    private static final int MINUTE = 20 * 60;

    public static final List<HavenQuest> ALL = List.of(
            // Torn, au comptoir du QG : la ville
            new HavenQuest("rues", HavenHero.TORN, 0, true, false, 40, 0, null),
            new HavenQuest("patrouille", HavenHero.TORN, 1, true, false, 60, 10 * MINUTE, PatrolRun::new),
            new HavenQuest("port", HavenHero.TORN, 2, true, true, 25, 0, HoldRun::port),
            // Sig, sur la place du bras ouest : le combat
            new HavenQuest("chasse", HavenHero.SIG, 0, true, false, 40, 0, HuntRun::new),
            new HavenQuest("brutes", HavenHero.SIG, 1, true, false, 60, 6 * MINUTE, BrutesRun::new),
            new HavenQuest("marche", HavenHero.SIG, 2, true, false, 50, 5 * MINUTE, MarketRun::new),
            // Keira, dans la cour du bras central : la conduite
            new HavenQuest("anneaux", HavenHero.KEIRA, 0, false, false, 50, 0, RingsRun::new),
            new HavenQuest("taxi", HavenHero.KEIRA, 1, false, false, 40, 10 * MINUTE, TaxiRun::new),
            new HavenQuest("chauffard", HavenHero.KEIRA, 2, false, false, 50, 3 * MINUTE, RecklessRun::new),
            // Tess, a la salle des armes : le tir (le bronze paie la recompense, l'or le double)
            new HavenQuest("tir1", HavenHero.TESS, 0, false, false, 30, 5 * MINUTE, RangeRun::fixed),
            new HavenQuest("tir2", HavenHero.TESS, 1, false, false, 30, 5 * MINUTE, RangeRun::moving),
            new HavenQuest("tir3", HavenHero.TESS, 2, false, false, 30, 5 * MINUTE, RangeRun::far),
            new HavenQuest("course", HavenHero.TESS, 3, false, true, 40, 0, JetRaceRun::new),
            // Samos, sur la terrasse de la tour ouest : l'eco
            new HavenQuest("ecos", HavenHero.SAMOS, 0, true, false, 60, 8 * MINUTE, EcosRun::new),
            new HavenQuest("plateforme", HavenHero.SAMOS, 1, true, false, 60, 0, HoldRun::platform),
            new HavenQuest("elite", HavenHero.SAMOS, 2, true, false, 80, 0, EliteRun::new),
            // le Pecheur, sur son bateau : l'eau
            new HavenQuest("peche", HavenHero.PECHEUR, 0, false, false, 50, 0, FishingRun::new),
            new HavenQuest("coffres", HavenHero.PECHEUR, 1, false, false, 50, 10 * MINUTE, ChestsRun::new),
            new HavenQuest("mouettes", HavenHero.PECHEUR, 2, false, false, 40, 0, GullsRun::new));

    private HavenQuestBook() {
    }

    /** Les quetes d'un heros, dans l'ordre. */
    public static List<HavenQuest> of(HavenHero hero) {
        List<HavenQuest> out = new ArrayList<>();
        for (HavenQuest quest : ALL) {
            if (quest.giver() == hero) {
                out.add(quest);
            }
        }
        out.sort(Comparator.comparingInt(HavenQuest::order));
        return out;
    }

    /** La quete a faire avant celle-ci chez le meme heros, ou null pour la premiere. */
    @Nullable
    public static HavenQuest before(HavenQuest quest) {
        for (HavenQuest other : ALL) {
            if (other.giver() == quest.giver() && other.order() == quest.order() - 1) {
                return other;
            }
        }
        return null;
    }

    @Nullable
    public static HavenQuest byId(String id) {
        for (HavenQuest quest : ALL) {
            if (quest.id().equals(id)) {
                return quest;
            }
        }
        return null;
    }
}
