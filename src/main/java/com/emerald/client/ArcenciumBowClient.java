package com.emerald.client;

import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.weapons.ArcenciumBowItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Predicats de modele "pull" / "pulling" pour l'Arcencium Bow.
 * Vanilla ne les enregistre que pour Items.BOW ; un BowItem custom doit
 * les declarer lui-meme. "pull" est normalise sur FULL_CHARGE_TICKS (50)
 * et non sur les 20 ticks vanilla : c'est lui qui selectionne les 5
 * textures de tension (une par cristal) via les overrides du modele.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ArcenciumBowClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.ARCENCIUM_BOW.get(),
                    ResourceLocation.withDefaultNamespace("pull"),
                    (stack, level, entity, seed) -> {
                        if (entity == null || entity.getUseItem() != stack) return 0.0F;
                        int used = stack.getUseDuration(entity) - entity.getUseItemRemainingTicks();
                        // Tension Rapide raccourcit la charge : sans ce facteur,
                        // la texture de l'arc resterait en retard sur son etat reel
                        if (com.emerald.artifact.Artifacts.has(stack,
                                com.emerald.artifact.Artifact.TENSION_RAPIDE)) {
                            used *= 2;
                        }
                        return Math.min(1.0F, used / (float) ArcenciumBowItem.FULL_CHARGE_TICKS);
                    });
            ItemProperties.register(ModItems.ARCENCIUM_BOW.get(),
                    ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
        });
    }
}
