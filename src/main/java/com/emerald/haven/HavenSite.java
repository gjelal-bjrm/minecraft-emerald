package com.emerald.haven;

import com.emerald.jak.JakBuilder;
import com.emerald.jak.JakVolume;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

/**
 * La pose de la ville dans sa dimension.
 *
 * « DEJA GENEREE » : dans un monde neuf, la ville est posee UNE FOIS, pendant
 * le demarrage du serveur, avant l'arrivee de quiconque. Le premier chargement
 * est plus long, puis on arrive dans une ville finie.
 *
 * Un monde neuf se reconnait a ce que la partie n'y a jamais ete preparee, et
 * a rien d'autre. Surtout pas a la validite du village : « /arcencium stop »
 * laisse un village sans Lame, et le demarrage suivant refait la mise en place
 * -- sur un monde qui a deja sa ville.
 *
 * La pose ne vide rien et ne pose pas l'eau : la dimension fournit la pierre et
 * la mer. Environ huit cent mille blocs au lieu de cinq millions huit cent mille.
 *
 * Elle resiste a un arret : HavenState retient qu'elle a ete demandee et
 * qu'elle n'est pas finie, et le demarrage suivant la reprend.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenSite {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /**
     * Marge laissee au chien de garde d'un serveur dedie.
     *
     * Il tue le serveur quand un tick depasse max-tick-time (une minute par
     * defaut), et le demarrage compte comme un tick : la recherche du village
     * y passe deja. La pose bloquante s'arrete donc quinze secondes avant, et
     * finit etalee.
     */
    private static final long WATCHDOG_MARGIN_NANOS = 15_000_000_000L;

    public enum Mode { BLOQUANT, ETALE }

    @Nullable
    private static JakBuilder.Job job;
    @Nullable
    private static JakBuilder.Report last;
    private static boolean freshWorld;

    private HavenSite() {
    }

    public static boolean busy() {
        return job != null && !job.done();
    }

    /** Le compte rendu de la derniere pose finie depuis le demarrage. */
    @Nullable
    public static JakBuilder.Report lastReport() {
        return last;
    }

    /** Vrai si ce demarrage a trouve un monde neuf. */
    public static boolean freshWorld() {
        return freshWorld;
    }

    /**
     * Au demarrage du serveur, apres la mise en place du village.
     *
     * @param fresh vrai si la partie n'avait jamais ete preparee avant ce demarrage
     */
    public static void onServerStarted(MinecraftServer server, boolean fresh) {
        freshWorld = fresh;
        if (Haven.level(server) == null) {
            LOGGER.error("dimension {} absente : la ville ne peut pas etre posee", Haven.LEVEL.location());
            return;
        }
        HavenState state = HavenState.get(server);
        if (fresh) {
            state.setPhase(HavenState.Phase.CHANTIER);
            Component failure = start(server, false, Mode.BLOQUANT, null);
            if (failure != null) {
                LOGGER.error("pose de la ville impossible dans le monde neuf : {}", failure.getString());
            }
            return;
        }
        if (state.wanted() && !state.built()) {
            LOGGER.info("la pose de la ville n'etait pas finie au dernier arret : reprise{}",
                    state.resetPending() ? ", remise au generateur comprise" : "");
            Component failure = start(server, state.resetPending(), Mode.ETALE, null);
            if (failure != null) {
                LOGGER.error("reprise de la pose de la ville impossible : {}", failure.getString());
            }
            return;
        }
        if (state.built()) {
            String now = Haven.volumeSha1(server);
            if (!now.equals(state.sha1())) {
                LOGGER.warn("la ville posee (sha1 {}) ne correspond plus au volume {} (sha1 {}) :"
                        + " /arcencium haven rebuild pour la reposer", state.sha1(), Haven.VOLUME, now);
            }
        }
    }

    /**
     * Lance une pose de la ville.
     *
     * @param reset   remettre d'abord la boite a l'etat du generateur et en retirer les entites
     * @param watcher le joueur qui recoit le message de fin, ou null
     * @return null si la pose est lancee, sinon le message d'echec
     */
    @Nullable
    public static Component start(MinecraftServer server, boolean reset, Mode mode,
                                  @Nullable ServerPlayer watcher) {
        if (busy()) {
            return Component.translatable("command.emeraldweapons.haven.busy");
        }
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return Component.translatable("command.emeraldweapons.haven.missing_level");
        }
        JakVolume volume = JakVolume.load(server, Haven.VOLUME);
        if (volume == null) {
            return Component.translatable("command.emeraldweapons.haven.missing_volume", Haven.VOLUME);
        }
        // le sha1 des donnees, verifie par la lecture : le meme que celui de
        // haven_rooms.json, auquel les salles se compareront
        String sha1 = volume.sha1() == null ? "" : volume.sha1();
        BlockPos origin = Haven.ORIGIN;
        UUID watcherId = watcher == null ? null : watcher.getUUID();

        JakBuilder.Plan plan = new JakBuilder.Plan(level, volume, origin)
                .label("ville de Haven")
                .baseline(reset ? Haven::generatorState : null)
                .skipWater(true)
                .clearFluidTicks(true)
                .awaitEntities(reset)
                .beforePlace(reset ? place -> removeEntities(place, volume, origin) : null)
                .onDone(report -> done(server, volume, sha1, origin, report, watcherId));

        HavenState.get(server).beginPose(origin, reset);
        JakBuilder.Job started = mode == Mode.BLOQUANT
                ? JakBuilder.runBlocking(plan, watchdogDeadline(server))
                : JakBuilder.start(plan);
        if (started == null) {
            return Component.translatable("command.emeraldweapons.haven.busy");
        }
        job = started;
        return null;
    }

    /** L'instant ou la pose bloquante doit rendre la main, en nanoTime. */
    private static long watchdogDeadline(MinecraftServer server) {
        if (server instanceof DedicatedServer dedicated && dedicated.getMaxTickLength() > 0L) {
            return server.getNextTickTime() + dedicated.getMaxTickLength() * 1_000_000L
                    - WATCHDOG_MARGIN_NANOS;
        }
        return Long.MAX_VALUE;
    }

    /** Tout ce qui n'est pas joueur quitte la boite avant la nouvelle pose. */
    private static void removeEntities(ServerLevel level, JakVolume volume, BlockPos origin) {
        AABB box = new AABB(origin.getX(), level.getMinBuildHeight(), origin.getZ(),
                origin.getX() + volume.width(), level.getMaxBuildHeight(), origin.getZ() + volume.depth());
        int removed = 0;
        for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            entity.discard();
            removed++;
        }
        LOGGER.info("ville de Haven : {} entites retirees de la boite avant la pose", removed);
    }

    private static void done(MinecraftServer server, JakVolume volume, String sha1, BlockPos origin,
                             JakBuilder.Report report, @Nullable UUID watcher) {
        HavenState state = HavenState.get(server);
        state.finishPose(sha1, origin, volume.width(), volume.height(), volume.depth());
        if (state.phase() == HavenState.Phase.CHANTIER) {
            state.setPhase(HavenState.Phase.ACCUEIL);
        }
        last = report;
        LOGGER.info("ville de Haven posee en {} ({} ms reels), sha1 {}, phase {}",
                report.mode(), report.totalNanos() / 1_000_000L, sha1, state.phase());
        ServerPlayer player = watcher == null ? null : server.getPlayerList().getPlayer(watcher);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("command.emeraldweapons.haven.done",
                            report.placed(), String.format(Locale.ROOT, "%.1f", report.totalNanos() / 1.0e9),
                            report.reset())
                    .withStyle(ChatFormatting.AQUA));
        }
        HavenAutotest.onPoseDone(server, volume, report);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        job = null;
        last = null;
        freshWorld = false;
    }
}
