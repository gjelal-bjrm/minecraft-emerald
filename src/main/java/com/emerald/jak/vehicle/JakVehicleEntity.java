package com.emerald.jak.vehicle;

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

    private final VehiclePart[] parts;
    private final VehicleDynamics.Controls controls = new VehicleDynamics.Controls();

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;

    private float roll;
    private float rollO;

    /** Ticks depuis le debut de la montee (MODE_MONTEE), -1 hors montee. Serveur. */
    private int transitionTicks = -1;
    /** Dernier plancher de voie haute trouve hors de la ville. */
    private double lastFloor = Double.NaN;
    /** Position au tick precedent, cote serveur : la vitesse d'une voiture conduite par un client. */
    private double serverX;
    private double serverY;
    private double serverZ;
    private boolean serverKnown;
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

    /** Un pilote pese sur le vehicule : un joueur a la place 0, ou le poids impose par l'autotest. */
    public boolean hasDriverWeight() {
        return this.autotestDriver || this.getControllingPassenger() != null;
    }

    public float roll(float partialTick) {
        return Mth.lerp(partialTick, this.rollO, this.roll);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MODEL, MODELS.get(0));
        builder.define(DATA_MODE, VehicleDynamics.MODE_SOL);
        for (EntityDataAccessor<Integer> seat : SEATS) {
            builder.define(seat, EMPTY);
        }
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
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString(TAG_MODEL, this.model());
        if (this.room != null) {
            tag.putString(TAG_ROOM, this.room);
        }
        tag.putInt(TAG_MODE, this.mode());
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
        this.lerpSteps = 0;
        this.serverKnown = false;
    }

    @Override
    public void tick() {
        super.tick();
        boolean client = this.level().isClientSide;
        if (!client) {
            this.serverTick();
            if (this.isRemoved()) {
                return;
            }
        }
        this.tickLerp();
        if (this.isControlledByLocalInstance()) {
            VehiclePhysics.tick(this, this.input());
        } else {
            if (!client && this.serverKnown) {
                this.setDeltaMovement(this.getX() - this.serverX, this.getY() - this.serverY,
                        this.getZ() - this.serverZ);
            }
            this.syncParts();
        }
        if (client) {
            this.updateRoll();
        } else {
            this.serverX = this.getX();
            this.serverY = this.getY();
            this.serverZ = this.getZ();
            this.serverKnown = true;
            this.pushAside();
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
            double vy = this.getDeltaMovement().y;
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
        Vec3 motion = this.getDeltaMovement();
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

    private void updateRoll() {
        this.rollO = this.roll;
        double dx = this.getX() - this.xo;
        double dz = this.getZ() - this.zo;
        double target = VehicleDynamics.rollTarget(this.spec(), Mth.wrapDegrees(this.getYRot() - this.yRotO),
                Math.sqrt(dx * dx + dz * dz));
        this.roll += (float) ((target - this.roll) * 0.3);
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
