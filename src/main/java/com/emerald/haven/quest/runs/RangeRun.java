package com.emerald.haven.quest.runs;

import com.emerald.haven.HavenArrival;
import com.emerald.haven.quest.HavenHero;
import com.emerald.haven.quest.HavenNpcs;
import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.HavenQuests;
import com.emerald.haven.quest.HavenTargetEntity;
import com.emerald.haven.quest.QuestMarkers;
import com.emerald.haven.quest.QuestRun;
import com.emerald.init.Jak3Registry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;
import java.util.List;

/**
 * LES EPREUVES DE TIR de Tess (§71) : une minute pour abattre le plus de cibles possible, au
 * stand de tir. Les cibles sont celles du stand de Jak 3 (HavenTargetEntity) : les gardes KG
 * en carton, un point ; le KG dore, trois ; les CIVILS, qu'on ne tire pas, deux de moins.
 * Elles se dressent autour du tireur, dans son champ (un trait libre depuis ses yeux) et dans
 * l'eventail du stand -- la ou il regardait au signal --, trois a la fois, et s'en vont au bout
 * de quelques secondes :
 *
 *  - epreuve 1 : des cibles fixes, a six a dix-huit blocs, peu de civils ;
 *  - epreuve 2 : des cibles MOBILES, qui glissent de cote ;
 *  - epreuve 3 : le tireur d'elite -- des cibles lointaines, de dix-huit a trente blocs, plus
 *    breves.
 *
 * Le score fait la medaille : bronze, argent, or (HavenQuest.rewardFor). L'epreuve commence
 * quand l'equipe est pres de Tess (trois secondes de compte a rebours).
 */
public class RangeRun extends QuestRun {

    public static final int DURATION = 20 * 60;
    /** L'amplitude du glissement des cibles mobiles, de part et d'autre de leur place. */
    public static final double SLIDE = 2.0;
    private static final int COUNTDOWN = 60;
    private static final int AT_ONCE = 3;
    private static final int CIVILIAN_PENALTY = 2;
    private static final int BONUS_POINTS = 3;

    private final int kind;
    private final int[] thresholds;
    private long started = -1L;
    private long lastSpawn = Long.MIN_VALUE / 2;
    private int score;
    /** La direction du stand : le regard du tireur au signal, gardee toute l'epreuve. */
    private float fanYaw;

    private RangeRun(HavenQuest quest, ServerLevel level, int kind, int[] thresholds) {
        super(quest, level);
        this.kind = kind;
        this.thresholds = thresholds;
    }

    public static RangeRun fixed(HavenQuest quest, ServerLevel level) {
        return new RangeRun(quest, level, 0, new int[]{10, 18, 26});
    }

    public static RangeRun moving(HavenQuest quest, ServerLevel level) {
        return new RangeRun(quest, level, 1, new int[]{8, 14, 20});
    }

    public static RangeRun far(HavenQuest quest, ServerLevel level) {
        return new RangeRun(quest, level, 2, new int[]{6, 11, 16});
    }

    @Override
    public void begin() {
    }

    /** Une cible abattue : un point, trois pour le KG dore, deux de moins pour un civil. */
    public void hit(HavenTargetEntity target) {
        if (this.started < 0 || status() != Status.RUNNING) {
            return;
        }
        MutableComponent line;
        if (target.civilian()) {
            this.score = Math.max(0, this.score - CIVILIAN_PENALTY);
            line = Component.translatable("game.emeraldweapons.haven.quete.tir.civil", CIVILIAN_PENALTY, this.score)
                    .withStyle(ChatFormatting.RED);
        } else {
            int points = target.bonus() ? BONUS_POINTS : 1;
            this.score += points;
            line = Component.translatable(target.bonus() ? "game.emeraldweapons.haven.quete.tir.bonus"
                    : "game.emeraldweapons.haven.quete.tir.touche", this.score, points).withStyle(ChatFormatting.GOLD);
        }
        int medal = medalFor(this.score);
        if (medal > 0) {
            line.append(" ").append(HavenQuests.medalName(medal));
        }
        for (ServerPlayer player : members()) {
            player.displayClientMessage(line, true);
            if (target.civilian()) {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
            } else {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.7F,
                        1.0F + Math.min(1.0F, this.score * 0.03F));
            }
        }
    }

    private int medalFor(int score) {
        int medal = 0;
        for (int i = 0; i < this.thresholds.length; i++) {
            if (score >= this.thresholds[i]) {
                medal = i + 1;
            }
        }
        return medal;
    }

    @Override
    public void tick(long now) {
        List<ServerPlayer> team = members();
        Vec3 tess = HavenNpcs.spot(HavenHero.TESS);
        if (this.started < 0) {
            ServerPlayer near = null;
            for (ServerPlayer player : team) {
                if (near == null && tess != null && player.position().distanceTo(tess) < 16.0) {
                    near = player;
                }
            }
            if (near != null) {
                this.started = now + COUNTDOWN;
                this.fanYaw = near.getYRot();
                for (ServerPlayer player : team) {
                    player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.quete.tir.pret",
                            this.thresholds[0], this.thresholds[1], this.thresholds[2]).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            } else if (now % 20 == 0 && tess != null) {
                QuestMarkers.show(team, List.of(QuestMarkers.beacon(tess, QuestMarkers.PURPLE)));
            }
            return;
        }
        if (now < this.started) {
            if ((this.started - now) % 20 == 0) {
                for (ServerPlayer player : team) {
                    player.displayClientMessage(Component.literal(String.valueOf((this.started - now) / 20))
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
                    player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                }
                QuestMarkers.clear(team);
            }
            return;
        }
        if (now - this.started >= DURATION) {
            int medal = medalFor(this.score);
            if (medal == 0) {
                fail("score");
            } else {
                succeed(medal);
            }
            return;
        }
        if (now % 5 == 0) {
            int alive = this.level.getEntities(EntityTypeTest.forClass(HavenTargetEntity.class),
                    t -> t.getTags().contains(tag()) && !t.isRemoved()).size();
            // UN FILET DE SECURITE : adosse a un mur, le tireur n'a rien en vue dans l'eventail
            // du stand -- on ouvre alors tout le tour, et on ne demande plus le trait libre.
            boolean relaxed = now - this.lastSpawn > 60;
            for (int i = alive; i < AT_ONCE && !team.isEmpty(); i++) {
                if (spawnTarget(team.get(this.level.random.nextInt(team.size())), relaxed)) {
                    this.lastSpawn = now;
                }
            }
        }
    }

    /** La variante d'une nouvelle cible : un civil de temps en temps, rarement le KG dore. */
    private int pickVariant() {
        double roll = this.level.random.nextDouble();
        double civilians = this.kind == 0 ? 0.15 : 0.25;
        if (roll < civilians) {
            return HavenTargetEntity.FIRST_CIVILIAN + this.level.random.nextInt(4);
        }
        if (roll < civilians + 0.10) {
            return HavenTargetEntity.BONUS;
        }
        return this.level.random.nextInt(3);
    }

    private boolean spawnTarget(ServerPlayer shooter, boolean relaxed) {
        double min = this.kind == 2 ? 18.0 : 6.0;
        double max = this.kind == 2 ? 30.0 : 18.0;
        boolean moving = this.kind == 1;
        Vec3 eyes = shooter.getEyePosition();
        for (int tries = 0; tries < 16; tries++) {
            // dans l'eventail du stand : cent quarante degres autour de la direction du signal
            double spread = relaxed ? 360.0 : 140.0;
            double yaw = Math.toRadians(this.fanYaw + (this.level.random.nextDouble() - 0.5) * spread);
            double dist = min + this.level.random.nextDouble() * (max - min);
            double x = shooter.getX() - Math.sin(yaw) * dist;
            double z = shooter.getZ() + Math.cos(yaw) * dist;
            BlockPos feet = standNear(BlockPos.containing(x, shooter.getY(), z));
            if (feet == null) {
                continue;
            }
            Vec3 at = Vec3.atBottomCenterOf(feet);
            if (!relaxed && !visible(eyes, at)) {
                continue;
            }
            Vec3 toShooter = eyes.subtract(at);
            Vec3 slide = new Vec3(-toShooter.z, 0, toShooter.x).normalize();
            // une cible mobile a besoin de sa glissiere : debout et en vue aux deux bouts
            if (moving && !(clearAt(at.add(slide.scale(SLIDE))) && clearAt(at.subtract(slide.scale(SLIDE)))
                    && visible(eyes, at.add(slide.scale(SLIDE))) && visible(eyes, at.subtract(slide.scale(SLIDE))))) {
                continue;
            }
            HavenTargetEntity target = Jak3Registry.HAVEN_TARGET.get().create(this.level);
            if (target == null) {
                return false;
            }
            int life = this.kind == 2 ? 50 : moving ? 80 : 70;
            target.setup(at, eyes, slide, moving, life, pickVariant());
            target.addTag(tag());
            return this.level.addFreshEntity(target);
        }
        return false;
    }

    /** Un sol libre sur deux blocs dans la colonne, de quatre plus bas a six plus haut. */
    @Nullable
    private BlockPos standNear(BlockPos column) {
        for (int dy = -4; dy <= 6; dy++) {
            BlockPos at = column.offset(0, dy, 0);
            if (HavenArrival.standable(this.level, at)) {
                return at;
            }
        }
        return null;
    }

    private boolean clearAt(Vec3 feet) {
        BlockPos at = BlockPos.containing(feet);
        return HavenArrival.standable(this.level, at) || HavenArrival.standable(this.level, at.above())
                || HavenArrival.standable(this.level, at.below());
    }

    /** Le milieu de la cible se voit des yeux du tireur. */
    private boolean visible(Vec3 eyes, Vec3 feet) {
        return this.level.clip(new ClipContext(eyes, feet.add(0, 0.8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    @Override
    public Component objective() {
        if (this.started < 0) {
            return Component.translatable("game.emeraldweapons.haven.quete.tir.aller");
        }
        long now = this.level.getGameTime();
        if (now < this.started) {
            return Component.translatable("game.emeraldweapons.haven.quete.tir.attente");
        }
        int left = (int) Math.max(0, (DURATION - (now - this.started) + 19) / 20);
        int medal = medalFor(this.score);
        int nextGoal = medal < this.thresholds.length ? this.thresholds[medal] : this.score;
        return Component.translatable("game.emeraldweapons.haven.quete.tir.objectif", this.score, nextGoal, clock(left));
    }

    @Override
    public float progress() {
        return Math.min(1.0F, this.score / (float) this.thresholds[2]);
    }

    @Override
    public BossEvent.BossBarColor color() {
        return BossEvent.BossBarColor.PINK;
    }

    @Override
    public void cleanup() {
        for (HavenTargetEntity target : this.level.getEntities(EntityTypeTest.forClass(HavenTargetEntity.class),
                t -> t.getTags().contains(tag()))) {
            target.discard();
        }
        QuestMarkers.clear(members());
    }

    /** Pour le banc : le score. */
    public int scoreForTest() {
        return this.score;
    }
}
