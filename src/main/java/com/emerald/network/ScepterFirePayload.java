package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * « J'ai appuye sur attaquer en tenant le sceptre. »
 *
 * Un clic gauche dans le vide ne remonte jamais au serveur : le client est le
 * seul a le voir. Ce paquet sans contenu comble ce trou -- le serveur verifie
 * lui-meme que le joueur tient bien un sceptre et qu'il a le droit de tirer,
 * donc rien d'exploitable n'y transite.
 */
public record ScepterFirePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ScepterFirePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "scepter_fire"));

    public static final StreamCodec<ByteBuf, ScepterFirePayload> STREAM_CODEC =
            StreamCodec.unit(new ScepterFirePayload());

    @Override
    public CustomPacketPayload.Type<ScepterFirePayload> type() {
        return TYPE;
    }
}
