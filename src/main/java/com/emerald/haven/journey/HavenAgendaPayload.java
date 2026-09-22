package com.emerald.haven.journey;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Les pages de l'agenda de Haven, ecrites par le serveur (qui sait ou en est le joueur) et
 * lues par le client dans l'ecran de livre de Minecraft (HavenAgendaClient). Les pages sont
 * des textes a traduire : chacun lit l'agenda dans sa langue.
 */
public record HavenAgendaPayload(List<Component> pages) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HavenAgendaPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "haven_agenda"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HavenAgendaPayload> STREAM_CODEC =
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list())
                    .map(HavenAgendaPayload::new, HavenAgendaPayload::pages);

    @Override
    public CustomPacketPayload.Type<HavenAgendaPayload> type() {
        return TYPE;
    }
}
