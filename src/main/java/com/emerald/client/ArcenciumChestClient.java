package com.emerald.client;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Branche le rendu 3D du Coffre d'Arcencium sur son objet. */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public class ArcenciumChestClient {

    @SubscribeEvent
    public static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private ArcenciumChestItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new ArcenciumChestItemRenderer();
                }
                return this.renderer;
            }
        }, ModBlocks.ARCENCIUM_CHEST.asItem());
    }
}
