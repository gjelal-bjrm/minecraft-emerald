package com.emerald.jak.vehicle;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Le choc de la voiture d'un joueur, du client du conducteur au serveur (VehicleImpacts.watchDriver).
 *
 * C'est son client qui simule la voiture : lui seul connait sa vraie vitesse. Le serveur ne
 * voit que les positions recues, une par tique du client, et une tique sans paquet lui
 * faisait entendre un choc en plein ciel (22 sept., cahier §83). Le serveur verifie que
 * l'expediteur conduit bien une voiture, borne la force et la cadence, puis joue le son et
 * les eclats pour tous.
 *
 * @param force la force du choc, de 0 (exclu) a 1
 */
public record VehicleCrashPayload(float force) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VehicleCrashPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "vehicle_crash"));

    public static final StreamCodec<ByteBuf, VehicleCrashPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeFloat(p.force),
            buf -> new VehicleCrashPayload(buf.readFloat()));

    @Override
    public CustomPacketPayload.Type<VehicleCrashPayload> type() {
        return TYPE;
    }
}
