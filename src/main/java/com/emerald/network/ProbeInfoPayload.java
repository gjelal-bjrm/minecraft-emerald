package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Ce que la Sonde lit sous le reticule.
 *
 * Quatre lignes, deja mises en forme par le serveur : le bloc vise, sa
 * position dans le monde, le chantier qui l'a pose et son adresse dans la
 * structure. Le client ne fait que les afficher -- il n'a pas le registre, et
 * lui en envoyer une copie pour qu'il compose lui-meme le texte couterait bien
 * plus cher que quatre chaines de quarante caracteres.
 */
public record ProbeInfoPayload(String block, String world, String part, String local)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProbeInfoPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "probe_info"));

    public static final StreamCodec<ByteBuf, ProbeInfoPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ProbeInfoPayload::block,
                    ByteBufCodecs.STRING_UTF8, ProbeInfoPayload::world,
                    ByteBufCodecs.STRING_UTF8, ProbeInfoPayload::part,
                    ByteBufCodecs.STRING_UTF8, ProbeInfoPayload::local,
                    ProbeInfoPayload::new);

    @Override
    public CustomPacketPayload.Type<ProbeInfoPayload> type() {
        return TYPE;
    }
}
