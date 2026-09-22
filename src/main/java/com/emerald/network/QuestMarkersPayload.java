package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Les reperes d'une quete de Haven, pour les joueurs de son equipe (lot 3, cahier §86) :
 * colonnes de lumiere (ou aller), anneaux (la course de Keira), cercles au sol (une zone a
 * tenir). Une liste vide les efface.
 *
 * @param markers les reperes, en coordonnees du monde
 */
public record QuestMarkersPayload(List<Marker> markers) implements CustomPacketPayload {

    /** Une colonne de lumiere. */
    public static final int BEACON = 0;
    /** Un anneau vertical, a traverser. */
    public static final int RING = 1;
    /** Un cercle au sol : une zone a tenir. */
    public static final int ZONE = 2;

    /**
     * @param kind  BEACON, RING ou ZONE
     * @param yaw   l'orientation d'un anneau (degres, lacet de Minecraft)
     * @param size  rayon (anneau, zone) ou hauteur (colonne)
     * @param argb  la couleur
     */
    public record Marker(int kind, double x, double y, double z, float yaw, float size, int argb) {
    }

    public static final Type<QuestMarkersPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "quest_markers"));

    private static final StreamCodec<ByteBuf, Marker> MARKER = StreamCodec.of((buf, m) -> {
        buf.writeByte(m.kind());
        buf.writeDouble(m.x());
        buf.writeDouble(m.y());
        buf.writeDouble(m.z());
        buf.writeFloat(m.yaw());
        buf.writeFloat(m.size());
        buf.writeInt(m.argb());
    }, buf -> new Marker(buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
            buf.readFloat(), buf.readInt()));

    public static final StreamCodec<ByteBuf, QuestMarkersPayload> STREAM_CODEC =
            MARKER.apply(ByteBufCodecs.collection(ArrayList::new)).map(list -> new QuestMarkersPayload(List.copyOf(list)),
                    p -> new ArrayList<>(p.markers()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
