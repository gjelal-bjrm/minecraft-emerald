package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Alt+clic sur une case d'un ecran de conteneur (client/QuickStashClient) : le serveur en range la
 * pile dans le sac porte (menu/bag/QuickStash). bulk : Alt+Maj+clic, tout le meme objet du conteneur.
 */
public record QuickStashPayload(int containerId, int slot, boolean bulk) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuickStashPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "quick_stash"));

    public static final StreamCodec<ByteBuf, QuickStashPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuickStashPayload::containerId,
            ByteBufCodecs.VAR_INT, QuickStashPayload::slot,
            ByteBufCodecs.BOOL, QuickStashPayload::bulk,
            QuickStashPayload::new);

    @Override
    public CustomPacketPayload.Type<QuickStashPayload> type() {
        return TYPE;
    }
}
