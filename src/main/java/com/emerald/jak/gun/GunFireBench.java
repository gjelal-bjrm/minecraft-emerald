package com.emerald.jak.gun;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenDestruction;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenInvasionData;
import com.emerald.haven.invasion.HavenMonsterKilledEvent;
import com.emerald.haven.invasion.HavenProtection;
import com.emerald.haven.invasion.HavenSpawner;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;

/**
 * Le banc du TIR, troisieme partie de l'autotest « armes » (GunAutotest le lance
 * apres le confinement).
 *
 * LE SITE : le ciel de la rade, cellule (700, 100, 420), a 43 blocs au-dessus de
 * l'eau, loin de tout decor. Les tireurs sont des FakePlayer inscrits comme
 * cobayes du gardien ; ils ne tiquent pas : le banc appelle GunFire.tick pour
 * eux a chaque tique et joue la gachette par GunFire.onTrigger, comme le paquet.
 * Les troncons du site sont forces : sans joueur, un niveau cesse de faire tiquer
 * ses entites (tirs du Blaster, boules du Peace Maker). Les monstres de test sont
 * poses par HavenSpawner (etiquetes, donc cibles) SANS IA : ils flottent la ou
 * on les met. Les murs sont des pierres posees par le banc, et retirees a la fin.
 *
 * CE QUI EST MESURE : cout exact de chaque arme, cadences (Blaster en appuis
 * repetes, Scatter Gun, Vulcan Fury en maintien, Peace Maker), ecritures de
 * composant par tique, bascule a reserve vide, chargeur cache, charge rendue,
 * recharge par ramassage (point et plafond) et par monstre tue (couleur ponderee),
 * un monstre tue par chaque arme, joueur factice, habitant et zombie sans
 * etiquette intacts, blocs casses puis reconstruits (echeance naturelle et
 * rebuildAll), blocs proteges intacts, MSPT avec quatre tireurs en continu.
 *
 * LIMITE CONNUE : un FakePlayer n'est pas dans le niveau, les recherches
 * d'entites ne le voient donc pas. Le garde-fou « jamais un joueur » est prouve
 * par le filtre lui-meme (isTarget, hurt) et, dans le monde, par un habitant et
 * un zombie SANS etiquette places dans le cone, la ligne de tir et l'explosion.
 */
final class GunFireBench {

    /** Le site, en cellules du volume. */
    static final BlockPos SITE_CELL = new BlockPos(700, 100, 420);
    private static final float EAST = -90.0F;
    /** L'attente de l'explosion du Peace Maker sur le mur, en tiques (charge 6, vol 3 : 17 mesurees d'habitude). */
    private static final int PEACE_WAIT = 60;

    private record Phase(String name, int max, IntPredicate step) {
    }

    private final MinecraftServer server;
    private final ServerLevel level;
    private final BlockPos origin;
    private final Vec3 site;
    private final Deque<Phase> phases = new ArrayDeque<>();
    private Phase current;
    private int t;
    private final List<FakePlayer> fakes = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();
    private final Set<BlockPos> placed = new LinkedHashSet<>();
    private final Set<ChunkPos> forced = new LinkedHashSet<>();
    private static final List<HavenMonsterKilledEvent> KILLS = new ArrayList<>();
    private static boolean listening;

    // etat partage entre les tiques d'une phase
    private FakePlayer a;
    private final Map<String, Object> memo = new HashMap<>();

    GunFireBench(MinecraftServer server) {
        this.server = server;
        this.level = Haven.level(server);
        this.origin = HavenState.get(server).origin();
        this.site = Vec3.atBottomCenterOf(this.origin.offset(SITE_CELL));
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((HavenMonsterKilledEvent e) -> KILLS.add(e));
        }
        plan();
    }

    /** Une tique du banc ; vrai quand tout est fini. */
    boolean tick() {
        if (this.current == null) {
            this.current = this.phases.poll();
            this.t = 0;
            if (this.current == null) {
                return true;
            }
            GunAutotest.line("--- tir : " + this.current.name());
        }
        boolean done;
        try {
            done = this.current.step().test(this.t);
        } catch (RuntimeException e) {
            GunAutotest.check("tir, " + this.current.name() + " : sans exception", false, e.toString());
            org.slf4j.LoggerFactory.getLogger("emeraldweapons").error("autotest armes : exception du banc de tir", e);
            done = true;
        }
        this.t++;
        if (done || this.t >= this.current.max()) {
            this.current = null;
        }
        return false;
    }

    private void phase(String name, int max, IntPredicate step) {
        this.phases.add(new Phase(name, max, step));
    }

    private void plan() {
        phase("preparation du site", 1, this::prepare);
        phase("attente des troncons", 120, t -> t >= 40 && this.level.isPositionEntityTicking(BlockPos.containing(this.site)));
        phase("cout par tir et charge", 140, this::costs);
        phase("cadence du Blaster (appuis repetes)", 420, this::blasterCadence);
        phase("trajectoire du Blaster calculee par le client", 10, this::blasterTrajectory);
        phase("cadence du Scatter Gun", 110, this::scatterCadence);
        phase("cadence de la Vulcan Fury (maintien)", 140, this::vulcanCadence);
        phase("cadence du Peace Maker", 80, this::peaceCadence);
        phase("reserve vide", 40, this::emptyReserve);
        phase("recharge par ramassage", 60, this::pickups);
        phase("recharge par monstre tue", 2, this::killDrop);
        for (GunSpec spec : GunSpec.values()) {
            phase("monstre tue par " + spec.form.id, 240, t -> killWith(spec, t));
        }
        phase("joueur factice, habitant et zombie sans etiquette intacts", 90, this::harmless);
        phase("decor casse puis reconstruit", 420, this::decor);
        phase("blocs proteges intacts", 12, this::guarded);
        phase("MSPT, quatre tireurs en continu", 820, this::mspt);
        phase("nettoyage du site", 1, this::cleanup);
    }

    // ================================================================ outils

    private static void check(String what, boolean ok, String detail) {
        GunAutotest.check(what, ok, detail);
    }

    private static void line(String text) {
        GunAutotest.line(text);
    }

    private long now() {
        return this.level.getGameTime();
    }

    private FakePlayer shooter(String name, Vec3 feet, float yaw, float pitch) {
        FakePlayer fake = new FakePlayer(this.level, new GameProfile(
                UUID.nameUUIDFromBytes(("autotest-tir:" + name).getBytes(StandardCharsets.UTF_8)), "[Tir]"));
        fake.moveTo(feet.x, feet.y, feet.z, yaw, pitch);
        fake.setYHeadRot(yaw);
        MorphGunKeeper.addSubject(fake);
        MorphGunKeeper.ensure(fake);
        ItemStack gun = MorphGunKeeper.find(fake);
        for (int i = 0; i < 9; i++) {
            if (fake.getInventory().items.get(i) == gun) {
                fake.getInventory().selected = i;
            }
        }
        this.fakes.add(fake);
        return fake;
    }

    private static MorphGunData data(FakePlayer f) {
        return MorphGunData.of(f.getMainHandItem());
    }

    /** Pose une forme sans transformation en cours, gachette remise a neuf. */
    private void form(FakePlayer f, GunForm form) {
        GunFire.reset(f);
        MorphGunData d = data(f);
        long past = now() - 200L;
        MorphGunData.write(f.getMainHandItem(), d.withForm(form, past).withForm(form, past).withTrigger(0L, 0L));
    }

    private static void eco(FakePlayer f, int red, int yellow, int blue, int dark) {
        MorphGunData d = data(f);
        MorphGunData.write(f.getMainHandItem(), d.withEco(GunForm.Family.RED, red).withEco(GunForm.Family.YELLOW, yellow)
                .withEco(GunForm.Family.BLUE, blue).withEco(GunForm.Family.DARK, dark));
    }

    private static void full(FakePlayer f) {
        MorphGunData.write(f.getMainHandItem(), data(f).refilled());
    }

    private static void aim(FakePlayer f, Vec3 at) {
        Vec3 d = at.subtract(f.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        f.setYRot(yaw);
        f.setXRot(pitch);
        f.setYHeadRot(yaw);
        f.yRotO = yaw;
        f.xRotO = pitch;
    }

    private static void look(FakePlayer f, float yaw, float pitch) {
        f.setYRot(yaw);
        f.setXRot(pitch);
        f.setYHeadRot(yaw);
    }

    /** Un monstre de Haven sans IA, centre de sa boite au point donne. */
    private Mob monster(HavenInvasion.Kind kind, Vec3 center) {
        Mob mob = HavenSpawner.spawnMonster(this.level, kind, new Vec3(center.x, center.y - 0.975, center.z), true);
        if (mob != null) {
            mob.setNoAi(true);
            mob.setDeltaMovement(Vec3.ZERO);
            this.spawned.add(mob);
        }
        return mob;
    }

    private void stone(BlockPos pos) {
        this.level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        this.placed.add(pos.immutable());
    }

    private List<GunEcoEntity> ecoAround(Vec3 at, double radius) {
        return this.level.getEntitiesOfClass(GunEcoEntity.class, new AABB(at, at).inflate(radius), e -> !e.isRemoved());
    }

    private static List<Long> ticksOf(FakePlayer f, GunSpec spec) {
        List<Long> out = new ArrayList<>();
        for (long[] shot : GunFire.shotLog(f)) {
            if (shot[1] == spec.ordinal()) {
                out.add(shot[0]);
            }
        }
        return out;
    }

    private static String gaps(List<Long> ticks) {
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < ticks.size() && i < 40; i++) {
            out.append(ticks.get(i) - ticks.get(i - 1)).append(' ');
        }
        return out.toString().trim();
    }

    // ================================================================ phases

    private boolean prepare(int t) {
        int cx0 = (int) Math.floor((this.site.x - 8) / 16.0);
        int cx1 = (int) Math.floor((this.site.x + 32) / 16.0);
        int cz0 = (int) Math.floor((this.site.z - 40) / 16.0);
        int cz1 = (int) Math.floor((this.site.z + 40) / 16.0);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                this.level.setChunkForced(cx, cz, true);
                this.forced.add(new ChunkPos(cx, cz));
            }
        }
        // le point d'eco 1 aussi, pour le gardien des points
        List<HavenInvasionData.EcoPoint> points = HavenInvasionData.ecoPoints(this.server);
        if (!points.isEmpty()) {
            ChunkPos chunk = new ChunkPos(points.get(0).feetWorld(this.origin));
            this.level.setChunkForced(chunk.x, chunk.z, true);
            this.forced.add(chunk);
        }
        BlockPos feet = BlockPos.containing(this.site);
        this.level.getChunkAt(feet);
        BlockPos water = new BlockPos(feet.getX(), this.origin.getY() + 57, feet.getZ());
        boolean air = true;
        for (int dx = -2; dx <= 16; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                air &= this.level.getBlockState(feet.offset(dx, dy, 0)).isAir();
            }
        }
        check("site du banc de tir : ciel de la rade, air sur 18 blocs, eau dessous, cassable (non protege)",
                air && !this.level.getFluidState(water).isEmpty() && HavenProtection.reason(this.level, feet.offset(12, 0, 0)) == null,
                "pieds " + feet.toShortString() + ", air " + air + ", eau en Y " + water.getY() + " : "
                        + !this.level.getFluidState(water).isEmpty() + ", protection " + HavenProtection.reason(this.level, feet.offset(12, 0, 0))
                        + ", " + this.forced.size() + " troncons forces");
        // a la verticale : tirs et boules restent dans la colonne des troncons forces (sinon ils s'y figent)
        this.a = shooter("A", this.site, EAST, -90.0F);
        check("tireur A : Morph Gun en main, 4 formes, reserves pleines", data(this.a) != null && data(this.a).full(),
                "arme " + (data(this.a) == null ? "absente" : data(this.a).form().id));
        return true;
    }

    private boolean costs(int t) {
        FakePlayer f = this.a;
        switch (t) {
            case 0 -> {
                form(f, GunForm.RED_1);
                full(f);
                GunFire.clearLog(f);
                GunFire.onTrigger(f, true);
                GunFire.tick(f);
                GunFire.onTrigger(f, false);
                this.memo.put("red", data(f).ecoRed());
            }
            case 1, 2 -> GunFire.tick(f);
            case 3 -> {
                List<long[]> batches = GunFire.probeLog(f);
                String counts = batches.stream().map(b -> b[1] + "@" + b[0]).reduce((x, y) -> x + " " + y).orElse("");
                boolean ok = batches.size() == 3 && batches.get(0)[1] == 7 && batches.get(1)[1] == 7 && batches.get(2)[1] == 5
                        && batches.get(1)[0] == batches.get(0)[0] + 1 && batches.get(2)[0] == batches.get(0)[0] + 2;
                check("Scatter Gun : 1 eco rouge par tir (100 -> 99), 19 sondes en 3 tiques (7, 7, 5)",
                        (int) this.memo.get("red") == 99 && ticksOf(f, GunSpec.SCATTER).size() == 1 && ok,
                        "rouge " + this.memo.get("red") + ", sondes " + counts);
            }
            case 5 -> {
                form(f, GunForm.YELLOW_1);
                GunFire.onTrigger(f, true);
                GunFire.tick(f);
                GunFire.onTrigger(f, false);
                int shots = this.level.getEntitiesOfClass(GunBlasterShotEntity.class, f.getBoundingBox().inflate(4.0),
                        shot -> shot.getOwner() == f).size();
                check("Blaster : 1 eco jaune par tir (200 -> 199), un tir lance, possede par le tireur",
                        data(f).ecoYellow() == 199 && shots == 1, "jaune " + data(f).ecoYellow() + ", tirs " + shots);
            }
            case 8 -> {
                form(f, GunForm.BLUE_1);
                GunFire.onTrigger(f, true);
                GunFire.tick(f);
                GunFire.onTrigger(f, false);
                GunFire.tick(f);
                check("Vulcan Fury : 1 eco bleue par balle (200 -> 199), la premiere part a l'appui",
                        data(f).ecoBlue() == 199 && ticksOf(f, GunSpec.VULCAN).size() == 1, "bleu " + data(f).ecoBlue());
            }
            case 11 -> {
                form(f, GunForm.DARK_1);
                GunFire.onTrigger(f, true);
                GunFire.tick(f);
                MorphGunKeeper.Selection refused = MorphGunKeeper.select(f, GunForm.Family.RED);
                check("Peace Maker : 1 eco sombre a la charge (15 -> 14), boule au canon, changement d'arme refuse pendant la charge",
                        data(f).ecoDark() == 14 && GunFire.isCharging(f) && refused == MorphGunKeeper.Selection.CHARGING
                                && data(f).form() == GunForm.DARK_1,
                        "sombre " + data(f).ecoDark() + ", charge " + GunFire.isCharging(f) + ", fleche rouge " + refused);
                this.memo.put("chargeStart", now());
            }
            case 19 -> {
                GunFire.onTrigger(f, true);
                GunFire.tick(f);
                check("gachette tenue 8 tiques : la boule reste au canon", GunFire.isCharging(f), "charge " + GunFire.isCharging(f));
                GunFire.onTrigger(f, false);
            }
            case 23 -> {
                GunFire.tick(f);
                List<GunPeaceBallEntity> balls = this.level.getEntitiesOfClass(GunPeaceBallEntity.class,
                        new AABB(this.site, this.site).inflate(64.0), b -> b.getOwner() == f);
                check("relachee : la boule part (lancee, plus en charge), sans autre debit", !GunFire.isCharging(f)
                                && balls.size() == 1 && balls.get(0).launched() && data(f).ecoDark() == 14,
                        balls.size() + " boule(s), lancee " + (!balls.isEmpty() && balls.get(0).launched()));
                this.memo.put("explosions", GunEco.explosions().size());
            }
            case 62 -> {
                List<GunEco.Explosion> all = GunEco.explosions();
                int before = (int) this.memo.get("explosions");
                GunEco.Explosion last = all.isEmpty() ? null : all.get(all.size() - 1);
                check("sans cible, la boule vole 30 tiques puis eclate (explosion notee, aucune cible)",
                        all.size() == before + 1 && last != null && last.targets() == 0 && last.shooter().equals(f.getUUID()),
                        "explosions " + before + " -> " + all.size() + (last == null ? "" : ", a la tique " + last.tick()
                                + ", cibles " + last.targets()));
                // appui bref : la boule part a 0,3 s
                GunFire.reset(f);
                GunFire.onTrigger(f, true);
                GunFire.tick(f);
                this.memo.put("tap", now());
            }
            case 63 -> {
                GunFire.onTrigger(f, false);
                GunFire.tick(f);
            }
            default -> {
                if (t > 63 && t <= 75 && !this.memo.containsKey("tapLaunch")) {
                    GunFire.tick(f);
                    for (GunPeaceBallEntity ball : this.level.getEntitiesOfClass(GunPeaceBallEntity.class,
                            new AABB(this.site, this.site).inflate(8.0), b -> b.getOwner() == f)) {
                        if (ball.launched()) {
                            this.memo.put("tapLaunch", now());
                        }
                    }
                }
                if (t == 76) {
                    Long launch = (Long) this.memo.get("tapLaunch");
                    long tap = (long) this.memo.get("tap");
                    check("appui bref (1 tique) : la boule reste au moins 6 tiques (0,3 s) au canon, puis part",
                            launch != null && launch - tap == GunSpec.PEACE_CHARGE_MIN,
                            launch == null ? "jamais lancee" : "lancee " + (launch - tap) + " tiques apres l'appui");
                }
                if (t == 110) {
                    GunFire.reset(f);
                    eco(f, 100, 200, 200, 10);
                    GunFire.onTrigger(f, true);
                    GunFire.tick(f);
                    this.memo.put("chargedDark", data(f).ecoDark());
                    this.memo.put("slot", f.getInventory().selected);
                }
                if (t == 112) {
                    int slot = (int) this.memo.get("slot");
                    f.getInventory().selected = slot == 8 ? 7 : 8;
                    GunFire.tick(f);
                    f.getInventory().selected = slot;
                    GunFire.onTrigger(f, false);
                    MorphGunData d = data(f);
                    check("charge annulee (l'arme quitte la main) : boule eteinte, eco sombre rendue (+1)",
                            (int) this.memo.get("chargedDark") == 9 && d.ecoDark() == 10 && !GunFire.isCharging(f),
                            "apres la charge " + this.memo.get("chargedDark") + ", apres l'annulation " + d.ecoDark());
                    return true;
                }
            }
        }
        return false;
    }

    private boolean blasterCadence(int t) {
        FakePlayer f = this.a;
        if (t == 0) {
            form(f, GunForm.YELLOW_1);
            full(f);
            look(f, EAST, -90.0F);
            GunFire.clearLog(f);
            this.memo.put("maxWrites", 0L);
            this.memo.put("writes", 0L);
        }
        List<Long> shots = ticksOf(f, GunSpec.BLASTER);
        boolean enough = shots.size() >= 50;
        GunFire.onTrigger(f, !enough && t % 2 == 0);
        long w0 = MorphGunData.writes();
        GunFire.tick(f);
        long dw = MorphGunData.writes() - w0;
        this.memo.put("maxWrites", Math.max((long) this.memo.get("maxWrites"), dw));
        this.memo.put("writes", (long) this.memo.get("writes") + dw);
        if (enough || t == 419) {
            GunFire.onTrigger(f, false);
            List<Long> first = shots.subList(0, Math.min(50, shots.size()));
            double mean = first.size() < 2 ? 0.0 : (first.get(first.size() - 1) - first.get(0)) / (double) (first.size() - 1);
            check("Blaster : 50 tirs en appuis toutes les 2 tiques, intervalle moyen 6,4 tiques (+/- 0,1), reserve jaune debitee d'exactement 50",
                    first.size() == 50 && Math.abs(mean - 6.4) <= 0.1 && data(f).ecoYellow() == 150,
                    String.format(Locale.ROOT, "%d tirs, moyenne %.3f tiques, jaune %d ; ecarts %s", first.size(), mean,
                            data(f).ecoYellow(), gaps(first)));
            check("ecritures de composant pendant les 50 tirs : au plus 1 par tique",
                    (long) this.memo.get("maxWrites") <= 1L,
                    "maximum " + this.memo.get("maxWrites") + " par tique, " + this.memo.get("writes") + " en " + (t + 1) + " tiques");
            return true;
        }
        return false;
    }

    private boolean scatterCadence(int t) {
        FakePlayer f = this.a;
        if (t == 0) {
            form(f, GunForm.RED_1);
            full(f);
            look(f, EAST, -90.0F);
            GunFire.clearLog(f);
        }
        List<Long> shots = ticksOf(f, GunSpec.SCATTER);
        GunFire.onTrigger(f, shots.size() < 5 && t % 2 == 0);
        GunFire.tick(f);
        if (t == 109) {
            List<Long> after = ticksOf(f, GunSpec.SCATTER);
            boolean all22 = after.size() == 5;
            for (int i = 1; i < after.size(); i++) {
                all22 &= after.get(i) - after.get(i - 1) == 22;
            }
            int batches = GunFire.probeLog(f).size();
            check("Scatter Gun : 5 tirs, delai de 22 tiques (330 tiques Jak), 5 eco rouges, 15 lots de sondes",
                    all22 && data(f).ecoRed() == 95 && batches == 15,
                    after.size() + " tirs, ecarts " + gaps(after) + ", rouge " + data(f).ecoRed() + ", lots " + batches);
            return true;
        }
        return false;
    }

    private boolean vulcanCadence(int t) {
        FakePlayer f = this.a;
        if (t == 0) {
            form(f, GunForm.BLUE_1);
            full(f);
            look(f, EAST, -90.0F);
            GunFire.clearLog(f);
            this.memo.put("press", now());
            this.memo.put("maxWrites", 0L);
            this.memo.put("writes", 0L);
        }
        boolean hold = t < 80;
        GunFire.onTrigger(f, hold);
        long w0 = MorphGunData.writes();
        GunFire.tick(f);
        long dw = MorphGunData.writes() - w0;
        this.memo.put("maxWrites", Math.max((long) this.memo.get("maxWrites"), dw));
        this.memo.put("writes", (long) this.memo.get("writes") + dw);
        if (t == 29) {
            this.memo.put("spin30", GunFire.spin(f));
        }
        if (t == 80) {
            this.memo.put("release", now());
        }
        if (t == 139) {
            List<Long> shots = ticksOf(f, GunSpec.VULCAN);
            long press = (long) this.memo.get("press");
            long first = shots.isEmpty() ? -1 : shots.get(0) - press;
            long firstGap = shots.size() < 2 ? -1 : shots.get(1) - shots.get(0);
            List<Long> late = new ArrayList<>();
            for (long s : shots) {
                if (s - press >= 40 && s - press < 80) {
                    late.add(s);
                }
            }
            double lateMean = late.size() < 2 ? 0 : (late.get(late.size() - 1) - late.get(0)) / (double) (late.size() - 1);
            MorphGunData d = data(f);
            long release = (long) this.memo.get("release");
            check("Vulcan Fury : premiere balle a l'appui, premier ecart de 6 a 8 tiques, rotation pleine (1200 degres/s) en 30 tiques,"
                            + " puis une balle toutes les 2 tiques",
                    first == 0 && firstGap >= 6 && firstGap <= 8 && (double) this.memo.get("spin30") >= 1200.0
                            && Math.abs(lateMean - 2.0) <= 0.1,
                    String.format(Locale.ROOT, "premiere a +%d, rotation a 30 tiques %.0f, ecarts %s ; moyenne entre 40 et 80 tiques %.2f",
                            first, (double) this.memo.get("spin30"), gaps(shots), lateMean));
            check("Vulcan Fury : 1 eco bleue par balle, tiques de gachette ecrites (appui, relachement), rotation retombee a 0 en 40 tiques",
                    d.ecoBlue() == 200 - shots.size() && d.triggerStart() == press && d.triggerEnd() == release
                            && GunFire.spin(f) == 0.0,
                    shots.size() + " balles, bleu " + d.ecoBlue() + ", gachette " + d.triggerStart() + " -> " + d.triggerEnd()
                            + " (appui " + press + ", relachement " + release + "), rotation " + GunFire.spin(f));
            check("Vulcan Fury au maximum : au plus 1 ecriture de composant par tique ; 10 ecritures et 10 paquets de traces par seconde",
                    (long) this.memo.get("maxWrites") <= 1L,
                    String.format(Locale.ROOT, "maximum %d par tique ; %d ecritures en 140 tiques ; %.1f balles/s en rotation pleine",
                            (long) this.memo.get("maxWrites"), (long) this.memo.get("writes"), 20.0 / Math.max(lateMean, 1.0e-9)));
            double angle = GunSpec.spinAngle(press, release, release + 20.0);
            check("rotation du canon deduite par le client : 900 degres a 1,5 s, 2100 a 2,5 s (1200 degres/s) ; relachee, elle ralentit puis s'arrete",
                    Math.abs(GunSpec.spinAngle(100, 0, 130) - 900.0) < 1.0e-6 && Math.abs(GunSpec.spinAngle(100, 0, 150) - 2100.0) < 1.0e-6
                            && angle > GunSpec.spinAngle(press, release, release) && Math.abs(GunSpec.spinAngle(press, release, release + 200)
                            - GunSpec.spinAngle(press, release, release + 100)) < 1.0e-6,
                    String.format(Locale.ROOT, "angle a 1,5 s %.0f, a 2,5 s %.0f, 1 s apres le relachement %.0f",
                            GunSpec.spinAngle(100, 0, 130), GunSpec.spinAngle(100, 0, 150), angle));
            return true;
        }
        return false;
    }

    private boolean peaceCadence(int t) {
        FakePlayer f = this.a;
        if (t == 0) {
            form(f, GunForm.DARK_1);
            full(f);
            look(f, EAST, -90.0F);
            GunFire.clearLog(f);
        }
        GunFire.onTrigger(f, t < 60 && t % 2 == 0);
        GunFire.tick(f);
        if (t == 79) {
            List<Long> shots = ticksOf(f, GunSpec.PEACE);
            boolean all17 = shots.size() >= 3;
            for (int i = 1; i < shots.size(); i++) {
                all17 &= shots.get(i) - shots.get(i - 1) == 17;
            }
            check("Peace Maker : delai de 17 tiques (255 tiques Jak) entre deux charges, 1 eco sombre chacune",
                    all17 && data(f).ecoDark() == 15 - shots.size(),
                    shots.size() + " charges, ecarts " + gaps(shots) + ", sombre " + data(f).ecoDark());
            return true;
        }
        return false;
    }

    private boolean emptyReserve(int t) {
        FakePlayer f = this.a;
        switch (t) {
            case 0 -> {
                form(f, GunForm.YELLOW_1);
                eco(f, 50, 0, 200, 15);
                GunFire.clearLog(f);
                GunFire.onTrigger(f, true);
                GunFire.tick(f);
                GunFire.onTrigger(f, false);
                MorphGunData d = data(f);
                this.memo.put("switch", d.form() == GunForm.RED_1 && d.previous() == GunForm.YELLOW_1 && d.ecoRed() == 50);
                this.memo.put("switchDetail", d.form().id + " (avant " + d.previous().id + "), rouge " + d.ecoRed());
            }
            case 1 -> {
                GunFire.tick(f);
                MorphGunData d = data(f);
                check("Blaster a reserve jaune vide : bascule vers le Scatter Gun (ordre jaune, rouge, bleu, sombre) et le tir attendu part",
                        (boolean) this.memo.get("switch") && d.ecoRed() == 49 && ticksOf(f, GunSpec.SCATTER).size() == 1,
                        this.memo.get("switchDetail") + " ; puis rouge " + d.ecoRed());
            }
            case 5 -> {
                form(f, GunForm.DARK_1);
                eco(f, 0, 0, 5, 0);
                GunFire.clearLog(f);
                GunFire.onTrigger(f, true);
                GunFire.tick(f);
                GunFire.onTrigger(f, false);
                this.memo.put("toBlue", data(f).form() == GunForm.BLUE_1);
                this.memo.put("toBlueTick", now());
            }
            case 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23 -> GunFire.tick(f);
            case 24 -> {
                GunFire.tick(f);
                List<Long> shots = ticksOf(f, GunSpec.VULCAN);
                long wait = shots.isEmpty() ? -1 : shots.get(0) - (long) this.memo.get("toBlueTick");
                check("Peace Maker a sec, rouge et jaune vides : bascule vers la Vulcan Fury ; la balle attend la fin de la transformation (17 tiques)",
                        (boolean) this.memo.get("toBlue") && shots.size() == 1 && wait == 17 && data(f).ecoBlue() == 4,
                        "bascule " + this.memo.get("toBlue") + ", balle " + wait + " tiques apres, bleu " + data(f).ecoBlue());
            }
            case 26 -> {
                form(f, GunForm.YELLOW_1);
                eco(f, 0, 0, 0, 0);
                GunFire.clearLog(f);
                long writes = MorphGunData.writes();
                GunFire.onTrigger(f, true);
                GunFire.tick(f);
                GunFire.onTrigger(f, false);
                GunFire.tick(f);
                check("toutes les reserves vides, Blaster en main : ni tir, ni bascule, ni ecriture (un clic)",
                        GunFire.shotLog(f).isEmpty() && data(f).form() == GunForm.YELLOW_1 && MorphGunData.writes() == writes,
                        "tirs " + GunFire.shotLog(f).size() + ", forme " + data(f).form().id + ", ecritures " + (MorphGunData.writes() - writes));
            }
            case 27 -> {
                JakGunModel model = JakGunModel.gun();
                StringBuilder detail = new StringBuilder();
                boolean ok = model != null;
                if (model != null) {
                    for (GunForm.Family family : GunForm.Family.values()) {
                        GunForm base = GunForm.of(family, 1);
                        GunPose pose = new GunPose(model).pose(base);
                        int before = pose.visibleCount();
                        int bone = model.boneIndex(GunPose.MAGAZINES[family.ordinal()]);
                        int after = bone < 0 ? before : pose.tweak(1L << bone, -1, 0.0).visibleCount();
                        ok &= bone >= 0 && after < before;
                        detail.append(String.format(Locale.ROOT, "%s %d -> %d ; ", base.id, before, after));
                    }
                    GunPose still = new GunPose(model).pose(GunForm.BLUE_1);
                    GunPose spun = new GunPose(model).pose(GunForm.BLUE_1).tweak(0L, model.boneIndex("cylinders"), Math.toRadians(90.0));
                    double moved = 0.0;
                    double[] p = new double[3];
                    double[] q = new double[3];
                    int mainMoved = 0;
                    int cyl = model.boneIndex("cylinders");
                    for (int s = 0; s < model.triangles * 3; s++) {
                        still.vertex(s, p);
                        spun.vertex(s, q);
                        double dist = Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]));
                        if ((model.vertexBone[s] & 0xFF) == cyl) {
                            moved = Math.max(moved, dist);
                        } else if (dist > 1.0e-9 && (model.vertexBone[s] & 0xFF) == model.main) {
                            mainMoved++;
                        }
                    }
                    double[] box0 = still.visibleBox();
                    double[] box1 = spun.visibleBox();
                    ok &= moved > 0.01 && mainMoved == 0 && Math.abs(box0[5] - box1[5]) < 0.01;
                    detail.append(String.format(Locale.ROOT, "cylindres tournes de 90 degres : sommets deplaces de %.3f m au plus, corps immobile (%d),"
                            + " longueur du canon %.3f -> %.3f m", moved, mainMoved, box0[5], box1[5]));
                }
                check("rendu : chargeur de la famille a l'echelle 0 quand sa reserve est vide (triangles caches) ; cylindres de la Vulcan autour de l'axe du canon",
                        ok, detail.toString());
                full(f);
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private boolean pickups(int t) {
        FakePlayer f = this.a;
        if (t == 0) {
            form(f, GunForm.YELLOW_1);
            eco(f, 100, 195, 200, 14);
            GunEcoEntity yellow = GunEcoEntity.create(this.level, GunForm.Family.YELLOW, false, this.site);
            this.level.addFreshEntity(yellow);
            boolean took = GunEco.pickup(yellow, f);
            GunEcoEntity again = GunEcoEntity.create(this.level, GunForm.Family.YELLOW, false, this.site);
            this.level.addFreshEntity(again);
            boolean full = GunEco.pickup(again, f);
            GunEcoEntity dark = GunEcoEntity.create(this.level, GunForm.Family.DARK, false, this.site);
            this.level.addFreshEntity(dark);
            boolean tookDark = GunEco.pickup(dark, f);
            MorphGunData d = data(f);
            check("ramassage : +10 jaune plafonne (195 -> 200) et munition retiree ; reserve pleine : ramassage refuse, la munition reste ; +1 sombre (14 -> 15)",
                    took && yellow.isRemoved() && yellow.picked && !full && !again.isRemoved() && tookDark && d.ecoYellow() == 200 && d.ecoDark() == 15,
                    "jaune " + d.ecoYellow() + ", pris " + took + ", second refuse " + !full + " (reste " + !again.isRemoved()
                            + "), sombre " + d.ecoDark() + " (valeurs de Jak : rouge " + GunSpec.pickupAmount(GunForm.Family.RED)
                            + ", jaune " + GunSpec.pickupAmount(GunForm.Family.YELLOW) + ", bleu " + GunSpec.pickupAmount(GunForm.Family.BLUE)
                            + ", sombre " + GunSpec.pickupAmount(GunForm.Family.DARK) + ")");
            again.discard();

            List<HavenInvasionData.EcoPoint> points = HavenInvasionData.ecoPoints(this.server);
            StringBuilder near = new StringBuilder();
            for (HavenInvasionData.EcoPoint p : points) {
                double best = Double.MAX_VALUE;
                for (HavenInvasionData.EcoPoint o : points) {
                    if (o != p) {
                        best = Math.min(best, Math.hypot(p.feet().getX() - o.feet().getX(), p.feet().getZ() - o.feet().getZ()));
                    }
                }
                near.append(p.number()).append(':').append((int) best).append(' ');
            }
            line("distance de chaque point d'eco a son plus proche voisin (blocs, a vol d'oiseau) : " + near.toString().trim()
                    + " ; delai de retour choisi " + GunEco.RESPAWN_TICKS / 20 + " s");
            return false;
        }
        GunEcoEntity point = GunEco.point(0);
        if (t == 20) {
            List<HavenInvasionData.EcoPoint> points = HavenInvasionData.ecoPoints(this.server);
            BlockPos feet = points.isEmpty() ? BlockPos.ZERO : points.get(0).feetWorld(this.origin);
            boolean placedOk = point != null && !point.isRemoved() && point.family() == GunForm.Family.YELLOW
                    && Math.abs(point.getX() - (feet.getX() + 0.5)) < 1.0e-6 && Math.abs(point.getZ() - (feet.getZ() + 0.5)) < 1.0e-6;
            eco(f, 100, 150, 200, 15);
            boolean took = point != null && GunEco.pickup(point, f);
            check("point d'eco 1 (" + (points.isEmpty() ? "?" : points.get(0).name()) + ", jaune) pose a sa cellule, ramasse (+10)",
                    placedOk && took && data(f).ecoYellow() == 160,
                    (point == null ? "absent" : "en " + point.blockPosition().toShortString()) + ", pieds " + feet.toShortString()
                            + ", jaune " + data(f).ecoYellow());
            this.memo.put("pickTick", now());
        }
        if (t == 35) {
            long next = GunEco.nextSpawn(0) - (long) this.memo.get("pickTick");
            check("le point se vide et reviendra dans 30 s (600 tiques apres le passage du gardien des points, 10 tiques au plus apres le ramassage)",
                    point == null && next >= GunEco.RESPAWN_TICKS && next <= GunEco.RESPAWN_TICKS + 10,
                    "entite " + (point == null ? "absente" : "presente") + ", retour dans " + next + " tiques");
            GunEco.respawnNow(0);
        }
        if (t == 59) {
            check("echeance passee : le point est repose", point != null && !point.isRemoved(),
                    point == null ? "absent" : "present en " + point.blockPosition().toShortString());
            return true;
        }
        return false;
    }

    private boolean killDrop(int t) {
        FakePlayer f = this.a;
        if (t == 0) {
            form(f, GunForm.BLUE_1);
            full(f);
            eco(f, 100, 200, 150, 15);
            look(f, EAST, 0.0F);
            Vec3 at = f.getEyePosition().add(6.0, 0.0, 0.0);
            Mob zombie = monster(HavenInvasion.Kind.ZOMBIE, at);
            if (zombie == null) {
                check("recharge par monstre tue : zombie pose", false, "");
                return true;
            }
            zombie.setHealth(1.0F);
            aim(f, GunImpacts.center(zombie));
            int kills = KILLS.size();
            GunFire.onTrigger(f, true);
            GunFire.tick(f);
            GunFire.onTrigger(f, false);
            List<GunEcoEntity> drops = ecoAround(zombie.position(), 3.0);
            GunEcoEntity drop = drops.isEmpty() ? null : drops.get(0);
            HavenMonsterKilledEvent kill = KILLS.size() > kills ? KILLS.get(KILLS.size() - 1) : null;
            check("zombie tue par la Vulcan Fury : HavenMonsterKilledEvent (tueur = le tireur), une munition lachee, BLEUE (seule reserve entamee), 10 eco",
                    zombie.isDeadOrDying() && kill != null && kill.getKiller() == f && drops.size() == 1 && drop.isDrop()
                            && drop.family() == GunForm.Family.BLUE && drop.amount() == 10,
                    "mort " + zombie.isDeadOrDying() + ", tueur " + (kill == null ? null : kill.getKiller()) + ", munitions "
                            + drops.size() + (drop == null ? "" : " " + drop.family() + " x" + drop.amount()));
            int blue = data(f).ecoBlue();
            boolean took = drop != null && GunEco.pickup(drop, f);
            check("la munition lachee se ramasse : bleu " + blue + " -> " + (blue + 10), took && data(f).ecoBlue() == blue + 10,
                    "bleu " + data(f).ecoBlue());

            RandomSource random = RandomSource.create(42L);
            FakePlayer probe = shooter("tirage", this.site.add(0, 0, 3), EAST, 0.0F);
            eco(probe, 30, 200, 200, 15);
            int red = 0;
            for (int i = 0; i < 200; i++) {
                red += GunEco.dropFamily(probe, random) == GunForm.Family.RED ? 1 : 0;
            }
            full(probe);
            int[] counts = new int[4];
            for (int i = 0; i < 400; i++) {
                counts[GunEco.dropFamily(probe, random).ordinal()]++;
            }
            eco(probe, 50, 100, 200, 15);
            int[] mixed = new int[4];
            for (int i = 0; i < 1000; i++) {
                mixed[GunEco.dropFamily(probe, random).ordinal()]++;
            }
            boolean uniform = true;
            for (int c : counts) {
                uniform &= c >= 60 && c <= 140;
            }
            check("couleur des lachers : ponderee par la part manquante (seul le rouge manque : 200 rouges sur 200) ; tout plein : au hasard",
                    red == 200 && uniform && mixed[2] == 0 && mixed[3] == 0 && mixed[0] > 350 && mixed[1] > 350,
                    "rouge seul " + red + "/200 ; plein " + java.util.Arrays.toString(counts) + " ; rouge 50 % et jaune 50 % manquants "
                            + java.util.Arrays.toString(mixed));
            for (GunEcoEntity e : ecoAround(zombie.position(), 6.0)) {
                e.discard();
            }
        }
        return true;
    }

    /** Un monstre de Haven a 8 blocs, tue par une arme ; le banc tire jusqu'a sa mort. */
    private boolean killWith(GunSpec spec, int t) {
        FakePlayer f = this.a;
        String key = "kill:" + spec;
        if (t == 0) {
            form(f, spec.form);
            full(f);
            look(f, EAST, 0.0F);
            Mob zombie = monster(HavenInvasion.Kind.ZOMBIE, f.getEyePosition().add(8.0, 0.0, 0.0));
            this.memo.put(key, zombie);
            this.memo.put(key + ":kills", KILLS.size());
            this.memo.put(key + ":start", now());
            GunFire.clearLog(f);
            if (zombie == null) {
                check("monstre tue par " + spec.form.id + " : zombie pose", false, "");
                return true;
            }
            aim(f, GunImpacts.center(zombie));
        }
        Mob zombie = (Mob) this.memo.get(key);
        if (zombie == null) {
            return true;
        }
        boolean dead = zombie.isDeadOrDying() || zombie.isRemoved();
        if (!dead) {
            boolean down = switch (spec.trigger) {
                case PRESS, HOLD -> t % 2 == 0;
                case SPIN -> true;
            };
            GunFire.onTrigger(f, down);
            GunFire.tick(f);
            return false;
        }
        GunFire.onTrigger(f, false);
        GunFire.tick(f);
        int before = (int) this.memo.get(key + ":kills");
        HavenMonsterKilledEvent kill = null;
        for (int i = before; i < KILLS.size(); i++) {
            if (KILLS.get(i).getMonster() == zombie) {
                kill = KILLS.get(i);
            }
        }
        int shots = GunFire.shotLog(f).size();
        long took = now() - (long) this.memo.get(key + ":start");
        check("zombie casque (20 PV) a 8 blocs tue par " + spec.form.id + ", credite au tireur",
                kill != null && kill.getKiller() == f,
                shots + " tir(s), " + took + " tiques ; tueur " + (kill == null ? "aucun evenement" : kill.getKiller()));
        for (GunEcoEntity e : ecoAround(zombie.position(), 8.0)) {
            e.discard();
        }
        return true;
    }

    private boolean harmless(int t) {
        FakePlayer f = this.a;
        if (t == 0) {
            look(f, EAST, 0.0F);
            Vec3 eye = f.getEyePosition();
            FakePlayer cobaye = new FakePlayer(this.level, new GameProfile(
                    UUID.nameUUIDFromBytes("autotest-tir:cobaye".getBytes(StandardCharsets.UTF_8)), "[Cobaye]"));
            cobaye.moveTo(eye.x + 4.0, this.site.y, eye.z, 90.0F, 0.0F);
            this.memo.put("cobaye", cobaye);
            this.memo.put("cobayePos", cobaye.position());
            Villager villager = HavenSpawner.spawnVillager(this.level, VillagerType.PLAINS, new Vec3(eye.x + 5.0, this.site.y, eye.z));
            if (villager != null) {
                villager.setNoAi(true);
                this.spawned.add(villager);
            }
            Zombie untagged = EntityType.ZOMBIE.create(this.level);
            if (untagged != null) {
                untagged.moveTo(eye.x + 6.5, this.site.y, eye.z, 90.0F, 0.0F);
                untagged.setNoAi(true);
                untagged.setPersistenceRequired();
                this.level.addFreshEntity(untagged);
                this.spawned.add(untagged);
            }
            Mob target = monster(HavenInvasion.Kind.ZOMBIE, eye.add(9.0, 0.0, 0.0));
            if (target != null) {
                target.setHealth(target.getMaxHealth());
            }
            this.memo.put("villager", villager);
            this.memo.put("villagerPos", villager == null ? Vec3.ZERO : villager.position());
            this.memo.put("untagged", untagged);
            this.memo.put("target", target);
            float hp = cobaye.getHealth();
            boolean refused = !GunImpacts.hurt(f, null, cobaye, 16.0F) && !GunImpacts.isTarget(cobaye) && cobaye.getHealth() == hp
                    && !GunImpacts.isTarget(villager) && !GunImpacts.isTarget(untagged) && GunImpacts.isTarget(target)
                    && !GunImpacts.isTarget(f);
            check("le filtre : ni joueur factice, ni le tireur, ni habitant, ni zombie sans etiquette ne sont des cibles ; hurt refuse (16 points de Jak)",
                    refused, "cobaye " + cobaye.getHealth() + " PV, cible etiquetee " + GunImpacts.isTarget(target));
            DamageSource ray = GunImpacts.source(f, null);
            DamageSource thrown = GunImpacts.source(f, cobaye);
            check("source des degats : type emeraldweapons:morph_gun, ni auteur ni entite directe (heros, runes, artefacts et"
                            + " armes ameliorees ne la prennent pas pour un coup de joueur), point d'origine pour le recul",
                    ray.is(GunImpacts.DAMAGE_TYPE) && ray.getEntity() == null && ray.getDirectEntity() == null
                            && f.position().equals(ray.getSourcePosition()) && thrown.getEntity() == null
                            && thrown.getDirectEntity() == null && cobaye.position().equals(thrown.getSourcePosition()),
                    "type " + ray.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("?") + ", auteur "
                            + ray.getEntity() + ", directe " + ray.getDirectEntity() + ", origine " + ray.getSourcePosition());
            form(f, GunForm.RED_1);
            full(f);
            aim(f, GunImpacts.center(target));
            GunFire.onTrigger(f, true);
        }
        Mob target = (Mob) this.memo.get("target");
        if (t == 30 || t == 36 || t == 50) {
            // form() remet la gachette a neuf et efface le releve : on compte les tirs de l'arme precedente avant
            this.memo.put("harmlessShots", (int) this.memo.getOrDefault("harmlessShots", 0) + GunFire.shotLog(f).size());
        }
        switch (t) {
            case 1 -> GunFire.onTrigger(f, false);
            case 30 -> {
                form(f, GunForm.YELLOW_1);
                if (target != null && target.isAlive()) {
                    aim(f, GunImpacts.center(target));
                }
                GunFire.onTrigger(f, true);
            }
            case 31 -> GunFire.onTrigger(f, false);
            case 36 -> {
                form(f, GunForm.BLUE_1);
                GunFire.onTrigger(f, true);
            }
            case 50 -> {
                GunFire.onTrigger(f, false);
                form(f, GunForm.DARK_1);
                if (target == null || target.isDeadOrDying() || target.isRemoved()) {
                    // un monstre neuf a la meme place : la boule du Peace Maker doit eclater pres du cobaye
                    this.memo.put("firstTarget", target);
                    target = monster(HavenInvasion.Kind.ZOMBIE, f.getEyePosition().add(9.0, 0.0, 0.0));
                    this.memo.put("target", target);
                }
                if (target != null) {
                    aim(f, GunImpacts.center(target));
                }
                GunFire.onTrigger(f, true);
            }
            case 51 -> GunFire.onTrigger(f, false);
            default -> {
                if (t > 36 && t < 50) {
                    GunFire.onTrigger(f, true);
                }
            }
        }
        GunFire.tick(f);
        if (t == 89) {
            FakePlayer cobaye = (FakePlayer) this.memo.get("cobaye");
            Villager villager = (Villager) this.memo.get("villager");
            Zombie untagged = (Zombie) this.memo.get("untagged");
            Vec3 cobayePos = (Vec3) this.memo.get("cobayePos");
            List<GunEco.Explosion> explosions = GunEco.explosions();
            GunEco.Explosion last = explosions.isEmpty() ? null : explosions.get(explosions.size() - 1);
            double blastToCobaye = last == null ? -1 : last.at().distanceTo(cobaye.position());
            double blastToVillager = last == null || villager == null ? -1 : last.at().distanceTo(villager.position());
            int shots = (int) this.memo.getOrDefault("harmlessShots", 0) + GunFire.shotLog(f).size();
            boolean cobayeOk = cobaye.getHealth() == cobaye.getMaxHealth() && cobaye.position().equals(cobayePos)
                    && cobaye.getDeltaMovement().lengthSqr() < 1.0e-12;
            boolean villagerOk = villager != null && !villager.isRemoved() && villager.getHealth() == villager.getMaxHealth()
                    && villager.position().distanceTo((Vec3) this.memo.get("villagerPos")) < 1.0e-6;
            boolean untaggedOk = untagged != null && !untagged.isRemoved() && untagged.getHealth() == untagged.getMaxHealth();
            check("dans la ligne de tir, le cone du Scatter Gun et l'explosion du Peace Maker : joueur factice, habitant et zombie sans etiquette intacts"
                            + " (ni degat, ni recul), le monstre derriere eux touche",
                    cobayeOk && villagerOk && untaggedOk && shots >= 4 && last != null && blastToCobaye <= GunSpec.PEACE_BLAST
                            && blastToVillager <= GunSpec.PEACE_BLAST && (target == null || target.getHealth() < target.getMaxHealth() || target.isDeadOrDying()),
                    String.format(Locale.ROOT, "%d tirs (4 armes) ; cobaye %.1f PV, deplace %s ; habitant %s PV ; zombie sans etiquette %s PV ;"
                                    + " explosion a %.1f bloc du cobaye et %.1f de l'habitant ; monstre %s",
                            shots, cobaye.getHealth(), !cobaye.position().equals(cobayePos),
                            villager == null ? "?" : villager.getHealth(), untagged == null ? "?" : untagged.getHealth(),
                            blastToCobaye, blastToVillager, target == null ? "?" : (target.isDeadOrDying() ? "tue" : target.getHealth() + " PV")));
            for (Entity e : java.util.Arrays.asList(villager, untagged, target)) {
                if (e != null) {
                    e.discard();
                }
            }
            for (GunEcoEntity e : ecoAround(this.site, 16.0)) {
                e.discard();
            }
            return true;
        }
        return false;
    }

    /** La derniere explosion d'une boule de ce tireur depuis cette tique, ou null (le releve garde 64 entrees). */
    private static GunEco.Explosion explosionSince(FakePlayer f, long tick) {
        List<GunEco.Explosion> all = GunEco.explosions();
        for (int i = all.size() - 1; i >= 0; i--) {
            GunEco.Explosion e = all.get(i);
            if (e.tick() < tick) {
                return null;
            }
            if (e.shooter().equals(f.getUUID())) {
                return e;
            }
        }
        return null;
    }

    /**
     * Le client ne suit pas la vitesse synchronisee du tir du Blaster (plafonnee a
     * 3,9 blocs par tique) : il calcule origine + direction x 10 x age depuis le
     * lancer synchronise. Ce calcul doit retomber sur la position du serveur.
     */
    private boolean blasterTrajectory(int t) {
        FakePlayer f = this.a;
        if (t == 0) {
            form(f, GunForm.YELLOW_1);
            full(f);
            look(f, EAST, -90.0F);
            long before = now();
            Vec3 eye = f.getEyePosition();
            GunFire.onTrigger(f, true);
            GunFire.tick(f);
            GunFire.onTrigger(f, false);
            List<GunBlasterShotEntity> shots = this.level.getEntitiesOfClass(GunBlasterShotEntity.class,
                    new AABB(eye, eye).inflate(2.0), e -> !e.isRemoved() && e.launchTick() >= before);
            this.memo.put("trajectoryShot", shots.isEmpty() ? null : shots.get(0));
            this.memo.put("trajectoryLook", f.getLookAngle());
            return false;
        }
        if (t == 4) {
            GunBlasterShotEntity shot = (GunBlasterShotEntity) this.memo.get("trajectoryShot");
            Vec3 look = (Vec3) this.memo.get("trajectoryLook");
            if (shot == null) {
                check("Blaster vu du client : le tir est parti", false, "aucun tir trouve pres de l'oeil");
                return true;
            }
            long age = now() - shot.launchTick();
            Vec3 predicted = shot.predicted(age);
            double error = shot.position().distanceTo(predicted);
            Vec3 direction = shot.predicted(1.0).subtract(shot.predicted(0.0)).scale(1.0 / GunSpec.BLASTER_SPEED);
            double speed = shot.getDeltaMovement().length();
            boolean ok = !shot.isRemoved() && age >= 3 && error < 0.01 && direction.distanceTo(look) < 1.0e-5
                    && Math.abs(speed - GunSpec.BLASTER_SPEED) < 1.0e-6;
            check("Blaster vu du client : origine, direction et tique du lancer synchronises ; origine + direction x "
                            + GunSpec.BLASTER_SPEED + " x age retombe sur la position du serveur (la vitesse envoyee aux clients"
                            + " serait plafonnee a 3,9 blocs par tique)",
                    ok, String.format(Locale.ROOT, "age %d tiques, ecart %.5f bloc, direction a %.1e du regard, vitesse serveur %.3f,"
                                    + " parcouru %.1f blocs", age, error, direction.distanceTo(look), speed,
                            shot.position().distanceTo(shot.predicted(0.0))));
            shot.discard();
            return true;
        }
        return false;
    }

    private boolean decor(int t) {
        FakePlayer f = this.a;
        BlockPos feet = BlockPos.containing(this.site);
        int eyeY = (int) Math.floor(f.getEyeY());
        if (t == 0) {
            look(f, EAST, 0.0F);
            for (int x = 12; x <= 13; x++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        stone(new BlockPos(feet.getX() + x, eyeY + dy, feet.getZ() + dz));
                    }
                }
            }
            this.memo.put("pending0", HavenDestruction.pendingCount(this.server));
            // Vulcan Fury : une balle sur le bloc du centre gauche
            form(f, GunForm.BLUE_1);
            full(f);
            aim(f, new Vec3(feet.getX() + 12.0, eyeY + 0.5, feet.getZ() - 0.5));
            GunFire.onTrigger(f, true);
            GunFire.tick(f);
            GunFire.onTrigger(f, false);
            int vulcan = HavenDestruction.pendingCount(this.server) - (int) this.memo.get("pending0");
            this.memo.put("vulcan", vulcan);
            // Blaster sur le bloc du bas droite
            form(f, GunForm.YELLOW_1);
            aim(f, new Vec3(feet.getX() + 12.0, eyeY - 1.5, feet.getZ() + 1.5));
            GunFire.onTrigger(f, true);
            GunFire.tick(f);
            GunFire.onTrigger(f, false);
            this.memo.put("afterVulcan", HavenDestruction.pendingCount(this.server));
            return false;
        }
        if (t == 4) {
            int blaster = HavenDestruction.pendingCount(this.server) - (int) this.memo.get("afterVulcan");
            this.memo.put("blaster", blaster);
            // Scatter Gun a 5 blocs du mur
            FakePlayer s = shooter("mur", new Vec3(this.site.x + 7.0, this.site.y, this.site.z), EAST, 0.0F);
            form(s, GunForm.RED_1);
            this.memo.put("wallShooter", s);
            this.memo.put("beforeScatter", HavenDestruction.pendingCount(this.server));
            GunFire.onTrigger(s, true);
            GunFire.tick(s);
            GunFire.onTrigger(s, false);
        }
        if (t == 5 || t == 6) {
            GunFire.tick((FakePlayer) this.memo.get("wallShooter"));
        }
        if (t == 8) {
            int scatter = HavenDestruction.pendingCount(this.server) - (int) this.memo.get("beforeScatter");
            this.memo.put("scatter", scatter);
            MorphGunKeeper.removeSubject(((FakePlayer) this.memo.get("wallShooter")).getUUID());
            GunFire.forget(((FakePlayer) this.memo.get("wallShooter")).getUUID());
            // Peace Maker au centre du mur, ou sur le bloc INTACT le plus proche du centre. Les plombs
            // du Scatter Gun trouent le mur au hasard : le bloc du centre est deja parti 45 fois sur
            // 100, et 7 fois sur 100 la ligne de visee traverse les deux couches (Monte Carlo de
            // 200 000 salves, scratchpad verif/mur_peace.py) ; la boule filait alors dans le vide et
            // n'eclatait qu'apres 30 tiques de vol (KO du 13 sept., 20:05). form() remet la gachette a neuf.
            form(f, GunForm.DARK_1);
            BlockPos aimed = null;
            for (int ring = 0; ring <= 1 && aimed == null; ring++) {
                for (int dy = -ring; dy <= ring && aimed == null; dy++) {
                    for (int dz = -ring; dz <= ring && aimed == null; dz++) {
                        BlockPos p = new BlockPos(feet.getX() + 12, eyeY + dy, feet.getZ() + dz);
                        if (Math.max(Math.abs(dy), Math.abs(dz)) == ring && this.level.getBlockState(p).is(Blocks.STONE)) {
                            aimed = p;
                        }
                    }
                }
            }
            if (aimed == null) {
                aimed = new BlockPos(feet.getX() + 12, eyeY, feet.getZ());
            }
            aim(f, new Vec3(aimed.getX(), aimed.getY() + 0.5, aimed.getZ() + 0.5));
            this.memo.put("peaceAim", aimed);
            this.memo.put("peaceFired", now());
            this.memo.put("beforePeace", HavenDestruction.pendingCount(this.server));
            GunFire.onTrigger(f, true);
            GunFire.tick(f);
            GunFire.onTrigger(f, false);
        }
        int checkedAt = (int) this.memo.getOrDefault("peaceChecked", -1);
        GunEco.Explosion last = null;
        boolean due = false;
        if (t > 8 && checkedAt < 0) {
            GunFire.tick(f);
            last = explosionSince(f, (long) this.memo.get("peaceFired"));
            due = last != null || t >= 8 + PEACE_WAIT;
        }
        if (due) {
            this.memo.put("peaceChecked", t);
            int peace = HavenDestruction.pendingCount(this.server) - (int) this.memo.get("beforePeace");
            BlockPos aimed = (BlockPos) this.memo.get("peaceAim");
            int wallGone = 0;
            int strayPending = 0;
            for (BlockPos p : this.placed) {
                if (this.level.getBlockState(p).isAir()) {
                    wallGone++;
                }
            }
            int total = HavenDestruction.pendingCount(this.server) - (int) this.memo.get("pending0");
            int vulcan = (int) this.memo.get("vulcan");
            int blaster = (int) this.memo.get("blaster");
            int scatter = (int) this.memo.get("scatter");
            strayPending = total - wallGone;
            check("qui casse quoi : Vulcan Fury 1 bloc ; Blaster 1 a " + GunSpec.BLASTER_BLOCKS + " (rayon " + GunSpec.BLASTER_BREAK_RADIUS
                            + ") ; Scatter Gun 1 a " + GunSpec.SCATTER_BLOCKS + " ; Peace Maker au plus " + GunSpec.PEACE_BLOCKS
                            + " (rayon " + GunSpec.PEACE_BREAK_RADIUS + ") ; rien hors du mur",
                    vulcan == 1 && blaster >= 1 && blaster <= GunSpec.BLASTER_BLOCKS && scatter >= 1 && scatter <= GunSpec.SCATTER_BLOCKS
                            && peace >= 1 && peace <= GunSpec.PEACE_BLOCKS && last != null && last.broken() == peace && strayPending == 0,
                    "Vulcan " + vulcan + ", Blaster " + blaster + ", Scatter " + scatter + ", Peace Maker " + peace
                            + (last == null ? " (pas d'explosion en " + PEACE_WAIT + " tiques)"
                            : " (explosion : " + last.broken() + ", " + (last.tick() - (long) this.memo.get("peaceFired")) + " tiques apres l'appui)")
                            + " ; vise " + aimed.toShortString() + " (decalage " + (aimed.getY() - eyeY) + ", " + (aimed.getZ() - feet.getZ())
                            + " du centre) ; mur : " + wallGone + " trous sur " + this.placed.size() + ", en attente " + total
                            + ", hors du mur " + strayPending);
            this.memo.put("brokeAt", now());
            checkedAt = t;
        }
        if (checkedAt >= 0 && t == checkedAt + HavenDestruction.DELAY_MAX_TICKS + 20) {
            int holes = 0;
            for (BlockPos p : this.placed) {
                if (!this.level.getBlockState(p).is(Blocks.STONE)) {
                    holes++;
                }
            }
            check("reconstruction naturelle : 16 s apres, le mur est entier (pierre a l'identique), rien en attente",
                    holes == 0 && HavenDestruction.pendingCount(this.server) == 0,
                    holes + " trous, " + HavenDestruction.pendingCount(this.server) + " en attente, "
                            + (now() - (long) this.memo.get("brokeAt")) + " tiques apres la casse");
            // puis par l'API : un tir de Blaster et rebuildAll tout de suite
            form(f, GunForm.YELLOW_1);
            aim(f, new Vec3(feet.getX() + 12.0, eyeY + 0.5, feet.getZ() + 0.5));
            GunFire.onTrigger(f, true);
            GunFire.tick(f);
            GunFire.onTrigger(f, false);
        }
        if (checkedAt >= 0 && t == checkedAt + HavenDestruction.DELAY_MAX_TICKS + 24) {
            int pending = HavenDestruction.pendingCount(this.server);
            int rebuilt = HavenDestruction.rebuildAll(this.level);
            int holes = 0;
            for (BlockPos p : this.placed) {
                if (!this.level.getBlockState(p).is(Blocks.STONE)) {
                    holes++;
                }
            }
            check("HavenDestruction.rebuildAll apres un tir de Blaster : les blocs casses reviennent tout de suite",
                    pending >= 1 && rebuilt == pending && holes == 0 && HavenDestruction.pendingCount(this.server) == 0,
                    pending + " en attente, " + rebuilt + " reposes, " + holes + " trous");
            for (BlockPos p : this.placed) {
                this.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            this.placed.clear();
            return true;
        }
        return false;
    }

    private boolean guarded(int t) {
        FakePlayer f = this.a;
        if (t == 0) {
            form(f, GunForm.BLUE_1);
            full(f);
            look(f, EAST, 90.0F);
            GunImpacts.Ray ray = GunImpacts.ray(this.level, f, f.getEyePosition(), f.getLookAngle(), GunSpec.VULCAN_RANGE, 0.1F);
            this.memo.put("floor", ray.block());
            this.memo.put("floorState", ray.block() == null ? null : this.level.getBlockState(ray.block()));
            this.memo.put("pendingG", HavenDestruction.pendingCount(this.server));
        }
        GunFire.onTrigger(f, t < 10);
        GunFire.tick(f);
        if (t == 11) {
            BlockPos floor = (BlockPos) this.memo.get("floor");
            BlockState before = (BlockState) this.memo.get("floorState");
            int shots = GunFire.shotLog(f).size();
            check("Vulcan Fury tiree a la verticale dans la rade : la balle traverse l'eau, le fond (sous la surface) reste intact",
                    floor != null && HavenProtection.reason(this.level, floor) != null && this.level.getBlockState(floor) == before
                            && HavenDestruction.pendingCount(this.server) == (int) this.memo.get("pendingG") && shots >= 2,
                    (floor == null ? "aucun bloc touche" : "fond " + floor.toShortString() + " (" + HavenProtection.reason(this.level, floor)
                            + ")") + ", " + shots + " balles, en attente " + HavenDestruction.pendingCount(this.server));
            StringBuilder detail = new StringBuilder();
            boolean ok = true;
            record Probe(String what, BlockPos cell) {
            }
            int pendingBefore = HavenDestruction.pendingCount(this.server);
            int brokenTotal = 0;
            int guardedTotal = 0;
            for (Probe probe : List.of(new Probe("comptoir du Hip Hog", new BlockPos(333, 68, 167)),
                    new Probe("mur de l'appartement 1", new BlockPos(88, 70, 185)),
                    new Probe("parvis du bar", new BlockPos(362, 65, 210)),
                    new Probe("fond de la rade sous le site", new BlockPos(SITE_CELL.getX(), 56, SITE_CELL.getZ())))) {
                BlockPos center = this.origin.offset(probe.cell());
                this.level.getChunkAt(center);
                // l'etat de chaque bloc protege (non vide) du voisinage, avant l'explosion
                Map<BlockPos, BlockState> guardedStates = new HashMap<>();
                for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3))) {
                    BlockState state = this.level.getBlockState(p);
                    if (!state.isAir() && HavenProtection.reason(this.level, p) != null) {
                        guardedStates.put(p.immutable(), state);
                    }
                }
                int broken = GunImpacts.breakSphere(this.level, Vec3.atCenterOf(center), GunSpec.PEACE_BREAK_RADIUS,
                        GunSpec.PEACE_BLOCKS, f);
                brokenTotal += broken;
                int changed = 0;
                for (Map.Entry<BlockPos, BlockState> e : guardedStates.entrySet()) {
                    if (this.level.getBlockState(e.getKey()) != e.getValue()) {
                        changed++;
                    }
                }
                ok &= changed == 0;
                guardedTotal += guardedStates.size();
                detail.append(probe.what()).append(" : ").append(guardedStates.size()).append(" blocs proteges, ").append(changed)
                        .append(" touches ; ").append(broken).append(" blocs non proteges casses autour ; ");
            }
            check("explosion du Peace Maker (rayon 3) au comptoir du Hip Hog, a l'appartement 1, au parvis et au fond de la rade :"
                            + " aucun bloc protege touche",
                    ok && guardedTotal >= 100, detail.toString());
            int rebuilt = HavenDestruction.rebuildAll(this.level);
            line("blocs non proteges casses par ces essais, reposes tout de suite : " + rebuilt + " (en attente avant : "
                    + pendingBefore + ", casses " + brokenTotal + ")");
            return true;
        }
        return false;
    }

    private boolean mspt(int t) {
        if (t == 0) {
            line("reference : 200 tiques sans tir");
        }
        if (t < 200) {
            sample(t, "idle");
            return false;
        }
        int u = t - 200;
        List<FakePlayer> shooters = mspters();
        if (u == 0) {
            GunForm[] forms = {GunForm.BLUE_1, GunForm.RED_1, GunForm.YELLOW_1, GunForm.DARK_1};
            for (int i = 0; i < 4; i++) {
                FakePlayer s = shooters.get(i);
                form(s, forms[i]);
                full(s);
                GunFire.clearLog(s);
                BlockPos base = BlockPos.containing(s.getEyePosition());
                for (int x = 10; x <= 15; x++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        for (int dz = -4; dz <= 4; dz++) {
                            stone(base.offset(x, dy, dz));
                        }
                    }
                }
            }
            this.memo.put("killsM", KILLS.size());
            this.memo.put("pendingM", HavenDestruction.pendingCount(this.server));
        }
        for (int i = 0; i < 4; i++) {
            FakePlayer s = shooters.get(i);
            String key = "msptTarget" + i;
            Mob target = (Mob) this.memo.get(key);
            if ((target == null || target.isRemoved() || target.isDeadOrDying()) && u % 10 == 0) {
                target = monster(HavenInvasion.Kind.ZOMBIE, s.getEyePosition().add(6.0, 0.0, 0.0));
                this.memo.put(key, target);
            }
            look(s, EAST, 0.0F);
            if (u % 20 == 0) {
                full(s);
            }
            boolean down = switch (i) {
                case 0 -> true;
                default -> u % 2 == 0;
            };
            GunFire.onTrigger(s, down && u < 600);
            GunFire.tick(s);
        }
        if (u >= 600) {
            sample(t, "fire");
        } else {
            sample(t, "fire");
        }
        if (u == 619) {
            StringBuilder shots = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                shots.append(data(shooters.get(i)).form().id).append(' ').append(GunFire.shotLog(shooters.get(i)).size()).append(" ; ");
            }
            String idle = window("idle");
            String fire = window("fire");
            double mean = (double) this.memo.getOrDefault("fireMean", 0.0);
            line("MSPT sans tir : " + idle);
            line("MSPT avec 4 tireurs en continu (Vulcan Fury tenue, Scatter Gun, Blaster et Peace Maker en appuis) : " + fire);
            check("MSPT avec 4 tireurs en continu pendant 30 s : moyenne sous 20 ms (tique de 50 ms)",
                    mean > 0.0 && mean < 20.0,
                    String.format(Locale.ROOT, "moyenne %.2f ms ; tirs %s; monstres tues %d ; blocs casses %d (en attente)", mean, shots,
                            KILLS.size() - (int) this.memo.get("killsM"),
                            HavenDestruction.pendingCount(this.server) - (int) this.memo.get("pendingM")));
            for (FakePlayer s : shooters) {
                GunFire.onTrigger(s, false);
                GunFire.tick(s);
            }
            return true;
        }
        return false;
    }

    private List<FakePlayer> mspters() {
        @SuppressWarnings("unchecked")
        List<FakePlayer> list = (List<FakePlayer>) this.memo.get("mspters");
        if (list == null) {
            list = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                list.add(shooter("mspt" + i, this.site.add(0.0, 0.0, -30.0 + 20.0 * i), EAST, 0.0F));
            }
            this.memo.put("mspters", list);
        }
        return list;
    }

    private final Map<String, long[]> windows = new HashMap<>();
    private final Map<String, StringBuilder> perWindow = new HashMap<>();

    private void sample(int t, String name) {
        if (t % 100 != 99) {
            return;
        }
        long[] times = this.server.getTickTimesNanos();
        long sum = 0;
        long max = 0;
        for (long v : times) {
            sum += v;
            max = Math.max(max, v);
        }
        long[] w = this.windows.computeIfAbsent(name, k -> new long[3]);
        w[0] += sum / times.length;
        w[1] = Math.max(w[1], max);
        w[2]++;
        this.perWindow.computeIfAbsent(name, k -> new StringBuilder())
                .append(String.format(Locale.ROOT, "%.2f/%.1f ", sum / (double) times.length / 1.0e6, max / 1.0e6));
        if ("fire".equals(name)) {
            this.memo.put("fireMean", w[0] / (double) w[2] / 1.0e6);
        }
    }

    private String window(String name) {
        long[] w = this.windows.get(name);
        if (w == null || w[2] == 0) {
            return "pas de mesure";
        }
        return String.format(Locale.ROOT, "moyenne %.2f ms, pire tique %.2f ms (%d fenetres de 100 tiques ; moyenne/pire : %s)",
                w[0] / (double) w[2] / 1.0e6, w[1] / 1.0e6, w[2], this.perWindow.get(name).toString().trim());
    }

    private boolean cleanup(int t) {
        int entities = 0;
        for (Entity e : this.spawned) {
            if (!e.isRemoved()) {
                e.discard();
                entities++;
            }
        }
        AABB around = new AABB(this.site, this.site).inflate(96.0);
        for (Class<? extends Entity> type : List.of(GunBlasterShotEntity.class, GunPeaceBallEntity.class, GunArcEntity.class)) {
            for (Entity e : this.level.getEntitiesOfClass(type, around)) {
                e.discard();
                entities++;
            }
        }
        for (Mob mob : this.level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.isNoAi()
                && m.getTags().contains(HavenInvasion.MONSTER_TAG) && m.distanceToSqr(this.site) < 96.0 * 96.0)) {
            mob.discard();
            entities++;
        }
        entities += GunEco.removeAll(this.level, true);
        int rebuilt = HavenDestruction.rebuildAll(this.level);
        for (BlockPos p : this.placed) {
            this.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        int blocks = this.placed.size();
        this.placed.clear();
        for (FakePlayer fake : this.fakes) {
            GunFire.forget(fake.getUUID());
            MorphGunKeeper.removeSubject(fake.getUUID());
        }
        for (ChunkPos chunk : this.forced) {
            this.level.setChunkForced(chunk.x, chunk.z, false);
        }
        line("site nettoye : " + entities + " entites retirees, " + rebuilt + " blocs reconstruits, " + blocks
                + " pierres du banc retirees, " + this.forced.size() + " troncons liberes ; troncons forces restants dans Haven "
                + this.level.getForcedChunks().size() + ", en attente " + HavenDestruction.pendingCount(this.server));
        return true;
    }
}
