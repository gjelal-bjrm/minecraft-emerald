package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
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
 * attend donc que la fermeture normale ait eu le temps de finir, puis arrete le processus
 * s'il est encore la.
 *
 * LA SAUVEGARDE D'ABORD (26 sept.). Le fil comptait ses vingt secondes des la demande de
 * fermeture : apres un releve de l'atelier, qui charge toute la ville, l'ecriture du monde en
 * prenait plus, et l'arret force l'a coupee en plein milieu. Il attend maintenant que le
 * serveur integre ait fini (son fil s'arrete apres la sauvegarde), dix minutes au plus, et ne
 * compte qu'ensuite le delai de l'ecran noir.
 */
public final class AutomatonExit {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);
    /** Au-dela, la sauvegarde est tenue pour bloquee elle aussi. */
    private static final long SAVE_MAX_MS = 10L * 60L * 1000L;

    private AutomatonExit() {
    }

    /** Ferme le client ; s'il vit encore « graceSeconds » apres la fin du serveur integre, l'arrete. */
    public static void stop(String who, int graceSeconds) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        Thread serverThread = server == null ? null : server.getRunningThread();
        Thread watchdog = new Thread(() -> {
            try {
                long deadline = System.currentTimeMillis() + SAVE_MAX_MS;
                while (serverThread != null && serverThread.isAlive() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(500L);
                }
                if (serverThread != null && serverThread.isAlive()) {
                    LOGGER.warn("{} : le serveur integre ecrit encore le monde apres {} s", who, SAVE_MAX_MS / 1000L);
                }
                Thread.sleep(graceSeconds * 1000L);
            } catch (InterruptedException e) {
                return;
            }
            LOGGER.warn("{} : le client ne s'est pas ferme {} s apres le serveur (ecran noir), arret force", who,
                    graceSeconds);
            Runtime.getRuntime().halt(0);
        }, "emeraldweapons-sortie-" + who);
        watchdog.setDaemon(true);
        watchdog.start();
        Minecraft.getInstance().stop();
    }
}
