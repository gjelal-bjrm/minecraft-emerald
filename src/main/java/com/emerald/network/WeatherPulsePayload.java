package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Un COUP de meteo : l'eclair qui vient de tomber, le meteore qui vient de
 * toucher. Le client en tire un eclat d'ecran et une secousse de camera.
 *
 * Ces deux effets ne peuvent pas se deviner cote client : rien ne distingue,
 * dans le flux des particules, l'impact d'un meteore a trente blocs du bruit
 * de fond de la tempete. On envoie donc l'evenement lui-meme, avec sa force et
 * sa couleur, plutot que de le faire reconstituer.
 *
 * La force est un centieme : le paquet reste entier et ne coute rien.
 */
public record WeatherPulsePayload(int color, int flash, int shake)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WeatherPulsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "weather_pulse"));

    public static final StreamCodec<ByteBuf, WeatherPulsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, WeatherPulsePayload::color,
                    ByteBufCodecs.VAR_INT, WeatherPulsePayload::flash,
                    ByteBufCodecs.VAR_INT, WeatherPulsePayload::shake,
                    WeatherPulsePayload::new);

    @Override
    public CustomPacketPayload.Type<WeatherPulsePayload> type() {
        return TYPE;
    }
}
