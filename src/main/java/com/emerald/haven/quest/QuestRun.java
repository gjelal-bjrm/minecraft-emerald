package com.emerald.haven.quest;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenRules;
import com.emerald.haven.invasion.HavenMonsterKilledEvent;
import com.emerald.jak.gun.GunForm;
import com.emerald.jak.vehicle.JakVehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Une quete EN COURS : son equipe, son etat, sa facon de se derouler (lot 3, cahier §86).
 *
 * Chaque quete a sa sous-classe (paquet runs). HavenQuests la cree a l'acceptation, lui
 * donne son equipe, l'appelle a chaque tique et lui transmet les evenements de la ville
 * (monstre abattu, mort d'un joueur, eco touche, poisson peche, voiture percutee...). Elle
 * dit ou elle en est (barre d'objectif) et finit par {@link #succeed} ou {@link #fail} ;
 * {@link #cleanup} retire ce qu'elle a pose.
 */
public abstract class QuestRun {

    public enum Status { RUNNING, DONE, FAILED }

    public final HavenQuest quest;
    public final ServerLevel level;
    public final long start;
    /** L'equipe : celui qui a accepte, puis ceux qui ont rejoint. */
    public final Set<UUID> team = new LinkedHashSet<>();
    private Status status = Status.RUNNING;
    @Nullable
    private String failKey;
    private int medal;
    /** Un numero unique, pour etiqueter ce que la quete pose. */
    public final int number;
    private static int nextNumber = 1;

    protected QuestRun(HavenQuest quest, ServerLevel level) {
        this.quest = quest;
        this.level = level;
        this.start = level.getGameTime();
        this.number = nextNumber++;
    }

    // ------------------------------------------------------------- a ecrire par chaque quete

    /** L'equipe est la (au moins celui qui accepte) : poser ce qu'il faut. */
    public abstract void begin();

    /** Une tique : avancer, et appeler succeed ou fail le moment venu. */
    public abstract void tick(long now);

    /** Le texte de la barre d'objectif. */
    public abstract Component objective();

    /** L'avancement, de 0 a 1. */
    public abstract float progress();

    /** Retirer ce que la quete a pose (monstres, anneaux, coffres...). */
    public void cleanup() {
    }

    public BossEvent.BossBarColor color() {
        return this.quest.invades() ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.GREEN;
    }

    // ------------------------------------------------------------- evenements (rien par defaut)

    public void onKill(HavenMonsterKilledEvent event, ServerPlayer killer) {
    }

    public void onDeath(ServerPlayer member) {
    }

    public void onEco(ServerPlayer member, GunForm.Family family) {
    }

    public void onFish(ServerPlayer member, List<ItemStack> drops) {
    }

    public void onRam(ServerPlayer driver, JakVehicleEntity car, double momentum) {
    }

    public void onFaunaKill(ServerPlayer member, Mob mob) {
    }

    /** Un joueur de l'equipe a clique sur une entite : vrai si la quete l'a prise. */
    public boolean onInteract(ServerPlayer member, Entity target) {
        return false;
    }

    /** Un joueur de l'equipe a clique sur un bloc (un coffre englouti...) : vrai si la quete l'a pris. */
    public boolean onBlock(ServerPlayer member, BlockPos pos) {
        return false;
    }

    // ------------------------------------------------------------- fin

    protected final void succeed(int medal) {
        if (this.status == Status.RUNNING) {
            this.status = Status.DONE;
            this.medal = medal;
        }
    }

    protected final void succeed() {
        succeed(0);
    }

    protected final void fail(String key) {
        if (this.status == Status.RUNNING) {
            this.status = Status.FAILED;
            this.failKey = key;
        }
    }

    public final Status status() {
        return this.status;
    }

    @Nullable
    public final String failKey() {
        return this.failKey;
    }

    public final int medal() {
        return this.medal;
    }

    // ------------------------------------------------------------- outils

    /** Les joueurs de l'equipe presents dans Haven, vivants, hors chantier et spectateurs. */
    public List<ServerPlayer> members() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : this.team) {
            ServerPlayer player = HavenQuests.playerOf(this.level, id);
            if (player != null && Haven.is(player.level()) && !player.isSpectator() && !HavenRules.chantier(player)) {
                out.add(player);
            }
        }
        return out;
    }

    public boolean member(UUID id) {
        return this.team.contains(id);
    }

    /** Tiques depuis le debut. */
    public long elapsed() {
        return this.level.getGameTime() - this.start;
    }

    /** Secondes restantes avant la limite de la quete (0 sans limite). */
    public int secondsLeft() {
        if (this.quest.timeLimit() <= 0) {
            return 0;
        }
        return (int) Math.max(0, (this.quest.timeLimit() - elapsed() + 19) / 20);
    }

    /** « 4:07 ». */
    public static String clock(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    /** L'etiquette des entites posees par cette quete. */
    public String tag() {
        return "emeraldweapons.quete." + this.number;
    }
}
