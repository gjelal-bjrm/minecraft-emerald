package com.emerald.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Les types de rendu de la maison.
 *
 * LE LISERE. Pour dessiner le CONTOUR d'un corps et rien d'autre, on rend une
 * coque un peu plus grande que lui en ne gardant que ses faces ARRIERE. Les
 * faces arriere sont, par construction, de l'autre cote du corps : partout ou
 * le corps est devant, le test de profondeur les cache ; elles ne survivent
 * qu'au bord de la silhouette, la ou la coque deborde. Ce qui reste a l'ecran
 * est un trait de la largeur du debord -- une ligne de lumiere autour de la
 * piece, jamais par-dessus. C'est le vieux tour des contours de dessin anime.
 *
 * LA PREMIERE TENTATIVE INVERSAIT L'ELIMINATION DANS L'ETAT GL (glCullFace
 * GL_FRONT) : en jeu, la coque s'est dessinee PLEINE -- l'ordre n'a pas
 * survecu au pipeline (Embeddium reprend l'etat a son compte). On ne touche
 * donc plus au pilote : on retourne les faces ELLES-MEMES en inversant l'ordre
 * des sommets de chaque quad ({@link Flipped}). Une face avant retournee est
 * une face arriere ; l'elimination ordinaire fait le reste, quel que soit le
 * moteur de rendu.
 */
public final class ModRenderTypes extends RenderType {

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                           boolean affectsCrumbling, boolean sortOnUpload, Runnable setup,
                           Runnable clear) {
        super(name, format, mode, size, affectsCrumbling, sortOnUpload, setup, clear);
    }

    private static final Function<ResourceLocation, RenderType> RIM = Util.memoize(texture ->
            create("emeraldweapons_rim", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    1536, false, true, CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(CULL)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setLightmapState(NO_LIGHTMAP)
                            .setOverlayState(NO_OVERLAY)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    /** Le lisere : translucide, insensible a la lumiere ; a nourrir par {@link #flipped}. */
    public static RenderType rim(ResourceLocation texture) {
        return RIM.apply(texture);
    }

    /** Le meme tampon, les faces retournees : ce qui etait devant passe derriere. */
    public static VertexConsumer flipped(VertexConsumer inner) {
        return new Flipped(inner);
    }

    /**
     * Retourne chaque quad en emettant ses quatre sommets a l'envers.
     *
     * Le modele ecrit un sommet par appel a addVertex, puis ses attributs
     * (couleur, texture, superposition, lumiere, normale) sur ce sommet
     * courant. On retient les quatre sommets d'un quad et on les rejoue dans
     * l'ordre inverse : le sens de parcours change, la face aussi.
     */
    private static final class Flipped implements VertexConsumer {
        private final VertexConsumer inner;
        private final float[][] pos = new float[4][3];
        private final int[][] colour = new int[4][4];
        private final float[][] uv = new float[4][2];
        private final int[][] overlay = new int[4][2];
        private final int[][] light = new int[4][2];
        private final float[][] normal = new float[4][3];
        private final boolean[] hasColour = new boolean[4];
        private final boolean[] hasUv = new boolean[4];
        private final boolean[] hasOverlay = new boolean[4];
        private final boolean[] hasLight = new boolean[4];
        private final boolean[] hasNormal = new boolean[4];
        private int count = -1;

        private Flipped(VertexConsumer inner) {
            this.inner = inner;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.count++;
            if (this.count == 4) {
                this.flush();
                this.count = 0;
            }
            int i = this.count;
            this.pos[i][0] = x;
            this.pos[i][1] = y;
            this.pos[i][2] = z;
            this.hasColour[i] = this.hasUv[i] = this.hasOverlay[i] = this.hasLight[i]
                    = this.hasNormal[i] = false;
            if (i == 3) {
                // le quad est complet des que son quatrieme sommet a ses attributs :
                // on le rejoue au prochain addVertex, ou a la fin (voir flushIfComplete)
            }
            return this;
        }

        private void flush() {
            for (int i = 3; i >= 0; i--) {
                this.inner.addVertex(this.pos[i][0], this.pos[i][1], this.pos[i][2]);
                if (this.hasColour[i]) {
                    this.inner.setColor(this.colour[i][0], this.colour[i][1],
                            this.colour[i][2], this.colour[i][3]);
                }
                if (this.hasUv[i]) {
                    this.inner.setUv(this.uv[i][0], this.uv[i][1]);
                }
                if (this.hasOverlay[i]) {
                    this.inner.setUv1(this.overlay[i][0], this.overlay[i][1]);
                }
                if (this.hasLight[i]) {
                    this.inner.setUv2(this.light[i][0], this.light[i][1]);
                }
                if (this.hasNormal[i]) {
                    this.inner.setNormal(-this.normal[i][0], -this.normal[i][1], -this.normal[i][2]);
                }
            }
        }

        /** A appeler quand le modele a fini : rejoue le dernier quad retenu. */
        void finish() {
            if (this.count == 3) {
                this.flush();
            }
            this.count = -1;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            int i = this.count;
            this.colour[i][0] = r;
            this.colour[i][1] = g;
            this.colour[i][2] = b;
            this.colour[i][3] = a;
            this.hasColour[i] = true;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            int i = this.count;
            this.uv[i][0] = u;
            this.uv[i][1] = v;
            this.hasUv[i] = true;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            int i = this.count;
            this.overlay[i][0] = u;
            this.overlay[i][1] = v;
            this.hasOverlay[i] = true;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            int i = this.count;
            this.light[i][0] = u;
            this.light[i][1] = v;
            this.hasLight[i] = true;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            int i = this.count;
            this.normal[i][0] = x;
            this.normal[i][1] = y;
            this.normal[i][2] = z;
            this.hasNormal[i] = true;
            return this;
        }
    }

    /** Termine un tampon retourne : rejoue le dernier quad. */
    public static void finish(VertexConsumer flipped) {
        if (flipped instanceof Flipped f) {
            f.finish();
        }
    }
}
