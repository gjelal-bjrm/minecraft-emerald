package com.emerald.haven.quest.runs;

import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenSpawner;
import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.QuestMarkers;
import com.emerald.haven.quest.QuestRun;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * TENIR UNE ZONE : le quai du port (le contrat de Torn, qui se refait) ou la terrasse de la tour
 * ouest (Samos). Soixante secondes a tenir, les vagues de monstres qui montent a l'assaut ; le
 * temps ne court que si quelqu'un de l'equipe est dans le cercle, et la quete echoue si le
 * cercle reste vide cinq secondes.
 *
 * Les places d'apparition des vagues sont cherchees DANS LE MONDE autour de la zone, au debut :
 * la terrasse de la tour est hors de la carte de l'invasion (qui ne monte pas si haut).
 */
public class HoldRun extends QuestRun {

    private static final int HOLD = 20 * 60;
    private static final int GRACE = 20 * 5;
    private static final int WAVE_EVERY = 20 * 8;

    private final BlockPos hint;
    private final double radius;
    private final boolean phantoms;
    private Vec3 center = Vec3.ZERO;
    private final List<BlockPos> spawns = new ArrayList<>();
    private int held;
    private int empty;
    private long nextWave;
    private boolean prepared;

    public HoldRun(HavenQuest quest, ServerLevel level, BlockPos hint, double radius, boolean phantoms) {
        super(quest, level);
        this.hint = hint;
        this.radius = radius;
        this.phantoms = phantoms;
    }

    /** Le quai du port, au pied de l'escalier du bateau, sous l'arc nord. */
    public static HoldRun port(HavenQuest quest, ServerLevel level) {
        return new HoldRun(quest, level, new BlockPos(560, 66, 258), 11.0, false);
    }

    /** La terrasse de la tour ouest, pres du portail : la plate-forme de Samos. */
    public static HoldRun platform(HavenQuest quest, ServerLevel level) {
        return new HoldRun(quest, level, new BlockPos(482, 123, 596), 9.0, true);
    }

    @Override
    public void begin() {
        BlockPos origin = HavenState.get(this.level.getServer()).origin();
        this.center = Vec3.atBottomCenterOf(origin.offset(this.hint));
        this.nextWave = this.level.getGameTime() + 60;
    }

    /**
     * La zone et les places des vagues, cherchees quand le troncon est charge : on accepte la
     * quete loin d'elle (au QG, sur la tour), et ce qu'on ne voit pas n'est pas encore charge.
     */
    private void prepare() {
        BlockPos origin = HavenState.get(this.level.getServer()).origin();
        BlockPos start = origin.offset(this.hint);
        BlockPos feet = null;
        for (int r = 0; r <= 8 && feet == null; r++) {
            for (int dx = -r; dx <= r && feet == null; dx++) {
                for (int dz = -r; dz <= r && feet == null; dz++) {
                    for (int dy = -3; dy <= 3 && feet == null; dy++) {
                        BlockPos at = start.offset(dx, dy, dz);
                        if (HavenArrival.standable(this.level, at)) {
                            feet = at;
                        }
                    }
                }
            }
        }
        this.center = Vec3.atBottomCenterOf(feet == null ? start : feet);
        // les places des vagues : du sol ou l'on tient debout, entre le bord du cercle et vingt blocs plus loin
        BlockPos c = BlockPos.containing(this.center);
        int reach = (int) this.radius + 12;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < this.radius + 3 || d > reach) {
                    continue;
                }
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos at = c.offset(dx, dy, dz);
                    if (this.level.isLoaded(at) && HavenSpawner.standable(this.level, at)) {
                        this.spawns.add(at);
                        break;
                    }
                }
            }
        }
        this.prepared = true;
    }

    private boolean inside(ServerPlayer player) {
        double dx = player.getX() - this.center.x;
        double dz = player.getZ() - this.center.z;
        return dx * dx + dz * dz <= this.radius * this.radius && Math.abs(player.getY() - this.center.y) <= 4.0;
    }

    @Override
    public void tick(long now) {
        if (!this.prepared) {
            if (!this.level.isLoaded(BlockPos.containing(this.center))) {
                QuestMarkers.show(members(), List.of(QuestMarkers.beacon(this.center, QuestMarkers.RED)));
                return;
            }
            prepare();
        }
        List<ServerPlayer> team = members();
        boolean anyone = false;
        for (ServerPlayer player : team) {
            anyone |= inside(player);
        }
        if (anyone) {
            this.held++;
            this.empty = 0;
        } else if (++this.empty > GRACE && this.held > 0) {
            fail("zone");
            return;
        }
        if (this.held >= HOLD) {
            succeed();
            return;
        }
        if (now >= this.nextWave && this.held > 0 || now >= this.nextWave && anyone) {
            this.nextWave = now + WAVE_EVERY;
            wave(team);
        }
        if (now % 20 == 0) {
            QuestMonsters.keepHunting(this.level, this, team);
            QuestMarkers.show(team, List.of(QuestMarkers.zone(this.center, (float) this.radius,
                    anyone ? QuestMarkers.GREEN : QuestMarkers.RED)));
            if (!anyone && this.held > 0) {
                for (ServerPlayer player : team) {
                    player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.quete.tenir.dehors",
                            Math.max(0, (GRACE - this.empty) / 20)).withStyle(ChatFormatting.RED), true);
                    player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
                }
            }
        }
    }

    private void wave(List<ServerPlayer> team) {
        if (this.spawns.isEmpty()) {
            return;
        }
        int size = 3 + team.size() + this.level.random.nextInt(2);
        for (int i = 0; i < size; i++) {
            BlockPos at = this.spawns.get(this.level.random.nextInt(this.spawns.size()));
            HavenInvasion.Kind kind = HavenSpawner.pickGroundKind(this.level.random);
            QuestMonsters.spawn(this.level, this, kind, at, team);
        }
        if (this.phantoms) {
            for (int i = 0; i < 2; i++) {
                Vec3 at = this.center.add(this.level.random.nextInt(21) - 10, 10 + this.level.random.nextInt(6),
                        this.level.random.nextInt(21) - 10);
                Mob phantom = HavenSpawner.spawnMonster(this.level, HavenInvasion.Kind.PHANTOM, at, true);
                if (phantom != null) {
                    phantom.addTag(tag());
                    phantom.addTag(HavenInvasion.generationTag(this.level));
                    QuestMonsters.hunt(phantom, team);
                }
            }
        }
    }

    @Override
    public Component objective() {
        int left = Math.max(0, (HOLD - this.held + 19) / 20);
        return this.held == 0
                ? Component.translatable("game.emeraldweapons.haven.quete.tenir.aller")
                : Component.translatable("game.emeraldweapons.haven.quete.tenir.objectif", left);
    }

    @Override
    public float progress() {
        return this.held / (float) HOLD;
    }

    @Override
    public BossEvent.BossBarColor color() {
        return BossEvent.BossBarColor.RED;
    }

    @Override
    public void cleanup() {
        QuestMonsters.removeAll(this.level, this);
        QuestMarkers.clear(members());
    }
}
