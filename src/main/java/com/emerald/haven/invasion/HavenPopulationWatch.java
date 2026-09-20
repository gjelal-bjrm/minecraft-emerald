package com.emerald.haven.invasion;

import com.emerald.haven.Haven;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * La surveillance de la population : qui fait disparaitre les monstres, et pourquoi.
 *
 * LE JOUEUR (19 sept.) : « de temps en temps, tous les monstres disparaissent et une
 * nouvelle vague les remplace ». La relecture du code n'a rien donne -- la generation
 * sauvegardee valait toujours 0, donc {@link HavenInvasion#removeAll} n'avait jamais
 * ete appelee --, et le jeu ne retire pas une entite persistante. Il fallait donc
 * regarder la disparition elle-meme.
 *
 * COMMENT. Minecraft donne a chaque retrait SA RAISON (Entity.RemovalReason) :
 *  - KILLED : l'entite est morte (soleil, noyade, chute, une arme) ;
 *  - DISCARDED : quelqu'un a appele discard() -- notre code, ou un autre mod ;
 *  - UNLOADED_TO_CHUNK : son troncon s'est deconnecte, elle est sauvegardee (normal,
 *    c'est ce qui arrive en permanence quand on roule dans la ville).
 * On compte les trois par seconde. Des {@value #BURST} monstres perdus dans la meme
 * seconde autrement que par un dechargement, une ligne le dit, AVEC LA PILE D'APPEL
 * du premier retrait de la seconde : elle nomme la methode qui l'a demande, qu'elle
 * soit a nous ou a un autre mod.
 *
 * Trois relevés au plus par demarrage : de quoi identifier un coupable, pas de quoi
 * noyer le journal. Rien n'est ecrit tant que la ville se comporte bien.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenPopulationWatch {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Monstres perdus dans une seconde, hors dechargement, a partir desquels on ecrit. */
    private static final int BURST = 8;
    private static final int MAX_REPORTS = 3;
    /** Profondeur de pile gardee : de quoi voir l'appelant sans remplir le journal. */
    private static final int FRAMES = 14;

    private static long second = Long.MIN_VALUE;
    private static int killed;
    private static int discarded;
    private static int unloaded;
    @Nullable
    private static StackTraceElement[] first;
    @Nullable
    private static String firstName;
    private static int reports;

    private HavenPopulationWatch() {
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Haven.is(level) || reports >= MAX_REPORTS) {
            return;
        }
        Entity entity = event.getEntity();
        if (!entity.getTags().contains(HavenInvasion.MONSTER_TAG)) {
            return;
        }
        Entity.RemovalReason reason = entity.getRemovalReason();
        long now = level.getGameTime() / 20L;
        if (now != second) {
            flush(level);
            second = now;
            killed = 0;
            discarded = 0;
            unloaded = 0;
            first = null;
            firstName = null;
        }
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            unloaded++;
            return;
        }
        if (reason == Entity.RemovalReason.KILLED) {
            killed++;
        } else {
            discarded++;
        }
        if (first == null) {
            // la pile est celle de l'appel a discard() ou a la mort : elle nomme le coupable
            first = Thread.currentThread().getStackTrace();
            firstName = entity.getName().getString() + " en " + entity.blockPosition().toShortString();
        }
    }

    /** Ecrit le releve de la seconde ecoulee si elle a perdu beaucoup de monstres. */
    private static void flush(ServerLevel level) {
        if (killed + discarded < BURST || reports >= MAX_REPORTS) {
            return;
        }
        reports++;
        StringBuilder trace = new StringBuilder();
        if (first != null) {
            for (int i = 2; i < first.length && i < 2 + FRAMES; i++) {
                trace.append("\n    ").append(first[i]);
            }
        }
        LOGGER.warn("ville de Haven : {} monstres perdus en une seconde ({} morts, {} retires, {} troncons decharges) ;"
                        + " mode {}, generation {}, difficulte {}, ville ouverte {} ; premier : {}{}",
                killed + discarded, killed, discarded, unloaded, HavenInvasion.mode(level.getServer()),
                HavenInvasionState.get(level).generation(), level.getServer().getWorldData().getDifficulty(),
                HavenInvasion.cityOpen(level.getServer()), firstName, trace);
        if (reports >= MAX_REPORTS) {
            LOGGER.warn("ville de Haven : surveillance de la population arretee apres {} releves", MAX_REPORTS);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        second = Long.MIN_VALUE;
        killed = 0;
        discarded = 0;
        unloaded = 0;
        first = null;
        firstName = null;
        reports = 0;
    }
}
