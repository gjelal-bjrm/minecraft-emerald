package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La fermeture d'un client lance pour un automate (l'automate de photos, le releve de
 * l'atelier) -- et, s'il reste bloque, sa fin forcee.
 *
 * « A chaque fois que tu fermes le jeu, il reste ouvert avec un ecran noir, et je suis
 * oblige de le fermer via le gestionnaire de taches » (22 sept.). Minecraft.stop()
 * sauvegarde le monde, puis la fermeture des mods se bloquait apres celle de JEI : la
 * fenetre restait noire, et le processus ne rendait jamais la main. Un fil de garde
 * attend donc que la fermeture normale ait eu le temps de finir -- la sauvegarde du
 * monde passe en quelques secondes -- puis arrete le processus s'il est encore la.
 */
public final class AutomatonExit {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private AutomatonExit() {
    }

    /** Ferme le client ; s'il vit encore au bout de « graceSeconds », l'arrete. */
    public static void stop(String who, int graceSeconds) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(graceSeconds * 1000L);
            } catch (InterruptedException e) {
                return;
            }
            LOGGER.warn("{} : le client ne s'est pas ferme en {} s (ecran noir), arret force", who, graceSeconds);
            Runtime.getRuntime().halt(0);
        }, "emeraldweapons-sortie-" + who);
        watchdog.setDaemon(true);
        watchdog.start();
        Minecraft.getInstance().stop();
    }
}
