package com.emerald.haven.journey;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Un titre du parcours de Haven, pour le client du joueur (HavenJourneyClient) : la premiere
 * arrivee, la deuxieme (la ville envahie), les rues reprises.
 *
 * PAS LES PAQUETS DE TITRE DE MINECRAFT : l'arrivee se fait juste apres la connexion ou un
 * changement de dimension, et le client reste sur « Chargement du terrain... » plusieurs
 * secondes -- plus de huit sur la photo de controle du 21 sept. Un titre envoye pendant ce
 * temps se joue sous l'ecran de chargement, et le joueur ne le voit jamais. Le client le
 * garde donc et le joue une fois l'ecran ferme.
 *
 * @param kind {@link #ARRIVEE}, {@link #ENVAHIE} ou {@link #REPRISE}
 */
public record HavenTitlePayload(int kind) implements CustomPacketPayload {

    /** La premiere arrivee : « Bienvenue a Haven ». */
    public static final int ARRIVEE = 0;
    /** La deuxieme arrivee, au retour du Defi : « Haven a ete envahie ». */
    public static final int ENVAHIE = 1;
    /** Les rues reprises : « Les rues sont a vous ». Joue en pleine partie, sans ecran de chargement. */
    public static final int REPRISE = 2;
    /** L'equipe reunie au QG : « L'equipe est reunie » -- quel mode voulez-vous ? (§83). */
    public static final int REUNION = 3;

    public static final CustomPacketPayload.Type<HavenTitlePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "haven_title"));

    public static final StreamCodec<ByteBuf, HavenTitlePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(HavenTitlePayload::new, HavenTitlePayload::kind);

    @Override
    public CustomPacketPayload.Type<HavenTitlePayload> type() {
        return TYPE;
    }
}
