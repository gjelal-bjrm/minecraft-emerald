package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Un chiffre de degats a faire flotter au-dessus d'une cible.
 *
 * Le serveur seul connait les degats REELLEMENT infliges, apres armure et
 * resistances, et lui seul sait si le coup etait critique -- c'est lui qui a
 * tire. Le client, lui, ne voit passer qu'une baisse de vie. Le chiffre doit
 * donc descendre du serveur ; on envoie la position plutot que l'identite de
 * la cible, parce qu'une cible morte n'existe deja plus quand le paquet arrive
 * et que son dernier coup est justement celui qu'on veut lire.
 */
public record DamagePopPayload(double x, double y, double z, float amount, boolean crit)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DamagePopPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    EmeraldWeaponsMod.MODID, "damage_pop"));

    public static final StreamCodec<ByteBuf, DamagePopPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, DamagePopPayload::x,
                    ByteBufCodecs.DOUBLE, DamagePopPayload::y,
                    ByteBufCodecs.DOUBLE, DamagePopPayload::z,
                    ByteBufCodecs.FLOAT, DamagePopPayload::amount,
                    ByteBufCodecs.BOOL, DamagePopPayload::crit,
                    DamagePopPayload::new);

    @Override
    public CustomPacketPayload.Type<DamagePopPayload> type() {
        return TYPE;
    }
}
