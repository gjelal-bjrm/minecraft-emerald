package com.emerald.client;

//import com.emerald.client.renderer.CrystalSwordRenderer;
import com.emerald.client.renderer.CrystalSwordRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//import net.neoforged.neoforge.event.TickEvent;
// net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import net.minecraft.world.item.Items;
import com.emerald.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;

public class ClientEvents {
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        ItemStack itemStack = event.getItemStack();
        if (itemStack.is(ModItems.EMERALD_SWORD.get())) {
            System.out.println("Rendering crystal animation");
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource buffer = event.getMultiBufferSource();
            int light = event.getPackedLight();
            float partialTicks = 0.0F;
            ItemDisplayContext displayContext = ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

            CrystalSwordRenderer.render(
                    itemStack,
                    displayContext,
                    poseStack,
                    buffer,
                    light,
                    partialTicks
            );

        }
    }

    private static float globalAngle = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        globalAngle += 1.5f;
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        float angle = globalAngle + partialTicks;

        poseStack.pushPose();

        double x = mc.player.getX() - mc.gameRenderer.getMainCamera().getPosition().x;
        double y = mc.player.getY() - mc.gameRenderer.getMainCamera().getPosition().y + 2.0;
        double z = mc.player.getZ() - mc.gameRenderer.getMainCamera().getPosition().z;

        poseStack.translate(x, y, z);

        for (int i = 0; i < 5; i++) {
            poseStack.pushPose();

            float orbit = angle + i * 72f;
            float radius = 0.5f;
            float px = Mth.cos(orbit * 0.05f) * radius;
            float py = Mth.sin(orbit * 0.1f) * 0.2f;
            float pz = Mth.sin(orbit * 0.05f) * radius;

            poseStack.translate(px, py, pz);
            poseStack.mulPose(Axis.YP.rotationDegrees(orbit * 2));
            poseStack.scale(0.25f, 0.25f, 0.25f);

            ItemRenderer itemRenderer = mc.getItemRenderer();
            itemRenderer.renderStatic(
                    new ItemStack(Items.AMETHYST_SHARD), // Placeholder crystal
                    ItemDisplayContext.FIXED,
                    15728880, 0, poseStack, buffer, mc.level, 0
            );

            poseStack.popPose();
        }

        poseStack.popPose();
    }
}
