package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * L'etat de la partie, envoye au client pour son affichage.
 *
 * Seul ce qui se dessine transite : le statut, le temps restant, la phase et le
 * nombre d'ancres. Le client n'a pas a connaitre le reste, et ne peut donc rien
 * en deduire qu'il ne verrait pas deja a l'ecran.
 */
public record GameSyncPayload(int status, long remaining, int phase, int anchors)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GameSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "game_sync"));

    public static final StreamCodec<ByteBuf, GameSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GameSyncPayload::status,
                    ByteBufCodecs.VAR_LONG, GameSyncPayload::remaining,
                    ByteBufCodecs.VAR_INT, GameSyncPayload::phase,
                    ByteBufCodecs.VAR_INT, GameSyncPayload::anchors,
                    GameSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<GameSyncPayload> type() {
        return TYPE;
    }
}
