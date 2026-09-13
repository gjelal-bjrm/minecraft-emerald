package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * « Changer de zone de survol » : le conducteur d'une voiture de Haven appuie
 * sur la touche.
 *
 * Le paquet ne porte rien : le serveur sait dans quelle voiture est le joueur,
 * s'il la conduit, et dans quel mode elle vole. C'est lui qui bascule, et la
 * donnee d'entite du mode renvoie la decision a tous les clients.
 */
public record VehicleModePayload() implements CustomPacketPayload {

    public static final VehicleModePayload INSTANCE = new VehicleModePayload();

    public static final CustomPacketPayload.Type<VehicleModePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "vehicle_mode"));

    public static final StreamCodec<ByteBuf, VehicleModePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<VehicleModePayload> type() {
        return TYPE;
    }
}
