package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Les filons que l'Aurore designe a CE joueur.
 *
 * Chaque joueur recoit les siens : le panneau dit « a vingt-trois metres,
 * en bas, sur ta gauche », et cela n'a de sens que depuis sa place.
 *
 * On envoie des POSITIONS et non des distances : la distance et la direction
 * changent a chaque pas, et le client les recalcule a chaque image sans qu'on
 * ait a lui reparler. Le serveur ne parle donc que toutes les deux secondes.
 *
 * {@code kinds} : DEUX bits par entree -- 0 Arcencium, 1 diamant, 2 la Proie
 * de la Battue, 3 reserve aux Brumes d'Aurore. Un entier vaut mieux qu'une
 * liste d'objets pour six elements, et la boussole sert a tout ce qui se
 * cherche : un filon, une bete, une sortie.
 */
public record VeinSyncPayload(java.util.List<Long> positions, int kinds)
        implements CustomPacketPayload {

    public static final int KIND_ARCENCIUM = 0;
    public static final int KIND_DIAMOND = 1;
    public static final int KIND_PREY = 2;
    public static final int KIND_MIST = 3;

    /** Le genre de la n-ieme entree. */
    public static int kindAt(int kinds, int index) {
        return (kinds >> (2 * index)) & 3;
    }

    public static final CustomPacketPayload.Type<VeinSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "vein_sync"));

    public static final StreamCodec<ByteBuf, VeinSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), VeinSyncPayload::positions,
            ByteBufCodecs.VAR_INT, VeinSyncPayload::kinds,
            VeinSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<VeinSyncPayload> type() {
        return TYPE;
    }
}
