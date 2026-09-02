package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Les failles de la Dechirure, telles que le serveur les tient.
 *
 * Elles DOIVENT transiter : depuis qu'une faille est de la geometrie et non un
 * nuage de particules, le client a besoin de savoir OU elle est et depuis
 * combien de temps -- une particule se suffit a elle-meme, une forme dessinee
 * a besoin d'une position et d'un age. Le serveur envoie la liste entiere,
 * toutes les dix ticks : quelques failles a la fois, cinq nombres chacune, le
 * cout est nul et le client n'a rien a deviner.
 *
 * L'age sert au fondu : une faille nait en s'ouvrant et meurt en se
 * refermant, et c'est le client qui l'anime a partir de life et maxLife.
 */
public record RiftSyncPayload(List<Entry> rifts) implements CustomPacketPayload {

    /** Une faille : sa position, et ou elle en est de sa vie. */
    public record Entry(double x, double y, double z, int life, int maxLife) {

        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Entry::x,
                ByteBufCodecs.DOUBLE, Entry::y,
                ByteBufCodecs.DOUBLE, Entry::z,
                ByteBufCodecs.VAR_INT, Entry::life,
                ByteBufCodecs.VAR_INT, Entry::maxLife,
                Entry::new);
    }

    public static final CustomPacketPayload.Type<RiftSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "rift_sync"));

    public static final StreamCodec<ByteBuf, RiftSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), RiftSyncPayload::rifts,
                    RiftSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<RiftSyncPayload> type() {
        return TYPE;
    }
}
