package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.specialization.WingSkin;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Le LISERE de chaque apparence d'ailes : blanc sur le bord de sa peinture -- le contour, les
 * trous, les decoupes des plumes --, fondu vers l'interieur sur un quatre-vingt-cinquieme de sa
 * largeur, transparent partout ailleurs.
 *
 * Les reflets d'Arcencium des ailes +20 (cahier §96, WingsLayer) s'y posent, et seulement la. Sur
 * toute l'aile, ils denaturaient ses couleurs et son theme -- le joueur, sur les premieres photos :
 * « ca denature totalement les couleurs des ailes ; mets-le uniquement sur les bordures ». Refaits
 * a chaque rechargement des ressources, d'apres les peintures du moment.
 */
public final class WingMasks implements ResourceManagerReloadListener {

    public static final WingMasks INSTANCE = new WingMasks();
    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);
    private static final Map<WingSkin, ResourceLocation> MASKS = new EnumMap<>(WingSkin.class);
    /** La largeur du lisere : la largeur de la peinture divisee par autant. */
    private static final float RIM_FRACTION = 85.0F;
    /** En dessous, un pixel est hors de la forme (le bord adouci de la peinture compris). */
    private static final int SOLID = 128;

    private WingMasks() {
    }

    /** Le masque d'une apparence, ou null s'il n'a pas pu etre fait. */
    @Nullable
    public static ResourceLocation of(WingSkin skin) {
        return MASKS.get(skin);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        for (WingSkin skin : WingSkin.values()) {
            try (InputStream in = manager.open(skin.texture())) {
                NativeImage image = NativeImage.read(in);
                rim(image);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
                        "wing_mask/" + skin.id());
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
                MASKS.put(skin, id);
            } catch (IOException e) {
                MASKS.remove(skin);
                LOGGER.warn("ailes : pas de masque pour {} ({})", skin.id(), e.toString());
            }
        }
    }

    /**
     * La peinture devient son lisere : pour chaque pixel de la forme, sa distance au plus proche
     * pixel qui n'en est pas (le bord de l'image compris), en chanfrein 3-4 -- deux passes, aller
     * et retour --, puis un blanc qui s'efface avec elle.
     */
    private static void rim(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] alpha = new int[w * h];
        int[] d = new int[w * h];
        int far = Integer.MAX_VALUE / 2;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = image.getPixelRGBA(x, y) >>> 24;
                alpha[y * w + x] = a;
                d[y * w + x] = a < SOLID ? 0 : far;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (d[i] == 0) {
                    continue;
                }
                int best = d[i];
                if (x == 0 || y == 0) {
                    best = Math.min(best, 3);
                }
                if (x > 0) {
                    best = Math.min(best, d[i - 1] + 3);
                }
                if (y > 0) {
                    best = Math.min(best, d[i - w] + 3);
                    if (x > 0) {
                        best = Math.min(best, d[i - w - 1] + 4);
                    }
                    if (x + 1 < w) {
                        best = Math.min(best, d[i - w + 1] + 4);
                    }
                }
                d[i] = best;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                int i = y * w + x;
                if (d[i] == 0) {
                    continue;
                }
                int best = d[i];
                if (x == w - 1 || y == h - 1) {
                    best = Math.min(best, 3);
                }
                if (x + 1 < w) {
                    best = Math.min(best, d[i + 1] + 3);
                }
                if (y + 1 < h) {
                    best = Math.min(best, d[i + w] + 3);
                    if (x + 1 < w) {
                        best = Math.min(best, d[i + w + 1] + 4);
                    }
                    if (x > 0) {
                        best = Math.min(best, d[i + w - 1] + 4);
                    }
                }
                d[i] = best;
            }
        }
        float radius = Math.max(2.0F, w / RIM_FRACTION);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float t = alpha[i] < SOLID ? 0.0F : 1.0F - (d[i] / 3.0F) / radius;
                int a = t <= 0.0F ? 0 : (int) (t * t * (3.0F - 2.0F * t) * alpha[i]);
                image.setPixelRGBA(x, y, (a << 24) | 0x00FFFFFF);
            }
        }
    }
}
