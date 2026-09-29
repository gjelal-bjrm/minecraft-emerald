package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Les onglets du panneau du sac : l'icone de chaque sac porte, pour le menu ouvert. Les
 * donnees d'un menu ne portent que des entiers ; les piles viennent par ce paquet, a
 * chaque changement de la liste (BagPanel.sendTabs).
 */
public record BagTabsPayload(int containerId, List<ItemStack> icons) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BagTabsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "bag_tabs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BagTabsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BagTabsPayload::containerId,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(8)), BagTabsPayload::icons,
            BagTabsPayload::new);

    @Override
    public CustomPacketPayload.Type<BagTabsPayload> type() {
        return TYPE;
    }
}
