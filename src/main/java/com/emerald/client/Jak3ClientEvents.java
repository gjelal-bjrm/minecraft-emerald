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
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
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