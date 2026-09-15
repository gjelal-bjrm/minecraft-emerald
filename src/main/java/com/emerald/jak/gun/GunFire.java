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
 * La gachette du Morph Gun, cote serveur : cadence, reserves, et les quatre armes de base.
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
        // la salve du Scatter Gun en cours
        final ArrayDeque<Vec3> probes = new ArrayDeque<>();
        Vec3 probeFrom = Vec3.ZERO;
        final Set<Integer> probeHits = new HashSet<>();
        int probeBlocks;
        // releves
        final List<long[]> shots = new ArrayList<>();
        final List<long[]> probeBatches = new ArrayList<>();

        boolean idle() {
            return !this.down && this.pending == 0 && this.charge == null && this.probes.isEmpty() && this.spin <= 0.0
                    && !this.spinHeld;
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
        if (player.isPassenger()) {
            return Refusal.VEHICLE;
        }
        return MorphGunKeeper.inMenu(player) ? Refusal.MENU : Refusal.NONE;
    }

    /** La gachette de ce joueur est-elle tenue (selon le serveur) ? */
    public static boolean isDown(@Nullable Entity player) {
        State s = player == null ? null : STATES.get(player.getUUID());
        return s != null && s.down;
    }

    /** Une boule du Peace Maker est-elle en charge au canon ? (le changement d'arme est alors refuse) */
    public static boolean isCharging(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s != null && s.charge != null && !s.charge.isRemoved() && !s.charge.launched();
    }

    /** La rotation du canon, en degres par seconde. */
    public static double spin(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s == null ? 0.0 : s.spin;
    }

    /** Oublie l'etat d'un joueur (depart, deconnexion) ; une boule en charge s'eteint, sans remboursement. */
    public static void forget(UUID player) {
        State s = STATES.remove(player);
        if (s != null && s.charge != null && !s.charge.isRemoved() && !s.charge.launched()) {
            s.charge.fizzle();
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

    static void clearLog(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s != null) {
            s.shots.clear();
            s.probeBatches.clear();
        }
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
        if (data == null || spec == null) {
            s.edge = false;
            s.pending = 0;
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
        if (spec.trigger == GunSpec.Trigger.SPIN) {
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
        }
        s.edge = false;
        if (s.pending > 0 && active && since >= delay) {
            s.pending = 0;
            next = fire(level, player, next, spec, s, now, delay);
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
        MorphGunData next = data.withEco(family, data.eco(family) - spec.cost);
        switch (spec) {
            case SCATTER -> startScatter(level, player, s, now);
            case BLASTER -> shootBlaster(level, player);
            case VULCAN -> shootVulcan(level, player);
            case PEACE -> startCharge(level, player, s);
        }
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

    /** Les eclats de l'impact d'un tir, emis par le serveur (types neufs, un par arme). */
    static void sparks(ServerLevel level, Vec3 at, boolean blaster) {
        if (blaster) {
            level.sendParticles(ModParticles.GUN_BLASTER_SPARK.get(), at.x, at.y, at.z, 10, 0.08, 0.08, 0.08, 0.22);
        } else {
            level.sendParticles(ModParticles.GUN_VULCAN_SPARK.get(), at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.15);
        }
    }
}
