package com.emerald.jak;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import javax.annotation.Nullable;

/**
 * Pose un quartier, etage par etage, sans figer le serveur.
 *
 * Le port de Haven fait sept cent mille blocs. Les poser d'un bloc sur le tick
 * de la commande gelerait le jeu une minute entiere et ferait expirer les
 * clients ; on etale donc la pose sur plusieurs secondes, par paquets.
 *
 * L'ordre de parcours du fichier est horizontal -- une couche complete, puis la
 * suivante -- ce qui n'est pas un detail : le quartier POUSSE du sol vers le
 * ciel sous les yeux du joueur au lieu d'apparaitre par tranches verticales, et
 * on voit tout de suite si le plan au sol est bon sans attendre la fin.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class JakBuilder {

    /** Blocs poses par tick. Assez pour aller vite, assez peu pour rester fluide. */
    private static final int PER_TICK = 20_000;

    @Nullable
    private static Job job;

    private JakBuilder() {
    }

    /** Vrai si une pose est deja en cours : on n'en veut pas deux a la fois. */
    public static boolean busy() {
        return job != null;
    }

    public static void cancel() {
        job = null;
    }

    /**
     * Lance la pose.
     *
     * L'origine est le coin (minx, miny, minz) du volume : le quartier se batit
     * donc vers le nord-est et vers le haut depuis le point donne, ce qui est
     * previsible et suffit pour un outil d'essai.
     */
    public static void start(ServerLevel level, JakVolume volume, BlockPos origin,
                             @Nullable ServerPlayer watcher) {
        job = new Job(level, volume, origin, watcher);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (job == null
                || !(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        if (job.step()) {
            job.finish();
            job = null;
        }
    }

    /** L'etat d'une pose en cours : ou l'on en est dans les plages. */
    private static final class Job {
        private final ServerLevel level;
        private final JakVolume volume;
        private final BlockPos origin;
        @Nullable
        private final ServerPlayer watcher;

        private int run;
        private int withinRun;
        private long cursor;
        private long placed;
        private final long started;

        Job(ServerLevel level, JakVolume volume, BlockPos origin,
            @Nullable ServerPlayer watcher) {
            this.level = level;
            this.volume = volume;
            this.origin = origin;
            this.watcher = watcher;
            this.started = level.getGameTime();
        }

        /** @return vrai quand tout est pose */
        boolean step() {
            int budget = PER_TICK;
            while (budget > 0 && this.run < this.volume.runCount()) {
                int block = this.volume.runBlock(this.run);
                int length = this.volume.runLength(this.run);
                int left = length - this.withinRun;

                if (block == 0) {
                    // l'air : on saute la plage entiere d'un coup, sans rien
                    // poser. C'est ce qui rend la pose tenable -- les neuf
                    // dixiemes du volume sont vides
                    this.cursor += left;
                    this.run++;
                    this.withinRun = 0;
                    continue;
                }

                int take = Math.min(budget, left);
                BlockState state = this.volume.state(block);
                for (int i = 0; i < take; i++) {
                    place(this.cursor + i, state);
                }
                this.cursor += take;
                this.placed += take;
                this.withinRun += take;
                budget -= take;
                if (this.withinRun >= length) {
                    this.run++;
                    this.withinRun = 0;
                }
            }
            return this.run >= this.volume.runCount();
        }

        /**
         * De l'index lineaire aux coordonnees.
         *
         * L'ordre d'ecriture est y, puis z, puis x -- il faut le defaire dans le
         * meme ordre. Se tromper ici ne plante pas : cela produit un quartier
         * transpose, ce qui se voit tout de suite mais ne se devine pas.
         */
        private void place(long index, BlockState state) {
            int w = this.volume.width();
            int d = this.volume.depth();
            int x = (int) (index % w);
            int z = (int) ((index / w) % d);
            int y = (int) (index / ((long) w * d));
            BlockPos pos = this.origin.offset(x, y, z);
            if (pos.getY() < this.level.getMinBuildHeight()
                    || pos.getY() >= this.level.getMaxBuildHeight()) {
                return;
            }
            // drapeau 2 : on previent le client, sans cascade de mises a jour
            this.level.setBlock(pos, state, 2);
        }

        void finish() {
            long seconds = Math.max(1, (this.level.getGameTime() - this.started) / 20);
            Component message = Component.translatable(
                            "command.emeraldweapons.jak.done", this.placed, seconds)
                    .withStyle(ChatFormatting.AQUA);
            if (this.watcher != null) {
                this.watcher.sendSystemMessage(message);
            }
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID)
                    .info("quartier pose : {} blocs en {} s", this.placed, seconds);
        }
    }
}
