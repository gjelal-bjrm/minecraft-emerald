package com.emerald.haven.quest;

import com.emerald.haven.journey.HavenProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * UN ORBE PRECURSEUR CACHE dans Haven (lot 3, cahier §86) : l'oeuf a glyphes de Jak 3
 * (modele « collectables-skill », cuit par tools/jak_gun.py), qui tourne et flotte.
 *
 * UN ORBE PAR JOUEUR : il ne se montre qu'a ceux qui ne l'ont pas encore pris
 * ({@link #broadcastToPlayer}) ; qui le prend le voit disparaitre (HavenOrbs.collect lui
 * envoie le retrait), les autres le voient toujours. Jamais sauvegarde : HavenOrbs le repose
 * quand son troncon est charge.
 */
public class HavenOrbEntity extends Entity {

    private static final EntityDataAccessor<Integer> INDEX = SynchedEntityData.defineId(HavenOrbEntity.class,
            EntityDataSerializers.INT);
    /** Portee de ramassage, en blocs, autour de l'orbe. */
    static final double REACH = 1.2;

    public HavenOrbEntity(EntityType<? extends HavenOrbEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(INDEX, -1);
    }

    public int index() {
        return this.entityData.get(INDEX);
    }

    public void setIndex(int index) {
        this.entityData.set(INDEX, index);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level) || this.tickCount % 4 != 0) {
            return;
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(REACH),
                p -> p.isAlive() && !p.isSpectator())) {
            HavenOrbs.collect(level, this, player);
        }
    }

    /** Il ne se montre qu'a ceux qui ne l'ont pas encore pris. */
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return this.index() >= 0 && !HavenProgress.found(player.getUUID(), this.index());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setIndex(tag.getInt("Orbe"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Orbe", this.index());
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }
}
