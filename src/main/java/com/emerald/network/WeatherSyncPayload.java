package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * La meteo courante, envoyee au client pour son brouillard et son affichage.
 *
 * `pending` vaut -1 hors preavis : le client affiche alors la meteo en cours,
 * sinon l'avertissement avec son compte a rebours.
 */
public record WeatherSyncPayload(int weather, int remaining, int pending, int warning)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WeatherSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "weather_sync"));

    public static final StreamCodec<ByteBuf, WeatherSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, WeatherSyncPayload::weather,
                    ByteBufCodecs.VAR_INT, WeatherSyncPayload::remaining,
                    ByteBufCodecs.VAR_INT, WeatherSyncPayload::pending,
                    ByteBufCodecs.VAR_INT, WeatherSyncPayload::warning,
                    WeatherSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<WeatherSyncPayload> type() {
        return TYPE;
    }
}
