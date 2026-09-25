package com.emerald.jak.board;

import com.emerald.haven.Haven;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * LE JET-BOARD DE JAK 3 (cahier §100) : la planche sous les pieds du joueur, sortie par sa touche
 * (JetBoard.toggle) et rangee de meme ; sans personne dessus, elle disparait.
 *
 * TOUT VIENT DU JEU (engine/target/board, target-board.gc), en blocs et en tiques : 1 m = 1 bloc,
 * 20 tiques par seconde. Dans Jak 3 la planche n'a pas de physique a elle : c'est le controleur
 * de Jak avec les reglages de la planche. Ici :
 *  - AU SOL, elle vise 25 m/s bouton tenu, 10 m/s lachee -- elle glisse encore, le jeu n'a pas de
 *    frein --, 35 au plus ; 18,75 m/s2 d'acceleration, une constante de temps d'une seconde un
 *    tiers. S freine (31,25 m/s2, l'acceleration arriere du jeu). On dirige comme au baton de Jak,
 *    par rapport au regard : Z va ou l'on regarde, Q et D a gauche et a droite ; la planche tourne
 *    vers la ou l'on veut aller a 180 degres par seconde, 270 en l'air, et sa vitesse la suit.
 *  - ELLE PLANE a un demi-bloc du sol, a huit dixiemes sur l'eau -- elle va sur l'eau comme sur la
 *    pierre, sans ralentir (water.gc:266-276) --, et monte d'elle-meme une marche d'un bloc : le jeu
 *    ne fait monter une marche qu'a ce qui est pose au sol, et la planche, qui plane, butait contre
 *    la premiere marche de l'escalier du quai ouest (banc de la course) ; elle se dit donc posee
 *    pendant son pas quand elle plane.
 *  - LE SAUT se charge en tenant Espace, avec la barre de saut du cheval (PlayerRideableJumping) :
 *    d'un metre a six et demi (le saut du jeu va d'un a trois et demi, la charge « launch » en
 *    ajoute trois et demi). La gravite de la planche est celle du jeu : 60 m/s2.
 *  - SUR UN RAIL (JetBoardRails), elle s'accroche d'elle-meme quand on y retombe -- le jeu demande
 *    le bouton carre tenu --, garde sa vitesse, freine un peu (3,75 m/s2 lente, 0,75 a pleine
 *    vitesse), gagne a la descente (12 m/s2 fois la pente) et perd a la montee (6) ; Z pousse
 *    (+10 m/s2), S freine. Au bout du rail, elle s'envole droit devant ; Espace saute du rail.
 *    Sous un demi-metre par seconde, elle tombe de cote.
 *  - LES TREMPLINS (JetBoardPads) : au sol, en passant sur l'un d'eux, elle part sur le rail qu'il
 *    vise, sans qu'on la dirige jusqu'au sommet ; il faut en etre sorti pour qu'il relance.
 *  - PAS DE CHUTE QUI BLESSE : comme Jak, on retombe de toute hauteur (le jeu passerait sinon la
 *    chute de la planche a son passager).
 *
 * QUI SIMULE : le client du joueur dessus, comme le bateau et les vehicules de Haven ; le serveur
 * recoit ses positions. Sans gravite du jeu (setNoGravity) : un serveur dedie expulse le
 * passager d'un vehicule « flottant ». Le banc la fait rouler au serveur, sans passager, avec
 * des commandes imposees (setAutotestInput).
 */
public class JetBoardEntity extends Entity implements PlayerRideableJumping {

    static final double TICK = 0.05;
    /** Croisiere au sol, bouton tenu, et plafond, en m/s (target-board.gc:105). */
    static final double CRUISE_MS = 25.0;
    static final double CAP_MS = 35.0;
    /** Acceleration, et freinage (l'acceleration arriere du jeu), en m/s2. */
    static final double ACCEL_MS2 = 18.75;
    static final double BRAKE_MS2 = 31.25;
    /** Bouton lache : la planche vise 0,4 x la croisiere, elle glisse a 10 m/s. */
    static final double COAST = 0.4;
    /** La planche tourne vers ou l'on veut aller, en degres par tique : 180 par seconde au sol, 270 en l'air. */
    static final float TURN_GROUND = 9.0F;
    static final float TURN_AIR = 13.5F;
    /** Sa vitesse suit son nez, en degres par tique : 360 par seconde au sol, 60 dans un saut. */
    static final float FOLLOW_GROUND = 18.0F;
    static final float FOLLOW_AIR = 3.0F;
    /** La gravite de la planche, m/s2, et sa vitesse de chute limite, m/s (target-board.gc:1864-1913). */
    static final double GRAVITY_MS2 = 60.0;
    static final double TERMINAL_MS = 40.0;
    /** Ou plane la planche : au-dessus du sol, de l'eau, d'un rail, en blocs. */
    static final double HOVER = 0.5;
    static final double HOVER_WATER = 0.8;
    static final double HOVER_RAIL = 0.35;
    /** Le saut, en blocs de haut : a vide, et charge a fond. */
    static final double JUMP_MIN = 1.0;
    static final double JUMP_MAX = 6.5;
    /** Un saut ne repart pas avant tant de tiques. */
    static final int JUMP_GAP = 4;
    /** Un saut demande attend tant de tiques qu'on touche le sol : un appui juste avant compte. */
    static final int JUMP_BUFFER = 5;
    /** Le rail : on s'y accroche a tant de blocs de cote, dans cette bande de hauteur au-dessus de lui. */
    static final double RAIL_REACH = 1.6;
    static final double RAIL_BELOW = -0.5;
    static final double RAIL_ABOVE = 1.4;
    /** Pas de rail dans les 0,4 s qui suivent le precedent, ni dans les 0,1 s d'un saut. */
    static final int RAIL_COOLDOWN = 8;
    static final int RAIL_AFTER_JUMP = 2;
    /** La glisse, en m/s2 (target-board.gc:2715-2756). */
    static final double RAIL_FRICTION_REST = 3.75;
    static final double RAIL_FRICTION_FAST = 0.75;
    static final double RAIL_PUSH = 10.0;
    static final double RAIL_BRAKE = 10.0;
    static final double RAIL_DOWNHILL = 12.0;
    static final double RAIL_UPHILL = 6.0;
    static final double RAIL_MIN_SPEED = 0.5;
    /** Le coup de pouce en quittant le bout d'un rail, m/s vers le haut. */
    static final double RAIL_EXIT_LIFT = 2.0;
    /** Ou se tiennent les pieds du joueur : le dessus de la planche. */
    static final double BOARD_TOP = 0.04;
    /** Apres le sommet du saut d'un tremplin, encore tant de tiques sans direction : le temps d'accrocher. */
    static final int LAUNCH_LOCK_AFTER = 6;

    /** Ce que demande le joueur (ou le banc) : ses touches avant et de cote, et la ou il regarde. */
    public record Input(float forward, float side, float yaw) {
    }

    /** La glisse en cours. */
    private static final class Grind {
        final JetBoardRails.Rail rail;
        int segment;
        double along;
        final int sense;
        double speed;

        Grind(JetBoardRails.Rail rail, int segment, double along, int sense, double speed) {
            this.rail = rail;
            this.segment = segment;
            this.along = along;
            this.sense = sense;
            this.speed = speed;
        }
    }

    @Nullable
    private Grind grind;
    private boolean grounded;
    private int sinceJump = JUMP_GAP;
    private int sinceGrind = RAIL_COOLDOWN;
    private int pendingJump = -1;
    private int jumpWait;
    /** Lancee par un tremplin : tant de tiques encore sans direction. */
    private int launchLock;
    /** Un tremplin ne relance qu'une planche qui est passee au sol hors de tout tremplin depuis. */
    private boolean padArmed = true;
    /** Commandes imposees par le banc, sans passager ; null en jeu. */
    @Nullable
    private Input autotestInput;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;

    /** L'inclinaison dessinee, en degres, cote client : tangage, roulis, et ceux de la tique d'avant. */
    private float shownPitch;
    private float shownPitchO;
    private float shownRoll;
    private float shownRollO;

    public JetBoardEntity(EntityType<? extends JetBoardEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.blocksBuilding = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    // ------------------------------------------------------------- vie

    @Override
    public void tick() {
        super.tick();
        Entity rider = this.getFirstPassenger();
        if (!this.level().isClientSide && this.autotestInput == null
                && (!(rider instanceof Player) || !Haven.is(this.level()))) {
            // personne dessus, ou sortie de la ville : la planche retourne dans sa case
            this.discard();
            return;
        }
        this.tickLerp();
        if (this.isControlledByLocalInstance()) {
            Input input = rider instanceof Player player
                    ? new Input(player.zza, player.xxa, player.getYRot()) : this.autotestInput;
            if (input != null) {
                this.simulate(input);
            }
        }
        if (this.level().isClientSide) {
            this.updateTilt();
        }
    }

    /** Une tique de la planche, du cote qui la simule. */
    private void simulate(Input input) {
        this.sinceJump++;
        this.sinceGrind++;
        if (this.launchLock > 0) {
            this.launchLock--;
        }
        if (this.grind != null) {
            this.grindTick(input);
            return;
        }
        Vec3 v = this.getDeltaMovement().scale(20.0);
        double hs = Math.hypot(v.x, v.z);
        double vy = v.y;
        float heading = hs > 1.0E-3 ? (float) Math.toDegrees(Math.atan2(-v.x, v.z)) : this.getYRot();
        // L'ATTERRISSAGE EST UN ETAT : on retombe en descendant, si l'on passe sa hauteur dans la tique ; on
        // decolle en sautant, ou quand le sol se derobe. Deduit de la vitesse du moment, le premier essai
        // rebondissait sous la surface de l'eau apres un grand saut, et le saut suivant se perdait (banc).
        Surface ground = this.probe(Math.max(0.0, -vy * TICK));
        double gap = ground == null ? Double.POSITIVE_INFINITY : this.getY() - (ground.y() + ground.hover());
        if (!this.grounded) {
            this.grounded = vy <= 0.0 && gap + vy * TICK <= 0.3;
        } else if (gap > 0.6) {
            this.grounded = false;
        }
        boolean onGround = this.grounded;
        if (onGround) {
            JetBoardPads.Pad pad = JetBoardPads.under(this.position());
            if (pad == null) {
                this.padArmed = true;
            } else if (this.padArmed) {
                this.launch(pad);
                return;
            }
        }
        boolean brake = input.forward() < -0.1F;
        Vec3 wish = wish(input);
        if (wish != null && this.launchLock <= 0) {
            float toward = (float) Math.toDegrees(Math.atan2(-wish.x, wish.z));
            this.setYRot(approach(this.getYRot(), toward, onGround ? TURN_GROUND : TURN_AIR));
        }
        if (onGround) {
            if (brake) {
                hs = Math.max(0.0, hs - BRAKE_MS2 * TICK);
            } else {
                double k = wish != null ? 1.0 : COAST;
                hs += ACCEL_MS2 * k * TICK;
                hs *= 1.0 - ACCEL_MS2 * TICK / CRUISE_MS;
            }
            hs = Math.min(hs, CAP_MS);
        }
        heading = approach(heading, this.getYRot(), onGround ? FOLLOW_GROUND : FOLLOW_AIR);
        double vx = -Mth.sin(heading * Mth.DEG_TO_RAD) * hs;
        double vz = Mth.cos(heading * Mth.DEG_TO_RAD) * hs;
        if (onGround) {
            // plane a sa hauteur : la moitie de l'ecart par tique
            vy = -gap * 20.0 * 0.5;
            if (this.pendingJump >= 0 && this.sinceJump > JUMP_GAP) {
                vy = jumpSpeed(this.pendingJump);
                this.sinceJump = 0;
                this.pendingJump = -1;
                this.grounded = false;
            }
        } else {
            vy = Math.max(vy - GRAVITY_MS2 * TICK, -TERMINAL_MS);
        }
        if (this.pendingJump >= 0 && ++this.jumpWait > JUMP_BUFFER) {
            this.pendingJump = -1;
        }
        this.step(new Vec3(vx, vy, vz).scale(TICK));
        this.tryRail();
    }

    /** Avancer d'un pas, en butant sur les blocs ; la vitesse garde ce qui n'a pas bute. */
    private void step(Vec3 wanted) {
        Vec3 before = this.position();
        // posee pendant le pas si elle plane : c'est ce qui lui fait monter les marches (maxUpStep)
        this.setOnGround(this.grounded);
        this.move(MoverType.SELF, wanted);
        Vec3 moved = this.position().subtract(before);
        this.setDeltaMovement(new Vec3(this.horizontalCollision ? moved.x : wanted.x,
                this.verticalCollision ? 0.0 : wanted.y, this.horizontalCollision ? moved.z : wanted.z));
    }

    /** Un tremplin : la planche part de son centre, avec sa vitesse, vers son rail. */
    private void launch(JetBoardPads.Pad pad) {
        this.padArmed = false;
        this.grounded = false;
        this.pendingJump = -1;
        this.sinceJump = 0;
        this.launchLock = pad.flight() + LAUNCH_LOCK_AFTER;
        this.setPos(pad.at().x, pad.at().y + HOVER, pad.at().z);
        this.setYRot(pad.yaw());
        this.step(pad.launch().scale(TICK));
        if (this.level().isClientSide) {
            this.level().playLocalSound(pad.at().x, pad.at().y, pad.at().z, SoundEvents.BREEZE_JUMP, SoundSource.NEUTRAL,
                    1.0F, 0.8F, false);
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2.0 / 24.0;
                this.level().addParticle(JetBoardPads.SPARK, pad.at().x + Math.cos(a) * 0.9, pad.at().y + 0.1,
                        pad.at().z + Math.sin(a) * 0.9, Math.cos(a) * 0.5, 3.0, Math.sin(a) * 0.5);
            }
        }
    }

    /** Ou le joueur veut aller, dans le monde, d'apres ses touches et son regard ; null s'il ne demande rien. */
    @Nullable
    private static Vec3 wish(Input input) {
        double forward = Math.max(0.0F, input.forward());
        double side = input.side();
        if (forward < 0.1 && Math.abs(side) < 0.1) {
            return null;
        }
        double yaw = Math.toRadians(input.yaw());
        double x = side * Math.cos(yaw) - forward * Math.sin(yaw);
        double z = forward * Math.cos(yaw) + side * Math.sin(yaw);
        double length = Math.hypot(x, z);
        return length < 1.0E-6 ? null : new Vec3(x / length, 0.0, z / length);
    }

    /**
     * La vitesse de depart d'un saut de cette charge (0 a 100), en m/s : celle qui met le sommet a la
     * hauteur voulue AU PAS D'UNE TIQUE -- racine de 2gh seule depassait de 0,7 bloc a pleine charge
     * (banc) ; le jeu corrige de meme ses images a 60 Hz, de g/120 (target.gc:585-658).
     */
    static double jumpSpeed(int power) {
        double height = JUMP_MIN + (JUMP_MAX - JUMP_MIN) * Mth.clamp(power, 0, 100) / 100.0;
        double half = GRAVITY_MS2 * TICK / 2.0;
        return Math.sqrt(2.0 * GRAVITY_MS2 * height + half * half) - half;
    }

    /** Le sol sous la planche -- ou l'eau, sur laquelle elle va comme sur la pierre -- et la hauteur a y garder. */
    private record Surface(double y, double hover) {
    }

    /** Le sol sous la planche, jusqu'a {@code extra} blocs plus bas encore : ce qu'elle descend dans la tique. */
    @Nullable
    private Surface probe(double extra) {
        Vec3 from = this.position().add(0.0, 0.2, 0.0);
        Vec3 to = from.add(0.0, -(HOVER_WATER + 1.2 + extra), 0.0);
        BlockHitResult hit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, this));
        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        boolean water = this.level().getFluidState(hit.getBlockPos()).is(FluidTags.WATER);
        return new Surface(hit.getLocation().y, water ? HOVER_WATER : HOVER);
    }

    // ------------------------------------------------------------- rails

    /** Retomber sur un rail : on s'y accroche, avec sa vitesse, dans le sens ou l'on va. */
    private void tryRail() {
        if (this.sinceGrind < RAIL_COOLDOWN || this.sinceJump < RAIL_AFTER_JUMP) {
            return;
        }
        Vec3 v = this.getDeltaMovement().scale(20.0);
        if (v.y > 2.0) {
            return;                                   // encore en train de monter
        }
        JetBoardRails.Hit hit = JetBoardRails.nearest(this.position(), RAIL_REACH, RAIL_BELOW, RAIL_ABOVE);
        if (hit == null) {
            return;
        }
        Vec3 along = JetBoardRails.direction(hit.rail(), hit.segment(), 1);
        double flat = Math.hypot(v.x, v.z);
        double dot = flat > 0.5 ? v.x * along.x + v.z * along.z
                : -Mth.sin(this.getYRot() * Mth.DEG_TO_RAD) * along.x + Mth.cos(this.getYRot() * Mth.DEG_TO_RAD) * along.z;
        this.grind = new Grind(hit.rail(), hit.segment(), hit.along(), dot >= 0.0 ? 1 : -1, flat);
        this.grounded = false;
        this.launchLock = 0;
        Vec3 on = hit.point().add(0.0, HOVER_RAIL, 0.0);
        this.setPos(on.x, on.y, on.z);
    }

    private void grindTick(Input input) {
        Grind g = this.grind;
        Vec3 dir = JetBoardRails.direction(g.rail, g.segment, g.sense);
        double friction = Mth.lerp(Math.min(1.0, g.speed / CAP_MS), RAIL_FRICTION_REST, RAIL_FRICTION_FAST);
        g.speed -= friction * TICK;
        Vec3 wish = wish(input);
        if (input.forward() < -0.1F) {
            g.speed -= RAIL_BRAKE * TICK;
        } else if (wish != null) {
            double flat = Math.hypot(dir.x, dir.z);
            double push = flat < 1.0E-6 ? 0.0 : (wish.x * dir.x + wish.z * dir.z) / flat;
            if (push > 0.3) {
                g.speed += RAIL_PUSH * (1.0 - g.speed / CAP_MS) * TICK;
            }
        }
        // la pente : dir.y est le sinus, negatif a la descente
        g.speed += (dir.y < 0.0 ? -dir.y * RAIL_DOWNHILL : -dir.y * RAIL_UPHILL) * TICK;
        g.speed = Mth.clamp(g.speed, 0.0, CAP_MS);
        if (this.pendingJump >= 0) {
            // on saute du rail : sa vitesse, et le saut
            double vy = jumpSpeed(this.pendingJump);
            this.pendingJump = -1;
            this.leaveRail(dir.scale(g.speed).add(0.0, vy, 0.0));
            this.sinceJump = 0;
            return;
        }
        if (g.speed < RAIL_MIN_SPEED) {
            // trop lent : on tombe de cote (target-board.gc:2540-2579)
            Vec3 side = new Vec3(-dir.z, 0.0, dir.x).normalize().scale(3.0);
            this.leaveRail(side);
            return;
        }
        double left = g.speed * TICK;
        while (left > 0.0) {
            double length = JetBoardRails.length(g.rail, g.segment);
            double room = g.sense > 0 ? length - g.along : g.along;
            if (left <= room) {
                g.along += g.sense * left;
                left = 0.0;
                continue;
            }
            left -= room;
            int next = g.segment + g.sense;
            if (next < 0 || next >= g.rail.points().size() - 1) {
                // le bout du rail : la planche s'envole droit devant
                Vec3 end = JetBoardRails.at(g.rail, g.segment, g.sense > 0 ? length : 0.0).add(0.0, HOVER_RAIL, 0.0);
                this.setPos(end.x, end.y, end.z);
                this.leaveRail(dir.scale(g.speed).add(0.0, RAIL_EXIT_LIFT, 0.0));
                return;
            }
            g.segment = next;
            g.along = g.sense > 0 ? 0.0 : JetBoardRails.length(g.rail, next);
            dir = JetBoardRails.direction(g.rail, g.segment, g.sense);
        }
        Vec3 on = JetBoardRails.at(g.rail, g.segment, g.along).add(0.0, HOVER_RAIL, 0.0);
        this.setPos(on.x, on.y, on.z);
        this.setDeltaMovement(dir.scale(g.speed * TICK));
        this.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
    }

    /** Quitter le rail avec cette vitesse, en m/s. */
    private void leaveRail(Vec3 velocity) {
        this.grind = null;
        this.sinceGrind = 0;
        this.grounded = false;
        this.setDeltaMovement(velocity.scale(TICK));
    }

    /** Sur un rail en ce moment (du cote qui simule). */
    public boolean grinding() {
        return this.grind != null;
    }

    public boolean grounded() {
        return this.grounded;
    }

    /** Pour le banc : le rail glisse (numero de JetBoardRails), -1 hors rail. */
    public int grindRail() {
        return this.grind == null ? -1 : this.grind.rail.index();
    }

    /** Pour le banc : ce qui reste de rail devant la planche, dans le sens de la glisse (-1 hors rail). */
    public double grindLeft() {
        Grind g = this.grind;
        if (g == null) {
            return -1.0;
        }
        double left = g.sense > 0 ? JetBoardRails.length(g.rail, g.segment) - g.along : g.along;
        for (int i = g.segment + g.sense; i >= 0 && i < g.rail.points().size() - 1; i += g.sense) {
            left += JetBoardRails.length(g.rail, i);
        }
        return left;
    }

    /** Pour le banc : la planche roule seule, au serveur, avec ces commandes. */
    public void setAutotestInput(@Nullable Input input) {
        this.autotestInput = input;
    }

    /** Pour le banc : un saut de cette charge, des qu'il touche le sol. */
    public void autotestJump(int power) {
        this.onPlayerJump(power);
    }

    private static float approach(float from, float to, float step) {
        float delta = Mth.wrapDegrees(to - from);
        return from + Mth.clamp(delta, -step, step);
    }

    // ------------------------------------------------------------- le saut (barre du cheval)

    @Override
    public void onPlayerJump(int power) {
        this.pendingJump = Math.max(0, power);
        this.jumpWait = 0;
    }

    @Override
    public boolean canJump() {
        return !this.level().isClientSide || this.grounded || this.grind != null;
    }

    @Override
    public void handleStartJump(int power) {
    }

    @Override
    public void handleStopJump() {
    }

    // ------------------------------------------------------------- le joueur dessus

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    public boolean isControlledByLocalInstance() {
        return this.getControllingPassenger() != null ? super.isControlledByLocalInstance()
                : !this.level().isClientSide && this.autotestInput != null;
    }

    /** Debout, pas assis : les pieds sur la planche. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** L'attache d'un joueur est a 0,6 sous ses pieds assis : debout, ses pieds sont sur la planche. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return new Vec3(0.0, BOARD_TOP + passenger.getVehicleAttachmentPoint(this).y, 0.0);
    }

    /** Le corps suit la planche ; la tete reste libre. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (passenger instanceof LivingEntity living) {
            living.setYBodyRot(this.getYRot());
        }
    }

    /** Descendre : on reste ou l'on est, les pieds a la place de la planche. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(this.getX(), this.getY() + BOARD_TOP, this.getZ());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /** Ni la planche ni son joueur ne se blessent en retombant. */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public float maxUpStep() {
        return 1.0F;
    }

    // ------------------------------------------------------------- dessin

    /** Le tangage et le roulis dessines, en degres. */
    public float pitch(float partial) {
        return Mth.lerp(partial, this.shownPitchO, this.shownPitch);
    }

    public float roll(float partial) {
        return Mth.lerp(partial, this.shownRollO, this.shownRoll);
    }

    /** La planche penche dans les virages et suit la pente de sa course, cote client. */
    private void updateTilt() {
        this.shownPitchO = this.shownPitch;
        this.shownRollO = this.shownRoll;
        Vec3 v = this.getDeltaMovement();
        double flat = Math.hypot(v.x, v.z);
        float pitch = flat > 0.02 ? (float) -Math.toDegrees(Math.atan2(v.y, flat)) : 0.0F;
        float turn = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        float roll = Mth.clamp(-turn * 2.5F, -25.0F, 25.0F);
        this.shownPitch += (Mth.clamp(pitch, -35.0F, 35.0F) - this.shownPitch) * 0.3F;
        this.shownRoll += (roll - this.shownRoll) * 0.3F;
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

    /** Le client du joueur dessus garde sa vitesse : c'est lui qui simule (voir JakVehicleEntity.lerpMotion). */
    @Override
    public void lerpMotion(double x, double y, double z) {
        if (!this.isControlledByLocalInstance()) {
            super.lerpMotion(x, y, z);
        }
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

    // ------------------------------------------------------------- jamais sauvegardee seule

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
