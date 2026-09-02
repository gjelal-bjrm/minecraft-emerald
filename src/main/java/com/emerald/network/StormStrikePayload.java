package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Ce que l'Orage dit aux clients : ou la foudre va frapper, ou elle a frappe,
 * et qui porte la Surcharge.
 *
 * Les arcs d'ambiance sont tout client ; mais une frappe est un fait de jeu,
 * et son annonce doit etre la meme pour tous -- les arcs qui convergent vers
 * le point, puis qui eclatent en etoile, viennent donc d'ici. Trois sens :
 * WARN (une frappe dans ticks), IMPACT (elle vient de tomber), CRACKLE (un
 * porteur de Surcharge est la, faites-lui courir des arcs autour).
 */
public record StormStrikePayload(double x, double y, double z, int kind, int ticks)
        implements CustomPacketPayload {

    public static final int WARN = 0;
    public static final int IMPACT = 1;
    public static final int CRACKLE = 2;

    public static final CustomPacketPayload.Type<StormStrikePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "storm_strike"));

    public static final StreamCodec<ByteBuf, StormStrikePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, StormStrikePayload::x,
                    ByteBufCodecs.DOUBLE, StormStrikePayload::y,
                    ByteBufCodecs.DOUBLE, StormStrikePayload::z,
                    ByteBufCodecs.VAR_INT, StormStrikePayload::kind,
                    ByteBufCodecs.VAR_INT, StormStrikePayload::ticks,
                    StormStrikePayload::new);

    @Override
    public CustomPacketPayload.Type<StormStrikePayload> type() {
        return TYPE;
    }
}
