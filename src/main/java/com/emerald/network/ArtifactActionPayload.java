package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Demande d'activation d'un artefact qui reagit a une touche.
 *
 * Le double saut et le retour ne peuvent pas etre detectes cote serveur : ni la
 * touche de saut en l'air, ni une touche personnalisee ne lui parviennent. Le
 * client signale donc l'intention, et le serveur verifie ensuite qu'elle est
 * legitime -- artefact porte, rechargement ecoule, joueur reellement en l'air.
 */
public record ArtifactActionPayload(Action action) implements CustomPacketPayload {

    public enum Action { DOUBLE_JUMP, RETURN }

    public static final CustomPacketPayload.Type<ArtifactActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "artifact_action"));

    public static final StreamCodec<ByteBuf, ArtifactActionPayload> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(
                    b -> new ArtifactActionPayload(Action.values()[Math.floorMod(b, Action.values().length)]),
                    p -> (byte) p.action().ordinal());

    @Override
    public CustomPacketPayload.Type<ArtifactActionPayload> type() {
        return TYPE;
    }
}
