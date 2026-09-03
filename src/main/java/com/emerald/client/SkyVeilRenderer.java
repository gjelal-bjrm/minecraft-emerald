package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.weather.Weather;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Le voile de ciel : ce qui COUVRE le ciel pendant une meteo.
 *
 * Les brumes des meteos passaient par les evenements de brouillard
 * (ComputeFogColor, RenderFog). Deux limites, vues en jeu : le brouillard
 * vanilla ne touche pas le ciel -- on avait « du brouillard au sol et nulle
 * part ailleurs » --, et un pack de shaders recalcule le brouillard a sa
 * maniere et ignore les notres : sous Complementary, une Pluie de Meteores
 * se jouait sous un beau ciel bleu.
 *
 * On dessine donc le ciel couvert NOUS-MEMES : une coupole autour de la
 * camera, en geometrie ordinaire avec test et ecriture de profondeur, a la
 * FIN du rendu du monde. Ce qui est plus proche que son rayon reste visible ;
 * le ciel et le terrain plus lointains disparaissent derriere elle -- le
 * voile est le mur de brouillard lui-meme, a la distance que chaque meteo
 * choisit (voir WeatherClient.veilFor). Son opacite monte avec l'intensite.
 *
 * Pourquoi a la fin et pas juste apres le ciel : sous Iris, tout ce qui se
 * dessine pendant la phase « ciel » passe par le programme de ciel du pack,
 * qui calcule sa propre couleur et ignore la notre -- la coupole devenait
 * du ciel bleu. Apres la meteo, la phase est retombee, et la coupole est un
 * objet comme un autre, a une distance que le pack embrume a peine.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class SkyVeilRenderer {

    /** Tranches en azimut et en elevation. */
    private static final int AZIMUTHS = 32;
    private static final int BANDS = 10;
    /** La coupole descend sous l'horizon : depuis une hauteur, le ciel se voit aussi en bas. */
    private static final double ELEVATION_MIN = Math.toRadians(-28.0);
    private static final double ELEVATION_MAX = Math.toRadians(90.0);

    private SkyVeilRenderer() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float intensity = WeatherClient.intensity();
        if (intensity <= 0.02F) {
            return;
        }
        Weather w = WeatherClient.current();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float[] veil = WeatherClient.veilFor(w, mc.level.getGameTime() + partial);
        if (veil == null) {
            return;
        }
        float alpha = veil[3] * intensity;
        if (alpha <= 0.01F) {
            return;
        }
        // le rayon est la distance du voile : plus pres que le terrain lointain,
        // qui disparait derriere lui comme dans un brouillard
        double radius = Math.min(veil[5], mc.gameRenderer.getRenderDistance() * 0.9);
        // LA CLARTE DU CIEL. getStarBrightness vaut zero en plein jour et un en
        // pleine nuit : le voile suit, avec un plancher pour qu'il reste lisible.
        // Sans cela, la Brume Prismatique devenait des taches blanches
        // lumineuses la nuit, surtout sous un shader qui assombrit tout le reste.
        float night = mc.level.getStarBrightness(partial);
        float lit = 0.16F + 0.84F * (1.0F - Math.min(1.0F, Math.max(0.0F, night)));
        if (w == Weather.ORAGE) {
            lit = Math.max(lit, WeatherAtmosphere.flash());   // l'eclair allume la nuit aussi
        }
        draw(event.getPoseStack(), veil[0] * lit, veil[1] * lit, veil[2] * lit,
                alpha, veil[4], radius);
    }

    /**
     * La coupole, en bandes d'elevation. L'opacite peut s'amincir vers le
     * zenith (zenith < 1) : une brume laisse passer un peu de lumiere en haut,
     * un ciel de cendres non.
     */
    private static void draw(PoseStack pose, float r, float g, float b, float alpha, float zenith,
                             double radius) {
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4f matrix = pose.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
        for (int band = 0; band < BANDS; band++) {
            double e0 = ELEVATION_MIN + (ELEVATION_MAX - ELEVATION_MIN) * band / BANDS;
            double e1 = ELEVATION_MIN + (ELEVATION_MAX - ELEVATION_MIN) * (band + 1) / BANDS;
            float a0 = alpha * fade(e0, zenith);
            float a1 = alpha * fade(e1, zenith);
            for (int i = 0; i < AZIMUTHS; i++) {
                double az0 = Math.PI * 2.0 * i / AZIMUTHS;
                double az1 = Math.PI * 2.0 * (i + 1) / AZIMUTHS;
                vertex(matrix, vc, e0, az0, radius, r, g, b, a0);
                vertex(matrix, vc, e0, az1, radius, r, g, b, a0);
                vertex(matrix, vc, e1, az1, radius, r, g, b, a1);
                vertex(matrix, vc, e1, az0, radius, r, g, b, a1);
            }
        }
        buffer.endBatch(RenderType.debugQuads());
    }

    private static float fade(double elevation, float zenith) {
        double t = (elevation - ELEVATION_MIN) / (ELEVATION_MAX - ELEVATION_MIN);
        return (float) (1.0 - (1.0 - zenith) * t);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer vc, double elevation, double azimuth,
                               double radius, float r, float g, float b, float a) {
        float x = (float) (Math.cos(elevation) * Math.cos(azimuth) * radius);
        float y = (float) (Math.sin(elevation) * radius);
        float z = (float) (Math.cos(elevation) * Math.sin(azimuth) * radius);
        vc.addVertex(matrix, x, y, z).setColor(r, g, b, a);
    }
}
