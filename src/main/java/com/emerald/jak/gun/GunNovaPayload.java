package com.emerald.jak.gun;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * La detonation d'une Super Nova, pour les clients proches : ils jouent l'eclair
 * blanc et la secousse (GunNovaClient), d'autant plus forts qu'ils sont pres.
 *
 * @param x le point de la detonation
 */
public record GunNovaPayload(double x, double y, double z) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GunNovaPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "gun_nova"));

    public static final StreamCodec<ByteBuf, GunNovaPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
            },
            buf -> new GunNovaPayload(buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public CustomPacketPayload.Type<GunNovaPayload> type() {
        return TYPE;
    }
}
