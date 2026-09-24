package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.specialization.WingSkin;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * L'ANIMATION DE CHAQUE APPARENCE D'AILES (cahier §97) : une bande d'images empilees,
 * textures/wings/anim/<apparence>.png, faite par tools/wing_anim.py comme celle de l'epee -- ce qui
 * bouge dans la peinture (ses veines, ses flammes, ses cristaux, ses eclairs, ses etoiles), posee
 * par-dessus la peinture fixe (WingsLayer).
 *
 * Une peinture d'ailes n'est pas dans l'atlas des objets : le jeu ne l'animerait pas de lui-meme.
 * On fait donc ce qu'il fait pour une texture animee fondue : a chaque tique, l'image du moment et
 * la suivante, melangees, dans une texture a part. Le melange tient compte de l'opacite (une
 * etincelle qui s'eteint palit, elle ne noircit pas), et un pixel transparent sort noir.
 *
 * Une bande se charge a sa premiere apparition a l'ecran, et se relit a chaque rechargement des
 * ressources ; une apparence sans bande n'est pas animee.
 */
public final class WingAnims implements ResourceManagerReloadListener {

    public static final WingAnims INSTANCE = new WingAnims();
    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);
    /** Tiques par image : vingt images font une boucle de trois secondes. */
    private static final int FRAME_TICKS = 3;
    private static final Map<WingSkin, Anim> ANIMS = new EnumMap<>(WingSkin.class);
    /** Les apparences sans bande lisible, pour ne pas la rechercher a chaque image. */
    private static final Set<WingSkin> MISSING = EnumSet.noneOf(WingSkin.class);

    /** Une bande chargee : ses images bout a bout (pixels du jeu, ABGR) et la texture du moment. */
    private record Anim(int size, int frames, int[] pixels, ResourceLocation id, DynamicTexture texture,
                        long[] shown) {
    }

    private WingAnims() {
    }

    /** La texture de l'animation a cette tique, ou null si l'apparence n'en a pas. */
    @Nullable
    public static ResourceLocation frame(WingSkin skin, long gameTime) {
        Anim anim = ANIMS.get(skin);
        if (anim == null) {
            if (MISSING.contains(skin)) {
                return null;
            }
            anim = load(skin);
            if (anim == null) {
                MISSING.add(skin);
                return null;
            }
            ANIMS.put(skin, anim);
        }
        if (anim.shown()[0] != gameTime) {
            anim.shown()[0] = gameTime;
            blend(anim, gameTime);
        }
        return anim.id();
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        for (Anim anim : ANIMS.values()) {
            Minecraft.getInstance().getTextureManager().release(anim.id());
        }
        ANIMS.clear();
        MISSING.clear();
    }

    @Nullable
    private static Anim load(WingSkin skin) {
        ResourceLocation strip = ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
                "textures/wings/anim/" + skin.id() + ".png");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(strip);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open();
             NativeImage image = NativeImage.read(NativeImage.Format.RGBA, in)) {
            int size = image.getWidth();
            int frames = size > 0 ? image.getHeight() / size : 0;
            if (frames < 2 || image.getHeight() != frames * size) {
                LOGGER.warn("ailes : la bande de {} n'est pas une pile d'images carrees ({}x{})", skin.id(),
                        image.getWidth(), image.getHeight());
                return null;
            }
            int[] pixels = new int[size * size * frames];
            for (int y = 0; y < size * frames; y++) {
                for (int x = 0; x < size; x++) {
                    pixels[y * size + x] = image.getPixelRGBA(x, y);
                }
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
                    "wing_anim/" + skin.id());
            DynamicTexture texture = new DynamicTexture(size, size, false);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            return new Anim(size, frames, pixels, id, texture, new long[]{Long.MIN_VALUE});
        } catch (IOException e) {
            LOGGER.warn("ailes : pas d'animation pour {} ({})", skin.id(), e.toString());
            return null;
        }
    }

    /**
     * L'image du moment fondue dans la suivante, d'un tiers a chaque tique. L'opacite se melange
     * droite ; la couleur, ponderee par l'opacite de chaque image.
     */
    private static void blend(Anim anim, long gameTime) {
        int area = anim.size() * anim.size();
        int now = (int) Math.floorMod(Math.floorDiv(gameTime, FRAME_TICKS), (long) anim.frames());
        int next = (now + 1) % anim.frames();
        int w = (int) Math.floorMod(gameTime, FRAME_TICKS);
        int[] px = anim.pixels();
        NativeImage out = anim.texture().getPixels();
        if (out == null) {
            return;
        }
        for (int i = 0; i < area; i++) {
            int a = px[now * area + i];
            int b = px[next * area + i];
            int wa = (a >>> 24) * (FRAME_TICKS - w);
            int wb = (b >>> 24) * w;
            int sum = wa + wb;
            int color = 0;
            if (sum > 0) {
                int alpha = sum / FRAME_TICKS;
                color = alpha << 24;
                for (int shift = 0; shift < 24; shift += 8) {
                    int c = (((a >>> shift) & 0xFF) * wa + ((b >>> shift) & 0xFF) * wb) / sum;
                    color |= c << shift;
                }
            }
            out.setPixelRGBA(i % anim.size(), i / anim.size(), color);
        }
        anim.texture().upload();
    }
}
