package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Un achat demande dans la boutique de Tess : le serveur revalide tout (HavenShop.buy). */
public record HavenShopBuyPayload(String article) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HavenShopBuyPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "haven_shop_buy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HavenShopBuyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, HavenShopBuyPayload::article,
            HavenShopBuyPayload::new);

    @Override
    public CustomPacketPayload.Type<HavenShopBuyPayload> type() {
        return TYPE;
    }
}
