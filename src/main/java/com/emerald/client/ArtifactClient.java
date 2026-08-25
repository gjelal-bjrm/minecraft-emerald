package com.emerald.client;

import com.emerald.artifact.Artifact;
import com.emerald.artifact.Artifacts;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Expose l'artefact serti au modele, sous forme de nombre.
 *
 * Les predicats de modele ne savent comparer que des flottants : on encode donc
 * l'ordinal divise par dix, et chaque surcharge du modele repere le sien. C'est
 * le meme mecanisme que la tension de l'arc ou le rechargement du sceptre.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public class ArtifactClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.ARTIFACT.get(),
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "variant"),
                (stack, level, entity, seed) -> {
                    Artifact artifact = Artifacts.of(stack);
                    // l'ordinal brut, non divise : un entier est exact en
                    // flottant, alors qu'une fraction comme 0,07 ne l'est pas
                    // et ferait tomber une surcharge sur la mauvaise texture
                    return artifact == null ? 0.0F : artifact.ordinal();
                }));
    }
}
