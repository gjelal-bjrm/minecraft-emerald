package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Prepare et greffe {@link ArcenciumArmorLayer}.
 *
 * Les deux modeles declares ici sont des coques LEGEREMENT PLUS LARGES que
 * l'armure : 1,15 contre 1,0 pour la couche exterieure, 0,65 contre 0,5 pour
 * l'interieure. Sans ce decalage, la coque lumineuse partage exactement la
 * geometrie de l'armure et perd le test de profondeur des qu'on tourne autour
 * du personnage -- les fissures ne s'allument alors que sous certains angles.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public class ArcenciumArmorClient {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "arcencium_glow");

    public static final ModelLayerLocation GLOW_OUTER = new ModelLayerLocation(MODEL, "outer");
    public static final ModelLayerLocation GLOW_INNER = new ModelLayerLocation(MODEL, "inner");

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GLOW_OUTER, () -> LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(1.15F), 0.0F), 64, 32));
        event.registerLayerDefinition(GLOW_INNER, () -> LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(0.65F), 0.0F), 64, 32));
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        HumanoidModel<AbstractClientPlayer> inner =
                new HumanoidModel<>(event.getEntityModels().bakeLayer(GLOW_INNER));
        HumanoidModel<AbstractClientPlayer> outer =
                new HumanoidModel<>(event.getEntityModels().bakeLayer(GLOW_OUTER));

        for (var skin : event.getSkins()) {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer =
                    event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new ArcenciumArmorLayer(renderer, inner, outer));
            }
        }
    }
}
