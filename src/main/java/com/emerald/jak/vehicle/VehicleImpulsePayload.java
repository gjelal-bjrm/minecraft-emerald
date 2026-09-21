package com.emerald.jak.vehicle;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Le souffle d'une explosion sur la voiture d'un joueur, du serveur au client du conducteur.
 *
 * Le serveur ne touche jamais la vitesse d'une voiture conduite (il la figeait, §64) :
 * c'est le client du conducteur qui la simule. Il recoit donc le choc et l'applique
 * lui-meme, vitesse et equilibre (JakVehicleEntity.applyImpulse).
 *
 * @param vehicle l'identifiant de la voiture
 * @param impulse l'impulsion, en masse x blocs par tick, repere du monde
 * @param at      le point du monde ou elle porte
 */
public record VehicleImpulsePayload(int vehicle, Vec3 impulse, Vec3 at) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VehicleImpulsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "vehicle_impulse"));

    public static final StreamCodec<ByteBuf, VehicleImpulsePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.vehicle);
                buf.writeDouble(p.impulse.x);
                buf.writeDouble(p.impulse.y);
                buf.writeDouble(p.impulse.z);
                buf.writeDouble(p.at.x);
                buf.writeDouble(p.at.y);
                buf.writeDouble(p.at.z);
            },
            buf -> new VehicleImpulsePayload(buf.readInt(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));

    @Override
    public CustomPacketPayload.Type<VehicleImpulsePayload> type() {
        return TYPE;
    }
}
