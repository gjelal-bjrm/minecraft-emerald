package com.emerald.game;

import net.minecraft.server.level.ServerLevel;

/**
 * L'interrupteur du mode : tout ou rien.
 *
 * Il existe pour une raison de FABRICATION, pas de jeu. Le mode confine au
 * village tant que la Lame du Serment n'est pas retiree, puis impose son siege
 * -- ce qui est voulu en partie, et insupportable quand on veut simplement
 * aller regarder un batiment a huit cents blocs de la. Sans cet interrupteur,
 * la seule facon d'explorer etait de gagner le prologue d'abord.
 *
 * Eteint, le mode ne fait plus rien du tout : pas de confinement, pas de
 * meteo, pas de Maree, pas de chronometre, pas de paliers d'Apotheose. Le monde
 * redevient un Minecraft ordinaire, et nos blocs restent la pour qu'on batisse
 * avec.
 *
 * L'etat est volatil, comme les sieges : on le rallume d'une commande, et un
 * monde recharge repart mode allume. Le contraire ferait qu'on retrouverait un
 * jour une partie eteinte sans se souvenir de l'avoir eteinte.
 */
public final class ModeSwitch {

    private static boolean enabled = true;

    private ModeSwitch() {
    }

    public static boolean enabled() {
        return enabled;
    }

    /** Vrai quand le mode doit s'abstenir : le raccourci lu par tous les systemes. */
    public static boolean off() {
        return !enabled;
    }

    /**
     * Allume ou eteint. En eteignant, on nettoie ce qui aurait survecu.
     *
     * La meteo surtout : une Nuit d'Arcencium en cours au moment de l'extinction
     * aurait laisse le monde a minuit sous la pluie pour toujours, l'horloge
     * n'etant rendue qu'a la fin de la tempete.
     */
    public static void set(ServerLevel level, boolean value) {
        enabled = value;
        if (!value) {
            com.emerald.weather.WeatherManager.stop(level);
        }
    }
}
