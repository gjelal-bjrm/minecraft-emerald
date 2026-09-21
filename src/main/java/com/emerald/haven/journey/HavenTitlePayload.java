package com.emerald.haven.journey;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Le titre de la premiere arrivee dans Haven, pour le client du joueur (HavenJourneyClient).
 *
 * PAS LES PAQUETS DE TITRE DE MINECRAFT : l'arrivee se fait juste apres la connexion ou un
 * changement de dimension, et le client reste sur « Chargement du terrain... » plusieurs
 * secondes -- plus de huit sur la photo de controle du 21 sept. Un titre envoye pendant ce
 * temps se joue sous l'ecran de chargement, et le joueur ne le voit jamais. Le client le
 * garde donc et le joue une fois l'ecran ferme.
 */
public record HavenTitlePayload() implements CustomPacketPayload {

    public static final HavenTitlePayload INSTANCE = new HavenTitlePayload();

    public static final CustomPacketPayload.Type<HavenTitlePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "haven_title"));

    public static final StreamCodec<ByteBuf, HavenTitlePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<HavenTitlePayload> type() {
        return TYPE;
    }
}
