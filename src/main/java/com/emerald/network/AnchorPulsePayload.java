package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * La pulsation du sanctuaire : « c'est LA ».
 *
 * Le serveur l'envoie de temps en temps aux joueurs qui sont dans l'enceinte.
 * Le client en tire une colonne lumineuse dessinee SANS test de profondeur,
 * donc visible a travers la maconnerie -- ce qu'aucune particule ne sait faire,
 * puisqu'elles sont occultees par les blocs.
 *
 * La HAUTEUR distingue les deux usages : une colonne qui barre le ciel pour
 * l'ancre, une borne courte pour un sceau. Un sceau signale par une colonne de
 * cent soixante blocs donnerait la reponse depuis l'autre bout de la carte, ce
 * qui n'aide pas -- on veut dire « fouille par ici », pas « c'est ici ».
 */
public record AnchorPulsePayload(int x, int y, int z, int ticks, int height)
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
                    ByteBufCodecs.VAR_INT, AnchorPulsePayload::height,
                    AnchorPulsePayload::new);

    @Override
    public CustomPacketPayload.Type<AnchorPulsePayload> type() {
        return TYPE;
    }
}
