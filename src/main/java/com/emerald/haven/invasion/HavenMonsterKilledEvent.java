package com.emerald.haven.invasion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;

import javax.annotation.Nullable;

/**
 * « Un monstre de Haven est mort » : le point d'accroche des autres chantiers.
 *
 * Poste sur NeoForge.EVENT_BUS par HavenInvasion, pendant LivingDeathEvent en
 * priorite LOWEST (donc apres tout abonne qui aurait annule la mort), une fois
 * par monstre de l'invasion tue dans Haven. Le butin et l'experience vanilla du
 * monstre sont supprimes par ailleurs : c'est ici qu'on lache ce qu'on veut
 * (les munitions d'eco du chantier du tir, par exemple).
 *
 * Non annulable. Le monstre est encore dans le monde, a sa position de mort.
 *
 * <pre>
 * {@literal @}SubscribeEvent
 * public static void onHavenKill(HavenMonsterKilledEvent event) {
 *     if (event.getKiller() != null) { ... event.getLevel(), event.getPosition(), event.getKind() ... }
 * }
 * </pre>
 */
public class HavenMonsterKilledEvent extends Event {

    private final ServerLevel level;
    private final Mob monster;
    private final HavenInvasion.Kind kind;
    private final DamageSource source;
    @Nullable
    private final ServerPlayer killer;

    public HavenMonsterKilledEvent(ServerLevel level, Mob monster, HavenInvasion.Kind kind, DamageSource source,
                                   @Nullable ServerPlayer killer) {
        this.level = level;
        this.monster = monster;
        this.kind = kind;
        this.source = source;
        this.killer = killer;
    }

    /** Le niveau de Haven. */
    public ServerLevel getLevel() {
        return this.level;
    }

    /** Le monstre tue (zombie, villageois zombie, squelette ou phantom). */
    public Mob getMonster() {
        return this.monster;
    }

    public HavenInvasion.Kind getKind() {
        return this.kind;
    }

    public DamageSource getSource() {
        return this.source;
    }

    /**
     * Le joueur a qui revient la mort : l'auteur du coup (source.getEntity()),
     * sinon le dernier joueur qui l'a frappe ; null si personne (vide, /kill).
     */
    @Nullable
    public ServerPlayer getKiller() {
        return this.killer;
    }

    /** La position du monstre au moment de sa mort. */
    public Vec3 getPosition() {
        return this.monster.position();
    }
}
