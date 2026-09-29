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
 * Les sortes de munitions presentes dans les sacs du joueur (une pile d'une de chaque),
 * pour que son arc se bande chez lui quand l'inventaire n'a plus de fleches (BagAmmo).
 */
public record BagAmmoPayload(List<ItemStack> kinds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BagAmmoPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "bag_ammo"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BagAmmoPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(16)), BagAmmoPayload::kinds,
            BagAmmoPayload::new);

    @Override
    public CustomPacketPayload.Type<BagAmmoPayload> type() {
        return TYPE;
    }
}
