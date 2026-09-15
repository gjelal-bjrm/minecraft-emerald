package com.emerald.jak.gun;

import com.emerald.main.EmeraldWeaponsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * La gachette du Morph Gun : le clic gauche tenu ou relache.
 *
 * Le client l'envoie a chaque changement, et redit « tenue » toutes les
 * {@value GunSpec#TRIGGER_KEEPALIVE} tiques : un relachement perdu est borne par le
 * delai de garde du serveur ({@value GunSpec#TRIGGER_TIMEOUT} tiques). Le paquet ne
 * porte rien d'autre ; cadence, reserve, forme et visee sont decidees et
 * revalidees par le serveur (GunFire).
 */
public record GunTriggerPayload(boolean down) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GunTriggerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "gun_trigger"));

    public static final StreamCodec<ByteBuf, GunTriggerPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(GunTriggerPayload::new, GunTriggerPayload::down);

    @Override
    public CustomPacketPayload.Type<GunTriggerPayload> type() {
        return TYPE;
    }
}
