package com.emerald.jak.gun;

import com.emerald.init.Jak3Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Un segment de la foudre du Peace Maker, d'un point a un autre, purement visuel.
 *
 * Il ne blesse rien lui-meme (GunFire.chain porte les coups) et n'est jamais
 * sauvegarde. Sa fin est RELATIVE a sa position, synchronisee avec lui ; sa
 * graine fait le zigzag, que le rendu (GunRenderers.Arc) retire toutes les deux
 * tiques pour le gresillement. Vie de {@value #LIFE} tiques.
 */
public class GunArcEntity extends Entity {

    public static final int LIFE = 6;

    private static final EntityDataAccessor<Vector3f> END =
            SynchedEntityData.defineId(GunArcEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> SEED =
            SynchedEntityData.defineId(GunArcEntity.class, EntityDataSerializers.INT);

    public GunArcEntity(EntityType<? extends GunArcEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static GunArcEntity spawn(ServerLevel level, Vec3 from, Vec3 to) {
        GunArcEntity arc = new GunArcEntity(Jak3Registry.GUN_ARC.get(), level);
        arc.setPos(from.x, from.y, from.z);
        arc.entityData.set(END, new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z)));
        arc.entityData.set(SEED, level.random.nextInt());
        level.addFreshEntity(arc);
        return arc;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(END, new Vector3f());
        builder.define(SEED, 0);
    }

    /** La fin, relative a la position. */
    public Vector3f end() {
        return this.entityData.get(END);
    }

    public int seed() {
        return this.entityData.get(SEED);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount >= LIFE) {
            this.discard();
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Vector3f e = this.end();
        return new AABB(this.position(), this.position().add(e.x, e.y, e.z)).inflate(1.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0 * 128.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
