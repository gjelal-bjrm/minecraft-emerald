package com.emerald.jak.vehicle;

import com.emerald.haven.traffic.HavenTraffic;
import com.emerald.haven.traffic.TrafficDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Un vehicule civil de Haven -- voiture a trois places ou moto monoplace --,
 * solide, montable, pilotable, en rase-sol ou en voie haute.
 *
 * Voitures et motos sont la meme entite : seule la fiche du modele change
 * (VehicleSpec), avec ses constantes GOAL, ses boites et ses sieges. On dit
 * « la voiture » dans ce qui suit pour les deux.
 *
 * LE MODELE DU BATEAU. Le client du conducteur simule la voiture et envoie sa
 * position au serveur (LocalPlayer.tick, ServerboundMoveVehiclePacket) ; le
 * serveur simule les voitures sans conducteur, et fait autorite sur la montee,
 * les places et le mode de vol. Les autres clients interpolent.
 *
 * PAS DE GRAVITE VANILLA. Un serveur dedie expulse le conducteur d'un vehicule
 * « flottant » -- ce qu'est toujours une voiture a trois blocs du sol -- sauf
 * si le vehicule est sans gravite (ServerGamePacketListenerImpl.java:465-469).
 * La voiture est donc setNoGravity(true), et sa gravite est dans sa physique.
 *
 * LES PLACES SONT STABLES. Trois donnees d'entite portent l'identifiant de
 * l'occupant de chaque siege : on monte a la premiere place libre, la place 0
 * est celle du conducteur, et un depart ne fait pas glisser les autres. Une moto
 * n'a qu'un siege (VehicleSpec.seatCount) : un second passager est refuse.
 *
 * L'origine de l'entite est celle du modele du jeu : sieges et propulseurs du
 * code GOAL s'y lisent tels quels.
 *
 * L'EQUILIBRE (VehicleAttitude : tangage, roulis, vrille) se simule du cote qui
 * simule la voiture et se publie dans deux donnees d'entite : le serveur les pose
 * pour le trafic et les voitures sans conducteur, le client du conducteur les envoie
 * (VehicleAttitudePayload). Les autres clients dessinent ce qui est publie ; celui
 * qui reprend la simulation part de la.
 */
public class JakVehicleEntity extends Entity {

    /** Les trois voitures civiles retenues par le joueur, dans l'ordre des appartements. */
    public static final List<String> CARS = List.of("cara", "carb", "carc");
    /** Les trois motos civiles, monoplaces, dans l'ordre des appartements. */
    public static final List<String> BIKES = List.of("bikea", "bikeb", "bikec");
    public static final List<String> MODELS = List.of("cara", "carb", "carc", "bikea", "bikeb", "bikec");

    /** Trois parties de collision le long de la voiture (voir VehiclePart). */
    public static final int PART_COUNT = VehicleSpec.PARTS;

    /**
     * Demi-cote horizontal de la boite de rendu. Les voitures font 7,6 a 8,4
     * blocs : sans cette boite elargie, elles disparaitraient des qu'on ne
     * regarde plus leur centre. carc va jusqu'a 5,6 blocs de l'origine, bikec
     * jusqu'a 3,6.
     */
    private static final double CULL_RADIUS = 6.0;
    private static final double CULL_BELOW = 2.5;
    private static final double CULL_ABOVE = 2.5;

    /** Jusqu'ou l'on cherche un sol sous la portiere, en blocs : la voie haute est a 9 blocs de la rue. */
    private static final int DISMOUNT_DROP = 16;

    private static final String TAG_MODEL = "Modele";
    private static final String TAG_ROOM = "Appartement";
    private static final String TAG_MODE = "Zone";
    private static final String TAG_TRAFFIC = "Trafic";

    private static final int EMPTY = -1;

    private static final EntityDataAccessor<String> DATA_MODEL =
            SynchedEntityData.defineId(JakVehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(JakVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT_0 =
            SynchedEntityData.defineId(JakVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT_1 =
            SynchedEntityData.defineId(JakVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT_2 =
            SynchedEntityData.defineId(JakVehicleEntity.class, EntityDataSerializers.INT);
    private static final List<EntityDataAccessor<Integer>> SEATS = List.of(DATA_SEAT_0, DATA_SEAT_1, DATA_SEAT_2);
    /**
     * L'equilibre vu de tous (VehicleAttitude), en radians : tangage (nez en haut positif)
     * et roulis (gauche en haut positif). Le cote qui simule le vehicule le publie -- le
     * serveur pour le trafic et les vehicules sans conducteur, le client du conducteur par
     * VehicleAttitudePayload --, les autres clients le dessinent.
     */
    private static final EntityDataAccessor<Float> DATA_PITCH =
            SynchedEntityData.defineId(JakVehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL =
            SynchedEntityData.defineId(JakVehicleEntity.class, EntityDataSerializers.FLOAT);
    /** Ecart d'equilibre, en radians, sous lequel on ne republie pas (0,1 degre). */
    private static final float ATTITUDE_EPSILON = 0.0017F;

    private final VehiclePart[] parts;
    private final VehicleDynamics.Controls controls = new VehicleDynamics.Controls();

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;

    /** La gite visuelle des virages du trafic, en degres, cote client (voir updateLean). */
    private float lean;
    private float leanO;

    /** L'equilibre simule, du cote qui simule le vehicule (VehicleAttitude). */
    private final VehicleAttitude.State attitude = new VehicleAttitude.State();
    /** L'equilibre dessine, en degres, cote client : celui de ce tick et du precedent. */
    private float shownPitch;
    private float shownPitchO;
    private float shownRoll;
    private float shownRollO;
    /** Ce cote simulait-il le vehicule au tick precedent ? A la reprise, il part de l'equilibre publie. */
    private boolean simulatedHere;
    /** Le dernier equilibre envoye au serveur par le client du conducteur. */
    private float sentPitch;
    private float sentRoll;

    /** Ticks depuis le debut de la montee (MODE_MONTEE), -1 hors montee. Serveur. */
    private int transitionTicks = -1;
    /** Dernier plancher de voie haute trouve hors de la ville. */
    private double lastFloor = Double.NaN;
    /** Position au tick precedent, cote serveur : la vitesse d'une voiture conduite par un client. */
    private double serverX;
    private double serverY;
    private double serverZ;
    private boolean serverKnown;
    /** Le deplacement recu du client du conducteur au dernier tick, cote serveur (voir tick). */
    private Vec3 drivenMotion = Vec3.ZERO;
    /** Vitesse a plat de la tique precedente, vue du serveur : un choc la fait chuter d'un coup. */
    private double lastFlatSpeed;
    /** Tiques d'etourdissement apres un choc : le trafic ne pilote plus, il derive. */
    private int stun;
    /** La vitesse du debut de la tique : celle que l'autre lit dans un choc (VehicleImpacts). */
    private Vec3 impactBase = Vec3.ZERO;
    /** Tique ou un autre vehicule a deja regle notre choc pour nous (VehicleImpacts). */
    private long impactHandled = Long.MIN_VALUE;
    /** Le siege que vient de quitter le passager en train de descendre. */
    private int leavingSeat = EMPTY;

    /**
     * La cle de la place d'appartement dont c'est le vehicule (HavenCars.Place.key :
     * l'identifiant de la salle pour sa voiture, suivi de « _moto » pour sa moto),
     * ou null (vehicule d'essai).
     */
    @Nullable
    private String room;
    /** Commandes imposees par l'autotest quand personne ne conduit. */
    @Nullable
    private VehicleDynamics.Input autotestInput;
    /**
     * Le poids d'un pilote, impose par l'autotest. Le serveur de test n'a pas de
     * joueur a asseoir, et un porte-armure a la place 0 ne conduit pas
     * (getControllingPassenger) : sans ce drapeau, la hauteur pilotee ne se
     * mesurerait jamais en jeu.
     */
    private boolean autotestDriver;
    /**
     * Un client conduit, impose par l'autotest, cote serveur. Le serveur d'essai
     * n'a pas de joueur, et un FakePlayer ne monte dans rien : avec ce drapeau, le
     * serveur traite la voiture comme conduite par un client, et l'autotest rejoue
     * les positions que ce client enverrait.
     */
    private boolean autotestRemoteDriver;
    /** Le pilote du trafic de la ville paisible, cote serveur ; null pour un vehicule des appartements ou d'essai. */
    @Nullable
    private TrafficDriver traffic;

    public JakVehicleEntity(EntityType<? extends JakVehicleEntity> type, Level level) {
        super(type, level);
        VehiclePart[] made = new VehiclePart[PART_COUNT];
        for (int i = 0; i < PART_COUNT; i++) {
            made[i] = new VehiclePart(this, i);
        }
        this.parts = made;
        // les identifiants des parties suivent celui de la voiture, des deux cotes
        // (meme reserve que le dragon de l'End, EnderDragon.java:106)
        this.setId(ENTITY_COUNTER.getAndAdd(PART_COUNT + 1) + 1);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.blocksBuilding = true;
        this.refreshDimensions();
    }

    public static boolean isModel(String name) {
        return MODELS.contains(name);
    }

    /** Profondeur du point le plus bas sous l'origine du modele, en blocs. */
    public static float bottom(String model) {
        return (float) -VehicleSpec.of(model).minY;
    }

    // ------------------------------------------------------------- donnees

    public String model() {
        return this.entityData.get(DATA_MODEL);
    }

    public void setModel(String model) {
        if (isModel(model)) {
            this.entityData.set(DATA_MODEL, model);
        }
    }

    public VehicleSpec spec() {
        return VehicleSpec.of(this.model());
    }

    public int mode() {
        return this.entityData.get(DATA_MODE);
    }

    public void setMode(int mode) {
        this.entityData.set(DATA_MODE, mode);
        this.transitionTicks = mode == VehicleDynamics.MODE_MONTEE ? 0 : -1;
    }

    public int transitionTicks() {
        return this.transitionTicks;
    }

    @Nullable
    public String room() {
        return this.room;
    }

    public void setRoom(@Nullable String room) {
        this.room = room;
    }

    public VehicleDynamics.Controls controls() {
        return this.controls;
    }

    /**
     * La vitesse a prendre pour un choc : celle que connait le cote qui simule.
     *
     * Le client du conducteur a la sienne dans getDeltaMovement ; le serveur, pour une
     * voiture conduite par un client, n'a que le deplacement recu (drivenMotion).
     */
    public Vec3 impactVelocity() {
        return this.level().isClientSide || this.isControlledByLocalInstance()
                ? this.getDeltaMovement() : this.drivenMotion;
    }

    /**
     * La vitesse d'AVANT les chocs de la tique, celle que l'autre vehicule doit lire.
     *
     * Les deux vehicules calculent le meme choc, chacun de son cote, mais l'un tique
     * avant l'autre : si le second lisait la vitesse deja renvoyee du premier, il ne
     * verrait plus d'approche et ne partirait pas. Chacun fige donc la sienne au debut
     * de sa tique.
     */
    public Vec3 impactBase() {
        return this.impactBase;
    }

    /** Le choc de cette tique a deja ete regle par l'autre vehicule : ne pas le refaire. */
    public boolean impactHandled(long tick) {
        return this.impactHandled == tick;
    }

    public void setImpactHandled(long tick) {
        this.impactHandled = tick;
    }

    public double lastFlatSpeed() {
        return this.lastFlatSpeed;
    }

    void setLastFlatSpeed(double speed) {
        this.lastFlatSpeed = speed;
    }

    /** Etourdit le vehicule : le trafic lache son volant et derive (VehicleImpacts). */
    public void stun(int ticks) {
        this.stun = Math.max(this.stun, ticks);
    }

    public boolean stunned() {
        return this.stun > 0;
    }

    /** Une tique d'etourdissement passee ; vrai s'il en reste. */
    public boolean tickStun() {
        return this.stun > 0 && this.stun-- > 0;
    }

    public double lastFloor() {
        return this.lastFloor;
    }

    void setLastFloor(double floor) {
        this.lastFloor = floor;
    }

    public void setAutotestInput(@Nullable VehicleDynamics.Input input) {
        this.autotestInput = input;
    }

    public void setAutotestDriver(boolean driver) {
        this.autotestDriver = driver;
    }

    public void setAutotestRemoteDriver(boolean remote) {
        this.autotestRemoteDriver = remote;
    }

    @Nullable
    public TrafficDriver traffic() {
        return this.traffic;
    }

    public void setTraffic(@Nullable TrafficDriver traffic) {
        this.traffic = traffic;
    }

    /** Un pilote pese sur le vehicule : un joueur a la place 0, ou le poids impose par l'autotest. */
    public boolean hasDriverWeight() {
        return this.autotestDriver || this.getControllingPassenger() != null;
    }

    /** L'equilibre simule, pour le cote qui simule le vehicule. */
    public VehicleAttitude.State attitude() {
        return this.attitude;
    }

    /** Le tangage dessine, en degres, nez en haut positif (cote client). */
    public float pitch(float partialTick) {
        return Mth.lerp(partialTick, this.shownPitchO, this.shownPitch);
    }

    /**
     * Le roulis dessine, en degres, gauche en haut positif (cote client) : l'equilibre, plus
     * la gite visuelle des virages pour un vehicule sans pilote -- celui d'un pilote se
     * couche de lui-meme sous son poids (VehicleAttitude).
     */
    public float roll(float partialTick) {
        return Mth.lerp(partialTick, this.shownRollO, this.shownRoll) + Mth.lerp(partialTick, this.leanO, this.lean);
    }

    /**
     * Un choc sur l'equilibre seul : l'impulsion J (masse x blocs par tick, repere du monde)
     * au point {@code at} du monde. La vitesse, l'appelant la regle.
     *
     * Seule la part qui passe vraiment compte (VehicleAttitude.leverShare) : touche loin de
     * son centre, le vehicule tourne plus qu'il ne recule.
     */
    public void tilt(Vec3 impulse, Vec3 at) {
        VehicleSpec spec = this.spec();
        double yaw = Math.toRadians(this.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        // le repere du vehicule : gauche (cos, sin), avant (-sin, cos)
        double rx = at.x - this.getX();
        double rz = at.z - this.getZ();
        double localX = rx * cos + rz * sin;
        double localY = at.y - this.getY();
        double localZ = -rx * sin + rz * cos;
        double jx = (impulse.x * cos + impulse.z * sin) / VehicleSpec.TICK;
        double jy = impulse.y / VehicleSpec.TICK;
        double jz = (-impulse.x * sin + impulse.z * cos) / VehicleSpec.TICK;
        double length = Math.sqrt(jx * jx + jy * jy + jz * jz);
        if (length < 1.0E-9) {
            return;
        }
        double share = VehicleAttitude.leverShare(spec, localX, localY, localZ, jx / length, jy / length, jz / length);
        VehicleAttitude.impulse(spec, this.attitude, localX, localY, localZ, jx * share, jy * share, jz * share);
    }

    /**
     * Un choc entier -- vitesse et equilibre --, pour le cote qui simule le vehicule :
     * l'impulsion J (masse x blocs par tick, monde) au point {@code at}. Le souffle d'une
     * explosion (VehicleImpacts.blast).
     */
    public void applyImpulse(Vec3 impulse, Vec3 at) {
        this.setDeltaMovement(this.getDeltaMovement().add(impulse.scale(1.0 / this.spec().mass)));
        this.hasImpulse = true;
        this.tilt(impulse, at);
    }

    /**
     * L'equilibre du trafic pour un tick : il vole en voie haute, a sa hauteur d'equilibre,
     * sans pilote (HavenTraffic). Rend la vrille du tick, en degres de lacet.
     */
    public double stepTrafficAttitude() {
        VehicleSpec spec = this.spec();
        return VehicleAttitude.step(spec, this.attitude, new VehicleAttitude.Inputs(Double.NaN, Double.NaN,
                VehicleDynamics.MODE_HAUT, -VehicleDynamics.highHang(spec, false), 0.0, false, 0.0, false));
    }

    /** L'equilibre publie par le client du conducteur (VehicleAttitudePayload), cote serveur. */
    public void acceptDriverAttitude(float pitch, float roll) {
        if (!Float.isFinite(pitch) || !Float.isFinite(roll)) {
            return;
        }
        float max = (float) VehicleAttitude.MAX_TILT;
        this.entityData.set(DATA_PITCH, Mth.clamp(pitch, -max, max));
        this.entityData.set(DATA_ROLL, Mth.clamp(roll, -max, max));
    }

    /** L'equilibre publie, en radians : {tangage, roulis}. */
    public float[] publishedAttitude() {
        return new float[]{this.entityData.get(DATA_PITCH), this.entityData.get(DATA_ROLL)};
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MODEL, MODELS.get(0));
        builder.define(DATA_MODE, VehicleDynamics.MODE_SOL);
        for (EntityDataAccessor<Integer> seat : SEATS) {
            builder.define(seat, EMPTY);
        }
        builder.define(DATA_PITCH, 0.0F);
        builder.define(DATA_ROLL, 0.0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_MODEL.equals(key)) {
            this.refreshDimensions();
            if (this.parts != null) {
                for (VehiclePart part : this.parts) {
                    part.refreshDimensions();
                }
                this.syncParts();
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains(TAG_MODEL, Tag.TAG_STRING)) {
            this.setModel(tag.getString(TAG_MODEL));
        }
        this.room = tag.contains(TAG_ROOM, Tag.TAG_STRING) ? tag.getString(TAG_ROOM) : null;
        // personne ne conduit une voiture qu'on recharge : celle qui etait en l'air redescend
        this.setMode(tag.getInt(TAG_MODE) == VehicleDynamics.MODE_SOL
                ? VehicleDynamics.MODE_SOL : VehicleDynamics.MODE_DESCENTE);
        this.traffic = tag.contains(TAG_TRAFFIC, Tag.TAG_COMPOUND) ? TrafficDriver.load(tag.getCompound(TAG_TRAFFIC)) : null;
        if (this.traffic != null) {
            this.setMode(VehicleDynamics.MODE_HAUT);      // le trafic roule en voie haute, et ne redescend pas
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString(TAG_MODEL, this.model());
        if (this.room != null) {
            tag.putString(TAG_ROOM, this.room);
        }
        tag.putInt(TAG_MODE, this.mode());
        if (this.traffic != null) {
            tag.put(TAG_TRAFFIC, this.traffic.save());
        }
    }

    // ------------------------------------------------------------- boites

    /** Un carre de la largeur des parties, du bas du modele au-dessus du siege. */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        VehicleSpec spec = this.spec();
        return EntityDimensions.scalable((float) spec.boxSide, (float) (spec.boxTop - spec.boxBottom));
    }

    /** La boite descend sous l'origine : le bas du modele est jusqu'a 1,8 bloc plus bas. */
    @Override
    protected AABB makeBoundingBox() {
        return this.getDimensions(Pose.STANDING)
                .makeBoundingBox(this.getX(), this.getY() + this.spec().boxBottom, this.getZ());
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return this.parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (this.parts != null) {
            for (int i = 0; i < this.parts.length; i++) {
                this.parts[i].setId(id + i + 1);
            }
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        if (this.parts != null) {
            this.syncParts();
        }
    }

    @Override
    public void setYRot(float yRot) {
        super.setYRot(yRot);
        if (this.parts != null) {
            this.syncParts();
        }
    }

    /** Pose les parties le long de l'axe de la voiture, a son lacet. */
    public void syncParts() {
        VehicleSpec spec = this.spec();
        double yaw = Math.toRadians(this.getYRot());
        for (int i = 0; i < this.parts.length; i++) {
            double along = spec.partZ(i);
            this.parts[i].setPos(this.getX() - Math.sin(yaw) * along, this.getY(), this.getZ() + Math.cos(yaw) * along);
        }
    }

    /** La boite de la partie {@code index} pour une position et un lacet donnes. */
    public AABB partBox(int index, double x, double y, double z, float yawDegrees) {
        return partBox(this.spec(), index, x, y, z, yawDegrees);
    }

    /** La boite de la partie {@code index} d'un vehicule de fiche {@code spec}, sans entite. */
    public static AABB partBox(VehicleSpec spec, int index, double x, double y, double z, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double along = spec.partZ(index);
        double cx = x - Math.sin(yaw) * along;
        double cz = z + Math.cos(yaw) * along;
        double half = spec.boxSide / 2.0;
        return new AABB(cx - half, y + spec.boxBottom, cz - half, cx + half, y + spec.boxTop, cz + half);
    }

    /**
     * La boite de l'entite puis celles des parties d'un vehicule de fiche
     * {@code spec}, sans entite : ce que {@link #collisionBoxes()} rendrait a
     * cette position et a ce lacet. L'autotest s'en sert pour une place vide.
     */
    public static List<AABB> boxesAt(VehicleSpec spec, double x, double y, double z, float yaw) {
        double half = spec.boxSide / 2.0;
        return List.of(new AABB(x - half, y + spec.boxBottom, z - half, x + half, y + spec.boxTop, z + half),
                partBox(spec, 0, x, y, z, yaw), partBox(spec, 1, x, y, z, yaw), partBox(spec, 2, x, y, z, yaw));
    }

    /** La boite de l'entite puis celles des parties, a la position courante. */
    public List<AABB> collisionBoxes() {
        float yaw = this.getYRot();
        return List.of(this.getBoundingBox(),
                this.partBox(0, this.getX(), this.getY(), this.getZ(), yaw),
                this.partBox(1, this.getX(), this.getY(), this.getZ(), yaw),
                this.partBox(2, this.getX(), this.getY(), this.getZ(), yaw));
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(this.getX() - CULL_RADIUS, this.getY() - CULL_BELOW, this.getZ() - CULL_RADIUS,
                this.getX() + CULL_RADIUS, this.getY() + CULL_ABOVE, this.getZ() + CULL_RADIUS);
    }

    // ------------------------------------------------------------- solide et indestructible

    /** Indestructible : les coups ne font rien (un /kill la retire toujours). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return true;
    }

    /** Visable : c'est ce qui permet le clic droit, et F3 la nomme. */
    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    /** Solide comme un bateau : joueurs et creatures s'y cognent, et peuvent s'y tenir. */
    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.emeraldweapons.jak_vehicle." + this.model());
    }

    // ------------------------------------------------------------- places

    /** Le siege de ce passager selon le serveur, ou -1. */
    public int seatOf(Entity passenger) {
        for (int i = 0; i < SEATS.size(); i++) {
            if (this.entityData.get(SEATS.get(i)) == passenger.getId()) {
                return i;
            }
        }
        return EMPTY;
    }

    /** L'occupant d'un siege, s'il est encore a bord. */
    @Nullable
    public Entity occupant(int seat) {
        int id = this.entityData.get(SEATS.get(seat));
        if (id != EMPTY) {
            for (Entity passenger : this.getPassengers()) {
                if (passenger.getId() == id) {
                    return passenger;
                }
            }
        }
        return null;
    }

    /**
     * Le siege a utiliser pour placer un passager. Tant que la donnee du serveur
     * n'est pas arrivee au client, son rang dans la liste des passagers.
     */
    private int seatFor(Entity passenger) {
        int seat = this.seatOf(passenger);
        if (seat >= 0) {
            return seat;
        }
        int index = this.getPassengers().indexOf(passenger);
        return Math.max(0, Math.min(index, this.spec().seatCount() - 1));
    }

    /** Monter : le clic droit, et l'autotest. */
    public boolean board(Entity passenger) {
        return passenger.startRiding(this);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        if (this.traffic != null && passenger instanceof Player) {
            return false;                 // on ne monte pas dans le trafic (pas de detournement, pour l'instant)
        }
        return !this.isRemoved() && this.getPassengers().size() < Math.min(SEATS.size(), this.spec().seatCount());
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (this.level().isClientSide || this.seatOf(passenger) >= 0) {
            return;
        }
        for (int seat = 0; seat < SEATS.size(); seat++) {
            if (this.occupant(seat) == null) {
                this.entityData.set(SEATS.get(seat), passenger.getId());
                return;
            }
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (this.level().isClientSide) {
            return;
        }
        int seat = this.seatOf(passenger);
        if (seat >= 0) {
            this.entityData.set(SEATS.get(seat), EMPTY);
            this.leavingSeat = seat;
            if (seat == 0) {
                // le serveur reprend la physique : la voiture garde son elan, et son equilibre
                this.setDeltaMovement(this.drivenMotion);
                this.attitude.set(this.entityData.get(DATA_PITCH), this.entityData.get(DATA_ROLL));
                // le jeu remet la voie a zero quand on sort (hvehicle.gc:1113)
                this.controls.reset();
                if (this.mode() != VehicleDynamics.MODE_SOL) {
                    this.setMode(VehicleDynamics.MODE_DESCENTE);
                }
            }
        }
    }

    /** Seul un joueur assis a la place 0 conduit. */
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.occupant(0) instanceof Player player ? player : null;
    }

    @Override
    public boolean isControlledByLocalInstance() {
        return !this.autotestRemoteDriver && super.isControlledByLocalInstance();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        VehicleSpec spec = this.spec();
        int seat = this.seatFor(passenger);
        return new Vec3(spec.seat(seat, 0), spec.seat(seat, 1), spec.seat(seat, 2))
                .yRot(-this.getYRot() * Mth.DEG_TO_RAD);
    }

    /** Les passagers tournent avec la voiture, la tete libre de 105 degres de chaque cote (motif du bateau). */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        float turned = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        if (turned != 0.0F) {
            passenger.setYRot(passenger.getYRot() + turned);
            passenger.setYHeadRot(passenger.getYHeadRot() + turned);
        }
        this.clampRotation(passenger);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        this.clampRotation(passenger);
    }

    private void clampRotation(Entity passenger) {
        passenger.setYBodyRot(this.getYRot());
        float offset = Mth.wrapDegrees(passenger.getYRot() - this.getYRot());
        float clamped = Mth.clamp(offset, -105.0F, 105.0F);
        passenger.yRotO += clamped - offset;
        passenger.setYRot(passenger.getYRot() + clamped - offset);
        passenger.setYHeadRot(passenger.getYRot());
    }

    /**
     * Descendre a cote de la voiture, dans un endroit libre, SUR LE SOL.
     *
     * On essaie d'abord le flanc du siege quitte, puis l'autre flanc, puis
     * l'arriere et l'avant ; dans chaque colonne, du haut de la voiture vers le
     * bas, le premier sol ou le passager tient debout sans toucher un bloc NI la
     * voiture. La voiture plane a trois blocs, la voie haute a plus de dix : on
     * pose le passager au sol plutot que de le laisser tomber.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        VehicleSpec spec = this.spec();
        int seat = this.leavingSeat >= 0 ? this.leavingSeat : 0;
        this.leavingSeat = EMPTY;
        double side = spec.seat(seat, 0) >= 0.0 ? 1.0 : -1.0;
        double half = passenger.getBbWidth() / 2.0;
        // hors des carres tournes : leur demi-diagonale depasse la demi-largeur
        double reach = Math.max(Math.max(spec.maxX, -spec.minX), spec.boxSide * Math.sqrt(0.5)) + half + 0.25;
        double spacing = spec.partZ(0) - spec.centerZ;
        double[][] local = {
                {side * reach, spec.seat(seat, 2)},
                {-side * reach, spec.seat(seat, 2)},
                {side * reach, spec.centerZ + spacing},
                {side * reach, spec.centerZ - spacing},
                {-side * reach, spec.centerZ + spacing},
                {-side * reach, spec.centerZ - spacing},
                {0.0, spec.minZ - half - 1.0},
                {0.0, spec.maxZ + half + 1.0}};
        double yaw = Math.toRadians(this.getYRot());
        int top = Mth.floor(this.getY() + spec.boxTop);
        int bottom = Mth.floor(this.getY() + spec.boxBottom) - DISMOUNT_DROP;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double[] offset : local) {
            double x = this.getX() + offset[0] * Math.cos(yaw) - offset[1] * Math.sin(yaw);
            double z = this.getZ() + offset[1] * Math.cos(yaw) + offset[0] * Math.sin(yaw);
            for (int y = top; y >= bottom; y--) {
                pos.set(x, y, z);
                double floor = this.level().getBlockFloorHeight(pos);
                if (!DismountHelper.isBlockFloorValid(floor)) {
                    continue;
                }
                Vec3 at = new Vec3(x, y + floor, z);
                for (Pose pose : passenger.getDismountPoses()) {
                    AABB box = passenger.getLocalBoundsForPose(pose).move(at);
                    if (DismountHelper.canDismountTo(this.level(), passenger, box)
                            && this.level().noCollision(passenger, box)) {
                        passenger.setPose(pose);
                        return at;
                    }
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult result = super.interact(player, hand);
        if (result != InteractionResult.PASS) {
            return result;
        }
        if (player.isSecondaryUseActive() || player.getVehicle() == this) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return this.canAddPassenger(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return this.board(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    // ------------------------------------------------------------- vol

    /**
     * La touche de zone de survol, cote serveur.
     *
     * Montee : en MODE_MONTEE si l'origine est a plus de deux blocs sous la carte
     * de trafic (hvehicle-util.gc:294, qui compare la position a flight-level et
     * non au plancher), bornee a deux secondes ; sinon directement en voie haute.
     * Descente : jusqu'au contact des sondes.
     */
    public void toggleMode() {
        if (this.mode() == VehicleDynamics.MODE_SOL) {
            double traffic = VehiclePhysics.trafficY(this);
            if (Double.isNaN(traffic)) {
                return;
            }
            this.setMode(this.getY() + 2.0 < traffic ? VehicleDynamics.MODE_MONTEE : VehicleDynamics.MODE_HAUT);
        } else {
            this.setMode(VehicleDynamics.MODE_DESCENTE);
        }
    }

    /** Remet la voiture a l'arret et au rase-sol : apres une remise en place. */
    public void resetFlight() {
        this.setMode(VehicleDynamics.MODE_SOL);
        this.controls.reset();
        this.setDeltaMovement(Vec3.ZERO);
        this.drivenMotion = Vec3.ZERO;
        this.lerpSteps = 0;
        this.serverKnown = false;
        this.attitude.reset();
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_PITCH, 0.0F);
            this.entityData.set(DATA_ROLL, 0.0F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.impactBase = this.impactVelocity();
        boolean client = this.level().isClientSide;
        if (!client) {
            this.serverTick();
            if (this.isRemoved()) {
                return;
            }
        }
        this.tickLerp();
        boolean simulating = this.traffic != null ? !client : this.isControlledByLocalInstance();
        if (simulating && !this.simulatedHere) {
            // on reprend la simulation : on part de l'equilibre que tout le monde voit
            this.attitude.set(this.entityData.get(DATA_PITCH), this.entityData.get(DATA_ROLL));
        }
        this.simulatedHere = simulating;
        if (!client && this.traffic != null) {
            // le trafic : le serveur conduit sur les voies de Jak 3, a la place de la physique de vol
            HavenTraffic.drive(this);
            if (this.isRemoved()) {
                return;
            }
        } else if (this.isControlledByLocalInstance()) {
            VehiclePhysics.tick(this, this.input());
        } else {
            if (!client) {
                this.drivenMotion = this.serverKnown
                        ? new Vec3(this.getX() - this.serverX, this.getY() - this.serverY, this.getZ() - this.serverZ)
                        : Vec3.ZERO;
                // PAS DANS getDeltaMovement, comme le bateau (Boat.tick). Le serveur
                // renvoie tous les trois ticks la vitesse de ses entites a ceux qui
                // les voient, conducteur compris (ServerEntity.sendChanges,
                // ClientboundSetEntityMotionPacket), et le client la pose telle quelle
                // (Entity.lerpMotion). Ce deplacement vaut zero sur un tick du serveur
                // ou n'est arrive aucun paquet du conducteur -- les deux horloges
                // derivent, et un a-coup du client suffit : la voiture du conducteur
                // s'arretait net en pleine course. Voir aussi lerpMotion.
                this.setDeltaMovement(Vec3.ZERO);
            }
            this.syncParts();
        }
        if (simulating) {
            this.publishAttitude(client);
        }
        if (client) {
            this.updateLean();
            this.updateAttitudeView(simulating);
        } else {
            this.serverX = this.getX();
            this.serverY = this.getY();
            this.serverZ = this.getZ();
            this.serverKnown = true;
            this.pushAside();
            // les chocs : ce qu'on renverse, puis la vitesse perdue d'un coup (VehicleImpacts)
            VehicleImpacts.ram(this);
            VehicleImpacts.watch(this);
        }
    }

    private void serverTick() {
        if (this.tickCount % 20 == 0) {
            HavenCars.carTick(this);
            if (this.isRemoved()) {
                return;
            }
        }
        int mode = this.mode();
        if (mode == VehicleDynamics.MODE_MONTEE) {
            double traffic = VehiclePhysics.trafficY(this);
            // La vitesse du tick precedent : celle de la physique si le serveur simule,
            // le deplacement recu si un client conduit (voir tick). Pas getY() - serverY :
            // a cet instant la voiture n'a pas encore bouge, l'ecart vaut toujours zero,
            // et la montee finissait en pleine course (mesure : fin au tick 7, 3,2 blocs
            // de depassement au lieu de 1,4).
            double vy = this.serverMotion().y;
            // fin de montee : l'origine a moins de 2 m de la carte de trafic, a moins
            // de 2 m/s (hvehicle.gc:577-589) ; au-dela de deux secondes, on redescend
            if (!Double.isNaN(traffic) && Math.abs(this.getY() - traffic) < 2.0 && Math.abs(vy) < 0.1) {
                this.setMode(VehicleDynamics.MODE_HAUT);
            } else if (++this.transitionTicks > VehicleDynamics.TRANSITION_TICKS) {
                this.setMode(VehicleDynamics.MODE_DESCENTE);
            }
        } else if (mode == VehicleDynamics.MODE_DESCENTE) {
            VehicleSpec spec = this.spec();
            if (VehicleDynamics.grounded(VehiclePhysics.probe(this, spec.thrusterFrontZ),
                    VehiclePhysics.probe(this, spec.thrusterRearZ))) {
                this.setMode(VehicleDynamics.MODE_SOL);
            }
        }
    }

    /** La vitesse vue du serveur : celle de sa physique, ou le deplacement recu du conducteur. */
    public Vec3 serverMotion() {
        return this.isControlledByLocalInstance() ? this.getDeltaMovement() : this.drivenMotion;
    }

    /**
     * Le client du conducteur garde sa vitesse : c'est lui qui simule la voiture.
     * Un paquet de vitesse du serveur ne peut que la ramener en arriere -- a zero
     * quand le serveur n'a recu aucune position au tick precedent.
     * ClientPacketListener ne protege que les positions d'une entite pilotee
     * (handleMoveEntity, handleTeleportEntity), pas sa vitesse (handleSetEntityMotion).
     */
    @Override
    public void lerpMotion(double x, double y, double z) {
        if (!this.isControlledByLocalInstance()) {
            super.lerpMotion(x, y, z);
        }
    }

    /** Ce que demande le conducteur : les memes champs des deux cotes (ServerboundPlayerInputPacket). */
    private VehicleDynamics.Input input() {
        LivingEntity driver = this.getControllingPassenger();
        if (driver != null) {
            int steer = driver.xxa > 0.1F ? 1 : driver.xxa < -0.1F ? -1 : 0;
            return new VehicleDynamics.Input(driver.zza > 0.1F, driver.zza < -0.1F, steer);
        }
        return this.autotestInput != null ? this.autotestInput : VehicleDynamics.Input.NONE;
    }

    /**
     * Ecarte les creatures et les joueurs que la voiture recouvre.
     *
     * La voiture ne se cogne pas aux creatures (elles ne sont pas « collidables ») :
     * sans cette poussee, elle roulerait au travers. Un joueur ne recoit sa
     * poussee que si le serveur marque son mouvement.
     */
    private void pushAside() {
        List<AABB> boxes = this.collisionBoxes();
        AABB all = boxes.get(0);
        for (AABB box : boxes) {
            all = all.minmax(box);
        }
        Vec3 motion = this.serverMotion();
        double strength = 0.15 + Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        for (LivingEntity entity : this.level().getEntitiesOfClass(LivingEntity.class, all.inflate(0.05),
                e -> e.isPushable() && !e.isSpectator() && !e.isPassengerOfSameVehicle(this))) {
            for (AABB box : boxes) {
                if (!box.intersects(entity.getBoundingBox())) {
                    continue;
                }
                double dx = entity.getX() - box.getCenter().x;
                double dz = entity.getZ() - box.getCenter().z;
                double length = Math.sqrt(dx * dx + dz * dz);
                if (length < 1.0E-4) {
                    dx = 1.0;
                    dz = 0.0;
                    length = 1.0;
                }
                entity.push(dx / length * strength, 0.0, dz / length * strength);
                if (entity instanceof ServerPlayer) {
                    entity.hurtMarked = true;
                }
                break;
            }
        }
    }

    /**
     * La gite visuelle des virages, sans pilote : le trafic se couche un peu quand il tourne.
     * Un vehicule pilote n'en a pas : c'est le poids de son pilote qui le couche, pour de
     * vrai (VehicleAttitude).
     */
    private void updateLean() {
        this.leanO = this.lean;
        double target = 0.0;
        if (this.getControllingPassenger() == null) {
            double dx = this.getX() - this.xo;
            double dz = this.getZ() - this.zo;
            target = VehicleDynamics.rollTarget(this.spec(), Mth.wrapDegrees(this.getYRot() - this.yRotO),
                    Math.sqrt(dx * dx + dz * dz));
        }
        this.lean += (float) ((target - this.lean) * 0.3);
    }

    /** L'equilibre a dessiner : le sien si ce client simule le vehicule, sinon celui publie. */
    private void updateAttitudeView(boolean simulating) {
        this.shownPitchO = this.shownPitch;
        this.shownRollO = this.shownRoll;
        double pitch = simulating ? this.attitude.pitch : this.entityData.get(DATA_PITCH);
        double roll = simulating ? this.attitude.roll : this.entityData.get(DATA_ROLL);
        this.shownPitch = (float) Math.toDegrees(pitch);
        this.shownRoll = (float) Math.toDegrees(roll);
    }

    /**
     * Publie l'equilibre simule ici : le serveur dans la donnee d'entite, le client du
     * conducteur au serveur (qui la pose a son tour). Rien tant qu'il ne bouge pas.
     */
    private void publishAttitude(boolean client) {
        float pitch = (float) this.attitude.pitch;
        float roll = (float) this.attitude.roll;
        if (client) {
            if (Math.abs(pitch - this.sentPitch) > ATTITUDE_EPSILON || Math.abs(roll - this.sentRoll) > ATTITUDE_EPSILON) {
                this.sentPitch = pitch;
                this.sentRoll = roll;
                JakVehicleClient.sendAttitude(pitch, roll);
            }
            return;
        }
        if (Math.abs(pitch - this.entityData.get(DATA_PITCH)) > ATTITUDE_EPSILON
                || Math.abs(roll - this.entityData.get(DATA_ROLL)) > ATTITUDE_EPSILON) {
            this.entityData.set(DATA_PITCH, pitch);
            this.entityData.set(DATA_ROLL, roll);
        }
    }

    // ------------------------------------------------------------- interpolation (motif du bateau)

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpSteps = Math.max(3, steps);
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? this.lerpYRot : this.getYRot();
    }

    private void tickLerp() {
        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        }
        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ,
                    this.lerpYRot, this.getXRot());
            this.lerpSteps--;
        }
    }
}
