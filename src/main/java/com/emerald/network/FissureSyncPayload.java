package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Les fissures de la Pluie de Meteores, telles que le serveur les tient.
 *
 * Une fissure est un vrai trou dans le sol, mais elle s'ANNONCE par une fente
 * dessinee, et les grandes gardent une lueur au fond : le client doit savoir
 * ou elle est, dans quelle direction, sur quelle longueur et largeur, et
 * depuis combien de temps. Il n'a pas besoin de la hauteur : il suit lui-meme
 * le relief, point par point -- et une fois le sol effondre, c'est le fond du
 * trou qu'il suit. Les ramifications ne transitent pas : elles se deduisent
 * de la position (voir FissureShape), a l'identique des deux cotes.
 *
 * Le serveur envoie la liste entiere a chaque ouverture puis toutes les vingt
 * ticks : un paquet perdu se repare de lui-meme, et une liste vide efface
 * tout quand la meteo cesse.
 */
public record FissureSyncPayload(List<Entry> fissures) implements CustomPacketPayload {

    /** Une fissure : son centre, sa direction, sa longueur, sa largeur, et ou elle en est de sa vie. */
    public record Entry(double x, double z, float dir, float length, float width, int life, int maxLife) {

        // sept champs : un de plus que ce que composite accepte, on ecrit le codec
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, e) -> {
                    buf.writeDouble(e.x());
                    buf.writeDouble(e.z());
                    buf.writeFloat(e.dir());
                    buf.writeFloat(e.length());
                    buf.writeFloat(e.width());
                    buf.writeInt(e.life());
                    buf.writeInt(e.maxLife());
                },
                buf -> new Entry(buf.readDouble(), buf.readDouble(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt()));
    }

    public static final CustomPacketPayload.Type<FissureSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "fissure_sync"));

    public static final StreamCodec<ByteBuf, FissureSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), FissureSyncPayload::fissures,
                    FissureSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<FissureSyncPayload> type() {
        return TYPE;
    }
}
