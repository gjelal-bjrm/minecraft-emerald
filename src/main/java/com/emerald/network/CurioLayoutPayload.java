package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.menu.curio.CurioRef;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * La liste des cases d'artefacts du joueur, pour l'inventaire d'Arcencium ouvert : envoyee quand
 * elle change ecran ouvert (une ceinture de Relics ajoute ou retire des cases de charme).
 */
public record CurioLayoutPayload(int containerId, List<CurioRef> refs) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CurioLayoutPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "curio_layout"));

    private static final StreamCodec<ByteBuf, CurioRef> REF = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), CurioRef::identifier,
            ByteBufCodecs.VAR_INT, CurioRef::index,
            CurioRef::new);

    public static final StreamCodec<ByteBuf, CurioLayoutPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CurioLayoutPayload::containerId,
            REF.apply(ByteBufCodecs.list(512)), CurioLayoutPayload::refs,
            CurioLayoutPayload::new);

    @Override
    public CustomPacketPayload.Type<CurioLayoutPayload> type() {
        return TYPE;
    }
}
