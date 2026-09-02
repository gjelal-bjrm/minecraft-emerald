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
public record GameSyncPayload(int status, long remaining, int phase, int anchors,
                              java.util.List<Long> anchorPositions, int heldMask,
                              long finalePos)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GameSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "game_sync"));

    // sept champs : composite s'arrete a six, on ecrit le codec a la main
    public static final StreamCodec<ByteBuf, GameSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.status());
                ByteBufCodecs.VAR_LONG.encode(buf, p.remaining());
                ByteBufCodecs.VAR_INT.encode(buf, p.phase());
                ByteBufCodecs.VAR_INT.encode(buf, p.anchors());
                ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()).encode(buf, p.anchorPositions());
                ByteBufCodecs.VAR_INT.encode(buf, p.heldMask());
                ByteBufCodecs.VAR_LONG.encode(buf, p.finalePos());
            },
            buf -> new GameSyncPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()).decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf)));

    @Override
    public CustomPacketPayload.Type<GameSyncPayload> type() {
        return TYPE;
    }
}
