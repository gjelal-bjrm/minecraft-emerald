package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * La Serie de la Battue, envoyee au client a chaque changement.
 *
 * On envoie le NOMBRE de kills, le multiplicateur en dixiemes et LE TIC DU
 * DERNIER KILL -- pas le temps restant. La barre qui se vide se calcule sur le
 * client a chaque image depuis ce tic, sans qu'on ait a lui reparler ; le
 * serveur ne parle qu'aux kills, et une fois quand la serie tombe (count 0).
 */
public record StreakPayload(int count, int multiplierTenths, long killTick)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StreakPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "streak"));

    public static final StreamCodec<ByteBuf, StreakPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StreakPayload::count,
            ByteBufCodecs.VAR_INT, StreakPayload::multiplierTenths,
            ByteBufCodecs.VAR_LONG, StreakPayload::killTick,
            StreakPayload::new);

    @Override
    public CustomPacketPayload.Type<StreakPayload> type() {
        return TYPE;
    }
}
