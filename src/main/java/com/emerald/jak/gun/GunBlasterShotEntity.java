package com.emerald.jak.gun;

import com.emerald.init.Jak3Registry;
import com.emerald.particles.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Le tir du Blaster (gun-yellow-shot, Jak 3) : 200 m/s, soit
 * {@value GunSpec#BLASTER_SPEED} blocs par tique, sans gravite ni freinage,
 * {@value GunSpec#BLASTER_LIFE} tiques de vie.
 *
 * Pas un ThrowableProjectile : celui-ci freine de 1 % par tique (0,99), et le
 * tir aurait perdu 45 % de sa vitesse avant la fin de sa vie. Le deplacement et
 * les touches sont faits ici, cote serveur : blocs (collision, l'eau ne l'arrete
 * pas) puis monstres de Haven sur le segment de la tique. Les joueurs et les
 * habitants sont traverses.
 *
 * Impact sur un monstre : {@value GunSpec#BLASTER_DAMAGE} points de Jak. Sur un
 * bloc : les blocs a moins de {@value GunSpec#BLASTER_BREAK_RADIUS} du point
 * d'impact, {@value GunSpec#BLASTER_BLOCKS} au plus (GunImpacts).
 *
 * LE CLIENT NE SUIT PAS LA VITESSE SYNCHRONISEE. Minecraft plafonne la vitesse
 * envoyee a 3,9 blocs par tique, composante par composante
 * (ClientboundAddEntityPacket, ClientboundSetEntityMotionPacket) : le trait aurait
 * avance 2,5 fois trop lentement, de travers, et se serait arrete bien avant
 * l'impact. Le lancer est donc synchronise une fois pour toutes -- origine,
 * direction, tique de jeu du lancer -- et le client calcule la position,
 * origine + direction x {@value GunSpec#BLASTER_SPEED} x age, en s'arretant au
 * premier mur. Un joueur qui voit le tir apres son depart (entre dans la portee de
 * suivi) le prend a sa vraie place. Aucun paquet de position n'est utile : le
 * suivi ne les envoie pas (Jak3Registry, updateInterval).
 *
 * Aucun modele : il se voit a sa trainee, sur le segment de la tique cote client
 * (particule gun_blaster_bolt, neuve).
 */
public class GunBlasterShotEntity extends Projectile {

    private static final EntityDataAccessor<Vector3f> ORIGIN =
            SynchedEntityData.defineId(GunBlasterShotEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DIRECTION =
            SynchedEntityData.defineId(GunBlasterShotEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Long> LAUNCH =
            SynchedEntityData.defineId(GunBlasterShotEntity.class, EntityDataSerializers.LONG);

    /** Points de trainee par bloc : un tous les quart de bloc (moins laissait un pointille, planche-tir.png). */
    private static final int TRAIL_PER_BLOCK = 4;
    /** Au plus trois tiques de trajet dessinees d'un coup (tir vu en retard). */
    private static final int TRAIL_MAX_TICKS = 3;

    /** Cote client : la distance deja parcourue depuis l'origine, -1 avant la premiere tique. */
    private double clientDistance = -1.0;
    private boolean clientStopped;

    public GunBlasterShotEntity(EntityType<? extends GunBlasterShotEntity> type, Level level) {
        super(type, level);
    }

    public GunBlasterShotEntity(Level level, LivingEntity shooter) {
        this(Jak3Registry.GUN_BLASTER_SHOT.get(), level);
        this.setOwner(shooter);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ORIGIN, new Vector3f());
        builder.define(DIRECTION, new Vector3f());
        builder.define(LAUNCH, 0L);
    }

    /**
     * Pose le tir au depart, cote serveur, AVANT addFreshEntity : position,
     * vitesse, et le lancer synchronise que le client suivra.
     */
    public void launch(Vec3 origin, Vec3 direction, long gameTime) {
        Vec3 d = direction.normalize();
        this.setPos(origin.x, origin.y, origin.z);
        this.setDeltaMovement(d.scale(GunSpec.BLASTER_SPEED));
        this.entityData.set(ORIGIN, new Vector3f((float) origin.x, (float) origin.y, (float) origin.z));
        this.entityData.set(DIRECTION, new Vector3f((float) d.x, (float) d.y, (float) d.z));
        this.entityData.set(LAUNCH, gameTime);
    }

    /** La position du tir d'apres son lancer, `ticks` tiques apres le depart. */
    public Vec3 predicted(double ticks) {
        Vector3f o = this.entityData.get(ORIGIN);
        Vector3f d = this.entityData.get(DIRECTION);
        double distance = Math.max(0.0, ticks) * GunSpec.BLASTER_SPEED;
        return new Vec3(o.x + d.x * distance, o.y + d.y * distance, o.z + d.z * distance);
    }

    public long launchTick() {
        return this.entityData.get(LAUNCH);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel server)) {
            clientTick();
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        Vec3 from = this.position();
        Vec3 to = from.add(motion);
        BlockHitResult block = server.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        if (this.tickCount > GunSpec.BLASTER_LIFE || !(this.getOwner() instanceof ServerPlayer owner)
                || !MorphGunKeeper.allowed(owner)) {
            this.discard();
            return;
        }
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(server, this, from, stop,
                this.getBoundingBox().expandTowards(motion).inflate(1.0), GunImpacts::isTarget, 0.3F);
        if (hit != null) {
            Vec3 at = hit.getEntity().getBoundingBox().inflate(0.3).clip(from, stop).orElse(hit.getLocation());
            GunImpacts.hurt(owner, this, hit.getEntity(), GunSpec.BLASTER_DAMAGE);
            impact(server, at);
            return;
        }
        if (block.getType() == HitResult.Type.BLOCK) {
            GunImpacts.breakSphere(server, stop, GunSpec.BLASTER_BREAK_RADIUS, GunSpec.BLASTER_BLOCKS, owner);
            impact(server, stop);
            return;
        }
        this.setPos(to.x, to.y, to.z);
    }

    /**
     * Le client : la position vient du lancer et de l'horloge du monde, jamais de la
     * vitesse plafonnee. La trainee couvre le chemin fait depuis la tique d'avant ;
     * au mur, le trait s'arrete et attend le retrait envoye par le serveur.
     */
    private void clientTick() {
        Vector3f d = this.entityData.get(DIRECTION);
        if (this.clientStopped || d.lengthSquared() < 0.25F) {
            return;
        }
        long age = Math.min(this.level().getGameTime() - this.launchTick(), GunSpec.BLASTER_LIFE + 1L);
        double reach = Math.max(0.0, age) * GunSpec.BLASTER_SPEED;
        double start = this.clientDistance < 0.0
                ? Math.max(0.0, reach - TRAIL_MAX_TICKS * GunSpec.BLASTER_SPEED)
                : this.clientDistance;
        if (reach <= start) {
            return;
        }
        Vec3 from = this.predicted(start / GunSpec.BLASTER_SPEED);
        Vec3 to = this.predicted(reach / GunSpec.BLASTER_SPEED);
        BlockHitResult block = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        if (block.getType() == HitResult.Type.BLOCK) {
            to = block.getLocation();
            this.clientStopped = true;
        }
        this.clientDistance = reach;
        this.setPos(to.x, to.y, to.z);
        this.setDeltaMovement(Vec3.ZERO);
        trail(from, to);
    }

    private void impact(ServerLevel level, Vec3 at) {
        GunFire.sparks(level, at, true);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHULKER_BULLET_HIT, SoundSource.PLAYERS, 0.5F, 1.6F);
        this.discard();
    }

    private void trail(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int steps = Math.min(TRAIL_MAX_TICKS * (int) GunSpec.BLASTER_SPEED * TRAIL_PER_BLOCK,
                Math.max(1, (int) Math.ceil(d.length() * TRAIL_PER_BLOCK)));
        for (int i = 0; i < steps; i++) {
            double t = i / (double) steps;
            this.level().addParticle(ModParticles.GUN_BLASTER_BOLT.get(), from.x + d.x * t, from.y + d.y * t,
                    from.z + d.z * t, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0 * 128.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
