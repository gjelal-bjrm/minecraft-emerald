package com.emerald.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

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
 * piece, jamais par-dessus. C'est le vieux tour des contours de dessin anime,
 * et il ne demande aucun shader.
 *
 * Le pilote de rendu ne connait que « eliminer les faces arriere » ; on
 * inverse le sens dans le creneau de superposition, qui est un simple couple
 * d'actions avant/apres, et on le remet en sortant.
 */
public final class ModRenderTypes extends RenderType {

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                           boolean affectsCrumbling, boolean sortOnUpload, Runnable setup,
                           Runnable clear) {
        super(name, format, mode, size, affectsCrumbling, sortOnUpload, setup, clear);
    }

    /** Ne garder que les faces arriere, le temps du rendu. */
    private static final LayeringStateShard CULL_FRONT = new LayeringStateShard(
            "emeraldweapons_cull_front",
            () -> {
                RenderSystem.assertOnRenderThread();
                GL11.glCullFace(GL11.GL_FRONT);
            },
            () -> {
                RenderSystem.assertOnRenderThread();
                GL11.glCullFace(GL11.GL_BACK);
            });

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
                            .setLayeringState(CULL_FRONT)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    /** Le lisere : translucide, insensible a la lumiere, faces arriere seulement. */
    public static RenderType rim(ResourceLocation texture) {
        return RIM.apply(texture);
    }
}
