package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Le compteur d'orbes precurseurs d'un joueur, pour son ecran (HavenOrbsHud) : son solde, les
 * orbes caches deja trouves, et leur total.
 */
public record HavenOrbsPayload(int orbs, int found, int total) implements CustomPacketPayload {

    public static final Type<HavenOrbsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "haven_orbs"));

    public static final StreamCodec<ByteBuf, HavenOrbsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HavenOrbsPayload::orbs,
            ByteBufCodecs.VAR_INT, HavenOrbsPayload::found,
            ByteBufCodecs.VAR_INT, HavenOrbsPayload::total,
            HavenOrbsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
