package com.emerald.jak.gun;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Une fleche de la croix de Jak 3 : le joueur demande une famille.
 *
 * Le paquet ne porte que la famille (ordinal de GunForm.Family : 0 rouge,
 * 1 jaune, 2 bleu, 3 sombre). Le serveur decide de la forme -- la premiere
 * possedee de la famille, ou la suivante si c'est la famille tenue -- apres
 * avoir tout revalide (MorphGunKeeper.select) : dans la ville lobby ouvert,
 * l'arme en main, une forme possedee.
 */
public record GunSelectPayload(int family) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GunSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "gun_select"));

    public static final StreamCodec<ByteBuf, GunSelectPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(GunSelectPayload::new, GunSelectPayload::family);

    @Override
    public CustomPacketPayload.Type<GunSelectPayload> type() {
        return TYPE;
    }
}
