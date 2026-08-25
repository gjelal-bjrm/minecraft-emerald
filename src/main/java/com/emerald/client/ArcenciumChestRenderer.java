package com.emerald.client;

import com.emerald.block.entity.ArcenciumChestBlockEntity;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BrightnessCombiner;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Rendu du Coffre d'Arcencium.
 *
 * Repris de ChestRenderer, a une chose pres : le choix de la texture.
 * Sheets.chooseMaterial ne connait que les coffres vanilla et n'est pas
 * surchargeable, d'ou cette copie -- c'est la seule facon de donner sa propre
 * texture a un coffre sans toucher au code du jeu.
 *
 * Les trois textures sont posees dans assets/emeraldweapons/textures/entity/chest/.
 * Aucun fichier d'atlas n'est necessaire : la source du sheet vanilla balaie le
 * dossier entity/chest de TOUS les namespaces, donc les notres sont ramassees
 * automatiquement.
 */
public class ArcenciumChestRenderer implements BlockEntityRenderer<ArcenciumChestBlockEntity> {

    private static Material material(String name) {
        return new Material(Sheets.CHEST_SHEET, ResourceLocation.fromNamespaceAndPath(
                EmeraldWeaponsMod.MODID, "entity/chest/" + name));
    }

    private static final Material SINGLE = material("arcencium");
    private static final Material LEFT = material("arcencium_left");
    private static final Material RIGHT = material("arcencium_right");

    private final ModelPart lid;
    private final ModelPart bottom;
    private final ModelPart lock;
    private final ModelPart leftLid;
    private final ModelPart leftBottom;
    private final ModelPart leftLock;
    private final ModelPart rightLid;
    private final ModelPart rightBottom;
    private final ModelPart rightLock;

    public ArcenciumChestRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart single = context.bakeLayer(ModelLayers.CHEST);
        this.bottom = single.getChild("bottom");
        this.lid = single.getChild("lid");
        this.lock = single.getChild("lock");

        ModelPart left = context.bakeLayer(ModelLayers.DOUBLE_CHEST_LEFT);
        this.leftBottom = left.getChild("bottom");
        this.leftLid = left.getChild("lid");
        this.leftLock = left.getChild("lock");

        ModelPart right = context.bakeLayer(ModelLayers.DOUBLE_CHEST_RIGHT);
        this.rightBottom = right.getChild("bottom");
        this.rightLid = right.getChild("lid");
        this.rightLock = right.getChild("lock");
    }

    @Override
    public void render(ArcenciumChestBlockEntity chest, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = chest.getLevel();
        boolean placed = level != null;
        BlockState state = placed ? chest.getBlockState()
                : Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        ChestType type = state.hasProperty(ChestBlock.TYPE)
                ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
        if (!(state.getBlock() instanceof AbstractChestBlock<?> chestBlock)) {
            return;
        }

        pose.pushPose();
        float yaw = state.getValue(ChestBlock.FACING).toYRot();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(-0.5F, -0.5F, -0.5F);

        DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> combined =
                placed ? chestBlock.combine(state, level, chest.getBlockPos(), true)
                       : DoubleBlockCombiner.Combiner::acceptNone;

        // courbe d'ouverture vanilla : le couvercle ralentit en fin de course
        float openness = combined.apply(ChestBlock.opennessCombiner(chest)).get(partialTick);
        openness = 1.0F - openness;
        openness = 1.0F - openness * openness * openness;

        int light = combined.apply(new BrightnessCombiner<>()).applyAsInt(packedLight);
        Material material = type == ChestType.SINGLE ? SINGLE
                : (type == ChestType.LEFT ? LEFT : RIGHT);
        VertexConsumer vc = material.buffer(buffer, RenderType::entityCutout);

        switch (type) {
            case LEFT -> renderParts(pose, vc, this.leftLid, this.leftLock, this.leftBottom,
                    openness, light, packedOverlay);
            case RIGHT -> renderParts(pose, vc, this.rightLid, this.rightLock, this.rightBottom,
                    openness, light, packedOverlay);
            default -> renderParts(pose, vc, this.lid, this.lock, this.bottom,
                    openness, light, packedOverlay);
        }
        pose.popPose();
    }

    private void renderParts(PoseStack pose, VertexConsumer vc, ModelPart lid, ModelPart lock,
                             ModelPart bottom, float openness, int light, int overlay) {
        lid.xRot = -(openness * ((float) Math.PI / 2F));
        lock.xRot = lid.xRot;
        lid.render(pose, vc, light, overlay);
        lock.render(pose, vc, light, overlay);
        bottom.render(pose, vc, light, overlay);
    }
}
