package com.emerald.haven.quest.runs;

import com.emerald.haven.HavenState;
import com.emerald.haven.fauna.HavenFaunaData;
import com.emerald.haven.journey.HavenProgress;
import com.emerald.haven.quest.HavenOrbs;
import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.QuestMarkers;
import com.emerald.haven.quest.QuestRun;
import com.emerald.network.QuestMarkersPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * LES COFFRES ENGLOUTIS du Pecheur (§85.1) : six coffres au fond de l'eau, trois dans le
 * bassin et trois au large -- la ou rodent les dangers (HavenFauna). Une colonne de lumiere
 * sort de l'eau au-dessus de chacun ; on plonge, on l'ouvre (clic droit) : cinq orbes pour
 * qui l'ouvre, et le coffre s'en va. Les six en dix minutes.
 *
 * Les places sont tirees dans la carte de l'eau (haven_fauna.json) : cinq a quatorze blocs
 * de fond, a quarante blocs les unes des autres, et au large pas plus loin que quarante
 * blocs d'un quai. Un coffre n'est pose que quand son troncon est charge, au fond reel de
 * la colonne (la derniere eau au-dessus d'un bloc plein), gorge d'eau ; le nettoyage rend
 * l'eau. Le Pecheur prete son souffle : respiration aquatique dans l'eau, le temps de la
 * quete.
 */
public class ChestsRun extends QuestRun {

    public static final int BASIN_CHESTS = 3;
    public static final int SEA_CHESTS = 3;
    public static final int ORBS_PER_CHEST = 5;
    private static final int MIN_DEPTH = 5;
    private static final int MAX_DEPTH = 14;
    private static final double SPACING = 40.0;
    private static final double SEA_REACH = 40.0;

    private static final class Spot {
        int cellX;
        int cellZ;
        final int depth;
        final boolean sea;
        @Nullable
        BlockPos pos;
        boolean opened;
        int misses;

        Spot(int cellX, int cellZ, int depth, boolean sea) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.depth = depth;
            this.sea = sea;
        }
    }

    private final List<Spot> spots = new ArrayList<>();
    private int opened;

    public ChestsRun(HavenQuest quest, ServerLevel level) {
        super(quest, level);
    }

    @Override
    public void begin() {
        HavenFaunaData.Data data = HavenFaunaData.get(this.level.getServer());
        if (data == null) {
            return;
        }
        List<int[]> basin = new ArrayList<>();
        List<int[]> sea = new ArrayList<>();
        List<int[]> seaQuays = new ArrayList<>();
        for (HavenFaunaData.Tile tile : data.list()) {
            int x0 = tile.tx() * HavenFaunaData.TILE;
            int z0 = tile.tz() * HavenFaunaData.TILE;
            for (int dz = 0; dz < HavenFaunaData.TILE; dz++) {
                for (int dx = 0; dx < HavenFaunaData.TILE; dx++) {
                    HavenFaunaData.Water water = tile.kind(dx, dz);
                    int depth = tile.depth(dx, dz);
                    if (water == HavenFaunaData.Water.NONE || depth < MIN_DEPTH || depth > MAX_DEPTH) {
                        continue;
                    }
                    (water == HavenFaunaData.Water.BASIN ? basin : sea).add(new int[]{x0 + dx, z0 + dz, depth});
                }
            }
            for (int i = 0; i < tile.quays().length; i++) {
                if (tile.quaySide()[i] == 2) {
                    int q = tile.quays()[i];
                    seaQuays.add(new int[]{HavenFaunaData.unpackX(q), HavenFaunaData.unpackZ(q)});
                }
            }
        }
        pick(basin, BASIN_CHESTS, false, List.of());
        pick(sea, SEA_CHESTS, true, seaQuays);
    }

    private void pick(List<int[]> candidates, int count, boolean sea, List<int[]> nearQuays) {
        int placed = 0;
        for (int tries = 0; tries < 400 && placed < count && !candidates.isEmpty(); tries++) {
            int[] c = candidates.get(this.level.random.nextInt(candidates.size()));
            boolean spaced = true;
            for (Spot other : this.spots) {
                spaced &= Math.hypot(other.cellX - c[0], other.cellZ - c[1]) >= SPACING;
            }
            if (!spaced) {
                continue;
            }
            if (sea) {
                boolean nearShore = false;
                for (int[] q : nearQuays) {
                    if (Math.abs(q[0] - c[0]) <= SEA_REACH && Math.abs(q[1] - c[1]) <= SEA_REACH
                            && Math.hypot(q[0] - c[0], q[1] - c[1]) <= SEA_REACH) {
                        nearShore = true;
                        break;
                    }
                }
                if (!nearShore) {
                    continue;
                }
            }
            this.spots.add(new Spot(c[0], c[1], c[2], sea));
            placed++;
        }
    }

    @Override
    public void tick(long now) {
        if (this.spots.size() < BASIN_CHESTS + SEA_CHESTS) {
            fail("carte");
            return;
        }
        List<ServerPlayer> team = members();
        if (now % 20 == 0) {
            BlockPos origin = HavenState.get(this.level.getServer()).origin();
            HavenFaunaData.Data data = HavenFaunaData.get(this.level.getServer());
            int surface = data == null ? 57 : data.surface();
            List<QuestMarkersPayload.Marker> markers = new ArrayList<>();
            for (Spot spot : this.spots) {
                if (spot.opened) {
                    continue;
                }
                if (spot.pos == null) {
                    place(spot, origin, surface);
                }
                Vec3 at = spot.pos != null ? Vec3.atBottomCenterOf(spot.pos)
                        : new Vec3(origin.getX() + spot.cellX + 0.5, origin.getY() + surface - spot.depth + 1, origin.getZ() + spot.cellZ + 0.5);
                markers.add(QuestMarkers.beacon(at, spot.sea ? QuestMarkers.BLUE : QuestMarkers.GOLD));
            }
            QuestMarkers.show(team, markers);
            // le souffle du Pecheur
            for (ServerPlayer player : team) {
                MobEffectInstance breath = player.getEffect(MobEffects.WATER_BREATHING);
                if (player.isInWater() && (breath == null || breath.getDuration() < 200)) {
                    player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 300, 0, true, false, true));
                }
            }
        }
    }

    /** Le coffre au fond reel de la colonne, si son troncon est charge. */
    private void place(Spot spot, BlockPos origin, int surface) {
        BlockPos top = origin.offset(spot.cellX, surface, spot.cellZ);
        if (!this.level.isLoaded(top)) {
            return;
        }
        BlockPos at = top;
        if (!this.level.getFluidState(at).is(FluidTags.WATER)) {
            at = at.below();
        }
        BlockPos bottom = null;
        for (int i = 0; i < 40 && this.level.getFluidState(at).is(FluidTags.WATER); i++) {
            bottom = at;
            at = at.below();
        }
        if (bottom == null || !this.level.getBlockState(bottom).canBeReplaced()
                || !this.level.getBlockState(bottom.below()).isFaceSturdy(this.level, bottom.below(), Direction.UP)) {
            // pas de fond franc ici (une marche, une algue sur une dalle...) : a cote
            if (spot.misses++ < 24) {
                spot.cellX += this.level.random.nextInt(5) - 2;
                spot.cellZ += this.level.random.nextInt(5) - 2;
            }
            return;
        }
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(this.level.random);
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing)
                .setValue(ChestBlock.WATERLOGGED, true);
        if (this.level.setBlock(bottom, chest, 3)) {
            spot.pos = bottom.immutable();
        }
    }

    @Override
    public boolean onBlock(ServerPlayer member, BlockPos pos) {
        for (Spot spot : this.spots) {
            if (spot.opened || spot.pos == null || !spot.pos.equals(pos)) {
                continue;
            }
            spot.opened = true;
            this.opened++;
            this.level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
            this.level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    30, 0.4, 0.4, 0.4, 0.1);
            this.level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
            HavenProgress.addOrbs(member.getUUID(), ORBS_PER_CHEST);
            HavenOrbs.sync(member);
            for (ServerPlayer player : members()) {
                player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.quete.coffres.ouvert",
                        this.opened, BASIN_CHESTS + SEA_CHESTS, member.getDisplayName(), ORBS_PER_CHEST)
                        .withStyle(ChatFormatting.GOLD), true);
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.1F);
            }
            if (this.opened >= BASIN_CHESTS + SEA_CHESTS) {
                succeed();
            }
            return true;
        }
        return false;
    }

    @Override
    public Component objective() {
        return Component.translatable("game.emeraldweapons.haven.quete.coffres.objectif", this.opened,
                BASIN_CHESTS + SEA_CHESTS, clock(secondsLeft()));
    }

    @Override
    public float progress() {
        return this.opened / (float) (BASIN_CHESTS + SEA_CHESTS);
    }

    @Override
    public BossEvent.BossBarColor color() {
        return BossEvent.BossBarColor.BLUE;
    }

    @Override
    public void cleanup() {
        for (Spot spot : this.spots) {
            if (!spot.opened && spot.pos != null && this.level.getBlockState(spot.pos).is(Blocks.CHEST)) {
                this.level.setBlock(spot.pos, Blocks.WATER.defaultBlockState(), 3);
            }
        }
        QuestMarkers.clear(members());
    }

    /** Pour le banc : les colonnes choisies, en blocs du monde (a charger avant la pose). */
    public List<BlockPos> columnsForTest(net.minecraft.server.MinecraftServer server) {
        BlockPos origin = HavenState.get(server).origin();
        List<BlockPos> out = new ArrayList<>();
        for (Spot spot : this.spots) {
            out.add(origin.offset(spot.cellX, 0, spot.cellZ));
        }
        return out;
    }

    /** Pour le banc : les coffres poses. */
    public List<BlockPos> chestsForTest() {
        List<BlockPos> out = new ArrayList<>();
        for (Spot spot : this.spots) {
            if (spot.pos != null && !spot.opened) {
                out.add(spot.pos);
            }
        }
        return out;
    }
}
