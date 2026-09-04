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
  * LE PRISME ETEINT -- ce qui fut la Brume Prismatique.
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
     * DEUX MINUTES, pas une de plus : le noir et blanc est un effet fort, il ne
     * doit pas s'installer.
     */
    PRISME("prisme", false, GamePhase.EXPLORATION, 120 * 20, 120 * 20, 0xC8C8C8),
    AURORE("aurore", false, GamePhase.EXPLORATION, 120 * 20, 240 * 20, 0x9CE8FF),
    NUIT("nuit", true, GamePhase.MONTEE, 150 * 20, 240 * 20, 0xB98CFF),
    METEORES("meteores", true, GamePhase.PRESSION, 120 * 20, 200 * 20, 0xFF9C4A),
    DECHIRURE("dechirure", true, GamePhase.PRESSION, 120 * 20, 200 * 20, 0xE478FF),
    ORAGE("orage", true, GamePhase.PRESSION, 120 * 20, 200 * 20, 0xFF616B);

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
            case EXPLORATION -> List.of(PRISME, AURORE);
            case MONTEE -> List.of(PRISME, AURORE, NUIT);
            case PRESSION -> List.of(PRISME, AURORE, NUIT, METEORES, DECHIRURE, ORAGE);
            case ASSAUT -> List.of(METEORES, DECHIRURE, ORAGE);
            default -> List.of();
        };
    }
}
