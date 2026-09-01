package com.emerald.client;

import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.rune.RuneMark;
import com.emerald.rune.Runes;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Expose la FAMILLE de la rune au modele, sous forme de nombre.
 *
 * La famille et non la statistique : depuis que la rune porte plusieurs
 * options, il n'y a plus une seule statistique a representer. Et c'est la
 * bonne information -- ce qu'on veut savoir en voyant une pierre au sol, c'est
 * si elle ira sur l'arme, sur l'armure ou sur le casque.
 *
 * Meme mecanisme que pour les artefacts, et meme piege evite : on rend
 * l'ordinal BRUT et non divise. Les predicats de modele comparent des
 * flottants, et une fraction comme 0,07 ne s'y represente pas exactement --
 * une surcharge tomberait alors sur la mauvaise texture.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public class RuneClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.RUNE.get(),
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "variant"),
                (stack, level, entity, seed) -> {
                    RuneMark mark = Runes.of(stack);
                    return mark == null ? 0.0F : mark.family().ordinal();
                }));
    }
}
