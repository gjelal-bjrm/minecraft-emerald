package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * La touche du JET-Board (cahier §100) : le joueur la presse. Le paquet ne porte rien ; le serveur
 * sait s'il est dessus, s'il l'a achete et s'il est dans sa case (JetBoard.toggle).
 */
public record JetBoardTogglePayload() implements CustomPacketPayload {

    public static final JetBoardTogglePayload INSTANCE = new JetBoardTogglePayload();

    public static final CustomPacketPayload.Type<JetBoardTogglePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "jet_board"));

    public static final StreamCodec<ByteBuf, JetBoardTogglePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<JetBoardTogglePayload> type() {
        return TYPE;
    }
}
