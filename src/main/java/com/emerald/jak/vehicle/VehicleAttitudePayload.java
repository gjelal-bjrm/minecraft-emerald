package com.emerald.jak.vehicle;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * L'equilibre de la voiture d'un joueur, du client du conducteur au serveur.
 *
 * C'est son client qui simule la voiture, equilibre compris (VehicleAttitude) : les
 * autres joueurs ne la verraient jamais pencher. Le serveur verifie que l'expediteur
 * conduit bien cette voiture, borne les angles, et les pose dans la donnee d'entite, qui
 * part a tous. Envoye seulement quand l'equilibre change d'un dixieme de degre.
 *
 * @param pitch le tangage, en radians, nez en haut positif
 * @param roll  le roulis, en radians, gauche en haut positif
 */
public record VehicleAttitudePayload(float pitch, float roll) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VehicleAttitudePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "vehicle_attitude"));

    public static final StreamCodec<ByteBuf, VehicleAttitudePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.pitch);
                buf.writeFloat(p.roll);
            },
            buf -> new VehicleAttitudePayload(buf.readFloat(), buf.readFloat()));

    @Override
    public CustomPacketPayload.Type<VehicleAttitudePayload> type() {
        return TYPE;
    }
}
