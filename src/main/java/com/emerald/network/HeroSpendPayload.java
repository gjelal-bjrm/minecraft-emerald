package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * L'intention de placer des points, du client vers le serveur.
 *
 * Le client n'annonce que ce qu'il VEUT : une voie, une quantite. Il ne decide
 * de rien. Le serveur verifie les points libres et le plafond de la voie, puis
 * renvoie la fiche -- de sorte qu'un client modifie qui demanderait mille
 * points dans l'Attaque n'obtienne que ce qu'il possedait deja.
 */
public record HeroSpendPayload(int stat, int amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HeroSpendPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "hero_spend"));

    public static final StreamCodec<ByteBuf, HeroSpendPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, HeroSpendPayload::stat,
                    ByteBufCodecs.VAR_INT, HeroSpendPayload::amount,
                    HeroSpendPayload::new);

    @Override
    public CustomPacketPayload.Type<HeroSpendPayload> type() {
        return TYPE;
    }
}
