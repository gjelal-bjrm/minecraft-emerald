package com.emerald.game;

/**
 * Les phases d'une partie du Mode Arcencium.
 *
 * Chaque phase ouvre un cran de meteo et durcit les monstres ; leurs bornes
 * sont exprimees en minutes ecoulees depuis le retrait de la Lame du Serment.
 */
public enum GamePhase {
    LOBBY(0, "lobby", 0x8CFFB0),
    PROLOGUE(0, "prologue", 0xFFD36B),
    EXPLORATION(0, "exploration", 0x78E8AE),
    MONTEE(18, "montee", 0x9CE8FF),
    PRESSION(36, "pression", 0xFF9C30),
    ASSAUT(48, "assaut", 0xFF616B),
    FIN(60, "fin", 0xB98CFF);

    /** Minute a partir de laquelle la phase commence. */
    public final int fromMinute;
    private final String id;
    public final int color;

    GamePhase(int fromMinute, String id, int color) {
        this.fromMinute = fromMinute;
        this.id = id;
        this.color = color;
    }

    public String translationKey() {
        return "game.emeraldweapons.phase." + this.id;
    }

    /** La phase correspondant a un nombre de ticks ecoules depuis le depart. */
    public static GamePhase forTicks(long ticks) {
        long minutes = ticks / (20L * 60L);
        GamePhase best = EXPLORATION;
        for (GamePhase phase : new GamePhase[]{EXPLORATION, MONTEE, PRESSION, ASSAUT, FIN}) {
            if (minutes >= phase.fromMinute) {
                best = phase;
            }
        }
        return best;
    }
}
