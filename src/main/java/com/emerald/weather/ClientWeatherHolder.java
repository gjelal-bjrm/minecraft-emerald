package com.emerald.weather;

/**
 * La meteo courante, vue du client, dans une classe COMMUNE.
 *
 * Les blocs (GlowingPlantBlock, par exemple) veulent adapter leurs particules a
 * la meteo, mais leur code est commun : referencer une classe client depuis
 * animateTick ferait planter un serveur dedie au chargement de la classe. Ce
 * relais sans aucune dependance client regle le probleme -- le client l'ecrit,
 * le code commun le lit, un serveur dedie le laisse a zero.
 */
public final class ClientWeatherHolder {

    private ClientWeatherHolder() {
    }

    public static volatile int current = 0;

    public static boolean isAurora() {
        return current == Weather.AURORE.ordinal();
    }

    public static boolean is(Weather weather) {
        return current == weather.ordinal();
    }
}
