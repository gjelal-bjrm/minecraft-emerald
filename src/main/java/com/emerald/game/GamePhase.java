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
    MONTEE(27, "montee", 0x9CE8FF),
    PRESSION(54, "pression", 0xFF9C30),
    ASSAUT(72, "assaut", 0xFF616B),
    FIN(90, "fin", 0xB98CFF);

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

    /**
     * La phase d'un MONDE OUVERT, lue sur ce que le joueur a accompli.
     *
     * Sans horloge, il faut bien que la pression vienne de quelque part : elle
     * vient des ancres tenues. Trois ancres, ou l'Arc-en-ciel leve, valent
     * l'Assaut -- ce qui redonne aux dernieres minutes avant le boss l'orage
     * permanent qu'elles ont en mode Defi.
     */
    public static GamePhase forProgress(int anchors, boolean arenaRaised) {
        if (arenaRaised || anchors >= 3) {
            return ASSAUT;
        }
        return switch (Math.max(0, anchors)) {
            case 0 -> EXPLORATION;
            case 1 -> MONTEE;
            default -> PRESSION;
        };
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
