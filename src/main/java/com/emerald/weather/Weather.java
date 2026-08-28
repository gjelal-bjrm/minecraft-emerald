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
    BRUME("brume", false, GamePhase.EXPLORATION, 120 * 20, 240 * 20, 0xB9C6D6),
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
     * Ce que la phase autorise. Les douces d'abord, les agressives avec la
     * progression -- et pendant l'Assaut, plus que les agressives : c'est
     * l'« orage permanent » du cahier, obtenu par le tirage plutot que par une
     * regle a part.
     */
    public static List<Weather> poolFor(GamePhase phase) {
        return switch (phase) {
            case EXPLORATION -> List.of(BRUME, AURORE);
            case MONTEE -> List.of(BRUME, AURORE, NUIT);
            case PRESSION -> List.of(BRUME, AURORE, NUIT, METEORES, DECHIRURE, ORAGE);
            case ASSAUT -> List.of(METEORES, DECHIRURE, ORAGE);
            default -> List.of();
        };
    }
}
