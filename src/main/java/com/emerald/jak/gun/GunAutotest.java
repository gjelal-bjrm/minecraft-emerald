package com.emerald.jak.gun;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenAutotest;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenSite;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Le banc d'essai du Morph Gun, INERTE sans EMERALDWEAPONS_AUTOTEST=armes.
 *
 * DEUX PARTIES.
 *
 * 1. LES POSES, PAR LE CHEMIN JAVA : JakGunModel lit morph_gun.bin depuis le jar
 *    (comme le rendu), GunPose pose les quatre formes de base -- et les huit
 *    autres --, et l'on compare les tailles visibles a build/jak/gun/morph-gun.json
 *    a 1 mm pres, la bouche du canon, et les transformations du changement de
 *    famille (coupe directe sur la premiere clef, fin sur la pose cible). Les
 *    sommets poses par Java -- formes, images des transformations, prise en
 *    troisieme et premiere personne avec les formules vanilla de la pose
 *    d'arbalete et du calque de main, icone d'inventaire -- sont ecrits dans
 *    armes_sommets.json, qu'un script dessine hors ligne.
 *
 * 2. LE CONFINEMENT, sur des FakePlayer inscrits comme cobayes du gardien.
 *    Ce qu'un FakePlayer ne peut pas faire, mesure par le banc du vote : changer
 *    de dimension (NullPointerException au milieu du changement). Les departs
 *    sont donc joues par l'evenement que ServerPlayer.changeDimension publie en
 *    premier (EntityTravelToDimensionEvent, :888) -- c'est le seul point par
 *    lequel passent vote, skip, closeIfStarted, retardataire, /haven back et les
 *    teleportations des autres mods --, puis PlayerChangedDimensionEvent. La
 *    connexion, la deconnexion et la reapparition appellent les gestionnaires du
 *    gardien directement : publier ces evenements pour un FakePlayer ferait
 *    aussi tourner quetes, heros et specialisations. Les jets et les menus
 *    passent par le vrai code (drop, clicked, closeContainer). La mort aussi,
 *    etape par etape : FakePlayer.die est VIDE dans NeoForge (FakePlayer.java,
 *    mesure au premier lancement : aucune arme retiree, aucune pierre tombee),
 *    on rejoue donc ce que ServerPlayer.die fait d'utile ici --
 *    CommonHooks.onLivingDeath, puis dropAllDeathLoot (inventaire lache sans
 *    keepInventory, CommonHooks.onLivingDrops, entites ajoutees).
 *
 * Rapport dans armes_autotest.txt, dans le dossier du serveur, puis arret.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class GunAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final boolean ENABLED = "armes".equalsIgnoreCase(
            Objects.requireNonNullElse(System.getenv(HavenAutotest.VARIABLE), "").trim());

    private static final int SETTLE_TICKS = 60;
    private static final int TIMEOUT_TICKS = 20 * 60 * 20;
    /** Un passage du gardien, plus une tique. */
    private static final int GUARD_WAIT = MorphGunKeeper.GUARD_TICKS + 1;

    private record Step(String name, int pause, Runnable run) {
    }

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static final StringBuilder OUT = new StringBuilder();
    private static final Set<UUID> CANCEL_DEATH = new HashSet<>();
    private static final List<UUID> SUBJECTS = new ArrayList<>();
    private static int passed;
    private static int failed;
    private static int waited;
    private static int delay;
    private static boolean started;
    private static boolean finished;
    @Nullable
    private static MinecraftServer server;
    @Nullable
    private static JsonObject reference;

    private GunAutotest() {
    }

    // ================================================================ deroulement

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || finished) {
            return;
        }
        if (!started) {
            waited++;
            if (waited < SETTLE_TICKS || (HavenSite.busy() && waited < TIMEOUT_TICKS)) {
                return;
            }
            started = true;
            server = event.getServer();
            line("autotest du Morph Gun, " + LocalDateTime.now().withNano(0));
            plan();
        }
        if (delay > 0) {
            delay--;
            return;
        }
        if (bench != null) {
            if (!bench.tick()) {
                return;
            }
            bench = null;
        }
        Step step = STEPS.poll();
        if (step == null) {
            end();
            return;
        }
        try {
            step.run().run();
        } catch (RuntimeException e) {
            check(step.name() + " : sans exception", false, e.toString());
            LOGGER.error("autotest armes : exception dans {}", step.name(), e);
        }
        Step next = STEPS.peek();
        delay = next == null ? 0 : next.pause();
    }

    /** Annule la mort d'un cobaye, APRES le retrait du gardien (HIGHEST) : « un autre mod annule ». */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (ENABLED && CANCEL_DEATH.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    private static void then(String name, int pause, Runnable run) {
        STEPS.add(new Step(name, pause, run));
    }

    private static void plan() {
        then("poses", 0, GunAutotest::poses);
        then("etat du lobby", 0, GunAutotest::lobbyState);
        Confinement c = new Confinement();
        then("arrivee", 0, c::arrival);
        then("gardien, arme en main", 100, c::guardHeld);
        then("reconnexion", 0, c::reconnect);
        then("inventaire plein", 0, c::fullInventory);
        then("inventaire plein, case liberee", GUARD_WAIT, c::fullInventoryFreed);
        then("jets", 0, c::tosses);
        then("coffre de l'Ender", 0, c::enderChest);
        then("mort, keepInventory faux", 0, c::deathDrop);
        then("mort, keepInventory vrai", 0, c::deathKeep);
        then("mort annulee", 0, c::deathCancelled);
        then("mort annulee, gardien", GUARD_WAIT, c::deathCancelledGuard);
        then("departs", 0, c::departures);
        then("hors de la ville", 0, c::outside);
        then("hors de la ville, gardien", GUARD_WAIT, c::outsideGuard);
        then("lobby ferme et ancien lobby", 0, c::closedLobby);
        then("operateur en chantier", 0, c::chantier);
        then("changement d'arme", 0, c::selection);
        then("nettoyage", 0, c::cleanup);
        // 3. le tir : GunFireBench, sur plusieurs centaines de tiques
        then("tir", 0, () -> {
            boolean open = HavenInvasion.cityOpen(server);
            check("--- tir : ville ouverte (lobby ouvert, posee, sans pose en cours)", open, "ville ouverte " + open);
            bench = open ? new GunFireBench(server) : null;
        });
    }

    /** Le banc du tir en cours (partie 3), ou null. */
    @Nullable
    private static GunFireBench bench;

    // ================================================================ 1. poses

    private static void poses() {
        line("--- poses, par le chemin Java (JakGunModel depuis le jar, GunPose)");
        JakGunModel model;
        try {
            model = JakGunModel.load(JakGunModel.GUN);
        } catch (IOException e) {
            check("morph_gun.bin lu depuis le jar", false, e.toString());
            return;
        }
        check("morph_gun.bin lu depuis le jar : 1115 triangles, 47 os, 13 poses, 13 transformations",
                model.triangles == 1115 && model.bones == 47 && model.poseNames.length == 13
                        && model.animNames.length == 13 && model.main >= 0 && "main".equals(model.boneNames[model.main]),
                model.triangles + " triangles, " + model.bones + " os, " + model.poseNames.length + " poses, "
                        + model.animNames.length + " transformations, main " + model.main);
        int[] ammoTriangles = {50, 44, 44, 48};
        StringBuilder ammo = new StringBuilder();
        boolean ammoOk = true;
        for (GunForm.Family family : GunForm.Family.values()) {
            JakGunModel m = JakGunModel.ammo(family);
            int want = ammoTriangles[family.ordinal()];
            ammoOk &= m != null && m.triangles == want && m.poseNames.length == 0;
            ammo.append(family.jak).append(' ').append(m == null ? "illisible" : m.triangles + " triangles").append(" ; ");
        }
        check("gun_ammo_red/yellow/blue/dark.bin lus : 50, 44, 44, 48 triangles, sans pose", ammoOk, ammo.toString());

        reference = readReference();
        if (reference == null) {
            check("mesures de l'etude lues (build/jak/gun/morph-gun.json)", false, "fichier absent ou illisible");
        }
        GunPose pose = new GunPose(model);
        Sommets out = new Sommets();
        int muzzle = model.boneIndex("muzzle");
        double[] origin = new double[3];
        for (GunForm form : GunForm.values()) {
            pose.pose(form);
            double[] box = pose.visibleBox();
            double[] size = {box[3] - box[0], box[4] - box[1], box[5] - box[2]};
            int visible = pose.visibleCount();
            pose.boneOrigin(muzzle, origin);
            String key = "gun-" + form.family.jak + "-" + form.rank;
            JsonObject ref = reference == null ? null : reference.getAsJsonObject("formes").getAsJsonObject(key);
            double worst = Double.NaN;
            int refVisible = -1;
            double refMuzzle = Double.NaN;
            if (ref != null) {
                JsonArray want = ref.getAsJsonArray("taille_xyz_m");
                worst = 0.0;
                for (int k = 0; k < 3; k++) {
                    worst = Math.max(worst, Math.abs(size[k] - want.get(k).getAsDouble()));
                }
                refVisible = ref.get("triangles_visibles").getAsInt();
                refMuzzle = ref.getAsJsonArray("muzzle").get(2).getAsDouble();
            }
            boolean base = (GunForm.BASE_MASK & form.bit()) != 0;
            // les references sont arrondies au millimetre : 1 mm + le demi-millimetre d'arrondi
            boolean ok = ref != null && worst <= 0.0015 && visible == refVisible
                    && Math.abs(origin[2] - refMuzzle) <= 0.0015;
            check((base ? "forme de base " : "forme ") + form.id + " : taille visible a 1 mm de morph-gun.json,"
                            + " memes triangles visibles, bouche du canon",
                    ok, String.format(Locale.ROOT, "Java %.4f x %.4f x %.4f m, ecart %.4f m ; %d triangles (etude %d) ;"
                                    + " bouche z %.3f (etude %.3f)", size[0], size[1], size[2], worst, visible,
                            refVisible, origin[2], refMuzzle));
            out.pose("forme:" + form.id, pose, null);
        }
        double[] plan = {0.64, 1.26, 0.86, 1.20};
        StringBuilder mouths = new StringBuilder();
        boolean mouthOk = true;
        for (GunForm.Family family : GunForm.Family.values()) {
            pose.pose(GunForm.of(family, 1)).boneOrigin(muzzle, origin);
            mouthOk &= Math.abs(origin[2] - plan[family.ordinal()]) <= 0.01;
            mouths.append(String.format(Locale.ROOT, "%s %.3f ; ", family.jak, origin[2]));
        }
        check("bouche en z 0,64 (rouge 1), 1,26 (jaune 1), 0,86 (bleu 1), 1,20 (sombre 1) a 1 cm", mouthOk,
                mouths.toString());

        transformations(model, out);
        hold(model, out);
        out.write(server.getServerDirectory().resolve("armes_sommets.json"));
    }

    @Nullable
    private static JsonObject readReference() {
        Path project = server.getServerDirectory().toAbsolutePath().normalize().getParent();
        Path file = project.resolve("build").resolve("jak").resolve("gun").resolve("morph-gun.json");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("autotest armes : {} illisible", file, e);
            return null;
        }
    }

    /**
     * Ecart entre deux poses, sur les triangles visibles des deux : {triangles de
     * visibilite differente, plus grand ecart d'un sommet (m)}.
     */
    private static double[] gap(JakGunModel model, GunPose a, GunPose b) {
        double[] p = new double[3];
        double[] q = new double[3];
        int mismatch = 0;
        double worst = 0.0;
        for (int t = 0; t < model.triangles; t++) {
            boolean va = a.visible(t);
            if (va != b.visible(t)) {
                mismatch++;
                continue;
            }
            if (!va) {
                continue;
            }
            for (int v = 0; v < 3; v++) {
                a.vertex(t * 3 + v, p);
                b.vertex(t * 3 + v, q);
                worst = Math.max(worst, Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1])
                        + (p[2] - q[2]) * (p[2] - q[2])));
            }
        }
        return new double[]{mismatch, worst};
    }

    private static void transformations(JakGunModel model, Sommets out) {
        line("--- changement de famille : coupe directe sur la premiere clef, puis l'animation");
        GunPose shown = new GunPose(model);
        GunPose key = new GunPose(model);
        GunPose target = new GunPose(model);
        GunPose red = new GunPose(model).pose(GunForm.RED_1);
        // {forme cible, ecart maximal du debut a la pose rouge, ecart maximal de la fin a la cible, tiques}
        Object[][] cases = {
                {GunForm.YELLOW_1, 0.02, 0.042, 7},
                {GunForm.BLUE_1, 0.02, 0.026, 17},
                {GunForm.DARK_1, 0.001, 0.001, 7}};
        double mainTarget = red.scale[model.main];
        for (Object[] c : cases) {
            GunForm to = (GunForm) c[0];
            GunPose.Clip clip = GunPose.clip(model, GunForm.RED_1, to);
            int anim = clip.anim();
            if (anim < 0) {
                check("rouge -> " + to.id + " : animation trouvee", false, "aucune animation " + GunPose.clipName(GunForm.RED_1, to));
                continue;
            }
            // premiere image : exactement la premiere clef, a l'echelle de main de la cible
            shown.show(GunForm.RED_1, to, 0.0);
            key.anim(anim, 0.0, to);
            double[] first = gap(model, shown, key);
            double[] fromRed = gap(model, key, red);
            double rawMain = new GunPose(model).anim(anim, 0.0).scale[model.main];
            double worstMain = 0.0;
            for (int i = 0; i <= 40; i++) {
                shown.show(GunForm.RED_1, to, clip.ticks() * i / 40.0);
                worstMain = Math.max(worstMain, Math.abs(shown.scale[model.main] - mainTarget));
            }
            // derniere image jouee et pose tenue ensuite
            shown.show(GunForm.RED_1, to, clip.ticks() - 1.0e-3);
            target.pose(to);
            double[] beforeEnd = gap(model, key.anim(anim, clip.seconds()), target);
            shown.show(GunForm.RED_1, to, clip.ticks());
            double[] after = gap(model, shown, target);
            double expectRed = (Double) c[1];
            boolean ok = clip.ticks() == (Integer) c[3] && first[0] == 0 && first[1] == 0.0 && worstMain < 1.0e-3
                    && fromRed[1] <= expectRed
                    && beforeEnd[0] == 0 && beforeEnd[1] <= (Double) c[2]
                    && after[0] == 0 && after[1] == 0.0;
            check(clip.name() + " : premiere image = premiere clef, a moins de 2 cm de la pose rouge (pas de saut),"
                            + " echelle de main de la cible tout du long, fin sur la pose " + to.pose + ", " + c[3] + " tiques",
                    ok, String.format(Locale.ROOT, "%d tiques (%.3f s) ; premiere image - clef 0 : %.0f triangles, %.4f m ;"
                                    + " clef 0 - pose rouge : %.0f triangles, %.3f m ; echelle de main brute a la clef 0"
                                    + " %.3f, jouee a %.3f pres de %.3f ; derniere clef - cible : %.0f triangles, %.3f m ;"
                                    + " apres %d tiques : %.0f triangles, %.4f m",
                            clip.ticks(), clip.seconds(), first[0], first[1], fromRed[0], fromRed[1], rawMain, worstMain,
                            mainTarget, beforeEnd[0], beforeEnd[1], clip.ticks(), after[0], after[1]));
            String id = clip.name().substring("gun-gun-".length());
            for (double t : new double[]{0.0, clip.ticks() / 3.0, 2.0 * clip.ticks() / 3.0, clip.ticks() - 0.01}) {
                shown.show(GunForm.RED_1, to, t);
                out.pose(String.format(Locale.ROOT, "trans:%s:%.2f", id, t), shown, null);
            }
        }
        GunPose.Clip toRed = GunPose.clip(model, GunForm.YELLOW_1, GunForm.RED_1);
        GunPose.Clip same = GunPose.clip(model, GunForm.BLUE_1, GunForm.BLUE_1);
        GunPose.Clip upgrade = GunPose.clip(model, GunForm.RED_1, GunForm.RED_2);
        check("vers le rouge : instantane ; meme forme : rien ; amelioration red1-red2 : animee (jalon B)",
                toRed.anim() < 0 && same.anim() < 0 && upgrade.anim() >= 0 && upgrade.ticks() == 7,
                "jaune -> rouge " + toRed.name() + ", bleu -> bleu " + same.name() + ", rouge 1 -> 2 "
                        + upgrade.name() + " " + upgrade.ticks() + " tiques");
    }

    // ================================================================ prise, par les formules vanilla

    /**
     * Le repere de l'objet en troisieme personne, main droite, comme le jeu le
     * construit : LivingEntityRenderer (lacet du corps 0, echelle -1 -1 1,
     * PlayerRenderer 0,9375, translation -1,501), bras droit en pose d'arbalete
     * (AnimationUtils.animateCrossbowHold + bobModelPart moyen), ItemInHandLayer
     * (X -90, Y 180, translation 1/16 ; 0,125 ; -0,625), display (echelle), puis
     * le -0,5 d'ItemRenderer et le +0,5 de MorphGunItemRenderer.
     */
    static Matrix4f thirdPersonFrame(float headPitch) {
        float pi = (float) Math.PI;
        Matrix4f m = entityBase();
        m.translate(-5.0F / 16.0F, 2.0F / 16.0F, 0.0F);
        m.rotate(new Quaternionf().rotationZYX(GunHold.BOB_ROLL, GunHold.CROSSBOW_YAW,
                GunHold.CROSSBOW_PITCH + headPitch));
        m.rotateX(-pi / 2.0F).rotateY(pi).translate(1.0F / 16.0F, 0.125F, -0.625F);
        m.scale(GunHold.THIRD_PERSON_SCALE).translate(-0.5F, -0.5F, -0.5F).translate(0.5F, 0.5F, 0.5F);
        return m;
    }

    private static Matrix4f entityBase() {
        return new Matrix4f().rotateY((float) Math.toRadians(180.0)).scale(-1.0F, -1.0F, 1.0F)
                .scale(0.9375F).translate(0.0F, -1.501F, 0.0F);
    }

    /** Le point d'objet de la main gauche en pose d'arbalete (sans display). */
    private static Vector3f leftItemPoint(float headPitch) {
        float pi = (float) Math.PI;
        Matrix4f m = entityBase();
        m.translate(5.0F / 16.0F, 2.0F / 16.0F, 0.0F);
        m.rotate(new Quaternionf().rotationZYX(-GunHold.BOB_ROLL, 0.6F, -1.5F + headPitch));
        m.rotateX(-pi / 2.0F).rotateY(pi).translate(-1.0F / 16.0F, 0.125F, -0.625F);
        return m.transformPosition(new Vector3f());
    }

    /** Les boites du joueur (tete, corps, bras en pose d'arbalete, jambes), huit coins chacune, en blocs. */
    private static List<float[]> playerBoxes(float headPitch) {
        List<float[]> out = new ArrayList<>();
        // {pivot x y z, rotation z y x, coin min x y z, coin max x y z} en pixels de modele
        float[][] parts = {
                {0, 0, 0, 0, 0, headPitch, -4, -8, -4, 4, 0, 4},
                {0, 0, 0, 0, 0, 0, -4, 0, -2, 4, 12, 2},
                {-5, 2, 0, GunHold.BOB_ROLL, GunHold.CROSSBOW_YAW, GunHold.CROSSBOW_PITCH + headPitch, -3, -2, -2, 1, 10, 2},
                {5, 2, 0, -GunHold.BOB_ROLL, 0.6F, -1.5F + headPitch, -1, -2, -2, 3, 10, 2},
                {-1.9F, 12, 0, 0, 0, 0, -2, 0, -2, 2, 12, 2},
                {1.9F, 12, 0, 0, 0, 0, -2, 0, -2, 2, 12, 2}};
        for (float[] part : parts) {
            Matrix4f m = entityBase().translate(part[0] / 16.0F, part[1] / 16.0F, part[2] / 16.0F)
                    .rotate(new Quaternionf().rotationZYX(part[3], part[4], part[5]));
            float[] corners = new float[24];
            for (int i = 0; i < 8; i++) {
                Vector3f c = new Vector3f((i & 1) != 0 ? part[9] : part[6], (i & 2) != 0 ? part[10] : part[7],
                        (i & 4) != 0 ? part[11] : part[8]).div(16.0F);
                m.transformPosition(c);
                corners[i * 3] = c.x;
                corners[i * 3 + 1] = c.y;
                corners[i * 3 + 2] = c.z;
            }
            out.add(corners);
        }
        return out;
    }

    private static void hold(JakGunModel model, Sommets out) {
        line("--- prise : troisieme personne (pose d'arbalete, formules vanilla), premiere personne, inventaire");
        GunPose pose = new GunPose(model);
        Matrix4f hold = new Matrix4f();
        for (int deg : new int[]{-30, 0, 30}) {
            out.boxes("joueur:" + deg, playerBoxes((float) Math.toRadians(deg)));
        }
        for (GunForm.Family family : GunForm.Family.values()) {
            GunForm form = GunForm.of(family, 1);
            Matrix4f fit = GunHold.fit(pose.pose(form), new Matrix4f());
            StringBuilder errors = new StringBuilder();
            double worst = 0.0;
            for (int deg = -60; deg <= 60; deg += 30) {
                float pitch = (float) Math.toRadians(deg);
                Matrix4f frame = thirdPersonFrame(pitch).mul(GunHold.hold(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        form, fit, hold));
                Vector3f barrel = frame.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
                Vector3f look = new Vector3f(0.0F, (float) -Math.sin(pitch), (float) Math.cos(pitch));
                double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, barrel.dot(look)))));
                if (deg == 0) {
                    worst = angle;
                }
                errors.append(String.format(Locale.ROOT, "%d deg : %.2f deg ; ", deg, angle));
                if (deg == -30 || deg == 0 || deg == 30) {
                    out.pose("prise3:" + form.id + ":" + deg, pose, frame);
                    double[] rh = form.rightHand();
                    double[] lh = form.leftHand();
                    Vector3f gripR = frame.transformPosition(new Vector3f((float) rh[0], (float) rh[1], (float) rh[2]));
                    Vector3f gripL = frame.transformPosition(new Vector3f((float) lh[0], (float) lh[1], (float) lh[2]));
                    Vector3f handR = thirdPersonFrame(pitch).transformPosition(new Vector3f());
                    Vector3f handL = leftItemPoint(pitch);
                    out.points("mains:" + form.id + ":" + deg, handR, gripR, handL, gripL);
                    if (deg == 0) {
                        line(String.format(Locale.ROOT, "  %s, regard horizontal : main droite du joueur (%.3f %.3f %.3f),"
                                        + " point main droite de l'arme a %.4f bloc ; main gauche du joueur a %.3f bloc du"
                                        + " point main gauche de l'arme", form.id, handR.x, handR.y, handR.z,
                                handR.distance(gripR), handL.distance(gripL)));
                    }
                }
            }
            check("troisieme personne, " + form.id + " : canon le long du regard horizontal (a 1 degre)",
                    worst < 1.0, "ecart canon - regard selon le tangage : " + errors);

            Matrix4f first = new Matrix4f().translate(GunHold.FIRST_X, GunHold.FIRST_Y, GunHold.FIRST_Z)
                    .scale(GunHold.FIRST_PERSON_SCALE).translate(-0.5F, -0.5F, -0.5F).translate(0.5F, 0.5F, 0.5F)
                    .mul(GunHold.hold(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, form, fit, hold));
            Vector3f barrel = first.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
            out.pose("prise1:" + form.id, pose, first);
            check("premiere personne, " + form.id + " : canon droit devant (-z de la camera), echelle 0,65",
                    barrel.z < -0.9999F, String.format(Locale.ROOT, "canon (%.4f %.4f %.4f)", barrel.x, barrel.y, barrel.z));

            Matrix4f gui = GunHold.hold(ItemDisplayContext.GUI, form, fit, new Matrix4f());
            double[] extent = extent(model, pose, gui);
            out.pose("gui:" + form.id, pose, gui);
            check("icone d'inventaire, " + form.id + " : l'arme entiere tient dans la case (+/-0,5) et la remplit",
                    extent[0] >= -0.5 && extent[1] <= 0.5 && extent[2] >= -0.5 && extent[3] <= 0.5
                            && Math.max(extent[1] - extent[0], extent[3] - extent[2]) >= 0.85,
                    String.format(Locale.ROOT, "x %.3f a %.3f, y %.3f a %.3f", extent[0], extent[1], extent[2], extent[3]));
        }
    }

    private static double[] extent(JakGunModel model, GunPose pose, Matrix4f m) {
        double[] p = new double[3];
        double[] e = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE};
        Vector3f v = new Vector3f();
        for (int t = 0; t < model.triangles; t++) {
            if (!pose.visible(t)) {
                continue;
            }
            for (int k = 0; k < 3; k++) {
                pose.vertex(t * 3 + k, p);
                m.transformPosition(v.set((float) p[0], (float) p[1], (float) p[2]));
                e[0] = Math.min(e[0], v.x);
                e[1] = Math.max(e[1], v.x);
                e[2] = Math.min(e[2], v.y);
                e[3] = Math.max(e[3], v.y);
            }
        }
        return e;
    }

    /** Les sommets poses par Java, pour le dessin hors ligne. */
    private static final class Sommets {
        private final StringBuilder json = new StringBuilder("{\"sets\": {");
        private final StringBuilder boxes = new StringBuilder();
        private final StringBuilder points = new StringBuilder();
        private boolean first = true;

        void pose(String name, GunPose pose, @Nullable Matrix4f m) {
            JakGunModel model = pose.model;
            StringBuilder tri = new StringBuilder();
            StringBuilder pos = new StringBuilder();
            double[] p = new double[3];
            Vector3f v = new Vector3f();
            for (int t = 0; t < model.triangles; t++) {
                if (!pose.visible(t)) {
                    continue;
                }
                tri.append(tri.isEmpty() ? "" : ",").append(t);
                for (int k = 0; k < 3; k++) {
                    pose.vertex(t * 3 + k, p);
                    double x = p[0];
                    double y = p[1];
                    double z = p[2];
                    if (m != null) {
                        m.transformPosition(v.set((float) x, (float) y, (float) z));
                        x = v.x;
                        y = v.y;
                        z = v.z;
                    }
                    pos.append(pos.isEmpty() ? "" : ",").append(String.format(Locale.ROOT, "%.5f,%.5f,%.5f", x, y, z));
                }
            }
            this.json.append(this.first ? "" : ",").append("\n\"").append(name).append("\": {\"tri\": [")
                    .append(tri).append("], \"p\": [").append(pos).append("]}");
            this.first = false;
        }

        void boxes(String name, List<float[]> list) {
            this.boxes.append(this.boxes.isEmpty() ? "" : ",").append("\n\"").append(name).append("\": [");
            for (int i = 0; i < list.size(); i++) {
                this.boxes.append(i == 0 ? "[" : ",[");
                float[] c = list.get(i);
                for (int k = 0; k < c.length; k++) {
                    this.boxes.append(k == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.5f", c[k]));
                }
                this.boxes.append(']');
            }
            this.boxes.append(']');
        }

        void points(String name, Vector3f... list) {
            this.points.append(this.points.isEmpty() ? "" : ",").append("\n\"").append(name).append("\": [");
            for (int i = 0; i < list.length; i++) {
                this.points.append(String.format(Locale.ROOT, "%s[%.5f,%.5f,%.5f]", i == 0 ? "" : ",",
                        list[i].x, list[i].y, list[i].z));
            }
            this.points.append(']');
        }

        void write(Path file) {
            String text = this.json + "},\n\"boxes\": {" + this.boxes + "},\n\"points\": {" + this.points + "}}\n";
            try {
                Files.writeString(file, text, StandardCharsets.UTF_8);
                line("sommets poses par Java ecrits dans " + file.toAbsolutePath().normalize() + " ("
                        + text.length() / 1024 + " ko)");
            } catch (IOException e) {
                check("sommets poses ecrits", false, e.toString());
            }
        }
    }

    // ================================================================ 2. confinement

    private static void lobbyState() {
        HavenState state = HavenState.get(server);
        ServerLevel haven = Haven.level(server);
        boolean open = HavenArrival.lobbyOpen(server);
        check("--- confinement : ville posee et lobby ouvert au depart de l'essai",
                haven != null && state.built() && open,
                "dimension " + (haven != null) + ", posee " + state.built() + ", phase " + state.phase()
                        + ", lobby " + (open ? "ouvert" : "ferme") + ", numero " + MorphGunKeeper.lobby(server));
    }

    /** Les essais sur cobayes, un par etape du deroulement. */
    private static final class Confinement {
        private FakePlayer a;
        private FakePlayer full;
        private FakePlayer cancelled;
        private MorphGunData cancelledBefore;
        private FakePlayer outsider;

        private boolean ready() {
            return Haven.level(server) != null && HavenArrival.lobbyOpen(server);
        }

        private static UUID uuid(String name) {
            return UUID.nameUUIDFromBytes(("autotest-armes:" + name).getBytes(StandardCharsets.UTF_8));
        }

        /** Un cobaye neuf dans la ville (ou dans un autre niveau), inscrit au gardien, devant le bar. */
        private FakePlayer subject(String name, @Nullable ServerLevel where) {
            ServerLevel level = where != null ? where : Haven.level(server);
            FakePlayer fake = new FakePlayer(level, new GameProfile(uuid(name), "[Armes]"));
            BlockPos feet = Haven.is(level) ? HavenState.get(server).origin().offset(Haven.BAR_FRONT_CELL)
                    : level.getSharedSpawnPos();
            level.getChunkAt(feet);
            fake.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
            MorphGunKeeper.addSubject(fake);
            if (!SUBJECTS.contains(fake.getUUID())) {
                SUBJECTS.add(fake.getUUID());
            }
            return fake;
        }

        /** L'arrivee dans la ville, par l'evenement que publie changeDimension. */
        private static void arrive(FakePlayer fake) {
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(fake, Level.OVERWORLD, Haven.LEVEL));
        }

        private static int inInventory(Player player) {
            int n = 0;
            Inventory inventory = player.getInventory();
            for (NonNullList<ItemStack> list : List.of(inventory.items, inventory.armor, inventory.offhand)) {
                for (ItemStack stack : list) {
                    if (MorphGunKeeper.isGun(stack)) {
                        n++;
                    }
                }
            }
            return n;
        }

        private static int gunEntities(Player player) {
            return player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(24.0),
                    e -> MorphGunKeeper.isGun(e.getItem())).size();
        }

        private static int slotOf(Player player, ItemStack stack) {
            NonNullList<ItemStack> items = player.getInventory().items;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) == stack) {
                    return i;
                }
            }
            return -1;
        }

        private static String describe(@Nullable ItemStack gun) {
            MorphGunData d = gun == null ? null : MorphGunData.of(gun);
            return d == null ? "aucune" : d.form().id + ", formes " + Integer.toBinaryString(d.owned()) + ", eco "
                    + d.ecoRed() + "/" + d.ecoYellow() + "/" + d.ecoBlue() + "/" + d.ecoDark() + ", lobby " + d.lobby();
        }

        private static ItemStack freshGun(long lobby) {
            ItemStack gun = new ItemStack(ModItems.MORPH_GUN.get());
            MorphGunData.write(gun, MorphGunData.fresh(lobby, GunForm.BASE_MASK));
            return gun;
        }

        private static ChestMenu openEnder(FakePlayer fake, int id) {
            ChestMenu menu = ChestMenu.threeRows(id, fake.getInventory(), fake.getEnderChestInventory());
            fake.containerMenu = menu;
            NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Open(fake, menu));
            return menu;
        }

        // ------------------------------------------------------------- arrivee

        void arrival() {
            if (!ready()) {
                check("arrivee : lobby ouvert", false, "essais de confinement sautes");
                STEPS.removeIf(s -> !s.name().equals("nettoyage"));
                return;
            }
            this.a = subject("arrivee", null);
            arrive(this.a);
            ItemStack gun = MorphGunKeeper.find(this.a);
            MorphGunData data = gun == null ? null : MorphGunData.of(gun);
            long lobby = MorphGunKeeper.lobby(server);
            check("arrivee (PlayerChangedDimensionEvent vers Haven) : exactement 1 arme, 4 formes de base, reserves"
                            + " pleines 100/200/200/15, lobby courant, dans la premiere case de la barre",
                    MorphGunKeeper.count(this.a) == 1 && data != null && data.owned() == GunForm.BASE_MASK
                            && data.full() && data.ecoRed() == 100 && data.ecoYellow() == 200 && data.ecoBlue() == 200
                            && data.ecoDark() == 15 && data.lobby() == lobby && slotOf(this.a, gun) == 0
                            && MorphGunKeeper.countStored(this.a) == 0,
                    "armes " + MorphGunKeeper.count(this.a) + ", " + describe(gun) + ", case " + slotOf(this.a, gun)
                            + ", lobby courant " + lobby);
            this.a.getInventory().selected = 0;
            MorphGunKeeper.ensure(this.a);
            MorphGunKeeper.login(this.a);
            arrive(this.a);
            check("arrivee rappelee (ensure, connexion, arrivee) : toujours la meme arme, seule",
                    MorphGunKeeper.count(this.a) == 1 && MorphGunKeeper.find(this.a) == gun,
                    "armes " + MorphGunKeeper.count(this.a) + ", meme pile " + (MorphGunKeeper.find(this.a) == gun));
        }

        void guardHeld() {
            ItemStack gun = this.a.getInventory().getSelected();
            check("100 tiques de gardien avec l'arme dans la case choisie : exactement 1 (pas de double comptage),"
                            + " la meme pile",
                    MorphGunKeeper.count(this.a) == 1 && MorphGunKeeper.isGun(gun) && MorphGunKeeper.find(this.a) == gun,
                    "armes " + MorphGunKeeper.count(this.a) + ", en main " + describe(gun));
        }

        // ------------------------------------------------------------- reconnexion

        void reconnect() {
            ItemStack gun = MorphGunKeeper.find(this.a);
            MorphGunData.write(gun, MorphGunData.of(gun).withEco(GunForm.Family.YELLOW, 123));
            MorphGunKeeper.logout(this.a);
            MorphGunKeeper.login(this.a);
            ItemStack after = MorphGunKeeper.find(this.a);
            check("deconnexion puis reconnexion dans la ville : 1 arme, reserve jaune 123 gardee",
                    MorphGunKeeper.count(this.a) == 1 && after != null && MorphGunData.of(after).ecoYellow() == 123,
                    "armes " + MorphGunKeeper.count(this.a) + ", " + describe(after));

            int slot = slotOf(this.a, after);
            this.a.getInventory().items.set(slot, ItemStack.EMPTY);
            this.a.inventoryMenu.setCarried(after);
            MorphGunKeeper.logout(this.a);
            check("deconnexion avec l'arme sous le curseur : remise dans l'inventaire avant la sauvegarde, curseur vide",
                    this.a.inventoryMenu.getCarried().isEmpty() && inInventory(this.a) == 1
                            && MorphGunKeeper.count(this.a) == 1,
                    "curseur " + this.a.inventoryMenu.getCarried() + ", dans l'inventaire " + inInventory(this.a));

            openEnder(this.a, 90);
            boolean stashed = MorphGunKeeper.held(this.a) != null && inInventory(this.a) == 0;
            MorphGunKeeper.logout(this.a);
            check("deconnexion avec l'arme rangee (menu ouvert) : remise dans l'inventaire",
                    stashed && inInventory(this.a) == 1 && MorphGunKeeper.held(this.a) == null
                            && MorphGunKeeper.count(this.a) == 1,
                    "rangee avant " + stashed + ", dans l'inventaire " + inInventory(this.a) + ", reserve "
                            + MorphGunKeeper.held(this.a));
            this.a.closeContainer();
            this.a.getInventory().selected = Math.max(0, slotOf(this.a, MorphGunKeeper.find(this.a)));
        }

        // ------------------------------------------------------------- inventaire plein

        void fullInventory() {
            this.full = subject("plein", null);
            Inventory inventory = this.full.getInventory();
            for (int i = 0; i < inventory.items.size(); i++) {
                inventory.items.set(i, new ItemStack(Items.STONE, 64));
            }
            inventory.offhand.set(0, new ItemStack(Items.STONE, 64));
            arrive(this.full);
            boolean untouched = true;
            for (ItemStack stack : inventory.items) {
                untouched &= stack.is(Items.STONE) && stack.getCount() == 64;
            }
            check("inventaire plein (36 piles et main gauche) : aucun objet deplace, aucune entite d'objet,"
                            + " l'arme rangee cote serveur",
                    untouched && inventory.offhand.get(0).is(Items.STONE) && inInventory(this.full) == 0
                            && gunEntities(this.full) == 0 && MorphGunKeeper.isGun(MorphGunKeeper.held(this.full))
                            && MorphGunKeeper.count(this.full) == 1,
                    "objets intacts " + untouched + ", dans l'inventaire " + inInventory(this.full) + ", au sol "
                            + gunEntities(this.full) + ", rangee " + describe(MorphGunKeeper.held(this.full)));
            FakePlayer offhand = subject("plein-main-gauche", null);
            for (int i = 0; i < offhand.getInventory().items.size(); i++) {
                offhand.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
            }
            arrive(offhand);
            check("36 cases pleines, main gauche vide : l'arme va dans la main gauche",
                    MorphGunKeeper.isGun(offhand.getInventory().offhand.get(0)) && MorphGunKeeper.count(offhand) == 1,
                    "main gauche " + describe(offhand.getInventory().offhand.get(0)));
            MorphGunKeeper.removeSubject(offhand.getUUID());
            inventory.items.set(5, ItemStack.EMPTY);
        }

        void fullInventoryFreed() {
            check("une case liberee : l'arme y est posee en 20 tiques ou moins, plus rien en reserve",
                    MorphGunKeeper.isGun(this.full.getInventory().items.get(5)) && MorphGunKeeper.held(this.full) == null
                            && MorphGunKeeper.count(this.full) == 1,
                    "case 5 " + describe(this.full.getInventory().items.get(5)) + ", reserve "
                            + MorphGunKeeper.held(this.full));
            MorphGunKeeper.removeSubject(this.full.getUUID());
        }

        // ------------------------------------------------------------- jets

        void tosses() {
            ItemStack gun = MorphGunKeeper.find(this.a);
            MorphGunData before = MorphGunData.of(gun);
            this.a.getInventory().selected = Math.max(0, slotOf(this.a, gun));
            boolean dropped = this.a.drop(false);
            check("touche Q (ServerPlayer.drop) : refusee, l'arme reste en main",
                    !dropped && this.a.getInventory().getSelected() == gun && gunEntities(this.a) == 0,
                    "drop " + dropped + ", en main " + (this.a.getInventory().getSelected() == gun));

            int slot = slotOf(this.a, gun);
            this.a.getInventory().items.set(slot, ItemStack.EMPTY);
            this.a.inventoryMenu.setCarried(gun);
            this.a.inventoryMenu.clicked(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, 0, ClickType.PICKUP, this.a);
            ItemStack back = MorphGunKeeper.find(this.a);
            check("clic hors de la fenetre avec l'arme au curseur : 0 entite d'objet, 1 arme, reserves identiques",
                    gunEntities(this.a) == 0 && MorphGunKeeper.count(this.a) == 1 && back != null
                            && before.equals(MorphGunData.of(back)) && this.a.inventoryMenu.getCarried().isEmpty(),
                    "au sol " + gunEntities(this.a) + ", armes " + MorphGunKeeper.count(this.a) + ", " + describe(back));

            int index = slotOf(this.a, back);
            int menuSlot = index < 9 ? 36 + index : index;
            this.a.inventoryMenu.clicked(menuSlot, 0, ClickType.THROW, this.a);
            ItemStack thrown = MorphGunKeeper.find(this.a);
            check("jet d'une case (THROW) : 0 entite d'objet, 1 arme, reserves identiques",
                    gunEntities(this.a) == 0 && MorphGunKeeper.count(this.a) == 1 && thrown != null
                            && before.equals(MorphGunData.of(thrown)),
                    "case de menu " + menuSlot + ", au sol " + gunEntities(this.a) + ", armes "
                            + MorphGunKeeper.count(this.a) + ", " + describe(thrown));
        }

        // ------------------------------------------------------------- coffre de l'Ender

        void enderChest() {
            this.a.getEnderChestInventory().clearContent();
            this.a.getInventory().items.set(20, new ItemStack(Items.STONE, 16));
            ChestMenu menu = openEnder(this.a, 91);
            boolean hidden = inInventory(this.a) == 0 && MorphGunKeeper.isGun(MorphGunKeeper.held(this.a))
                    && MorphGunKeeper.inMenu(this.a);
            for (int i = 27; i < menu.slots.size(); i++) {
                menu.quickMoveStack(this.a, i);
            }
            int enderGuns = 0;
            int enderStone = 0;
            for (int i = 0; i < this.a.getEnderChestInventory().getContainerSize(); i++) {
                ItemStack s = this.a.getEnderChestInventory().getItem(i);
                enderGuns += MorphGunKeeper.isGun(s) ? 1 : 0;
                enderStone += s.is(Items.STONE) ? s.getCount() : 0;
            }
            check("coffre de l'Ender ouvert : l'arme n'est plus dans l'inventaire pendant le menu ; quickMoveStack de chaque"
                            + " case n'y depose rien (la pierre, elle, passe)",
                    hidden && enderGuns == 0 && enderStone == 16,
                    "rangee " + hidden + ", armes dans le coffre " + enderGuns + ", pierre deplacee " + enderStone);
            this.a.closeContainer();
            check("fermeture du coffre : 1 arme dans l'inventaire, coffre sans arme",
                    MorphGunKeeper.count(this.a) == 1 && inInventory(this.a) == 1 && !MorphGunKeeper.inMenu(this.a)
                            && MorphGunKeeper.countStored(this.a) == 0,
                    "armes " + MorphGunKeeper.count(this.a) + ", dans l'inventaire " + inInventory(this.a)
                            + ", rangees " + MorphGunKeeper.countStored(this.a));

            ItemStack gun = MorphGunKeeper.find(this.a);
            MorphGunData before = MorphGunData.of(gun);
            this.a.getInventory().items.set(slotOf(this.a, gun), ItemStack.EMPTY);
            this.a.getEnderChestInventory().setItem(4, gun);
            MorphGunKeeper.ensure(this.a);
            ItemStack out = MorphGunKeeper.find(this.a);
            check("arme deposee dans le coffre de l'Ender par un autre chemin : le gardien l'en ressort, meme etat",
                    MorphGunKeeper.countStored(this.a) == 0 && MorphGunKeeper.count(this.a) == 1 && out != null
                            && before.equals(MorphGunData.of(out)),
                    "dans le coffre " + MorphGunKeeper.countStored(this.a) + ", armes " + MorphGunKeeper.count(this.a));
            this.a.getEnderChestInventory().clearContent();
            this.a.getInventory().items.set(20, ItemStack.EMPTY);

            AbstractContainerMenu slotless = new AbstractContainerMenu(null, 92) {
                @Override
                public ItemStack quickMoveStack(Player player, int index) {
                    return ItemStack.EMPTY;
                }

                @Override
                public boolean stillValid(Player player) {
                    return true;
                }
            };
            check("menus : un menu du mod sans case (la borne) ne range pas l'arme, un coffre si",
                    MorphGunKeeper.exempt(slotless) && !MorphGunKeeper.exempt(menu), "sans case "
                            + MorphGunKeeper.exempt(slotless) + ", coffre " + MorphGunKeeper.exempt(menu));
        }

        // ------------------------------------------------------------- mort

        /** Ce que dropAll a lache a la derniere mort, AVANT les autres abonnes de LivingDropsEvent. */
        private static List<ItemStack> lastDrops = List.of();

        /**
         * La mort d'un cobaye, comme ServerPlayer.die la deroule (voir l'en-tete) :
         * l'evenement, puis, s'il n'est pas annule, les objets laches.
         *
         * @return vrai si la mort a eu lieu
         */
        private static boolean kill(FakePlayer fake) {
            DamageSource source = fake.damageSources().genericKill();
            if (CommonHooks.onLivingDeath(fake, source)) {
                return false;
            }
            fake.captureDrops(new ArrayList<>());
            if (!fake.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                fake.getInventory().dropAll();
            }
            Collection<ItemEntity> drops = fake.captureDrops(null);
            lastDrops = drops == null ? List.of() : drops.stream().map(entity -> entity.getItem().copy()).toList();
            if (drops != null && !CommonHooks.onLivingDrops(fake, source, drops, false)) {
                drops.forEach(entity -> fake.level().addFreshEntity(entity));
            }
            fake.setHealth(0.0F);
            return true;
        }

        /**
         * La reapparition dans l'ordre de PlayerList.respawn : restoreFrom, puis
         * PlayerEvent.Clone (ou HavenKeep, du chantier de l'invasion, rend les
         * poches gardees a la mort), puis PlayerRespawnEvent (le gardien).
         */
        private static void revive(FakePlayer dead, FakePlayer born, boolean keep) {
            born.restoreFrom(dead, keep);
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(born, dead, true));
            born.moveTo(dead.getX(), dead.getY(), dead.getZ(), 0.0F, 0.0F);
            MorphGunKeeper.addSubject(born);
            MorphGunKeeper.respawn(born);
        }

        void deathDrop() {
            GameRules.BooleanValue keep = server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
            boolean keepBefore = keep.get();
            keep.set(false, server);
            try {
                FakePlayer d = subject("mort", null);
                arrive(d);
                ItemStack gun = MorphGunKeeper.find(d);
                MorphGunData.write(gun, MorphGunData.of(gun).withOwned(GunForm.BASE_MASK | GunForm.RED_2.bit())
                        .withEco(GunForm.Family.RED, 7));
                d.getInventory().items.set(30, new ItemStack(Items.STONE, 3));
                boolean died = kill(d);
                int stones = d.level().getEntitiesOfClass(ItemEntity.class, d.getBoundingBox().inflate(24.0),
                        e -> e.getItem().is(Items.STONE)).size();
                MorphGunData last = MorphGunKeeper.lastKnown(d.getUUID());
                check("mort (keepInventory faux) : arme retiree avant la chute : rien de lache n'est l'arme (la pierre est gardee par HavenKeep ou lachee),"
                                + " dernier etat retenu",
                        died && MorphGunKeeper.count(d) == 0 && gunEntities(d) == 0 && lastDrops.stream().noneMatch(MorphGunKeeper::isGun) && last != null
                                && last.ecoRed() == 7,
                        "mort " + died + ", armes " + MorphGunKeeper.count(d) + ", au sol " + gunEntities(d) + ", laches par dropAll " + lastDrops.size() + ", pierres au sol apres tous les abonnes " + stones
                                + ", retenu " + (last == null ? "rien" : last.ecoRed() + " eco rouge"));
                FakePlayer born = new FakePlayer(Haven.level(server), d.getGameProfile());
                revive(d, born, false);
                ItemStack again = MorphGunKeeper.find(born);
                MorphGunData data = again == null ? null : MorphGunData.of(again);
                check("reapparition : 1 arme, formes gardees (rouge 2 compris), reserves pleines",
                        MorphGunKeeper.count(born) == 1 && data != null && data.owns(GunForm.RED_2) && data.full(),
                        "armes " + MorphGunKeeper.count(born) + ", " + describe(again));
                discardAround(d, Items.STONE);
            } finally {
                keep.set(keepBefore, server);
            }
        }

        void deathKeep() {
            GameRules.BooleanValue keep = server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
            boolean keepBefore = keep.get();
            keep.set(true, server);
            try {
                FakePlayer e = subject("garde", null);
                arrive(e);
                boolean died = kill(e);
                check("mort (keepInventory vrai) : arme retiree, 0 entite d'objet",
                        died && MorphGunKeeper.count(e) == 0 && gunEntities(e) == 0,
                        "mort " + died + ", armes " + MorphGunKeeper.count(e) + ", au sol " + gunEntities(e));
                FakePlayer born = new FakePlayer(Haven.level(server), e.getGameProfile());
                // comme PlayerList.respawn apres une mort : restoreFrom(ancien, false), et c'est la regle
                // keepInventory qui recopie l'inventaire (restoreFrom(.., true) est le retour de l'End : la sante aussi)
                revive(e, born, false);
                check("reapparition apres restoreFrom (inventaire recopie) : exactement 1 arme, pas de doublon",
                        MorphGunKeeper.count(born) == 1 && MorphGunData.of(MorphGunKeeper.find(born)).full(),
                        "armes " + MorphGunKeeper.count(born));
            } finally {
                keep.set(keepBefore, server);
            }
        }

        void deathCancelled() {
            this.cancelled = subject("annulee", null);
            arrive(this.cancelled);
            ItemStack gun = MorphGunKeeper.find(this.cancelled);
            MorphGunData.write(gun, MorphGunData.of(gun).withEco(GunForm.Family.DARK, 3));
            this.cancelledBefore = MorphGunData.of(gun);
            CANCEL_DEATH.add(this.cancelled.getUUID());
            boolean died;
            try {
                died = kill(this.cancelled);
            } finally {
                CANCEL_DEATH.remove(this.cancelled.getUUID());
            }
            check("mort annulee par un autre abonne (LOW) : le retrait (HIGHEST) a eu lieu, le joueur vit",
                    !died && MorphGunKeeper.count(this.cancelled) == 0 && this.cancelled.isAlive()
                            && gunEntities(this.cancelled) == 0,
                    "mort annulee " + !died + ", armes " + MorphGunKeeper.count(this.cancelled) + ", vivant " + this.cancelled.isAlive());

            List<ItemEntity> drops = new ArrayList<>();
            ItemEntity gunDrop = new ItemEntity(this.cancelled.level(), this.cancelled.getX(), this.cancelled.getY(),
                    this.cancelled.getZ(), freshGun(MorphGunKeeper.lobby(server)));
            ItemEntity stoneDrop = new ItemEntity(this.cancelled.level(), this.cancelled.getX(), this.cancelled.getY(),
                    this.cancelled.getZ(), new ItemStack(Items.STONE));
            drops.add(gunDrop);
            drops.add(stoneDrop);
            NeoForge.EVENT_BUS.post(new LivingDropsEvent(this.cancelled, this.cancelled.damageSources().genericKill(),
                    drops, false));
            check("LivingDropsEvent (HIGHEST) : l'entite d'objet arme est retiree des drops, la pierre reste",
                    !drops.contains(gunDrop) && drops.contains(stoneDrop), "drops " + drops.size());
        }

        void deathCancelledGuard() {
            ItemStack gun = MorphGunKeeper.find(this.cancelled);
            check("mort annulee : le gardien rend l'arme en 20 tiques ou moins, avec les memes reserves",
                    MorphGunKeeper.count(this.cancelled) == 1 && gun != null
                            && this.cancelledBefore.equals(MorphGunData.of(gun)),
                    "armes " + MorphGunKeeper.count(this.cancelled) + ", " + describe(gun) + " (avant : "
                            + this.cancelledBefore.ecoDark() + " eco sombre)");
        }

        private static void discardAround(Player player, net.minecraft.world.item.Item item) {
            for (ItemEntity e : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(24.0),
                    e -> e.getItem().is(item))) {
                e.discard();
            }
        }

        // ------------------------------------------------------------- departs

        void departures() {
            FakePlayer g = subject("depart", null);
            arrive(g);
            ItemStack gun = MorphGunKeeper.find(g);
            g.getInventory().items.set(slotOf(g, gun), ItemStack.EMPTY);
            g.inventoryMenu.setCarried(gun);
            NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(g, Level.OVERWORLD));
            check("depart (EntityTravelToDimensionEvent, publie par changeDimension avant la teleportation : vote,"
                            + " skip, closeIfStarted, retardataire, /haven back, autres mods), arme au curseur : 0 arme"
                            + " dans l'inventaire, les curseurs, la main gauche, l'armure, le coffre de l'Ender",
                    MorphGunKeeper.count(g) == 0 && MorphGunKeeper.countStored(g) == 0 && g.inventoryMenu.getCarried().isEmpty()
                            && MorphGunKeeper.lastKnown(g.getUUID()) == null,
                    "armes " + MorphGunKeeper.count(g) + ", rangees " + MorphGunKeeper.countStored(g) + ", etat oublie "
                            + (MorphGunKeeper.lastKnown(g.getUUID()) == null));

            MorphGunKeeper.ensure(g);
            openEnder(g, 93);
            boolean stashed = MorphGunKeeper.isGun(MorphGunKeeper.held(g));
            NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(g, Level.OVERWORLD));
            boolean closed = g.containerMenu == g.inventoryMenu;
            g.closeContainer();
            check("depart menu ouvert (arme rangee) : 0 arme, menu ferme, et toujours 0 apres la fermeture",
                    stashed && closed && MorphGunKeeper.count(g) == 0 && MorphGunKeeper.countStored(g) == 0,
                    "rangee avant " + stashed + ", menu ferme " + closed + ", armes " + MorphGunKeeper.count(g));

            MorphGunKeeper.ensure(g);
            ItemStack stored = MorphGunKeeper.find(g);
            g.getInventory().items.set(slotOf(g, stored), ItemStack.EMPTY);
            g.getEnderChestInventory().setItem(0, stored);
            NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(g, Level.OVERWORLD));
            check("depart avec l'arme dans le coffre de l'Ender : vide aussi",
                    MorphGunKeeper.count(g) == 0 && MorphGunKeeper.countStored(g) == 0,
                    "armes " + MorphGunKeeper.count(g) + ", rangees " + MorphGunKeeper.countStored(g));

            // grille d'artisanat 2x2 : ecran d'inventaire ouvert, l'arme posee dans une case de la grille
            MorphGunKeeper.ensure(g);
            ItemStack crafted = MorphGunKeeper.find(g);
            g.getInventory().items.set(slotOf(g, crafted), ItemStack.EMPTY);
            g.inventoryMenu.getCraftSlots().setItem(0, crafted);
            int seenInGrid = MorphGunKeeper.count(g);
            MorphGunKeeper.ensure(g);
            boolean keptInGrid = g.inventoryMenu.getCraftSlots().getItem(0) == crafted && MorphGunKeeper.count(g) == 1
                    && inInventory(g) == 0;
            NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(g, Level.OVERWORLD));
            boolean gridEmpty = g.inventoryMenu.getCraftSlots().isEmpty();
            // fermeture de l'ecran d'inventaire : InventoryMenu.removed rend a l'inventaire ce que tient la grille
            g.inventoryMenu.removed(g);
            check("depart avec l'arme dans la grille d'artisanat 2x2 (ecran d'inventaire ouvert) : comptee, pas de seconde"
                            + " arme donnee par le gardien, retiree au depart, rien ne revient a la fermeture de l'ecran",
                    seenInGrid == 1 && keptInGrid && gridEmpty && MorphGunKeeper.count(g) == 0 && inInventory(g) == 0
                            && MorphGunKeeper.countStored(g) == 0,
                    "comptees dans la grille " + seenInGrid + ", gardien sans doublon " + keptInGrid + ", grille vide au depart "
                            + gridEmpty + ", armes apres fermeture " + MorphGunKeeper.count(g) + " (inventaire " + inInventory(g) + ")");

            MorphGunKeeper.ensure(g);
            boolean had = MorphGunKeeper.count(g) == 1;
            EntityTravelToDimensionEvent refused = new EntityTravelToDimensionEvent(g, Level.OVERWORLD);
            refused.setCanceled(true);
            NeoForge.EVENT_BUS.post(refused);
            check("voyage annule par un autre mod avant nous : l'arme reste (LOWEST, evenements annules ignores)",
                    had && MorphGunKeeper.count(g) == 1, "armes " + MorphGunKeeper.count(g));

            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(g, Haven.LEVEL, Level.OVERWORLD));
            check("ceinture : PlayerChangedDimensionEvent hors de Haven retire aussi", MorphGunKeeper.count(g) == 0,
                    "armes " + MorphGunKeeper.count(g));
            MorphGunKeeper.removeSubject(g.getUUID());
            HavenState.get(server).forgetMode(g.getUUID());
        }

        // ------------------------------------------------------------- hors de la ville

        void outside() {
            ServerLevel overworld = server.overworld();
            long lobby = MorphGunKeeper.lobby(server);
            FakePlayer h = new FakePlayer(overworld, new GameProfile(uuid("hors-reapparition"), "[Armes]"));
            h.getInventory().items.set(0, freshGun(lobby));
            MorphGunKeeper.respawn(h);
            FakePlayer l = new FakePlayer(overworld, new GameProfile(uuid("hors-connexion"), "[Armes]"));
            l.getInventory().items.set(3, freshGun(lobby));
            l.getEnderChestInventory().setItem(2, freshGun(lobby));
            MorphGunKeeper.login(l);
            check("reapparition et connexion hors de la ville : 0 arme (inventaire et coffre de l'Ender)",
                    MorphGunKeeper.count(h) == 0 && MorphGunKeeper.count(l) == 0 && MorphGunKeeper.countStored(l) == 0,
                    "reapparition " + MorphGunKeeper.count(h) + ", connexion " + MorphGunKeeper.count(l) + " + "
                            + MorphGunKeeper.countStored(l));

            FakePlayer tick = new FakePlayer(overworld, new GameProfile(uuid("hors-tique"), "[Armes]"));
            tick.getInventory().items.set(1, freshGun(lobby));
            tick.getInventory().tick();
            check("pile placee de force hors de la ville : supprimee par inventoryTick cote serveur, a la tique",
                    MorphGunKeeper.count(tick) == 0, "armes " + MorphGunKeeper.count(tick));

            ServerLevel haven = Haven.level(server);
            BlockPos at = HavenState.get(server).origin().offset(Haven.BAR_FRONT_CELL);
            ItemEntity ground = new ItemEntity(haven, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    freshGun(lobby));
            haven.addFreshEntity(ground);
            ground.tick();
            check("arme au sol dans la ville : l'entite disparait a sa premiere tique (onEntityItemUpdate)",
                    ground.isRemoved(), "retiree " + ground.isRemoved());

            this.outsider = subject("hors-gardien", overworld);
            this.outsider.getInventory().items.set(7, freshGun(lobby));
            this.outsider.inventoryMenu.setCarried(freshGun(lobby));
        }

        void outsideGuard() {
            check("gardien : pile hors de la ville (inventaire et curseur) retiree en 20 tiques ou moins",
                    MorphGunKeeper.count(this.outsider) == 0, "armes " + MorphGunKeeper.count(this.outsider));
            MorphGunKeeper.removeSubject(this.outsider.getUUID());
        }

        // ------------------------------------------------------------- lobby ferme, ancien lobby

        void closedLobby() {
            HavenState state = HavenState.get(server);
            HavenState.Phase phase = state.phase();
            FakePlayer j = subject("ferme", null);
            arrive(j);
            int before = MorphGunKeeper.count(j);
            int[] after = new int[2];
            HavenState.Phase[] closed = {HavenState.Phase.PARTI, HavenState.Phase.ABSENTE};
            for (int i = 0; i < closed.length; i++) {
                state.setPhase(closed[i]);
                try {
                    MorphGunKeeper.guard(j);
                    after[i] = MorphGunKeeper.count(j) + MorphGunKeeper.countStored(j);
                } finally {
                    state.setPhase(phase);
                }
                MorphGunKeeper.ensure(j);
            }
            check("joueur reste dans la ville, lobby ferme (phase PARTI puis ABSENTE) : le gardien retire l'arme",
                    before == 1 && after[0] == 0 && after[1] == 0,
                    "avant " + before + ", PARTI " + after[0] + ", ABSENTE " + after[1] + " (phase rendue : " + state.phase() + ")");

            FakePlayer k = subject("ancien", null);
            long lobby = MorphGunKeeper.lobby(server);
            ItemStack old = new ItemStack(ModItems.MORPH_GUN.get());
            MorphGunData.write(old, MorphGunData.fresh(lobby - 1, GunForm.BASE_MASK).withEco(GunForm.Family.BLUE, 2));
            ItemStack bare = new ItemStack(ModItems.MORPH_GUN.get());
            k.getInventory().items.set(2, old);
            k.getInventory().items.set(9, bare);
            MorphGunKeeper.ensure(k);
            ItemStack now = MorphGunKeeper.find(k);
            MorphGunData data = now == null ? null : MorphGunData.of(now);
            check("pile d'un ancien lobby et pile sans composant : remplacees par une arme neuve du lobby courant",
                    MorphGunKeeper.count(k) == 1 && now != old && now != bare && data != null && data.lobby() == lobby
                            && data.full(),
                    "armes " + MorphGunKeeper.count(k) + ", " + describe(now));

            ItemStack first = freshGun(lobby);
            ItemStack second = freshGun(lobby);
            k.getInventory().items.set(4, first);
            k.getInventory().items.set(12, second);
            k.getInventory().selected = 4;
            MorphGunKeeper.ensure(k);
            check("plusieurs armes du lobby courant : il garde celle en main, les autres sont retirees",
                    MorphGunKeeper.count(k) == 1 && MorphGunKeeper.find(k) == first,
                    "armes " + MorphGunKeeper.count(k) + ", gardee en main " + (MorphGunKeeper.find(k) == first));
            MorphGunKeeper.removeSubject(k.getUUID());
        }

        // ------------------------------------------------------------- chantier

        void chantier() {
            FakePlayer op = subject("chantier", null);
            HavenRules.setChantier(op, true);
            try {
                arrive(op);
                MorphGunKeeper.ensure(op);
                MorphGunKeeper.guard(op);
                check("operateur en chantier dans la ville : rien n'est donne", MorphGunKeeper.count(op) == 0,
                        "armes " + MorphGunKeeper.count(op) + ", chantier " + HavenRules.chantier(op));
            } finally {
                HavenRules.setChantier(op, false);
                MorphGunKeeper.removeSubject(op.getUUID());
                HavenState.get(server).forgetMode(op.getUUID());
            }
        }

        // ------------------------------------------------------------- changement d'arme

        void selection() {
            FakePlayer m = subject("choix", null);
            arrive(m);
            ItemStack gun = MorphGunKeeper.find(m);
            m.getInventory().selected = slotOf(m, gun);
            long writes = MorphGunData.writes();
            MorphGunKeeper.Selection yellow = MorphGunKeeper.select(m, GunForm.Family.YELLOW);
            long afterYellow = MorphGunData.writes() - writes;
            MorphGunData d = MorphGunData.of(gun);
            check("fleche bas (jaune) : Blaster, forme precedente Scatter Gun, tique du changement, 1 ecriture de composant",
                    yellow == MorphGunKeeper.Selection.OK && d.form() == GunForm.YELLOW_1 && d.previous() == GunForm.RED_1
                            && d.changeTick() == m.level().getGameTime() && afterYellow == 1,
                    yellow + ", " + describe(gun) + ", precedente " + d.previous().id + ", ecritures " + afterYellow);
            check("changer la forme ne relance pas l'animation d'equipement (meme case, meme objet)",
                    !ModItems.MORPH_GUN.get().shouldCauseReequipAnimation(freshGun(0), gun, false)
                            && ModItems.MORPH_GUN.get().shouldCauseReequipAnimation(freshGun(0), gun, true),
                    "meme case " + ModItems.MORPH_GUN.get().shouldCauseReequipAnimation(freshGun(0), gun, false));

            writes = MorphGunData.writes();
            MorphGunKeeper.Selection again = MorphGunKeeper.select(m, GunForm.Family.YELLOW);
            check("nouvel appui sur jaune avec une seule arme jaune possedee : rien, 0 ecriture",
                    again == MorphGunKeeper.Selection.SAME && MorphGunData.writes() == writes, again.toString());

            MorphGunData.write(gun, MorphGunData.of(gun).withOwned(GunForm.BASE_MASK | GunForm.YELLOW_2.bit()));
            MorphGunKeeper.select(m, GunForm.Family.YELLOW);
            GunForm second = MorphGunData.of(gun).form();
            MorphGunKeeper.select(m, GunForm.Family.YELLOW);
            GunForm third = MorphGunData.of(gun).form();
            check("nouvel appui avec deux armes jaunes : 1 -> 2 -> 1", second == GunForm.YELLOW_2 && third == GunForm.YELLOW_1,
                    second.id + " puis " + third.id);

            MorphGunData.write(gun, MorphGunData.of(gun).withOwned(GunForm.RED_1.bit() | GunForm.YELLOW_1.bit()
                    | GunForm.BLUE_1.bit()));
            writes = MorphGunData.writes();
            MorphGunKeeper.Selection notOwned = MorphGunKeeper.select(m, GunForm.Family.DARK);
            check("forme non possedee (sombre) : refusee, 0 ecriture",
                    notOwned == MorphGunKeeper.Selection.NOT_OWNED && MorphGunData.writes() == writes
                            && MorphGunData.of(gun).form() == GunForm.YELLOW_1, notOwned.toString());
            MorphGunData.write(gun, MorphGunData.of(gun).withOwned(GunForm.BASE_MASK));

            MorphGunKeeper.onSelectRequest(m, new GunSelectPayload(2));
            GunForm viaPayload = MorphGunData.of(gun).form();
            MorphGunKeeper.onSelectRequest(m, new GunSelectPayload(9));
            check("paquet GunSelectPayload(2) : Vulcan Fury ; famille invalide (9) : ignoree",
                    viaPayload == GunForm.BLUE_1 && MorphGunData.of(gun).form() == GunForm.BLUE_1, viaPayload.id);

            int free = -1;
            for (int i = 0; i < 9; i++) {
                if (m.getInventory().items.get(i).isEmpty()) {
                    free = i;
                    break;
                }
            }
            m.getInventory().selected = free;
            MorphGunKeeper.Selection empty = MorphGunKeeper.select(m, GunForm.Family.RED);
            check("sans l'arme en main : refuse", empty == MorphGunKeeper.Selection.NO_GUN, empty + ", case " + free);

            FakePlayer out = new FakePlayer(server.overworld(), new GameProfile(uuid("choix-dehors"), "[Armes]"));
            out.getInventory().items.set(0, freshGun(MorphGunKeeper.lobby(server)));
            out.getInventory().selected = 0;
            MorphGunKeeper.Selection outside = MorphGunKeeper.select(out, GunForm.Family.YELLOW);
            check("hors de la ville : refuse", outside == MorphGunKeeper.Selection.NOT_IN_HAVEN, outside.toString());
            MorphGunKeeper.removeSubject(m.getUUID());
        }

        // ------------------------------------------------------------- nettoyage

        void cleanup() {
            HavenState state = HavenState.get(server);
            for (UUID id : SUBJECTS) {
                MorphGunKeeper.removeSubject(id);
                state.forgetMode(id);
                state.removeApartment(id);
            }
            line("cobayes retires du gardien, modes de jeu oublies ; phase " + state.phase() + ", lobby "
                    + (HavenArrival.lobbyOpen(server) ? "ouvert" : "ferme") + " numero " + MorphGunKeeper.lobby(server));
        }
    }

    // ================================================================ rapport

    private static void end() {
        finished = true;
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("armes_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest armes : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest armes : {} OK, {} KO, rapport dans {} ; arret du serveur",
                passed, failed, file.toAbsolutePath());
        server.halt(false);
    }

    static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest armes : {}", text);
    }

    static void check(String what, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        line((ok ? "OK  " : "KO  ") + what + " -- " + detail);
    }
}
