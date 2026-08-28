package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * La pulsation du sanctuaire : « l'ancre est LA ».
 *
 * Le serveur l'envoie de temps en temps aux joueurs qui sont dans l'enceinte.
 * Le client en tire une colonne lumineuse dessinee SANS test de profondeur,
 * donc visible a travers la maconnerie -- ce qu'aucune particule ne sait faire,
 * puisqu'elles sont occultees par les blocs.
 *
 * C'est la reponse au probleme qui revient depuis le debut : une ancre au
 * sommet d'une pyramide de quatre-vingt-dix blocs ne se trouve pas toute seule.
 */
public record AnchorPulsePayload(int x, int y, int z, int ticks)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnchorPulsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "anchor_pulse"));

    public static final StreamCodec<ByteBuf, AnchorPulsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AnchorPulsePayload::x,
                    ByteBufCodecs.VAR_INT, AnchorPulsePayload::y,
                    ByteBufCodecs.VAR_INT, AnchorPulsePayload::z,
                    ByteBufCodecs.VAR_INT, AnchorPulsePayload::ticks,
                    AnchorPulsePayload::new);

    @Override
    public CustomPacketPayload.Type<AnchorPulsePayload> type() {
        return TYPE;
    }
}
