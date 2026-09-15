package com.emerald.jak.gun;

import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Dessine le Morph Gun cuit par tools/jak_gun.py, pose par GunPose, tenu par GunHold.
 *
 * LE TEMPS. renderByItem ne recoit ni le porteur ni le temps partiel : la
 * transformation se joue sur level.getGameTime() et le temps partiel du timer,
 * compares a la tique du changement portee par le composant. Tous les clients
 * voient donc la meme transformation au meme moment.
 *
 * LE DESSIN, comme les voitures (JakVehicleRenderer) : chaque triangle en quad
 * dont le quatrieme sommet repete le troisieme, entityCutoutNoCull sur l'atlas
 * (les materiaux du glb sont en decoupe et double face) ; les triangles d'os
 * mis a l'echelle sous 0,2 x main sont sautes (GunPose.visible). Chaque sommet
 * suit l'habillage de SON os ; sa normale aussi, renormalisee.
 */
public final class MorphGunItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(
            EmeraldWeaponsMod.MODID, "jak_gun/morph_gun.png");

    @Nullable
    private GunPose pose;
    private final int[] magazines = {-1, -1, -1, -1};
    private int cylinders = -1;
    /** Le cadrage d'inventaire de chaque forme, calcule une fois (GunHold.fit). */
    private final Map<GunForm, Matrix4f> fits = new EnumMap<>(GunForm.class);
    private final Matrix4f hold = new Matrix4f();
    private final double[] point = new double[3];
    private final double[] normal = new double[3];

    public MorphGunItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay) {
        JakGunModel model = JakGunModel.gun();
        if (model == null) {
            return;
        }
        if (this.pose == null) {
            this.pose = new GunPose(model);
            GunForm.Family[] families = GunForm.Family.values();
            for (int i = 0; i < families.length; i++) {
                this.magazines[i] = model.boneIndex(GunPose.MAGAZINES[i]);
            }
            this.cylinders = model.boneIndex("cylinders");
        }
        MorphGunData data = MorphGunData.of(stack);
        GunForm form = data == null ? GunForm.RED_1 : data.form();
        GunForm previous = data == null ? form : data.previous();
        double since = Double.MAX_VALUE;
        double now = 0.0;
        Minecraft mc = Minecraft.getInstance();
        if (data != null && mc.level != null) {
            now = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
            since = now - data.changeTick();
        }
        Matrix4f fit = this.fits.computeIfAbsent(form, f -> GunHold.fit(new GunPose(model).pose(f), new Matrix4f()));
        this.pose.show(previous, form, since);
        if (data != null) {
            // chargeur d'une reserve vide a l'echelle 0 (gun-util.gc:348-423) ; canon de la Vulcan Fury qui tourne
            long hidden = 0L;
            for (GunForm.Family family : GunForm.Family.values()) {
                int bone = this.magazines[family.ordinal()];
                if (data.eco(family) <= 0 && bone >= 0 && bone < 64) {
                    hidden |= 1L << bone;
                }
            }
            double spin = form.family == GunForm.Family.BLUE && mc.level != null
                    ? Math.toRadians(GunSpec.spinAngle(data.triggerStart(), data.triggerEnd(), now) % 360.0) : 0.0;
            this.pose.tweak(hidden, this.cylinders, spin);
        }
        GunHold.hold(context, form, fit, this.hold);

        poseStack.pushPose();
        // ItemRenderer vient de reculer d'un demi-bloc (translate -0,5) apres le display : on l'annule
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(this.hold);
        PoseStack.Pose last = poseStack.last();
        emit(model, last, buffers.getBuffer(RenderType.entityCutoutNoCull(ATLAS)), light, overlay, false);
        if (model.hasBlend) {
            emit(model, last, buffers.getBuffer(RenderType.entityTranslucent(ATLAS)), light, overlay, true);
        }
        poseStack.popPose();
    }

    private void emit(JakGunModel model, PoseStack.Pose last, VertexConsumer out, int light, int overlay,
                      boolean blend) {
        GunPose pose = this.pose;
        double[] p = this.point;
        double[] n = this.normal;
        for (int t = 0; t < model.triangles; t++) {
            if (((model.flags[t] & JakGunModel.FLAG_BLEND) != 0) != blend || !pose.visible(t)) {
                continue;
            }
            for (int corner = 0; corner < 4; corner++) {
                int s = t * 3 + Math.min(corner, 2);   // le 4e sommet repete le 3e
                pose.vertex(s, p);
                pose.normal(s, n);
                out.addVertex(last, (float) p[0], (float) p[1], (float) p[2])
                        .setColor(model.colors[s])
                        .setUv(model.uvs[s * 2], model.uvs[s * 2 + 1])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(last, (float) n[0], (float) n[1], (float) n[2]);
            }
        }
    }
}
