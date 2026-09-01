package com.emerald.client;

import com.emerald.element.Attunement;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Expose l'element d'un cristal au modele, sous forme de nombre.
 *
 * Meme mecanisme que pour les artefacts et les runes, et meme piege evite : on
 * rend l'ordinal BRUT et non divise. Les predicats de modele comparent des
 * flottants, et une fraction ne s'y represente pas exactement.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public class ElementClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.ELEMENT_STONE.get(),
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "variant"),
                (stack, level, entity, seed) -> Attunement.of(stack).ordinal()));
    }
}
