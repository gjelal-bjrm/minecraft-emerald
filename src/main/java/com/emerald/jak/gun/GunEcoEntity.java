package com.emerald.jak.gun;

import com.emerald.haven.Haven;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.init.Jak3Registry;
import com.emerald.particles.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Une munition d'eco a ramasser : le modele gun-ammo-* de Jak 3 qui tourne lentement.
 *
 * DEUX SORTES :
 *  - POINT : posee a l'un des douze points d'eco de la carte validee
 *    (HavenInvasionData), immobile ; ramassee, elle revient apres
 *    {@value GunEco#RESPAWN_TICKS} tiques (GunEco) ;
 *  - LACHEE par un monstre de Haven tue : elle tombe au sol, vit
 *    {@value GunEco#DROP_LIFE} tiques (clignote les trois dernieres secondes), et part
 *    au passage de la ville en paisible.
 * Toutes partent a la fermeture de la ville (depart vers la partie, pose).
 * Jamais sauvegardees : GunEco les repose.
 *
 * LE RAMASSAGE, au contact, par quiconque porte un Morph Gun dans la ville : 10
 * eco jaunes ou bleues, 5 rouges, 1 sombre (collectables.gc:2511-2524), plafonnees
 * a la capacite (game-info.gc:790-821). Une reserve deja pleine ne ramasse pas :
 * la munition reste pour les autres.
 */
public class GunEcoEntity extends Entity {

    private static final EntityDataAccessor<Byte> FAMILY =
            SynchedEntityData.defineId(GunEcoEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DROP =
            SynchedEntityData.defineId(GunEcoEntity.class, EntityDataSerializers.BOOLEAN);
    /** Tique de jeu (tronquee a l'entier) de la fin d'une munition lachee ; 0 : jamais. */
    private static final EntityDataAccessor<Integer> EXPIRE =
            SynchedEntityData.defineId(GunEcoEntity.class, EntityDataSerializers.INT);

    /** Evenement d'entite : ramassee (eclats cote client). */
    public static final byte EVENT_PICKED = 60;
    private static final int BLINK_TICKS = 60;

    /** Vrai si un joueur l'a ramassee (GunEco distingue ramassage et dechargement du troncon). */
    public boolean picked;

    public GunEcoEntity(EntityType<? extends GunEcoEntity> type, Level level) {
        super(type, level);
    }

    /** Une munition prete a ajouter au monde. */
    public static GunEcoEntity create(ServerLevel level, GunForm.Family family, boolean drop, Vec3 at) {
        GunEcoEntity eco = new GunEcoEntity(Jak3Registry.GUN_ECO.get(), level);
        eco.setPos(at.x, at.y, at.z);
        eco.setYRot(level.random.nextFloat() * 360.0F);
        eco.entityData.set(FAMILY, (byte) family.ordinal());
        eco.entityData.set(DROP, drop);
        eco.entityData.set(EXPIRE, drop ? (int) (level.getGameTime() + GunEco.DROP_LIFE) : 0);
        return eco;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FAMILY, (byte) 0);
        builder.define(DROP, false);
        builder.define(EXPIRE, 0);
    }

    public GunForm.Family family() {
        GunForm.Family family = GunForm.Family.byIndex(this.entityData.get(FAMILY));
        return family == null ? GunForm.Family.YELLOW : family;
    }

    public boolean isDrop() {
        return this.entityData.get(DROP);
    }

    public int amount() {
        return GunSpec.pickupAmount(family());
    }

    /** Une munition lachee en fin de vie clignote : cachee une image sur deux periodes de trois tiques. */
    public boolean hidden(float partial) {
        int expire = this.entityData.get(EXPIRE);
        if (!isDrop() || expire == 0) {
            return false;
        }
        long now = this.level().getGameTime();
        return expire - now < BLINK_TICKS && (now / 3L) % 2L == 0L;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            if (this.random.nextInt(6) == 0) {
                this.level().addParticle(ModParticles.GUN_ECO_GLINT.get(),
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.5, this.getY() + 0.1 + this.random.nextDouble() * 0.4,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.5, family().ordinal(), 0.02, 0.0);
            }
            return;
        }
        MinecraftServer server = level.getServer();
        if (!Haven.is(level) || !HavenInvasion.cityOpen(server)) {
            this.discard();
            return;
        }
        if (isDrop()) {
            int expire = this.entityData.get(EXPIRE);
            if ((expire != 0 && level.getGameTime() >= expire) || HavenInvasion.mode(server) == HavenInvasion.Mode.PAISIBLE) {
                this.discard();
                return;
            }
            if (!this.onGround()) {
                Vec3 motion = this.getDeltaMovement().add(0.0, -0.04, 0.0).scale(0.98);
                this.setDeltaMovement(motion);
                this.move(MoverType.SELF, motion);
            } else {
                this.setDeltaMovement(Vec3.ZERO);
            }
            if (this.isInWater() || this.isInLava() || this.getY() < level.getMinBuildHeight()) {
                this.discard();
                return;
            }
        }
        if (this.tickCount % 2 == 0) {
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(0.35))) {
                if (GunEco.pickup(this, player)) {
                    return;
                }
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_PICKED) {
            for (int i = 0; i < 10; i++) {
                this.level().addParticle(ModParticles.GUN_ECO_GLINT.get(),
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.4, this.getY() + 0.2,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.4, family().ordinal(),
                        0.05 + this.random.nextDouble() * 0.08, 0.0);
            }
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0 * 96.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** Pour l'usage de GunEco : la munition existe-t-elle encore dans un monde qui tique ? */
    boolean gone() {
        return this.isRemoved() || this.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK;
    }
}
