package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Les ailes d'un joueur, pour tous ceux qui le voient.
 *
 * Le palier de specialisation et l'apparence vivent dans les donnees
 * persistantes du serveur, qui ne traversent jamais le reseau. Or les ailes
 * se voient surtout chez les AUTRES : le paquet part donc a tous les joueurs
 * qui suivent l'entite, et a elle-meme, a chaque changement et a chaque
 * rencontre (voir Specialization).
 */
public record WingsSyncPayload(int entityId, int level, int skin) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WingsSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "wings_sync"));

    public static final StreamCodec<ByteBuf, WingsSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, WingsSyncPayload::entityId,
                    ByteBufCodecs.VAR_INT, WingsSyncPayload::level,
                    ByteBufCodecs.VAR_INT, WingsSyncPayload::skin,
                    WingsSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<WingsSyncPayload> type() {
        return TYPE;
    }
}
