package com.emerald.client;

import com.emerald.weather.ArcenciumBoltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/**
 * Dessine un eclair d'Arcencium.
 *
 * C'est la geometrie exacte du LightningBoltRenderer vanilla -- memes zigzags,
 * memes quatre passes, meme scintillement par graine -- avec deux parametres
 * en plus : la couleur, prise sur la variante de l'entite, et la largeur, que
 * la frappe de l'Orage double presque. Le renderer vanilla a sa couleur en
 * dur, c'etait l'unique raison de le copier.
 */
public class ArcenciumBoltRenderer extends EntityRenderer<ArcenciumBoltEntity> {

    public ArcenciumBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ArcenciumBoltEntity entity, float yaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {
        int rgb = entity.variant().color;
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        float width = entity.variant().width;

        float[] xOffsets = new float[8];
        float[] zOffsets = new float[8];
        float x = 0.0F;
        float z = 0.0F;
        RandomSource seedRandom = RandomSource.create(entity.seed);
        for (int i = 7; i >= 0; i--) {
            xOffsets[i] = x;
            zOffsets[i] = z;
            x += seedRandom.nextInt(11) - 5;
            z += seedRandom.nextInt(11) - 5;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();

        // la frappe de l'Orage MONTE DU SOL : elle se revele segment par segment
        // depuis le bas, en deux ticks et demi -- l'inverse d'un eclair qui tombe
        int reveal = 8;
        if (entity.variant() == ArcenciumBoltEntity.Variant.ORAGE) {
            reveal = Math.min(8, (int) Math.ceil((entity.tickCount + partialTick) * 3.2F));
        }

        for (int pass = 0; pass < 4; pass++) {
            RandomSource branchRandom = RandomSource.create(entity.seed);
            for (int branch = 0; branch < 3; branch++) {
                int top = branch > 0 ? 7 - branch : 7;
                int bottom = branch > 0 ? top - 2 : 0;
                float bx = xOffsets[top] - x;
                float bz = zOffsets[top] - z;
                for (int segment = top; segment >= bottom; segment--) {
                    float prevX = bx;
                    float prevZ = bz;
                    if (branch == 0) {
                        bx += branchRandom.nextInt(11) - 5;
                        bz += branchRandom.nextInt(11) - 5;
                    } else {
                        bx += branchRandom.nextInt(31) - 15;
                        bz += branchRandom.nextInt(31) - 15;
                    }
                    if (segment >= reveal) {
                        continue;              // pas encore monte jusque-la
                    }
                    float wTop = (0.1F + pass * 0.2F) * width;
                    if (branch == 0) {
                        wTop *= segment * 0.1F + 1.0F;
                    }
                    float wBottom = (0.1F + pass * 0.2F) * width;
                    if (branch == 0) {
                        wBottom *= (segment - 1.0F) * 0.1F + 1.0F;
                    }
                    quad(matrix, consumer, bx, bz, segment, prevX, prevZ,
                            red, green, blue, wTop, wBottom, false, false, true, false);
                    quad(matrix, consumer, bx, bz, segment, prevX, prevZ,
                            red, green, blue, wTop, wBottom, true, false, true, true);
                    quad(matrix, consumer, bx, bz, segment, prevX, prevZ,
                            red, green, blue, wTop, wBottom, true, true, false, true);
                    quad(matrix, consumer, bx, bz, segment, prevX, prevZ,
                            red, green, blue, wTop, wBottom, false, true, false, false);
                }
            }
        }
    }

    private static void quad(Matrix4f matrix, VertexConsumer consumer, float x, float z,
                             int segment, float prevX, float prevZ,
                             float red, float green, float blue,
                             float wTop, float wBottom,
                             boolean flagA, boolean flagB, boolean flagC, boolean flagD) {
        consumer.addVertex(matrix, x + (flagA ? wBottom : -wBottom), segment * 16,
                        z + (flagB ? wBottom : -wBottom))
                .setColor(red, green, blue, 0.3F);
        consumer.addVertex(matrix, prevX + (flagA ? wTop : -wTop), (segment + 1) * 16,
                        prevZ + (flagB ? wTop : -wTop))
                .setColor(red, green, blue, 0.3F);
        consumer.addVertex(matrix, prevX + (flagC ? wTop : -wTop), (segment + 1) * 16,
                        prevZ + (flagD ? wTop : -wTop))
                .setColor(red, green, blue, 0.3F);
        consumer.addVertex(matrix, x + (flagC ? wBottom : -wBottom), segment * 16,
                        z + (flagD ? wBottom : -wBottom))
                .setColor(red, green, blue, 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(ArcenciumBoltEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
