package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * La page du carnet ou en est ce joueur : l'indice de l'etape, le total, et
 * la cle de l'etape en cours (vide quand tout est fait). Le client n'en fait
 * qu'une ligne en bas de l'ecran ; le texte vient des fichiers de langue.
 */
public record QuestPayload(int step, int total, String key) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<QuestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "quest"));
    public static final StreamCodec<ByteBuf, QuestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuestPayload::step,
            ByteBufCodecs.VAR_INT, QuestPayload::total,
            ByteBufCodecs.STRING_UTF8, QuestPayload::key,
            QuestPayload::new);

    @Override
    public CustomPacketPayload.Type<QuestPayload> type() {
        return TYPE;
    }
}
