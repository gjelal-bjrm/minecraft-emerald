package com.emerald.jak.gun;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Les traces des tirs instantanes (sondes du Scatter Gun, balle de la Vulcan
 * Fury), UN paquet par salve : le client dessine les trainees et les eclats.
 *
 * @param shooter l'identifiant d'entite du tireur (la bouche du canon se calcule chez le client)
 * @param weapon  {@link #SCATTER} ou {@link #VULCAN}
 * @param ends    quatre flottants par trace : x, y, z de la fin, puis {@link #MISS}, {@link #BLOCK} ou {@link #TARGET}
 */
public record GunTracePayload(int shooter, int weapon, double ox, double oy, double oz, float[] ends)
        implements CustomPacketPayload {

    public static final int SCATTER = 0;
    public static final int VULCAN = 1;

    public static final int MISS = 0;
    public static final int BLOCK = 1;
    public static final int TARGET = 2;

    /** Au plus 64 traces par paquet (le Scatter Gun en envoie 7). */
    private static final int MAX_TRACES = 64;

    public static final CustomPacketPayload.Type<GunTracePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "gun_trace"));

    public static final StreamCodec<ByteBuf, GunTracePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.shooter);
                buf.writeByte(p.weapon);
                buf.writeDouble(p.ox);
                buf.writeDouble(p.oy);
                buf.writeDouble(p.oz);
                int n = Math.min(MAX_TRACES, p.ends.length / 4);
                ByteBufCodecs.VAR_INT.encode(buf, n);
                for (int i = 0; i < n; i++) {
                    buf.writeFloat(p.ends[i * 4]);
                    buf.writeFloat(p.ends[i * 4 + 1]);
                    buf.writeFloat(p.ends[i * 4 + 2]);
                    buf.writeByte((int) p.ends[i * 4 + 3]);
                }
            },
            buf -> {
                int shooter = ByteBufCodecs.VAR_INT.decode(buf);
                int weapon = buf.readByte();
                double ox = buf.readDouble();
                double oy = buf.readDouble();
                double oz = buf.readDouble();
                int n = Math.max(0, Math.min(MAX_TRACES, ByteBufCodecs.VAR_INT.decode(buf)));
                float[] ends = new float[n * 4];
                for (int i = 0; i < n; i++) {
                    ends[i * 4] = buf.readFloat();
                    ends[i * 4 + 1] = buf.readFloat();
                    ends[i * 4 + 2] = buf.readFloat();
                    ends[i * 4 + 3] = buf.readByte();
                }
                return new GunTracePayload(shooter, weapon, ox, oy, oz, ends);
            });

    public int count() {
        return this.ends.length / 4;
    }

    @Override
    public CustomPacketPayload.Type<GunTracePayload> type() {
        return TYPE;
    }
}
