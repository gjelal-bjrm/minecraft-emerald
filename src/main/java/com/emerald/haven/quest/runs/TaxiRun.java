package com.emerald.haven.quest.runs;

import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasionData;
import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.QuestMarkers;
import com.emerald.haven.quest.QuestRun;
import com.emerald.jak.vehicle.JakVehicleEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * LE TAXI de Keira : un habitant attend a une place de la ville ; on s'arrete pres de lui, il
 * monte, et on le depose a une autre place, a l'autre bout de la ville, en trois minutes. Les
 * deux places sont tirees parmi les points d'eco des places et des rues (quatre cents blocs au
 * moins entre elles). Le client brille et porte un nom ; une colonne de lumiere dit ou il
 * attend, puis ou il va.
 */
public class TaxiRun extends QuestRun {

    public static final int RIDE = 20 * 180;
    private static final double BOARD = 6.0;
    private static final double DROP = 9.0;
    /** Moins d'un demi-bloc par tique (dix metres par seconde) : on s'est arrete. */
    private static final double SLOW = 0.5;

    @Nullable
    private Vec3 pickup;
    @Nullable
    private Vec3 destination;
    private Component destinationName = Component.empty();
    private Component pickupName = Component.empty();
    @Nullable
    private Villager client;
    private long boarded = -1L;
    private long dropped = -1L;

    public TaxiRun(HavenQuest quest, ServerLevel level) {
        super(quest, level);
    }

    @Override
    public void begin() {
        List<HavenInvasionData.EcoPoint> points = new ArrayList<>(HavenInvasionData.ecoPoints(this.level.getServer()));
        BlockPos origin = HavenState.get(this.level.getServer()).origin();
        for (int tries = 0; tries < 40 && points.size() >= 2; tries++) {
            HavenInvasionData.EcoPoint a = points.get(this.level.random.nextInt(points.size()));
            HavenInvasionData.EcoPoint b = points.get(this.level.random.nextInt(points.size()));
            if (a == b || a.feet().getY() > 70 || b.feet().getY() > 70) {
                continue;
            }
            Vec3 pa = Vec3.atBottomCenterOf(a.feetWorld(origin));
            Vec3 pb = Vec3.atBottomCenterOf(b.feetWorld(origin));
            if (pa.distanceTo(pb) >= 400.0) {
                this.pickup = pa;
                this.destination = pb;
                this.pickupName = Component.literal(a.name());
                this.destinationName = Component.literal(b.name());
                return;
            }
        }
    }

    @Override
    public void tick(long now) {
        if (this.pickup == null || this.destination == null) {
            fail("carte");
            return;
        }
        List<ServerPlayer> team = members();
        if (this.client == null || this.client.isRemoved()) {
            if (this.boarded >= 0) {
                fail("client");
                return;
            }
            spawnClient();
        }
        Villager client = this.client;
        if (client != null && this.boarded < 0) {
            for (ServerPlayer player : team) {
                if (player.getVehicle() instanceof JakVehicleEntity car && car.getControllingPassenger() == player
                        && car.position().distanceTo(client.position()) <= BOARD && speed(car) < SLOW
                        && client.startRiding(car, true)) {
                    this.boarded = now;
                    for (ServerPlayer member : team) {
                        member.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.quete.taxi.monte",
                                this.destinationName).withStyle(ChatFormatting.AQUA));
                        member.playNotifySound(SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                    break;
                }
            }
        }
        if (client != null && this.boarded >= 0 && this.dropped < 0) {
            if (now - this.boarded > RIDE) {
                fail("temps");
                return;
            }
            if (client.getVehicle() instanceof JakVehicleEntity car && car.position().distanceTo(this.destination) <= DROP
                    && speed(car) < SLOW) {
                client.stopRiding();
                client.setNoAi(false);
                client.teleportTo(this.destination.x, this.destination.y, this.destination.z);
                this.dropped = now;
                for (ServerPlayer member : team) {
                    member.playNotifySound(SoundEvents.VILLAGER_CELEBRATE, SoundSource.PLAYERS, 1.0F, 1.0F);
                }
                succeed();
                return;
            }
            if (client.getVehicle() == null) {
                // tombe de la voiture : il attend la ou il est, on revient le chercher
                this.boarded = -1L;
                this.pickup = client.position();
            }
        }
        if (now % 10 == 0) {
            Vec3 target = this.boarded < 0 ? (client == null ? this.pickup : client.position()) : this.destination;
            QuestMarkers.show(team, List.of(QuestMarkers.beacon(target, this.boarded < 0 ? QuestMarkers.GREEN : QuestMarkers.GOLD)));
        }
    }

    private static double speed(JakVehicleEntity car) {
        Vec3 motion = car.serverMotion();
        return Math.sqrt(motion.x * motion.x + motion.z * motion.z);
    }

    private void spawnClient() {
        BlockPos near = BlockPos.containing(this.pickup);
        if (!this.level.isLoaded(near)) {
            return;
        }
        BlockPos feet = null;
        for (int r = 0; r <= 6 && feet == null; r++) {
            for (int dx = -r; dx <= r && feet == null; dx++) {
                for (int dz = -r; dz <= r && feet == null; dz++) {
                    for (int dy = -2; dy <= 2 && feet == null; dy++) {
                        BlockPos at = near.offset(dx, dy, dz);
                        if (HavenArrival.standable(this.level, at)) {
                            feet = at;
                        }
                    }
                }
            }
        }
        if (feet == null) {
            return;
        }
        Villager villager = new Villager(EntityType.VILLAGER, this.level, VillagerType.PLAINS);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        villager.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, this.level.random.nextFloat() * 360.0F, 0.0F);
        villager.setInvulnerable(true);
        villager.setNoAi(true);
        villager.setPersistenceRequired();
        villager.setGlowingTag(true);
        villager.setCustomName(Component.translatable("game.emeraldweapons.haven.quete.taxi.client").withStyle(ChatFormatting.AQUA));
        villager.setCustomNameVisible(true);
        villager.addTag(tag());
        if (this.level.addFreshEntity(villager)) {
            this.client = villager;
            this.pickup = villager.position();
        }
    }

    @Override
    public Component objective() {
        if (this.boarded < 0) {
            return Component.translatable("game.emeraldweapons.haven.quete.taxi.chercher", this.pickupName);
        }
        int left = (int) Math.max(0, (RIDE - (this.level.getGameTime() - this.boarded) + 19) / 20);
        return Component.translatable("game.emeraldweapons.haven.quete.taxi.conduire", this.destinationName, clock(left));
    }

    @Override
    public float progress() {
        if (this.boarded < 0 || this.destination == null) {
            return 0.0F;
        }
        return Math.min(1.0F, (this.level.getGameTime() - this.boarded) / (float) RIDE);
    }

    @Override
    public void cleanup() {
        if (this.client != null) {
            this.client.stopRiding();
            this.client.discard();
        }
        QuestMarkers.clear(members());
    }
}
