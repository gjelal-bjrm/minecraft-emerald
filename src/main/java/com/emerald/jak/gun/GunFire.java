package com.emerald.jak.gun;

import com.emerald.haven.Haven;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.particles.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La gachette du Morph Gun, cote serveur : cadence, reserves, et les douze armes.
 *
 * TOUT EST REVALIDE A CHAQUE TIQUE, quoi que dise le client : dans Haven lobby
 * ouvert (MorphGunKeeper.allowed), vivant, pas spectateur, l'arme EN MAIN
 * DROITE, hors vehicule, hors menu etranger ; forme de base ; transformation
 * finie (tir bloque 7 tiques, 17 vers le bleu) ; reserve suffisante ; cadence.
 * Le client ne dit que « tenue » ou « relachee » (GunTriggerPayload) ; sans
 * nouvelle depuis {@value GunSpec#TRIGGER_TIMEOUT} tiques, la gachette est
 * consideree relachee.
 *
 * LA CADENCE, comme target-gun.gc:3275-3423 (voir GunSpec pour les trois
 * gachettes). Le delai a virgule (6,4 tiques pour le Blaster) passe par un
 * compteur : un tir parti dans la tique ou il devenait possible avance l'instant
 * du dernier tir d'un delai exact, et non jusqu'a la tique entiere ; en tir
 * continu, l'intervalle moyen est donc le delai de Jak 3.
 *
 * RESERVE VIDE (target-gun.gc:585-631, 3225-3240) : on bascule sur la premiere
 * famille possedee dont la reserve n'est pas vide, dans l'ordre du jeu -- jaune 1,
 * rouge 1, bleu 1, sombre 1 --, et le tir en attente part avec elle quand la
 * transformation est finie ; s'il n'y en a aucune, un clic.
 *
 * L'ETAT VOLATIL (gachette, delai, rotation, charge, salve en cours) reste ICI, en
 * memoire, par joueur. La pile ne recoit au plus qu'une ecriture par tique : les
 * reserves debitees et, pour la Vulcan Fury, les tiques de debut et de fin de
 * gachette dont le client deduit la rotation du canon.
 *
 * LA CHARGE DU WAVE CONCUSSOR (gun-red-shot.gc:700-770) : le tir ouvre une charge,
 * qui dure tant que la gachette est tenue, une seconde au plus utile. Elle DEBITE
 * SON ECO PAR PALIERS (GunSpec.waveCost : 1 a 5), et gele sa force si la reserve
 * rouge se vide. L'onde part au relachement, jamais avant 0,1 s -- et une gachette
 * PERDUE (plus de nouvelles du client) vaut un relachement : elle tire, elle
 * n'annule pas. Si l'arme quitte la main, la charge s'eteint et l'eco est rendu.
 * Changer d'arme est refuse pendant la charge, comme pour le Peace Maker.
 *
 * LE GYRO BURSTER : une soucoupe a la fois. L'appui la lance ; un nouvel appui tant
 * qu'elle ne tire pas encore la reveille sur place, et ne coute rien ; pendant sa
 * rafale, l'appui ne fait qu'un clic.
 *
 * L'ARC WIELDER : le tir l'ALLUME (1 eco bleu), puis il agit a chaque tique tant que la
 * gachette est tenue et boit 7,5 eco par seconde ; reserve vide, il s'eteint. LES
 * DELAIS PROPRES du Mass Inverter (2 s) et de la Super Nova (9 s) s'ajoutent au delai
 * de gachette, par arme et par joueur, et tiennent quand on change d'arme ; tenue
 * pendant l'attente, la gachette tire des qu'elle le peut, sans clic.
 *
 * LES CIBLES ET LE DECOR : GunImpacts. Aucun joueur n'est touche.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class GunFire {

    /** Pourquoi le tir est refuse. */
    public enum Refusal { NONE, DEAD, NOT_IN_HAVEN, NO_GUN, VEHICLE, MENU }

    private static final double NEVER = -1.0e9;
    /** Un clic de reserve vide au plus toutes les cinq tiques. */
    private static final int CLICK_TICKS = 5;
    /** Releves du banc d'essai, par joueur. */
    private static final int LOG_MAX = 512;

    static final class State {
        boolean down;
        long news = Long.MIN_VALUE / 4;
        boolean edge;
        int pending;
        double lastFire = NEVER;
        double spin;
        boolean spinHeld;
        long lastClick = Long.MIN_VALUE / 4;
        @Nullable
        GunPeaceBallEntity charge;
        // la charge du Wave Concussor : tique du debut (-1 hors charge), tiques comptees, eco deja paye
        long waveStart = -1L;
        int waveTicks;
        int wavePaid;
        @Nullable
        GunSaucerEntity saucer;
        // l'arc de l'Arc Wielder : allume, eco du au fil des tiques, et la retenue des coups par monstre
        boolean arcOn;
        double arcDebt;
        final Map<Integer, Long> arcGate = new HashMap<>();
        /** Dernier tir de chaque arme a delai propre, par ordinal de GunSpec. */
        final long[] lastUse = new long[GunSpec.values().length];

        State() {
            java.util.Arrays.fill(this.lastUse, Long.MIN_VALUE / 4);
        }
        // la salve du Scatter Gun en cours
        final ArrayDeque<Vec3> probes = new ArrayDeque<>();
        Vec3 probeFrom = Vec3.ZERO;
        final Set<Integer> probeHits = new HashSet<>();
        int probeBlocks;
        // releves
        final List<long[]> shots = new ArrayList<>();
        final List<long[]> probeBatches = new ArrayList<>();
        /** Les ondes parties : {tique, tiques de charge, eco paye, rayon final x 100}. */
        final List<long[]> waves = new ArrayList<>();
        /** Les tiques d'arc de l'Arc Wielder. */
        final List<GunArcBeam.Result> arcs = new ArrayList<>();

        boolean idle() {
            return !this.down && this.pending == 0 && this.charge == null && this.probes.isEmpty() && this.spin <= 0.0
                    && !this.spinHeld && this.waveStart < 0L && (this.saucer == null || this.saucer.isRemoved())
                    && !this.arcOn;
        }
    }

    /** Une chaine de foudre du Peace Maker en cours. */
    private static final class Chain {
        final ServerLevel level;
        final ServerPlayer shooter;
        final List<Mob> targets;
        int index;
        Vec3 from;
        long next;

        Chain(ServerLevel level, ServerPlayer shooter, List<Mob> targets, Vec3 from, long next) {
            this.level = level;
            this.shooter = shooter;
            this.targets = targets;
            this.from = from;
            this.next = next;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<Chain> CHAINS = new ArrayList<>();
    /** Tiques de blocage des transformations, par (forme precedente, forme) ; -1 : pas encore lu. */
    private static final int[][] CLIP_TICKS = new int[GunForm.values().length][GunForm.values().length];

    static {
        for (int[] row : CLIP_TICKS) {
            java.util.Arrays.fill(row, -1);
        }
    }

    private GunFire() {
    }

    // ================================================================ entrees

    /** Le paquet de gachette d'un joueur. */
    public static void onTrigger(ServerPlayer player, boolean down) {
        State s = STATES.get(player.getUUID());
        if (s == null) {
            if (!down) {
                return;
            }
            s = new State();
            STATES.put(player.getUUID(), s);
        }
        if (down && !s.down) {
            s.edge = true;
        }
        s.down = down;
        s.news = player.level().getGameTime();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    /** Pas d'arme en main pour frapper : le clic gauche est la gachette. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (Haven.is(player.level()) && MorphGunKeeper.isGun(player.getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (CHAINS.isEmpty()) {
            return;
        }
        for (Iterator<Chain> it = CHAINS.iterator(); it.hasNext(); ) {
            Chain chain = it.next();
            if (chain.level.getGameTime() < chain.next) {
                continue;
            }
            if (!MorphGunKeeper.allowed(chain.shooter) || chain.index >= chain.targets.size()) {
                it.remove();
                continue;
            }
            Mob target = chain.targets.get(chain.index++);
            if (GunImpacts.isTarget(target)) {
                Vec3 to = GunImpacts.center(target);
                GunArcEntity.spawn(chain.level, chain.from, to);
                GunImpacts.hurt(chain.shooter, null, target, GunSpec.PEACE_DAMAGE);
                chain.level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
                        0.5F, 1.8F);
                chain.from = to;
            }
            chain.next = chain.level.getGameTime() + GunSpec.PEACE_CHAIN_TICKS;
            if (chain.index >= chain.targets.size()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        CHAINS.clear();
    }

    // ================================================================ lecture

    /** Pourquoi ce joueur ne peut pas tirer maintenant ; NONE s'il le peut. */
    public static Refusal refusal(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) {
            return Refusal.DEAD;
        }
        if (!MorphGunKeeper.allowed(player)) {
            return Refusal.NOT_IN_HAVEN;
        }
        ItemStack stack = player.getMainHandItem();
        if (!MorphGunKeeper.isGun(stack) || MorphGunData.of(stack) == null) {
            return Refusal.NO_GUN;
        }
        // au volant ou a bord d'un vehicule de Haven, on tire (cahier §98) ; sur tout le reste, non
        if (player.isPassenger() && !(player.getVehicle() instanceof com.emerald.jak.vehicle.JakVehicleEntity)) {
            return Refusal.VEHICLE;
        }
        return MorphGunKeeper.inMenu(player) ? Refusal.MENU : Refusal.NONE;
    }

    /** La gachette de ce joueur est-elle tenue (selon le serveur) ? */
    public static boolean isDown(@Nullable Entity player) {
        State s = player == null ? null : STATES.get(player.getUUID());
        return s != null && s.down;
    }

    /** Une charge est-elle en cours -- boule du Peace Maker au canon, ou Wave Concussor ? (le changement d'arme est alors refuse) */
    public static boolean isCharging(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s != null && (s.waveStart >= 0L || (s.charge != null && !s.charge.isRemoved() && !s.charge.launched()));
    }

    /** La soucoupe du Gyro Burster de ce joueur, ou null. */
    @Nullable
    public static GunSaucerEntity saucer(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s == null || s.saucer == null || s.saucer.isRemoved() ? null : s.saucer;
    }

    /** La rotation du canon, en degres par seconde. */
    public static double spin(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s == null ? 0.0 : s.spin;
    }

    /** Oublie l'etat d'un joueur (depart, deconnexion) ; une boule en charge s'eteint, sans remboursement, et sa soucoupe avec. */
    public static void forget(UUID player) {
        State s = STATES.remove(player);
        if (s != null && s.charge != null && !s.charge.isRemoved() && !s.charge.launched()) {
            s.charge.fizzle();
        }
        if (s != null && s.saucer != null && !s.saucer.isRemoved()) {
            s.saucer.discard();
        }
    }

    /** Les tirs releves : {tique, ordinal de GunSpec}. */
    static List<long[]> shotLog(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s == null ? List.of() : List.copyOf(s.shots);
    }

    /** Les lots de sondes du Scatter Gun : {tique, nombre}. */
    static List<long[]> probeLog(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s == null ? List.of() : List.copyOf(s.probeBatches);
    }

    /** Les ondes du Wave Concussor : {tique, tiques de charge, eco paye, rayon final x 100}. */
    static List<long[]> waveLog(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s == null ? List.of() : List.copyOf(s.waves);
    }

    static void clearLog(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s != null) {
            s.shots.clear();
            s.probeBatches.clear();
            s.waves.clear();
            s.arcs.clear();
        }
    }

    /** Les tiques d'arc de l'Arc Wielder (banc d'essai). */
    static List<GunArcBeam.Result> arcLog(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s == null ? List.of() : List.copyOf(s.arcs);
    }

    /** Le banc remet un tireur a neuf : gachette, delai, rotation. */
    static void reset(ServerPlayer player) {
        forget(player.getUUID());
    }

    // ================================================================ tique

    /**
     * Une tique de gachette. Appelee pour chaque vrai joueur par PlayerTickEvent,
     * et par le banc d'essai pour ses FakePlayer (qui ne tiquent pas).
     */
    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        State s = STATES.get(id);
        if (s == null) {
            if (!MorphGunKeeper.isGun(player.getMainHandItem()) || !Haven.is(player.level())) {
                return;
            }
            s = new State();
            STATES.put(id, s);
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (s.down && now - s.news > GunSpec.TRIGGER_TIMEOUT) {
            s.down = false;
        }
        if (s.charge != null && (s.charge.isRemoved() || s.charge.launched())) {
            s.charge = null;
        }
        if (!s.probes.isEmpty()) {
            scatterBatch(level, player, s, now);
        }

        Refusal refusal = refusal(player);
        ItemStack stack = player.getMainHandItem();
        MorphGunData data = refusal == Refusal.NONE ? MorphGunData.of(stack) : null;
        GunSpec spec = data == null ? null : GunSpec.of(data.form());
        if (s.charge != null && (spec != GunSpec.PEACE)) {
            // l'arme a quitte la main (case changee, menu, vehicule, mort, depart) : l'eco est rendue
            GunPeaceBallEntity ball = s.charge;
            s.charge = null;
            ball.fizzle();
            ItemStack gun = MorphGunKeeper.find(player);
            if (gun != null) {
                MorphGunData.refill(gun, GunForm.Family.DARK, 1);
            }
            data = refusal == Refusal.NONE ? MorphGunData.of(stack) : null;
        }
        if (s.waveStart >= 0L && spec != GunSpec.WAVE) {
            // meme regle que la boule : l'arme a quitte la main, la charge s'eteint et l'eco paye est rendu
            ItemStack gun = MorphGunKeeper.find(player);
            if (gun != null && s.wavePaid > 0) {
                MorphGunData.refill(gun, GunForm.Family.RED, s.wavePaid);
            }
            s.waveStart = -1L;
            s.wavePaid = 0;
            s.waveTicks = 0;
            data = refusal == Refusal.NONE ? MorphGunData.of(stack) : null;
        }
        if (s.saucer != null && s.saucer.isRemoved()) {
            s.saucer = null;
        }
        if (data == null || spec == null) {
            s.edge = false;
            s.pending = 0;
            s.arcOn = false;
            if (refusal != Refusal.NONE) {
                s.down = false;
            }
            s.spin = Math.max(0.0, s.spin - GunSpec.SPIN_DOWN);
            if (s.spinHeld) {
                s.spinHeld = false;
                if (data != null) {
                    MorphGunData.write(stack, data.withTrigger(data.triggerStart(), now));
                }
            }
            if (s.idle() && s.shots.isEmpty()) {
                STATES.remove(id);
            }
            return;
        }

        MorphGunData next = data;
        boolean active = now - data.changeTick() >= transformTicks(data);
        if (spec.trigger == GunSpec.Trigger.SPIN || spec.trigger == GunSpec.Trigger.BEAM) {
            boolean spinning = s.down && active;
            if (spinning && !s.spinHeld) {
                // le client repart de la bonne vitesse : debut recule de la rotation restante
                long start = Math.max(1L, now - Math.round(s.spin / 800.0 * 20.0));
                next = next.withTrigger(start, Math.min(data.triggerEnd(), start - 1L));
                s.spinHeld = true;
            } else if (!spinning && s.spinHeld) {
                next = next.withTrigger(next.triggerStart(), now);
                s.spinHeld = false;
            }
            s.spin = spinning ? Math.min(GunSpec.SPIN_MAX, s.spin + GunSpec.SPIN_UP)
                    : Math.max(0.0, s.spin - GunSpec.SPIN_DOWN);
        } else {
            if (s.spinHeld) {
                next = next.withTrigger(next.triggerStart(), now);
                s.spinHeld = false;
            }
            s.spin = Math.max(0.0, s.spin - GunSpec.SPIN_DOWN);
        }

        if (s.waveStart >= 0L) {
            next = chargeWave(level, player, next, s, now);
        }

        double delay = spec.delay(s.spin);
        double since = now - s.lastFire;
        double window = delay - GunSpec.PENDING_WINDOW;
        switch (spec.trigger) {
            case PRESS -> {
                if (s.edge && since > window) {
                    s.pending = 1;
                }
            }
            case HOLD -> {
                if (s.down && s.charge == null && since > window) {
                    s.pending = 1;
                }
            }
            case SPIN -> {
                if (s.down && active && since > window) {
                    s.pending = 1;
                }
            }
            case CHARGE -> {
                if (s.down && s.waveStart < 0L && since > window) {
                    s.pending = 1;
                }
            }
            case BEAM -> {
                if (s.down && active && !s.arcOn) {
                    s.pending = 1;
                }
            }
        }
        s.edge = false;
        if (s.pending > 0 && active && since >= delay && s.waveStart < 0L) {
            s.pending = 0;
            next = fire(level, player, next, spec, s, now, delay);
        }
        if (s.arcOn) {
            next = spec == GunSpec.ARC && s.down && active ? arcTick(level, player, next, s, now) : arcOff(s, next);
        }
        if (!next.equals(data)) {
            MorphGunData.write(stack, next);
        }
    }

    /** Les tiques pendant lesquelles la transformation bloque le tir. */
    private static int transformTicks(MorphGunData data) {
        int a = data.previous().ordinal();
        int b = data.form().ordinal();
        int ticks = CLIP_TICKS[a][b];
        if (ticks < 0) {
            JakGunModel model = JakGunModel.gun();
            ticks = model == null ? 0 : GunPose.clip(model, data.previous(), data.form()).ticks();
            CLIP_TICKS[a][b] = ticks;
        }
        return ticks;
    }

    // ================================================================ tir

    private static MorphGunData fire(ServerLevel level, ServerPlayer player, MorphGunData data, GunSpec spec,
                                     State s, long now, double delay) {
        GunForm.Family family = spec.form.family;
        if (spec == GunSpec.GYRO && s.saucer != null && !s.saucer.isRemoved() && s.saucer.busy()) {
            // une soucoupe a la fois : l'appui la reveille, ou ne fait qu'un clic pendant sa rafale
            if (s.saucer.activate()) {
                level.playSound(null, s.saucer.getX(), s.saucer.getY(), s.saucer.getZ(), SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS, 0.7F, 1.9F);
            } else if (now - s.lastClick >= CLICK_TICKS) {
                s.lastClick = now;
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
                        SoundSource.PLAYERS, 0.6F, 1.4F);
            }
            return data;
        }
        if (spec.cooldown > 0 && now - s.lastUse[spec.ordinal()] < spec.cooldown) {
            // le delai propre de l'arme : la gachette tenue tirera des qu'il sera passe, sans clic
            s.pending = s.down ? 1 : 0;
            return data;
        }
        if (data.eco(family) < spec.cost) {
            GunForm other = outOfAmmo(data);
            if (other != null) {
                s.pending = 1;
                s.lastFire = NEVER;
                return data.withForm(other, now);
            }
            if (now - s.lastClick >= CLICK_TICKS) {
                s.lastClick = now;
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
                        SoundSource.PLAYERS, 0.6F, 1.4F);
            }
            s.lastFire = NEVER;
            return data;
        }
        MorphGunData next = data.withEco(family, data.eco(family) - spec.debit);
        switch (spec) {
            case SCATTER -> startScatter(level, player, s, now);
            case BLASTER -> shootBlaster(level, player);
            case VULCAN -> shootVulcan(level, player);
            case PEACE -> startCharge(level, player, s);
            case WAVE -> next = startWave(level, player, next, s, now);
            case PLASMITE -> shootPlasmite(level, player);
            case REFLEXOR -> shootReflexor(level, player);
            case GYRO -> launchSaucer(level, player, s);
            case ARC -> {
                s.arcOn = true;
                s.arcDebt = 0.0;
                s.arcGate.clear();
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
                        SoundSource.PLAYERS, 0.5F, 2.0F);
            }
            case NEEDLE -> {
                GunNeedles.salvo(level, player);
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.PLAYERS, 0.7F, 1.9F);
            }
            case INVERTER -> {
                GunGravityFieldEntity.spawn(level, player);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS, 1.0F, 0.5F);
            }
            case NOVA -> shootNova(level, player);
        }
        s.lastUse[spec.ordinal()] = now;
        s.lastFire = now - (s.lastFire + delay) < 1.0 ? s.lastFire + delay : now;
        if (s.shots.size() < LOG_MAX) {
            s.shots.add(new long[]{now, spec.ordinal()});
        }
        return next;
    }

    /** La premiere arme de base possedee dont la reserve n'est pas vide, dans l'ordre jaune, rouge, bleu, sombre. */
    @Nullable
    static GunForm outOfAmmo(MorphGunData data) {
        for (GunForm form : new GunForm[]{GunForm.YELLOW_1, GunForm.RED_1, GunForm.BLUE_1, GunForm.DARK_1}) {
            if (data.owns(form) && data.eco(form.family) > 0) {
                return form;
            }
        }
        return null;
    }

    // ------------------------------------------------------------- Scatter Gun

    private static void startScatter(ServerLevel level, ServerPlayer player, State s, long now) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        s.probes.clear();
        s.probeHits.clear();
        s.probeBlocks = 0;
        s.probeFrom = eye;
        Vec3 sphere = eye.add(look.scale(GunSpec.SCATTER_AIM_AHEAD));
        for (Mob mob : GunImpacts.targetsAround(level, sphere, GunSpec.SCATTER_AIM_RADIUS, GunSpec.SCATTER_PROBES)) {
            Vec3 to = GunImpacts.center(mob).subtract(eye);
            if (to.lengthSqr() > 1.0e-6) {
                s.probes.add(to.normalize());
            }
        }
        RandomSource random = player.getRandom();
        while (s.probes.size() < GunSpec.SCATTER_PROBES) {
            float yaw = player.getYRot() + (float) ((random.nextDouble() * 2.0 - 1.0) * GunSpec.SCATTER_CONE_H);
            float pitch = Mth.clamp(player.getXRot() + (float) ((random.nextDouble() * 2.0 - 1.0) * GunSpec.SCATTER_CONE_V),
                    -90.0F, 90.0F);
            s.probes.add(Vec3.directionFromRotation(pitch, yaw));
        }
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.0F, 0.55F);
        scatterBatch(level, player, s, now);
    }

    /** Sept sondes de la salve : une touche par cible par tir, le bloc touche casse (six au plus par tir). */
    private static void scatterBatch(ServerLevel level, ServerPlayer player, State s, long now) {
        int n = Math.min(GunSpec.SCATTER_PER_TICK, s.probes.size());
        float[] ends = new float[n * 4];
        for (int i = 0; i < n; i++) {
            Vec3 direction = s.probes.poll();
            GunImpacts.Ray ray = GunImpacts.ray(level, player, s.probeFrom, direction, GunSpec.SCATTER_RANGE,
                    GunSpec.SCATTER_PROBE_RADIUS);
            if (ray.target() != null) {
                if (s.probeHits.add(ray.target().getId())) {
                    double distance = ray.end().distanceTo(s.probeFrom);
                    GunImpacts.hurt(player, null, ray.target(), distance < GunSpec.SCATTER_NEAR
                            ? GunSpec.SCATTER_DAMAGE_NEAR : GunSpec.SCATTER_DAMAGE_FAR);
                }
            } else if (ray.block() != null && s.probeBlocks < GunSpec.SCATTER_BLOCKS
                    && GunImpacts.breakOne(level, ray.block(), player)) {
                s.probeBlocks++;
            }
            put(ends, i, ray);
        }
        if (s.probeBatches.size() < LOG_MAX) {
            s.probeBatches.add(new long[]{now, n});
        }
        send(level, player, GunTracePayload.SCATTER, s.probeFrom, ends);
    }

    // ------------------------------------------------------------- Blaster

    private static void shootBlaster(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        GunBlasterShotEntity shot = new GunBlasterShotEntity(level, player);
        shot.launch(new Vec3(eye.x + look.x * 0.3, eye.y - 0.1 + look.y * 0.3, eye.z + look.z * 0.3), look,
                level.getGameTime());
        level.addFreshEntity(shot);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.8F, 1.7F);
    }

    // ------------------------------------------------------------- Vulcan Fury

    private static void shootVulcan(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        RandomSource random = player.getRandom();
        double a = Math.toRadians(random.nextDouble() * GunSpec.VULCAN_SPREAD);
        double phi = random.nextDouble() * Math.PI * 2.0;
        Vec3 u = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (u.lengthSqr() < 1.0e-6) {
            u = new Vec3(1.0, 0.0, 0.0);
        }
        u = u.normalize();
        Vec3 v = u.cross(look).normalize();
        Vec3 direction = look.scale(Math.cos(a)).add(u.scale(Math.sin(a) * Math.cos(phi)))
                .add(v.scale(Math.sin(a) * Math.sin(phi)));
        GunImpacts.Ray ray = GunImpacts.ray(level, player, eye, direction, GunSpec.VULCAN_RANGE, 0.1F);
        if (ray.target() != null) {
            GunImpacts.hurt(player, null, ray.target(), GunSpec.VULCAN_DAMAGE);
        } else if (ray.block() != null) {
            GunImpacts.breakOne(level, ray.block(), player);
        }
        float[] ends = new float[4];
        put(ends, 0, ray);
        send(level, player, GunTracePayload.VULCAN, eye, ends);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.45F,
                1.8F + random.nextFloat() * 0.2F);
    }

    // ------------------------------------------------------------- Peace Maker

    private static void startCharge(ServerLevel level, ServerPlayer player, State s) {
        GunPeaceBallEntity ball = new GunPeaceBallEntity(level, player);
        level.addFreshEntity(ball);
        s.charge = ball;
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 0.6F, 1.5F);
    }

    /** La foudre de proche en proche apres l'impact : une cible toutes les {@value GunSpec#PEACE_CHAIN_TICKS} tiques. */
    static void chain(ServerLevel level, ServerPlayer shooter, Vec3 from, List<Mob> targets) {
        if (!targets.isEmpty()) {
            CHAINS.add(new Chain(level, shooter, new ArrayList<>(targets), from,
                    level.getGameTime() + GunSpec.PEACE_CHAIN_TICKS));
        }
    }

    // ------------------------------------------------------------- Wave Concussor

    /** Le tir ouvre la charge ; les tiques de gachette de la pile la montrent au client (jauge du HUD). */
    private static MorphGunData startWave(ServerLevel level, ServerPlayer player, MorphGunData data, State s, long now) {
        s.waveStart = now;
        s.waveTicks = 0;
        s.wavePaid = 0;
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, 0.7F, 0.8F);
        return data.withTrigger(now, Math.min(data.triggerEnd(), now - 1L));
    }

    /**
     * Une tique de charge : le palier paye s'il y a de quoi, sinon la force se fige ;
     * au relachement apres {@value GunSpec#WAVE_CHARGE_MIN} tiques, l'onde part.
     */
    private static MorphGunData chargeWave(ServerLevel level, ServerPlayer player, MorphGunData data, State s, long now) {
        MorphGunData next = data;
        if (s.waveTicks < GunSpec.WAVE_CHARGE_FULL) {
            int wanted = s.waveTicks + 1;
            if (GunSpec.waveCost(wanted) <= s.wavePaid) {
                s.waveTicks = wanted;
            } else if (next.eco(GunForm.Family.RED) >= 1) {
                next = next.withEco(GunForm.Family.RED, next.eco(GunForm.Family.RED) - 1);
                s.wavePaid++;
                s.waveTicks = wanted;
            }
        }
        if (now % 2L == 0L) {
            Vec3 at = GunPeaceBallEntity.muzzle(player);
            int count = 1 + s.waveTicks / 5;
            level.sendParticles(ModParticles.GUN_WAVE_CHARGE.get(), at.x, at.y, at.z, count, 0.12, 0.12, 0.12, 0.0);
        }
        if (s.down || now - s.waveStart < GunSpec.WAVE_CHARGE_MIN) {
            return next;
        }
        if (s.wavePaid > 0) {
            double strength = Math.min(1.0, s.waveTicks / (double) GunSpec.WAVE_CHARGE_FULL);
            GunShockwaveEntity wave = GunShockwaveEntity.spawn(level, player, strength);
            level.playSound(null, wave.getX(), wave.getY(), wave.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS,
                    0.9F, 1.3F - 0.4F * (float) strength);
            if (s.waves.size() < LOG_MAX) {
                s.waves.add(new long[]{now, s.waveTicks, s.wavePaid, Math.round(wave.maxRadius() * 100.0)});
            }
        }
        s.waveStart = -1L;
        s.waveTicks = 0;
        s.wavePaid = 0;
        return next.withTrigger(next.triggerStart(), now);
    }

    // ------------------------------------------------------------- Plasmite RPG

    private static void shootPlasmite(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 direction = GunGrenadeEntity.aim(level, player);
        GunGrenadeEntity grenade = new GunGrenadeEntity(level, player);
        grenade.launch(new Vec3(eye.x + direction.x * 0.6, eye.y - 0.15 + direction.y * 0.6, eye.z + direction.z * 0.6), direction);
        level.addFreshEntity(grenade);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    // ------------------------------------------------------------- Beam Reflexor

    private static void shootReflexor(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        GunReflexor.launch(level, player, new Vec3(eye.x + look.x * 0.3, eye.y - 0.1 + look.y * 0.3, eye.z + look.z * 0.3), look);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.8F, 1.35F);
    }

    // ------------------------------------------------------------- Gyro Burster

    private static void launchSaucer(ServerLevel level, ServerPlayer player, State s) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        GunSaucerEntity saucer = new GunSaucerEntity(level, player);
        saucer.launch(new Vec3(eye.x + look.x * 0.8, eye.y - 0.1, eye.z + look.z * 0.8), look);
        level.addFreshEntity(saucer);
        s.saucer = saucer;
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.9F, 1.5F);
    }

    // ------------------------------------------------------------- Arc Wielder

    /** Une tique d'arc allume : l'eco du au fil du temps, puis la corde de foudre. Reserve vide : il s'eteint. */
    private static MorphGunData arcTick(ServerLevel level, ServerPlayer player, MorphGunData data, State s, long now) {
        MorphGunData next = data;
        s.arcDebt += GunSpec.ARC_DRAIN;
        while (s.arcDebt >= 1.0) {
            int blue = next.eco(GunForm.Family.BLUE);
            if (blue < 1) {
                return arcOff(s, next);
            }
            next = next.withEco(GunForm.Family.BLUE, blue - 1);
            s.arcDebt -= 1.0;
        }
        GunArcBeam.Result result = GunArcBeam.fire(level, player, s.arcGate, now);
        if (s.arcs.size() < LOG_MAX) {
            s.arcs.add(result);
        }
        if (now % 3L == 0L) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.PLAYERS, 0.12F, 2.0F);
        }
        return next;
    }

    private static MorphGunData arcOff(State s, MorphGunData data) {
        s.arcOn = false;
        s.arcDebt = 0.0;
        return data;
    }

    // ------------------------------------------------------------- Super Nova

    private static void shootNova(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        GunNukeEntity nuke = new GunNukeEntity(level, player);
        nuke.launch(new Vec3(eye.x + look.x * 0.8, eye.y - 0.2 + look.y * 0.8, eye.z + look.z * 0.8), look);
        level.addFreshEntity(nuke);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.9F, 0.6F);
    }

    // ------------------------------------------------------------- traces

    private static void put(float[] ends, int i, GunImpacts.Ray ray) {
        ends[i * 4] = (float) ray.end().x;
        ends[i * 4 + 1] = (float) ray.end().y;
        ends[i * 4 + 2] = (float) ray.end().z;
        ends[i * 4 + 3] = ray.flag();
    }

    private static void send(ServerLevel level, ServerPlayer player, int weapon, Vec3 from, float[] ends) {
        GunTracePayload payload = new GunTracePayload(player.getId(), weapon, from.x, from.y, from.z, ends);
        PacketDistributor.sendToPlayersNear(level, null, from.x, from.y, from.z, 128.0, payload);
    }

    /** Les armes dont le serveur emet les eclats d'impact. */
    enum Sparks { BLASTER, VULCAN, REFLEXOR }

    /** Les eclats de l'impact d'un tir, emis par le serveur (types neufs, un par arme). */
    static void sparks(ServerLevel level, Vec3 at, boolean blaster) {
        sparks(level, at, blaster ? Sparks.BLASTER : Sparks.VULCAN);
    }

    static void sparks(ServerLevel level, Vec3 at, Sparks weapon) {
        switch (weapon) {
            case BLASTER -> level.sendParticles(ModParticles.GUN_BLASTER_SPARK.get(), at.x, at.y, at.z, 10, 0.08, 0.08, 0.08, 0.22);
            case VULCAN -> level.sendParticles(ModParticles.GUN_VULCAN_SPARK.get(), at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.15);
            case REFLEXOR -> level.sendParticles(ModParticles.GUN_REFLEXOR_SPARK.get(), at.x, at.y, at.z, 8, 0.08, 0.08, 0.08, 0.2);
        }
    }
}
