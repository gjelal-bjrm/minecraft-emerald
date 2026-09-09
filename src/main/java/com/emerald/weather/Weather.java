package com.emerald.weather;

import com.emerald.game.GamePhase;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Les meteos du Mode Arcencium.
 *
 * Deux familles : les douces habillent le monde et ouvrent une opportunite
 * (voir de nuit, entendre les filons) ; les agressives font mal mais paient --
 * c'est le principe arrete au cahier, « une fenetre d'opportunite qui fait
 * mal ». EMBELLIE n'est pas une meteo qu'on tire : c'est l'accalmie qui suit
 * chaque tempete agressive.
 */
public enum Weather {
    CLEAR("clear", false, null, 0, 0, 0x9AA0A6),
    EMBELLIE("embellie", false, null, 60 * 20, 90 * 20, 0xC0E8FF),
    /**
     * LA BATTUE -- ce qui fut la Brume Prismatique.
     *
     * Elle encourage LE COMBAT, comme l'Aurore encourage la mine : c'est le
     * partage voulu des deux meteos douces du debut de partie.
     *
     * La Brume ne tenait pas. Le brouillard etait mal rendu -- Distant Horizons
     * coupe le brouillard vanilla (`enableVanillaFog = false`) et dessine le
     * sien sur quatre mille blocs, si bien qu'on voyait le terrain lointain A
     * TRAVERS la brume, sous un ciel reste bleu -- et surtout elle ne DONNAIT
     * rien : son seul effet, une portee de detection reduite chez l'ennemi, ne
     * se voit pas. « C'est juste chiant », et c'etait exact.
     *
     * Plus un gramme de brouillard. Le prisme cesse de separer la lumiere : le
     * monde perd ses couleurs, en vrai noir et blanc de pellicule. On y voit
     * aussi loin qu'avant -- mieux, meme, puisque plus rien ne bouche
     * l'horizon -- et ce qui vit se detoure a travers les murs. C'est la
     * FENETRE DE CHASSE, comme l'Aurore est la fenetre de mine.
     *
     * CINQ MINUTES, comme toutes les autres. On avait ecrit « deux minutes, pas
     * une de plus : le noir et blanc est un effet fort, il ne doit pas
     * s'installer ». C'etait raisonner sur l'effet et non sur ce qu'on fait
     * pendant : deux minutes, le temps de repérer une bete detouree et de la
     * rejoindre, et la fenetre se refermait avant le combat. La Proie tient
     * une poursuite a elle seule ; il lui faut la duree d'une poursuite.
     */
    BATTUE("battue", false, GamePhase.EXPLORATION, 300 * 20, 300 * 20, 0xC8C8C8),
    /**
     * L'AURORE : la fenetre de LA MINE. CINQ MINUTES, et c'est mesure.
     *
     * Elle a dure deux a quatre minutes, et le joueur l'a jugee trop courte :
     * « il faut compter le temps pour descendre dans les bonnes couches, et
     * ensuite se diriger vers les diamants ; on n'a jamais assez de temps. »
     * Le compte lui donne raison -- de la surface a y = 12, un puits vertical
     * fait quatre-vingts blocs, soit une minute et demie a la pioche de fer
     * sous Hate II, et le filon le plus proche est souvent a vingt metres de
     * roche. Deux minutes ne laissaient pas de quoi arriver, encore moins de
     * quoi miner. Cinq minutes fixes : le temps de descendre, de suivre la
     * boussole et de remplir un sac.
     */
    AURORE("aurore", false, GamePhase.EXPLORATION, 300 * 20, 300 * 20, 0x9CE8FF),
    /**
     * L'HEURE DOREE : la fenetre de L'ATELIER.
     *
     * Les autres meteos vous poussent DEHORS -- miner, chasser, survivre.
     * Celle-ci vous fait RENTRER : tant qu'elle dure, la Forge d'Arcencium
     * reussit quinze points de plus et l'Etabli de Sertissage ne prend pas son
     * Eclat du Destin. C'est la seule qui recompense de s'asseoir.
     *
     * SON CIEL NE NOUS COUTE RIEN. On ne peint pas un voile, on ne pose pas de
     * coupole : on DEPLACE L'HORLOGE juste avant le coucher (voir
     * WeatherManager.clockFor). Le soleil devient rasant et dore, et c'est le
     * jeu -- ou le pack de shaders -- qui le rend, magnifiquement et sans que
     * nous ayons une seule chance de le rater. L'horloge est rendue a la fin,
     * exactement comme pour la Nuit d'Arcencium.
     *
     * CINQ MINUTES, et des Portes vers le village (weather/GoldenGate). Deux
     * minutes trente ne suffisaient pas : la fenetre recompense de s'asseoir a
     * l'atelier, et l'atelier est au village -- que le joueur avait quitte. On
     * lui ouvre donc une porte a cote de lui, et cinq minutes pour s'en servir.
     */
    HEURE_DOREE("heure_doree", false, GamePhase.EXPLORATION, 300 * 20, 300 * 20, 0xFFC46B),
    /**
     * LES QUATRE AGRESSIVES : CINQ MINUTES CHACUNE, ET FIXES.
     *
     * Elles duraient de deux a quatre minutes, tirees au hasard. Le joueur a
     * tranche apres essai : « deux minutes c'est bien trop court ». Il a
     * raison, et la raison est la meme pour toutes. Une meteo du mode n'est
     * pas un decor qui passe, c'est une FENETRE : on la voit tomber, on decide
     * ce qu'on en fait, on s'y rend, on le fait. Les trois premieres etapes
     * mangeaient les deux minutes, et il ne restait rien pour la quatrieme.
     *
     * Fixes, aussi. Une duree tiree entre deux et quatre minutes ne s'annonce
     * pas et ne se planifie pas : on ne sait jamais s'il reste de quoi tenter
     * quelque chose. Cinq minutes partout, c'est un contrat qu'on peut tenir.
     *
     * Ce que cela change au rythme : en Exploration, la meteo occupait deux
     * minutes sur cinq, elle en occupe cinq sur huit. L'ecart entre deux
     * tirages n'a pas bouge -- si le mode parait trop charge, c'est la qu'il
     * faudra donner de l'air, pas sur la duree.
     */
    NUIT("nuit", true, GamePhase.MONTEE, 300 * 20, 300 * 20, 0xB98CFF),
    METEORES("meteores", true, GamePhase.PRESSION, 300 * 20, 300 * 20, 0xFF9C4A),
    DECHIRURE("dechirure", true, GamePhase.PRESSION, 300 * 20, 300 * 20, 0xE478FF),
    ORAGE("orage", true, GamePhase.PRESSION, 300 * 20, 300 * 20, 0xFF616B);

    private final String id;
    public final boolean aggressive;
    @Nullable
    private final GamePhase unlockPhase;
    private final int minDuration;
    private final int maxDuration;
    public final int color;

    Weather(String id, boolean aggressive, @Nullable GamePhase unlockPhase,
            int minDuration, int maxDuration, int color) {
        this.id = id;
        this.aggressive = aggressive;
        this.unlockPhase = unlockPhase;
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
        this.color = color;
    }

    public String id() {
        return this.id;
    }

    /** Vrai pour une meteo qui se joue -- ni le ciel clair, ni l'accalmie. */
    public boolean real() {
        return this != CLEAR && this != EMBELLIE;
    }

    public int rollDuration(RandomSource random) {
        return this.minDuration + random.nextInt(Math.max(1, this.maxDuration - this.minDuration + 1));
    }

    public String translationKey() {
        return "weather.emeraldweapons." + this.id;
    }

    public String subtitleKey() {
        return translationKey() + ".sub";
    }

    /**
     * LE PRESAGE : ce qu'on sent venir sans savoir encore ce que c'est.
     *
     * On annoncait la meteo par son nom, quinze secondes a l'avance. C'etait
     * une fiche technique : on lisait « Orage Prismatique dans 12 s » et il ne
     * restait rien a decouvrir -- ni le ciel qui change, ni le doute. Chaque
     * meteo a donc sa phrase, qui decrit un SIGNE et jamais la chose : l'air
     * qui s'epaissit, le jour qui recule, la lumiere qui gresille. Le nom, lui,
     * arrive avec la meteo elle-meme, en plein ecran.
     */
    public String omenKey() {
        return "weather.emeraldweapons.omen." + this.id;
    }

    /**
     * Ce que la phase autorise. Les douces d'abord, les agressives avec la
     * progression -- et pendant l'Assaut, plus que les agressives : c'est
     * l'« orage permanent » du cahier, obtenu par le tirage plutot que par une
     * regle a part.
     */
    public static List<Weather> poolFor(GamePhase phase) {
        return switch (phase) {
            case EXPLORATION -> List.of(BATTUE, AURORE, HEURE_DOREE);
            case MONTEE -> List.of(BATTUE, AURORE, HEURE_DOREE, NUIT);
            case PRESSION -> List.of(BATTUE, AURORE, HEURE_DOREE, NUIT, METEORES,
                    DECHIRURE, ORAGE);
            case ASSAUT -> List.of(METEORES, DECHIRURE, ORAGE);
            default -> List.of();
        };
    }
}
