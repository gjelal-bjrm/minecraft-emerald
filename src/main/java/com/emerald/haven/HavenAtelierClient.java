package com.emerald.haven;

import com.emerald.main.EmeraldWeaponsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Le client de dev lance pour le releve automatique de l'atelier se ferme de lui-meme.
 *
 * Avec EMERALDWEAPONS_ATELIER=releve, le run « atelier » entre dans le monde
 * haven_atelier (--quickPlaySingleplayer), le serveur integre releve la ville
 * (HavenAtelier), et quand le fichier est ecrit, on quitte : le jeu sauvegarde le
 * monde et se ferme, sans que personne ait a toucher au clavier. Sans la variable,
 * rien ne se passe.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class HavenAtelierClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static boolean stopping;

    private HavenAtelierClient() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (stopping || !HavenAtelier.autoReleve() || !HavenAtelier.autoFinished()) {
            return;
        }
        stopping = true;
        LOGGER.info("atelier : releve automatique termine, fermeture du client");
        // la sauvegarde du monde de l'atelier passe d'abord : quatre-vingt-dix secondes avant
        // tout arret force (AutomatonExit)
        com.emerald.client.AutomatonExit.stop("atelier", 90);
    }
}
