package com.emerald.client;

import com.emerald.entity.KGDeathbotEntity;
import com.emerald.entity.WastelanderEntity;
import com.emerald.init.Jak3Registry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = "emeraldweapons", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class Jak3ClientEvents {

    @SubscribeEvent
    public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.emerald.menu.ModMenus.SOCKET_BENCH.get(), SocketBenchScreen::new);
        event.register(com.emerald.menu.ModMenus.ARCENCIUM_FORGE.get(), ArcenciumForgeScreen::new);
        event.register(com.emerald.menu.ModMenus.SPECIALIZATION_ALTAR.get(), SpecializationAltarScreen::new);
        event.register(com.emerald.menu.ModMenus.HAVEN_VOTE.get(), HavenVoteScreen::new);
    }

    /** Les modeles des voitures de Haven, relus a chaque rechargement des ressources. */
    @SubscribeEvent
    public static void registerReloadListeners(
            net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(com.emerald.jak.vehicle.JakVehicleModels.INSTANCE);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Le trait du sceptre n'a pas de modele : sa trainee de particules EST
        // son rendu. NoopRenderer suffit, mais il faut l'enregistrer, sinon le
        // jeu refuse de charger l'entite.
        event.registerEntityRenderer(Jak3Registry.PRISMATIC_BOLT.get(),
                net.minecraft.client.renderer.entity.NoopRenderer::new);

        event.registerBlockEntityRenderer(
                com.emerald.block.entity.ModBlockEntities.ARCENCIUM_CHEST.get(),
                ArcenciumChestRenderer::new);

        event.registerBlockEntityRenderer(
                com.emerald.block.entity.ModBlockEntities.OATH_BLADE.get(),
                OathBladeRenderer::new);

        // le ratelier du QG de Haven : le Morph Gun pose dessus
        event.registerBlockEntityRenderer(
                com.emerald.block.entity.ModBlockEntities.HAVEN_GUN_RACK.get(),
                HavenGunRackRenderer::new);

        // la porte precurseur de la victoire du Defi
        event.registerBlockEntityRenderer(
                com.emerald.block.entity.ModBlockEntities.HAVEN_GATE.get(),
                HavenGateRenderer::new);

        event.registerEntityRenderer(Jak3Registry.ARCENCIUM_BOLT.get(),
                ArcenciumBoltRenderer::new);

        // voitures de Haven : triangles cuits par tools/jak_vehicle.py
        event.registerEntityRenderer(Jak3Registry.JAK_VEHICLE.get(),
                com.emerald.jak.vehicle.JakVehicleRenderer::new);

        event.registerEntityRenderer(Jak3Registry.WASTELANDER.get(),
                ctx -> new MobRenderer<WastelanderEntity, HumanoidModel<WastelanderEntity>>(ctx,
                        new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), 0.5f) {
                    @Override
                    public ResourceLocation getTextureLocation(WastelanderEntity entity) {
                        return ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");
                    }
                });

        event.registerEntityRenderer(Jak3Registry.KG_DEATHBOT.get(),
                ctx -> new MobRenderer<KGDeathbotEntity, HumanoidModel<KGDeathbotEntity>>(ctx,
                        new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), 0.5f) {
                    @Override
                    public ResourceLocation getTextureLocation(KGDeathbotEntity entity) {
                        return ResourceLocation.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");
                    }
                });
    }
}