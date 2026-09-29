package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * La touche E : le client demande l'inventaire d'Arcencium. Le paquet ne porte rien ; le
 * serveur ouvre le menu (ArcInventoryMenu.open), dont il connait seul le sac et les cases
 * d'artefacts.
 */
public record OpenArcInventoryPayload() implements CustomPacketPayload {

    public static final OpenArcInventoryPayload INSTANCE = new OpenArcInventoryPayload();

    public static final CustomPacketPayload.Type<OpenArcInventoryPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "arc_inventory"));

    public static final StreamCodec<ByteBuf, OpenArcInventoryPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<OpenArcInventoryPayload> type() {
        return TYPE;
    }
}
